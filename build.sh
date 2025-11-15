#!/bin/bash

# Space Wars Build Script
# This script compiles all Java source files for the Space Wars game

echo "Building Space Wars..."
echo "======================"

# Detect Java version for better error messages
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ -z "$JAVA_VERSION" ]; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
fi

# Create output directory if it doesn't exist
mkdir -p out

# Find JavaFX path (check environment variable first, then common locations)
if [ -n "$JAVAFX_PATH" ] && [ -f "$JAVAFX_PATH/javafx.base.jar" ]; then
    # JAVAFX_PATH is already set and valid
    :
elif [ -d "/usr/share/openjfx/lib" ] && [ -f "/usr/share/openjfx/lib/javafx.base.jar" ]; then
    JAVAFX_PATH="/usr/share/openjfx/lib"
elif [ -d "/usr/lib/jvm/openjfx/lib" ] && [ -f "/usr/lib/jvm/openjfx/lib/javafx.base.jar" ]; then
    JAVAFX_PATH="/usr/lib/jvm/openjfx/lib"
elif [ -d "$HOME/javafx-sdk/lib" ] && [ -f "$HOME/javafx-sdk/lib/javafx.base.jar" ]; then
    JAVAFX_PATH="$HOME/javafx-sdk/lib"
else
    JAVAFX_PATH=""
fi

# Check if JavaFX is found
if [ -z "$JAVAFX_PATH" ] || [ ! -f "$JAVAFX_PATH/javafx.base.jar" ]; then
    echo "ERROR: JavaFX libraries not found in standard locations."
    echo ""
    echo "JavaFX is required to compile and run this game."
    echo ""
    echo "To install JavaFX, choose one of the following options:"
    echo ""
    echo "Option 1 - Quick install (Ubuntu/Debian):"
    echo "  ./install-javafx.sh"
    echo ""
    echo "Option 1b - Manual install via package manager:"
    echo "  sudo apt-get update"
    echo "  sudo apt-get install openjfx"
    echo ""
    echo "Option 2 - Download JavaFX SDK manually:"
    echo "  1. Visit https://openjfx.io/"
    if [ -n "$JAVA_VERSION" ]; then
        echo "  2. Download JavaFX SDK for Java $JAVA_VERSION"
    else
        echo "  2. Download JavaFX SDK matching your Java version"
    fi
    echo "  3. Extract to ~/javafx-sdk"
    echo "  4. Set JAVAFX_PATH: export JAVAFX_PATH=~/javafx-sdk/lib"
    echo ""
    echo "Option 3 - Use environment variable:"
    echo "  export JAVAFX_PATH=/path/to/javafx/lib"
    echo "  ./build.sh"
    echo ""
    exit 1
else
    echo "Found JavaFX at: $JAVAFX_PATH"
    echo ""
    
    # Compile with JavaFX
    COMPILE_SUCCESS=false
    javac --module-path "$JAVAFX_PATH" \
          --add-modules javafx.controls,javafx.media,javafx.graphics \
          -d out \
          -sourcepath . \
          sources/*.java 2>&1
    if [ $? -eq 0 ]; then
        COMPILE_SUCCESS=true
    fi
fi

# Check if compilation was successful
if [ "$COMPILE_SUCCESS" = true ]; then
    echo ""
    echo "Build successful! Compiled classes are in the 'out' directory."
    echo ""
    echo "To run the game, use: ./run.sh"
else
    echo ""
    echo "Build failed. Please check the error messages above."
    echo ""
    echo "Common issues:"
    echo "- JavaFX not installed or not found"
    echo "- Java version mismatch (JavaFX version must match Java version)"
    echo "- Incorrect JAVAFX_PATH setting"
    exit 1
fi
