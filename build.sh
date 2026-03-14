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

case "$1" in
    compile) compile ;;
    run)     run ;;
    *)       compile && run ;;
esac