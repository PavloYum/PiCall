#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
shared_tools="${PICALL_TOOLS_DIR:-/home/pavel/Documents/Codex/2026-09-07-androidserverbot-https-github-com-pavloyum-androidserverbot/.build-tools}"

export JAVA_HOME="$shared_tools/jdk17"
export ANDROID_HOME="$shared_tools/android-sdk"
export ANDROID_USER_HOME="$shared_tools/android-user"
export GRADLE_USER_HOME="$shared_tools/gradle-user"
export PATH="$JAVA_HOME/bin:$PATH"

for executable in "$JAVA_HOME/bin/java" /usr/bin/qemu-x86_64; do
    if [[ ! -x "$executable" ]]; then
        echo "Missing build tool: $executable" >&2
        exit 1
    fi
done

cd -- "$project_dir"
if [[ $# -eq 0 ]]; then
    set -- :app:assembleDebug
fi

exec ./gradlew \
    --no-daemon \
    --max-workers=2 \
    -Pandroid.aapt2FromMavenOverride="$project_dir/tools/aapt2" \
    "$@"

