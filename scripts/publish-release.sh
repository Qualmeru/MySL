#!/usr/bin/env bash
# Publishes the GitHub release for a tag with the built files. Run by android-release.yml.
#
#   publish-release.sh <tag> <notes.md> <dir with files>
#
# The release's title and notes come from release-notes/<tag>.md: its first line ("# Title") is
# the title, the rest the notes. A release already made in GitHub's web form keeps its own title
# and notes and only gets the files. Without either, GitHub writes notes from the commits.
# Needs gh with GH_TOKEN and GH_REPO set. Set DRY_RUN=1 to print the gh commands instead.
set -euo pipefail

tag="${1:?tag}"
notes="${2:?notes file}"
files_dir="${3:?files directory}"

gh_() {
  if [ "${DRY_RUN:-}" = "1" ]; then echo "gh $*"; else gh "$@"; fi
}

if [ "${DRY_RUN:-}" != "1" ] && gh release view "$tag" > /dev/null 2>&1; then
  gh release upload "$tag" "$files_dir"/* --clobber
elif [ -f "$notes" ]; then
  title=$(head -n 1 "$notes" | sed 's/^#[[:space:]]*//')
  body=$(mktemp)
  # Everything after the title line, without the blank line that follows it.
  tail -n +2 "$notes" | sed '1{/^[[:space:]]*$/d}' > "$body"
  gh_ release create "$tag" "$files_dir"/* --verify-tag --title "$title" --notes-file "$body"
  [ "${DRY_RUN:-}" = "1" ] && { echo "--- notes:"; cat "$body"; }
  rm -f "$body"
else
  gh_ release create "$tag" "$files_dir"/* --verify-tag --generate-notes
fi
