#!/usr/bin/env bash
# Fails if R8 stripped a method from the release APK that Android or a library calls by
# reflection. R8 can't see those calls, and a missing one only shows up as a crash on the phone:
# tapping the widget's ↻ threw AbstractMethodError until proguard-rules.pro kept ActionCallback
# members. Add a line below for every new entry point (manifest component, ActionCallback, ...).
#
# Usage: scripts/check-release-dex.sh path/to/release.apk
# Finds dexdump in $ANDROID_HOME's newest build-tools, or set DEXDUMP to its path.
set -euo pipefail

apk="${1:?usage: $0 path/to/release.apk}"
if [ -z "${DEXDUMP:-}" ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?set ANDROID_HOME or DEXDUMP}}"
  tools=$(ls -d "$sdk"/build-tools/*/ | sort -V | tail -1)
  DEXDUMP="${tools}dexdump"
  [ -x "$DEXDUMP" ] || DEXDUMP="${tools}dexdump.exe"
fi

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$work"
for dex in "$work"/classes*.dex; do "$DEXDUMP" "$dex" 2>/dev/null; done > "$work/dump.txt"

failed=0
# expect <class> <method>: the class must still have a method of that name.
expect() {
  local cls="L${1//.//};" method="'$2'"
  if awk -v want="Class descriptor  : '$cls'" -v m="$method" '
      index($0, want) { on = 1; next }
      on && /Class descriptor/ { exit }
      on && /^ +name +:/ && index($0, m) { found = 1; exit }
      END { exit !found }' "$work/dump.txt"; then
    echo "ok       $1.$2"
  else
    echo "MISSING  $1.$2"
    failed=1
  fi
}

expect se.mybus.MainActivity onCreate
expect se.mybus.widget.PickStopActivity onCreate
expect se.mybus.widget.DeparturesWidgetReceiver onUpdate
expect se.mybus.widget.SingleStopWidgetReceiver onUpdate
expect se.mybus.widget.MinuteRedrawReceiver onReceive
expect se.mybus.widget.RefreshAction '<init>'
expect se.mybus.widget.RefreshAction onAction
expect se.mybus.widget.WidgetUpdateWorker '<init>'
expect se.mybus.widget.DeparturesWidget '<init>'
expect se.mybus.widget.SingleStopWidget '<init>'

if [ "$failed" -ne 0 ]; then
  echo "R8 removed code the app needs at runtime; see app/proguard-rules.pro." >&2
  exit 1
fi
