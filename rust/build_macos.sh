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

# 3. Signing (if enabled)
if [ "$SIGN_ENABLED" = true ]; then
    echo -e "${BLUE}Step 3: Signing the .app bundle...${NC}"
    if [ ! -f "entitlements.plist" ]; then
        echo -e "${RED}Error: entitlements.plist not found in current directory.${NC}"
        exit 1
    fi

    # Sign the app bundle
    echo -e "${BLUE}Step 3: Signing the .app bundle (Bottom-Up Approach)...${NC}"
    
    # 1. Find and sign all nested binaries, dylibs, and frameworks
    echo -e "${BLUE}Signing nested components...${NC}"
    find "$APP_PATH" -type f \( -name "*.dylib" -o -name "*.so" -o -name "executable" -o -perm +111 \) ! -name "rust-paint" | while read -r component; do
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
    
    VERSION=$(grep '^version =' Cargo.toml | head -n 1 | cut -d '"' -f 2)
    ARCH=$(uname -m)
    DMG_NAME="RustPaint_${VERSION}_${ARCH}.dmg"
    DMG_PATH="target/release/$DMG_NAME"
    
    echo -e "${BLUE}Creating DMG manually to preserve signatures: $DMG_PATH${NC}"
    
    # Create a temporary folder for DMG staging
    DMG_STAGING="target/release/dmg_staging"
    rm -rf "$DMG_STAGING"
    mkdir -p "$DMG_STAGING"
    
    # Copy the SIGNED app to staging
    cp -R "$APP_PATH" "$DMG_STAGING/"
    
    # Create a symlink to Applications
    ln -s /Applications "$DMG_STAGING/Applications"
    
    # Create the DMG
    hdiutil create -volname "RustPaint" -srcfolder "$DMG_STAGING" -ov -format UDZO "$DMG_PATH"
    
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
    
    echo -e "${BLUE}Step 6: Stapling the ticket...${NC}"
    xcrun stapler staple "$DMG_PATH"
    echo -e "${GREEN}Notarization and stapling complete!${NC}"
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
    VERSION=$(grep '^version =' Cargo.toml | head -n 1 | cut -d '"' -f 2)
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
