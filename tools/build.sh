#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
api=${J2ME_API:-"$HOME/Downloads/kemnnx64/uei"}
for lib in cldc11 midp21 jsr184; do
  test -f "$api/$lib.jar" || { echo "Set J2ME_API to the directory containing cldc11.jar, midp21.jar and jsr184.jar" >&2; exit 1; }
done
mkdir -p build/tools dist
rm -rf build/raw build/cldc
mkdir -p build/raw build/cldc
exports="--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED"
javac "$exports" -d build/tools tools/Preverify.java
midp_javac=${JAVAC8:-/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/javac}
"$midp_javac" -encoding UTF-8 -source 1.3 -target 1.3 -Xlint:-options -bootclasspath "$api/cldc11.jar:$api/midp21.jar:$api/jsr184.jar" -d build/raw src/*.java
java "$exports" -cp build/tools Preverify build/raw build/cldc "$api/jsr184.jar" "$api/midp21.jar"
cat > build/manifest <<'EOF'
Manifest-Version: 1.0
MIDlet-Name: Windward Mondstadt
MIDlet-Version: 1.1.0
MIDlet-Vendor: Mondstadt Demo
MIDlet-1: Windward Mondstadt,/icon.png,Windward
MicroEdition-Configuration: CLDC-1.1
MicroEdition-Profile: MIDP-2.0

EOF
jar cfm dist/Windward.jar build/manifest -C build/cldc . -C res .
grep -v '^Manifest-Version:\|^$' build/manifest > dist/Windward.jad
printf 'MIDlet-Jar-URL: Windward.jar\nMIDlet-Jar-Size: %d\n' "$(wc -c < dist/Windward.jar)" >> dist/Windward.jad
