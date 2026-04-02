#!/bin/bash
# TurboClick — build & run script
# Usage: ./build.sh        (compile + run)
#        ./build.sh compile (compile only)
#        ./build.sh run     (run only)

CLASSPATH="lib/jnativehook-2.2.2.jar"
SRC_DIRS="src src/nodes src/engine src/ui"
OUT_DIR="out"

compile() {
    echo "🔨 Compiling TurboClick..."
    mkdir -p "$OUT_DIR"
    SOURCES=$(find src -name "*.java")
    javac -cp "$CLASSPATH" -sourcepath src -d "$OUT_DIR" $SOURCES 2>&1
    if [ $? -eq 0 ]; then
        echo "✅ Compilation successful"
    else
        echo "❌ Compilation failed"
        exit 1
    fi
}

run() {
    echo "🚀 Starting TurboClick..."
    java -cp "$OUT_DIR:$CLASSPATH" TurboClick
}

package() {
    compile
    echo "📦 Packaging TurboClick.app + DMG..."
    mkdir -p dist

    # Extract jnativehook into out/ so it's included in the fat jar
    cd out && jar xf ../lib/jnativehook-2.2.2.jar
    # Remove signature files that cause jar conflicts
    rm -f META-INF/*.SF META-INF/*.DSA META-INF/*.RSA
    cd ..

    # Write manifest
    echo "Main-Class: TurboClick" > dist/manifest.mf

    # Build fat jar
    jar cfm dist/TurboClick.jar dist/manifest.mf -C out .
    echo "✅ Fat JAR built"

    # Package into .app + .dmg
    jpackage \
        --input dist \
        --main-jar TurboClick.jar \
        --main-class TurboClick \
        --name TurboClick \
        --app-version 2.0 \
        --type dmg \
        --dest dist \
        --mac-package-name "TurboClick" \
        --mac-package-identifier "com.turboclick.app" \
        $([ -f TurboClick.icns ] && echo "--icon TurboClick.icns")

    echo "✅ DMG ready: dist/TurboClick-2.0.dmg"
}

case "$1" in
    compile) compile ;;
    run)     run ;;
    package) package ;;
    *)       compile && run ;;
esac