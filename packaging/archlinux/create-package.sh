#!/usr/bin/env bash
set -euo pipefail

app_image_dir="${1:?app image directory is required}"
output_dir="${2:?output directory is required}"
version="${3:-0.1.0}"
pkgrel="${4:-1}"

pkgname="focuseed"
arch="x86_64"
workdir="$(mktemp -d)"
pkgroot="$workdir/pkgroot"
builddate="${SOURCE_DATE_EPOCH:-$(date -u +%s)}"

mkdir -p "$pkgroot/opt/focuseed" "$pkgroot/usr/bin" "$pkgroot/usr/share/applications" "$output_dir"
cp -R "$app_image_dir"/. "$pkgroot/opt/focuseed/"

cat > "$pkgroot/usr/bin/focuseed" <<'EOF'
#!/usr/bin/env sh
exec /opt/focuseed/bin/focuseed "$@"
EOF
chmod 755 "$pkgroot/usr/bin/focuseed"

cat > "$pkgroot/usr/share/applications/focuseed.desktop" <<'EOF'
[Desktop Entry]
Type=Application
Name=FocuSeed
Comment=Strict Pomodoro focus timer
Exec=focuseed
Terminal=false
Categories=Utility;Office;
EOF

size="$(du -sb "$pkgroot" | awk '{print $1}')"
cat > "$pkgroot/.PKGINFO" <<EOF
pkgname = $pkgname
pkgbase = $pkgname
pkgver = $version-$pkgrel
pkgdesc = Strict Pomodoro focus timer
url = https://github.com/Dai2010/FocuSeed
builddate = $builddate
packager = GitHub Actions <actions@github.com>
size = $size
arch = $arch
license = GPL-3.0-only
provides = $pkgname
depend = java-runtime>=17
EOF

tar --sort=name \
    --mtime="@$builddate" \
    --owner=0 \
    --group=0 \
    --numeric-owner \
    -C "$pkgroot" \
    -I 'zstd -19 -T0' \
    -cf "$output_dir/${pkgname}-${version}-${pkgrel}-${arch}.pkg.tar.zst" \
    .
