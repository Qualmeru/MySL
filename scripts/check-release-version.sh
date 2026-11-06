#!/usr/bin/env bash
# Usage: check-release-version.sh <tag>
# Fails unless the app being released says the same version as its tag ("1.3.0" or "v1.3.0"
# both match versionName "1.3.0"), and its versionCode is higher than the previous release's.
# Without the second check Android may refuse the update as a downgrade.
# Needs the full git history and tags (actions/checkout with fetch-depth: 0).
set -euo pipefail

tag="$1"
gradle=app/build.gradle.kts

value_at() { # value_at <ref> <versionName|versionCode>
    git show "$1:$gradle" | sed -n "s/^ *$2 *= *\"\{0,1\}\([^\"]*\)\"\{0,1\} *$/\1/p" | head -n 1
}

name=$(value_at "$tag" versionName)
code=$(value_at "$tag" versionCode)
if [ "${tag#v}" != "$name" ]; then
    echo "::error::Tag $tag doesn't match versionName \"$name\" in $gradle. Bump versionName (and versionCode), push, then tag again."
    exit 1
fi

previous=$(git describe --tags --abbrev=0 "$tag^" 2>/dev/null || true)
if [ -n "$previous" ]; then
    previous_code=$(value_at "$previous" versionCode)
    if [ -n "$previous_code" ] && [ "$code" -le "$previous_code" ]; then
        echo "::error::versionCode $code is not higher than $previous_code in the previous release ($previous). Phones won't take it as an update."
        exit 1
    fi
fi

echo "Releasing $name (versionCode $code)${previous:+, after $previous}."
