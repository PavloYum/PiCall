#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
build_file="$project_dir/app/build.gradle.kts"
source_apk="$project_dir/app/build/outputs/apk/debug/app-debug.apk"

version="$(sed -n 's/^[[:space:]]*versionName = "\([^"]*\)"/\1/p' "$build_file")"
if [[ -z "$version" ]]; then
    echo "Cannot read versionName from $build_file" >&2
    exit 1
fi

destination_dir="$project_dir/apk/$version"
destination_apk="$destination_dir/PiCall-$version-debug.apk"

if [[ -e "$destination_dir" ]]; then
    echo "Version $version is already archived: $destination_dir" >&2
    exit 1
fi
if [[ ! -f "$source_apk" ]]; then
    echo "APK is missing; run ./tools/build-local.sh first" >&2
    exit 1
fi

mkdir -p "$destination_dir"
cp "$source_apk" "$destination_apk"
(
    cd -- "$destination_dir"
    sha256sum "$(basename -- "$destination_apk")" > SHA256SUMS
)

echo "Archived PiCall $version in $destination_dir"
