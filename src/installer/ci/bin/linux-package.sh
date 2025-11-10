#!/usr/bin/env bash
set -xe

# Simple packaging script for Linux: builds DEB and RPM from a GraalVM native binary
# Usage: ./linux-package.sh [output-dir]

OUTDIR=${1:-.}
APP_NAME=paint
STAGE=$(mktemp -d /tmp/${APP_NAME}-stage-XXXX)

echo "Staging into $STAGE"

# Locate native binary
BIN=target/${APP_NAME}
if [ ! -x "$BIN" ]; then
  if [ -x target/native/${APP_NAME} ]; then
    BIN=target/native/${APP_NAME}
  else
    echo "Native binary not found in target/ or target/native/. Build it first." >&2
    exit 1
  fi
fi

mkdir -p "$STAGE/usr/share/icons/hicolor/256x256/apps" "$STAGE/usr/share/applications" "$STAGE/DEBIAN"

# Copy icon if available
if [ -f src/main/resources/images/app.png ]; then
  cp src/main/resources/images/app.png "$STAGE/usr/share/icons/hicolor/256x256/apps/${APP_NAME}.png"
fi

# Copy extra provided filesystem resources (if any)
if [ -d src/installer/linux/usr/share ]; then
  cp -r src/installer/linux/usr/share/* "$STAGE/usr/share/" 2>/dev/null || true
fi

# Desktop entry
if [ -f src/installer/linux/${APP_NAME}.desktop ]; then
  cp src/installer/linux/${APP_NAME}.desktop "$STAGE/usr/share/applications/"
else
  cat > "$STAGE/usr/share/applications/${APP_NAME}.desktop" <<DESKTOP
[Desktop Entry]
Name=Paint
Comment=Simple Swing paint application
Exec=/opt/paint/paint
Icon=paint
Terminal=false
Type=Application
Categories=Graphics;
DESKTOP
fi

# maintainer scripts
if [ -f src/installer/linux/postinst ]; then
  cp src/installer/linux/postinst "$STAGE/DEBIAN/postinst" && chmod 0755 "$STAGE/DEBIAN/postinst"
fi
if [ -f src/installer/linux/postrm ]; then
  cp src/installer/linux/postrm "$STAGE/DEBIAN/postrm" && chmod 0755 "$STAGE/DEBIAN/postrm"
fi

# NOTE: we intentionally avoid installing anything under /usr/lib to
# prevent system-wide library path changes. All bundled shared objects
# will be installed under /opt/${APP_NAME} alongside the executable.

## Install under /opt to avoid touching system library paths. Place the
## executable and bundled shared objects directly under /opt/paint.
mkdir -p "$STAGE/opt/${APP_NAME}"
# install the executable directly under /opt/paint
cp "$BIN" "$STAGE/opt/${APP_NAME}/${APP_NAME}"
chmod 0755 "$STAGE/opt/${APP_NAME}/${APP_NAME}"

# Copy GraalVM-produced shared objects next to the executable (no lib/)
for so in target/*.so*; do
  if [ -f "$so" ]; then
    cp -a "$so" "$STAGE/opt/${APP_NAME}/" || true
  fi
done
chmod -R a+rX "$STAGE/opt/${APP_NAME}"

# We do not create a wrapper in /usr/bin. The executable is installed at
# /opt/paint/paint and the bundled .so files live alongside it.

# Read version from pom.xml (first <version> occurrence)
VERSION=$(sed -n 's:.*<version>\(.*\)</version>.*:\1:p' pom.xml | head -n1 || true)
if [ -z "$VERSION" ]; then
  VERSION=1.0.0
fi

# Parse installer.properties for a description (prefer unix), convert \n tokens to newlines with leading space
DESCRIPTION_SHORT="Paint - simple Swing paint application"
DESCRIPTION_LONG="A simple Swing paint program with common drawing tools."
if [ -f src/main/resources/installer.properties ]; then
  # try app.description.unix first
  DESC=$(grep -E '^app\.description\.unix=' src/main/resources/installer.properties || true)
  if [ -z "$DESC" ]; then
    DESC=$(grep -E '^app\.description=' src/main/resources/installer.properties || true)
  fi
  if [ -n "$DESC" ]; then
    # strip key= prefix
    DESC_VAL=${DESC#*=}
    # replace literal \n with newline and prefix continuation lines with a space (Debian control format)
    DESCRIPTION_LONG=$(printf '%s' "$DESC_VAL" | sed -e 's/\\n/\n /g')
  fi
fi

cat > "$STAGE/DEBIAN/control" <<EOF
Package: ${APP_NAME}
Version: ${VERSION}
Section: graphics
Priority: optional
Architecture: amd64
Maintainer: Ozkan Pakdil <ozkan.pakdil+paint@google.com>
Description: ${DESCRIPTION_SHORT}
 ${DESCRIPTION_LONG}
EOF

echo "Building deb package..."
mkdir -p "$OUTDIR"
fakeroot dpkg-deb --build "$STAGE" "$OUTDIR/${APP_NAME}_${VERSION}_amd64.deb"

echo "Building rpm package..."
RPMBUILD_DIR=$(mktemp -d)
mkdir -p "$RPMBUILD_DIR"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}
TMP_TAR_DIR=$(mktemp -d)
mkdir -p "$TMP_TAR_DIR/${APP_NAME}-${VERSION}"
cp -a "$STAGE/". "$TMP_TAR_DIR/${APP_NAME}-${VERSION}/"
tar -C "$TMP_TAR_DIR" -czf "$RPMBUILD_DIR/SOURCES/${APP_NAME}-${VERSION}.tar.gz" "${APP_NAME}-${VERSION}"
rm -rf "$TMP_TAR_DIR"

cat > "$RPMBUILD_DIR/SPECS/${APP_NAME}.spec" <<SPEC
Name: ${APP_NAME}
Version: ${VERSION}
Release: 1
Summary: Simple Swing paint application
License: ASL
Group: Applications/Graphics
BuildArch: x86_64

%description
Simple Swing paint program.

Source0: %{name}-%{version}.tar.gz

%prep
# We don't rely on %setup here; extract the tarball in %install to avoid
# issues with differing rpmbuild behavior inside containers.

%install
# extract the packaged tarball into the build area and then copy files
tar -xzf %{_sourcedir}/%{name}-%{version}.tar.gz -C .
mkdir -p %{buildroot}/opt/${APP_NAME}
cp -a %{name}-%{version}/opt/${name}/* %{buildroot}/opt/${name}/ || true
mkdir -p %{buildroot}/usr/share
cp -a %{name}-%{version}/usr/share/* %{buildroot}/usr/share/ || true

%files
/opt/${name}/*
/usr/share/applications/*
/usr/share/icons/hicolor/256x256/apps/*

%changelog
* $(date '+%a %b %d %Y') Packager <packager@example.com> - ${VERSION}-1
- initial
SPEC


# Prepare the rpmbuild tree under OUTDIR/rpmbuild for containerized build
# We'll use the temporary $RPMBUILD_DIR as the rpmbuild/SOURCES tree. To
# keep the OUTDIR clean we won't copy the whole rpmbuild tree into OUTDIR;
# instead we'll mount the temp directory into the container at the
# expected path (/work/<outdir>/rpmbuild).
REL_OUTDIR_SPEC=$(realpath --relative-to="$PWD" "$OUTDIR" 2>/dev/null || printf '%s' "$OUTDIR")

# Write the SPEC into the temporary rpmbuild tree so the container can use
# it when we mount the temp tree into the container.
cat > "$RPMBUILD_DIR/SPECS/${APP_NAME}.spec" <<SPEC
Name: ${APP_NAME}
Version: ${VERSION}
Release: 1
Summary: Simple Swing paint application
License: ASL
Group: Applications/Graphics
BuildArch: x86_64

%description
Simple Swing paint program.

Source0: %{name}-%{version}.tar.gz

%prep
# We don't rely on %setup here; extract the tarball in %install to avoid
# issues with differing rpmbuild behavior inside containers.

%install
# extract the packaged tarball into the build area and then copy files
# Use an absolute path into the mounted /work directory so extraction works
# reliably inside the container.
tar -xzf /work/${REL_OUTDIR_SPEC}/rpmbuild/SOURCES/${APP_NAME}-${VERSION}.tar.gz -C .
mkdir -p %{buildroot}/opt/${name}
cp -a %{name}-%{version}/opt/${name}/* %{buildroot}/opt/${name}/ || true
mkdir -p %{buildroot}/usr/share
cp -a %{name}-%{version}/usr/share/* %{buildroot}/usr/share/ || true

%files
/opt/%{name}/*
/usr/share/applications/*
/usr/share/icons/hicolor/256x256/apps/*

%changelog
* $(date '+%a %b %d %Y') Packager <packager@example.com> - ${VERSION}-1
- initial
SPEC

# We'll run the Fedora container with Docker. Assume `docker` is available on PATH
# (the caller/CI is responsible for ensuring Docker is present).
CONTAINER_CMD=docker

# Create a small helper script inside the outdir to run inside the container
REL_OUTDIR=$(realpath --relative-to="$PWD" "$OUTDIR" 2>/dev/null || printf '%s' "$OUTDIR")
CONTAINER_SCRIPT="$OUTDIR/docker-rpm-build.sh"
# Create simple fpm hook scripts on the host so we can pass them into fpm
AFTER_INSTALL_SH="$OUTDIR/${APP_NAME}-fpm-after-install.sh"
BEFORE_REMOVE_SH="$OUTDIR/${APP_NAME}-fpm-before-remove.sh"
cat > "$AFTER_INSTALL_SH" <<'SH'
#!/bin/sh
set -e
# no-op hook; we don't modify system ld paths when installing to /opt
exit 0
SH
chmod +x "$AFTER_INSTALL_SH"
cat > "$BEFORE_REMOVE_SH" <<'SH'
#!/bin/sh
set -e
# no-op hook; we don't modify system ld paths when installing to /opt
exit 0
SH
chmod +x "$BEFORE_REMOVE_SH"
cat > "$CONTAINER_SCRIPT" <<'SH'
#!/bin/sh
set -e
# Install fpm and build RPM from the staged directory. fpm is easier to use
# across distributions and avoids rpmbuild layout issues.
dnf install -y ruby rubygems rpm-build make gcc rpmdevtools >/dev/null 2>&1 || true
gem install --no-document fpm >/dev/null 2>&1 || true

# Extract sources if needed then run fpm from the extracted directory
EXTRACT_DIR="/work/REPLACE_OUTDIR/rpmbuild/SOURCES/REPLACE_SOURCE_DIR"
if [ -f "/work/REPLACE_OUTDIR/rpmbuild/SOURCES/REPLACE_SOURCE_ARCHIVE" ]; then
  mkdir -p "$EXTRACT_DIR"
  tar -xzf "/work/REPLACE_OUTDIR/rpmbuild/SOURCES/REPLACE_SOURCE_ARCHIVE" -C "/work/REPLACE_OUTDIR/rpmbuild/SOURCES/"
fi

cd "$EXTRACT_DIR"
# Build RPM with fpm; output into the OUTDIR mounted at /work/REPLACE_OUTDIR
fpm -s dir -t rpm -n REPLACE_NAME -v REPLACE_VERSION --architecture x86_64 --license ASL \
  --description "Simple Swing paint program." --url "https://example.org" \
  --after-install /work/REPLACE_OUTDIR/REPLACE_AFTER --before-remove /work/REPLACE_OUTDIR/REPLACE_BEFORE \
  -C "$EXTRACT_DIR" -p "/work/REPLACE_OUTDIR/REPLACE_NAME-REPLACE_VERSION.rpm" .
SH

# substitute placeholders in the container script
sed -i "s|REPLACE_OUTDIR|$REL_OUTDIR|g" "$CONTAINER_SCRIPT"
sed -i "s|REPLACE_SOURCE_ARCHIVE|${APP_NAME}-${VERSION}.tar.gz|g" "$CONTAINER_SCRIPT"
sed -i "s|REPLACE_SOURCE_DIR|${APP_NAME}-${VERSION}|g" "$CONTAINER_SCRIPT"
sed -i "s|REPLACE_NAME|${APP_NAME}|g" "$CONTAINER_SCRIPT"
sed -i "s|REPLACE_VERSION|${VERSION}|g" "$CONTAINER_SCRIPT"
sed -i "s|REPLACE_AFTER|$(basename "$AFTER_INSTALL_SH")|g" "$CONTAINER_SCRIPT"
sed -i "s|REPLACE_BEFORE|$(basename "$BEFORE_REMOVE_SH")|g" "$CONTAINER_SCRIPT"
chmod +x "$CONTAINER_SCRIPT"

echo "Running container ($CONTAINER_CMD) to produce RPMs..."
# Mount the temporary rpmbuild tree into the container at /work/<outdir>/rpmbuild
${CONTAINER_CMD} run --rm -v "$PWD":/work -v "$RPMBUILD_DIR":/work/${REL_OUTDIR}/rpmbuild -w /work registry.fedoraproject.org/fedora:latest /work/$REL_OUTDIR/docker-rpm-build.sh


# Copy any produced RPMs into OUTDIR (either produced by fpm at OUTDIR or
# from rpmbuild/RPMS if present).
echo "Collecting RPMs into $OUTDIR"
mkdir -p "$OUTDIR"
# Check for RPMs produced at the top-level outdir by fpm; if none, copy from rpmbuild/RPMS
shopt -s nullglob || true
RPM_FOUND=0
for r in "$OUTDIR"/*.rpm; do
  [ -e "$r" ] || continue
  echo "Found RPM: $r"
  RPM_FOUND=1
done
if [ "$RPM_FOUND" -eq 0 ]; then
  find "$OUTDIR/rpmbuild/RPMS" -type f -name "*.rpm" -exec cp -v {} "$OUTDIR/" \; || true
fi

RC=0
if ls "$OUTDIR"/*.rpm >/dev/null 2>&1; then
  echo "RPM(s) created:"; ls -la "$OUTDIR"/*.rpm
else
  echo "No RPMs were produced." >&2
  RC=2
fi

echo "Artifacts:"; ls -la "$OUTDIR" || true

# Cleanup helper artifacts we created inside OUTDIR (leave only .deb/.rpm)
# They are useful for debugging during packaging runs but usually not
# desired as final artifacts. Remove them when packaging completed.
rm -f "$OUTDIR/docker-rpm-build.sh" || true
rm -f "$AFTER_INSTALL_SH" "$BEFORE_REMOVE_SH" || true
rm -rf "$OUTDIR/rpmbuild" || true

exit $RC
