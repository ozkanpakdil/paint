#!/usr/bin/env bash

# macOS Build, Sign and Notarize Script for Rust Paint
# This script automates the process described in MACOS_SIGNING.md

set -e
set -o pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Starting macOS Build and Signing Process...${NC}"

# Detect Version and Architecture early
VERSION=$(grep -m 1 '^version =' Cargo.toml | cut -d '"' -f 2)
ARCH=$(uname -m)
echo -e "${BLUE}Detected Version: $VERSION, Architecture: $ARCH${NC}"

# Check for required environment variables if signing is requested
RELEASE_ENABLED=false
if [[ "$1" == "--sign" || "$2" == "--sign" ]]; then
    SIGN_ENABLED=true
    
    # Auto-detect SIGNING_IDENTITY if not provided
    if [[ -z "$SIGNING_IDENTITY" ]]; then
        echo -e "${BLUE}Attempting to auto-detect SIGNING_IDENTITY...${NC}"
        # Find valid Developer ID Application identities
        IDENTITIES=$(security find-identity -v -p codesigning | grep "Developer ID Application" || true)
        COUNT=$(echo "$IDENTITIES" | grep -c "Developer ID Application" || true)
        
        if [ "$COUNT" -eq 1 ]; then
            # Extract the ID (the alphanumeric string before the quoted name)
            SIGNING_IDENTITY=$(echo "$IDENTITIES" | awk '{print $2}')
            echo -e "${GREEN}Auto-detected SIGNING_IDENTITY: $SIGNING_IDENTITY${NC}"
        elif [ "$COUNT" -gt 1 ]; then
            echo -e "${RED}Error: Multiple 'Developer ID Application' identities found.${NC}"
            echo "$IDENTITIES"
            echo "Please set SIGNING_IDENTITY manually."
            exit 1
        else
            echo -e "${RED}Error: No 'Developer ID Application' identity found in Keychain.${NC}"
            echo "Please ensure your certificate is installed or set SIGNING_IDENTITY manually."
            exit 1
        fi
    fi

    if [[ -z "$APPLE_ID" || -z "$APPLE_PASSWORD" || -z "$TEAM_ID" ]]; then
        echo -e "${RED}Error: Notarization environment variables are missing.${NC}"
        echo "Please set APPLE_ID, APPLE_PASSWORD, and TEAM_ID for notarization."
        exit 1
    fi
else
    SIGN_ENABLED=false
    echo -e "${BLUE}Signing skipped. Run with --sign to enable signing and notarization.${NC}"
fi

if [[ "$1" == "--release" || "$2" == "--release" ]]; then
    RELEASE_ENABLED=true
    if ! command -v gh &> /dev/null; then
        echo -e "${RED}Error: GitHub CLI (gh) is not installed. Please install it to use --release.${NC}"
        exit 1
    fi
fi

# Function to safely unmount
unmount_volume() {
    local vol="/Volumes/RustPaint"
    if [ -d "$vol" ]; then
        echo -e "${BLUE}Attempting to unmount existing $vol volume...${NC}"
        # Try a few times to unmount
        for i in {1..3}; do
            if hdiutil detach "$vol" -force 2>/dev/null; then
                echo -e "${GREEN}Successfully unmounted $vol.${NC}"
                return 0
            fi
            echo -e "${BLUE}Retry unmounting $vol ($i/3)...${NC}"
            sleep 2
        done
        
        # If still mounted, try finding the disk and detaching that
        local disk=$(hdiutil info | grep -B 10 "/Volumes/RustPaint" | grep "/dev/disk" | awk '{print $1}' | head -n 1)
        if [[ -n "$disk" ]]; then
            echo -e "${BLUE}Forcing detach of disk $disk...${NC}"
            hdiutil detach "$disk" -force || true
        fi
    fi
}

# 1. Build the release binary
echo -e "${BLUE}Step 1: Building release binary...${NC}"
cargo build --release

# 2. Package into .app and .dmg
echo -e "${BLUE}Step 2: Packaging into .app...${NC}"
# We first generate the .app so we can sign it
cargo packager --release --formats app

# Find the generated app
APP_PATH=$(find target/release -name "RustPaint.app" -type d -maxdepth 1)

if [[ -z "$APP_PATH" ]]; then
    echo -e "${RED}Error: Could not find RustPaint.app in target/release${NC}"
    exit 1
fi

echo -e "${GREEN}Found App: $APP_PATH${NC}"

# 2.5 Bundle dependencies (Dylibs)
echo -e "${BLUE}Step 2.5: Bundling Homebrew dependencies...${NC}"
FRAMEWORKS_DIR="$APP_PATH/Contents/Frameworks"
mkdir -p "$FRAMEWORKS_DIR"

# Function to bundle dylibs recursively
bundle_dylib() {
    local target="$1"
    # Get list of non-system dylibs (Homebrew or /usr/local installs)
    local deps=$(otool -L "$target" | egrep -E "/opt/homebrew|/usr/local" | awk '{print $1}')
    
    for dep in $deps; do
        local filename=$(basename "$dep")
        local dest="$FRAMEWORKS_DIR/$filename"
        
        if [ ! -f "$dest" ]; then
            echo -e "${BLUE}  Bundling $filename...${NC}"
            cp "$dep" "$dest"
            chmod 755 "$dest"
            # Change the ID of the dylib to use @rpath so it's relocatable
            install_name_tool -id "@rpath/$filename" "$dest"
            # Recursively bundle this dylib's dependencies BEFORE relinking it
            bundle_dylib "$dest"
        fi
        
        # Update the target (could be the binary OR another dylib) to point to the bundled version
        echo -e "${BLUE}  Relinking $filename in $(basename "$target")...${NC}"
        install_name_tool -change "$dep" "@rpath/$filename" "$target"
    done
}

BINARY_PATH="$APP_PATH/Contents/MacOS/rust-paint"

echo -e "${BLUE}Setting RPATH for binary...${NC}"
# Remove existing /opt/homebrew rpaths to prevent the binary from looking there
# We use a loop to catch all of them
for rpath in $(otool -l "$BINARY_PATH" | grep LC_RPATH -A2 | grep path | awk '{print $2}'); do
    install_name_tool -delete_rpath "$rpath" "$BINARY_PATH" 2>/dev/null || true
done
install_name_tool -add_rpath "@executable_path/../Frameworks" "$BINARY_PATH" 2>/dev/null || true

# Explicitly relink GTK in the main binary if it points to Homebrew/Local
for gtkpath in "/opt/homebrew/opt/gtk4/lib/libgtk-4.1.dylib" "/usr/local/opt/gtk4/lib/libgtk-4.1.dylib"; do
    if otool -L "$BINARY_PATH" | grep -q "$gtkpath"; then
        echo -e "${BLUE}Relinking main binary GTK path: $gtkpath -> @rpath/libgtk-4.1.dylib${NC}"
        install_name_tool -change "$gtkpath" "@rpath/libgtk-4.1.dylib" "$BINARY_PATH" || true
    fi
done

# Start the recursive bundling
bundle_dylib "$BINARY_PATH"

# SECOND PASS: Ensure all bundled dylibs also point to each other via @rpath
# Sometimes the recursive call misses cross-dependencies between already bundled libs
echo -e "${BLUE}Performing second pass relinking on all Frameworks...${NC}"
find "$FRAMEWORKS_DIR" -name "*.dylib" | while read -r lib; do
    deps=$(otool -L "$lib" | egrep -E "/opt/homebrew|/usr/local" | awk '{print $1}')
    for dep in $deps; do
        filename=$(basename "$dep")
        install_name_tool -change "$dep" "@rpath/$filename" "$lib"
    done
done

# FINAL VERIFICATION: Check Mach-O load commands for forbidden absolute paths
echo -e "${BLUE}Verifying library paths in binary and frameworks...${NC}"
VIOLATIONS=0
while IFS= read -r file; do
    if file "$file" | grep -q "Mach-O"; then
        if otool -L "$file" | egrep -q "/opt/homebrew|/usr/local"; then
            echo -e "${RED}  Found external reference in: $file${NC}"
            otool -L "$file" | egrep "/opt/homebrew|/usr/local" | sed 's/^/    /'
            VIOLATIONS=1
        fi
    fi
done < <(find "$APP_PATH/Contents" -type f)

if [ "$VIOLATIONS" -eq 0 ]; then
    echo -e "${GREEN}Success: No Homebrew or /usr/local references remain in Mach-O load commands.${NC}"
else
    echo -e "${RED}Warning: Some Mach-O files still reference Homebrew or /usr/local paths. The app may not be fully portable.${NC}"
fi

# 3. Signing (if enabled)
if [ "$SIGN_ENABLED" = true ]; then
    # Sign the app bundle
    echo -e "${BLUE}Step 3: Signing the .app bundle (Bottom-Up Approach)...${NC}"
    
    # 1. Find and sign all nested binaries, dylibs, and frameworks
    echo -e "${BLUE}Signing nested components and bundled dylibs...${NC}"
    find "$APP_PATH" -type f \( -name "*.dylib" -o -name "*.so" -o -name "executable" -o -perm +111 \) ! -path "*/MacOS/rust-paint" | while read -r component; do
        if file "$component" | grep -q "Mach-O"; then
            echo -e "${BLUE}Signing component: $component${NC}"
            codesign --force --options runtime --timestamp --sign "$SIGNING_IDENTITY" "$component"
        fi
    done

    # 2. Sign the main binary with entitlements
    BINARY_PATH="$APP_PATH/Contents/MacOS/rust-paint"
    if [ -f "$BINARY_PATH" ]; then
        echo -e "${BLUE}Signing main binary with entitlements: $BINARY_PATH${NC}"
        codesign --force --options runtime --timestamp --entitlements entitlements.plist --sign "$SIGNING_IDENTITY" "$BINARY_PATH"
    fi

    # 3. Sign the whole bundle
    echo -e "${BLUE}Signing the .app bundle...${NC}"
    codesign --force --options runtime --timestamp --entitlements entitlements.plist --sign "$SIGNING_IDENTITY" "$APP_PATH"
    
    # Verify the signature before DMG creation
    echo -e "${BLUE}Verifying .app signature...${NC}"
    codesign -vvv --display "$APP_PATH"
    codesign -vvvv --verify "$APP_PATH"
    
    echo -e "${BLUE}Checking Gatekeeper assessment (spctl)...${NC}"
    spctl --assess --verbose --type execute "$APP_PATH" || {
        echo -e "${RED}Gatekeeper assessment failed.${NC}"
        # We don't exit here because unnotarized apps are always rejected by spctl
        # but it gives us good debug info.
    }
    
    echo -e "${GREEN}App signed and verified locally.${NC}"

    echo -e "${BLUE}Step 4: Creating the DMG with signed app...${NC}"
    
    # Ensure any previous volume is unmounted to avoid "Resource busy" errors
    unmount_volume

    # 100% RELIABLE DMG CREATION
    # cargo packager is stripping signatures because it re-copies the binary.
    # We will use hdiutil to create the DMG directly from our signed .app.
    
    DMG_NAME="RustPaint_${VERSION}_${ARCH}.dmg"
    DMG_PATH="target/release/$DMG_NAME"
    
    # Use a unique volume name to avoid "Operation not permitted" which often 
    # happens if a previous mount of the same name is "stuck" in the kernel.
    VOL_NAME="RustPaint_$(date +%s)"
    
    echo -e "${BLUE}Creating DMG manually to preserve signatures: $DMG_PATH${NC}"
    
    # Create a temporary folder for DMG staging OUTSIDE the project directory
    # This helps bypass sandbox/permission issues with the project folder
    DMG_STAGING=$(mktemp -d /tmp/rust-paint-dmg-XXXXXX)
    ABS_DMG_PATH=$(pwd)/$DMG_PATH
    echo -e "${BLUE}Using staging directory: $DMG_STAGING${NC}"
    
    # Remove any quarantine or problematic attributes from the app
    xattr -cr "$APP_PATH"
    
    # Copy the SIGNED app to staging
    cp -R "$APP_PATH" "$DMG_STAGING/"
    
    # Ensure permissions are correct in staging
    chmod -R 755 "$DMG_STAGING"
    
    # Create a symlink to Applications
    # We use /Applications as it is safer and standard. 
    # The previous ~/Applications suggestion caused the zip tool to follow the link 
    # into your personal apps!
    ln -s /Applications "$DMG_STAGING/Applications"
    
    # Create the DMG
    echo -e "${BLUE}Running hdiutil with volume name $VOL_NAME...${NC}"
    if hdiutil create -volname "$VOL_NAME" -srcfolder "$DMG_STAGING" -ov -format UDZO "$DMG_PATH"; then
        echo -e "${GREEN}DMG created successfully with hdiutil.${NC}"
    else
        echo -e "${RED}hdiutil failed even with a unique volume name.${NC}"
        echo -e "${BLUE}Attempting one last fallback: Creating a ZIP archive instead of a DMG...${NC}"
        ZIP_NAME="RustPaint_${VERSION}_${ARCH}.zip"
        ZIP_PATH="target/release/$ZIP_NAME"
        ABS_ZIP_PATH=$(pwd)/$ZIP_PATH
        
        # Clean up existing zip if it exists
        rm -f "$ABS_ZIP_PATH"
        
        # Zip from inside the staging directory
        # IMPORTANT: Use -y to store symlinks as links, NOT follow them!
        # This prevents zipping up your entire Applications folder.
        (cd "$DMG_STAGING" && zip -ry "$ABS_ZIP_PATH" .)
        
        if [ -f "$ABS_ZIP_PATH" ]; then
            echo -e "${GREEN}ZIP created at $ZIP_PATH as a fallback.${NC}"
            DMG_PATH="$ZIP_PATH"
        else
            echo -e "${RED}Failed to create ZIP fallback.${NC}"
            exit 1
        fi
    fi
    
    # Cleanup staging
    rm -rf "$DMG_STAGING"

    if [[ -f "$DMG_PATH" ]]; then
        echo -e "${BLUE}Signing the DMG...${NC}"
        codesign --force --timestamp --sign "$SIGNING_IDENTITY" "$DMG_PATH"
        echo -e "${GREEN}DMG created and signed successfully.${NC}"
    else
        echo -e "${RED}Error: Failed to create DMG.${NC}"
        exit 1
    fi

    # 4. Notarization
    echo -e "${BLUE}Step 5: Submitting for notarization...${NC}"
    # We capture the output to check for failure and potentially show logs
    NOTARY_OUTPUT=$(xcrun notarytool submit "$DMG_PATH" \
      --apple-id "$APPLE_ID" \
      --password "$APPLE_PASSWORD" \
      --team-id "$TEAM_ID" \
      --wait 2>&1)
    
    echo "$NOTARY_OUTPUT"
    
    if echo "$NOTARY_OUTPUT" | grep -q "status: Invalid"; then
        SUBMISSION_ID=$(echo "$NOTARY_OUTPUT" | grep "id:" | head -n 1 | awk '{print $2}')
        echo -e "${RED}Error: Notarization failed (Status: Invalid).${NC}"
        echo -e "${BLUE}Fetching notarization log for ID: $SUBMISSION_ID...${NC}"
        xcrun notarytool log "$SUBMISSION_ID" \
          --apple-id "$APPLE_ID" \
          --password "$APPLE_PASSWORD" \
          --team-id "$TEAM_ID"
        exit 1
    fi
    
    # Stapling only works for DMG, not for ZIP
    if [[ "$DMG_PATH" == *.dmg ]]; then
        echo -e "${BLUE}Step 6: Stapling the ticket...${NC}"
        xcrun stapler staple "$DMG_PATH"
        echo -e "${GREEN}Notarization and stapling complete!${NC}"
    else
        echo -e "${BLUE}Step 6: Skipping stapling (not supported for ZIP files).${NC}"
        echo -e "${GREEN}Notarization complete! macOS will verify the ZIP online during first launch.${NC}"
    fi
else
    echo -e "${BLUE}Step 4: Creating the DMG (unsigned)...${NC}"

    # Ensure any previous volume is unmounted to avoid "Resource busy" errors
    unmount_volume

    cargo packager --release --formats dmg
    DMG_PATH=$(find target/release -name "*.dmg" -maxdepth 1 | head -n 1)
fi

# 5. GitHub Release (if enabled)
if [ "$RELEASE_ENABLED" = true ]; then
    echo -e "${BLUE}Step 7: Creating GitHub Release...${NC}"
    TAG="v$VERSION-rust"
    
    if [[ -z "$DMG_PATH" ]]; then
        echo -e "${RED}Error: No DMG found to release.${NC}"
        exit 1
    fi

    echo -e "${BLUE}Releasing $DMG_PATH with tag $TAG...${NC}"
    
    # Check if tag already exists, if so, we might want to upload to existing release
    if gh release view "$TAG" &>/dev/null; then
        echo -e "${BLUE}Release $TAG already exists. Uploading asset...${NC}"
        gh release upload "$TAG" "$DMG_PATH" --clobber
    else
        echo -e "${BLUE}Creating new release $TAG...${NC}"
        gh release create "$TAG" "$DMG_PATH" --title "Rust Paint $VERSION" --notes "Automated release of Rust Paint for macOS."
    fi
    echo -e "${GREEN}GitHub Release complete!${NC}"
fi

echo -e "${GREEN}Build process finished!${NC}"
if [[ -n "$DMG_PATH" ]]; then
    echo -e "${GREEN}Final installer: $DMG_PATH${NC}"
fi
