#!/bin/sh
# KeyTest 빌드: JDK 17+의 javac --release 8 (RustJava classfile 범위) → keytest.jar
set -e
cd "$(dirname "$0")"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

rm -rf build keytest.jar
mkdir -p build
"$JAVAC" --release 8 -Xlint:-options -encoding UTF-8 -cp ../stubs -d build $(find ../stubs src -name "*.java")
rm -rf build/javax   # 스텁은 런타임에 에뮬레이터가 제공

cat > manifest.txt << 'EOF'
Manifest-Version: 1.0
MIDlet-Name: KeyTest
MIDlet-Version: 1.0.0
MIDlet-Vendor: FoldRetro
MIDlet-1: Key Test, , KeyTest
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
EOF

"$JAR" cfm keytest.jar manifest.txt -C build .
echo "OK: $(pwd)/keytest.jar"
