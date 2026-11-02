#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
root=$PWD
: "${FREEJ2ME_JAR:?Set FREEJ2ME_JAR to the absolute path of a FreeJ2ME-Plus runtime jar}"
mkdir -p build
qa=$(mktemp -d "$root/build/check.XXXXXX")
mkdir "$qa/portrait" "$qa/landscape"
javac -cp "$FREEJ2ME_JAR" -d "$qa" tools/test/EmulatorCheck.java tools/test/PlayCapture.java
for mode in portrait landscape; do
  cd "$qa/$mode"
  if [ "$mode" = portrait ]; then set -- 240 320; else set -- 320 240 boy; fi
  java -DfullOpening="${FULL_OPENING:-false}" -Dfile.encoding=ISO_8859_1 -Djava.awt.headless=true -cp "$qa:$FREEJ2ME_JAR" EmulatorCheck "$root/dist/Windward.jar" shots "$@"
done
printf 'Screenshots: %s\n' "$qa"
