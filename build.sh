#!/bin/bash

# Space Wars Build Script
# This script compiles all Java source files for the Space Wars game

echo "Building Space Wars..."
echo "======================"

# Create output directory if it doesn't exist
mkdir -p out

# Find JavaFX path (common locations)
JAVAFX_PATH=""
if [ -d "/usr/share/openjfx/lib" ]; then
    JAVAFX_PATH="/usr/share/openjfx/lib"
elif [ -d "/usr/lib/jvm/openjfx" ]; then
    JAVAFX_PATH="/usr/lib/jvm/openjfx/lib"
elif [ -d "$HOME/javafx-sdk/lib" ]; then
    JAVAFX_PATH="$HOME/javafx-sdk/lib"
fi

# Check if JavaFX is found
if [ -z "$JAVAFX_PATH" ] || [ ! -f "$JAVAFX_PATH/javafx.base.jar" ]; then
    echo "Warning: JavaFX libraries not found in standard locations."
    echo "Attempting to compile without explicit JavaFX path..."
    echo "If compilation fails, you may need to install JavaFX or set JAVAFX_PATH"
    echo ""
    
    # Try to compile without JavaFX path (if it's in the classpath)
    javac -d out -sourcepath . sources/*.java 2>&1 | head -20
    if [ ${PIPESTATUS[0]} -ne 0 ]; then
        echo ""
        echo "Compilation failed. Trying alternative method..."
        javac -d out sources/*.java 2>&1 | head -20
    fi
else
    echo "Found JavaFX at: $JAVAFX_PATH"
    echo ""
    
    # Compile with JavaFX
    javac --module-path "$JAVAFX_PATH" \
          --add-modules javafx.controls,javafx.media,javafx.graphics \
          -d out \
          -sourcepath . \
          sources/*.java
fi

# Check if compilation was successful
if [ $? -eq 0 ]; then
    echo ""
    echo "Build successful! Compiled classes are in the 'out' directory."
    echo ""
    echo "To run the game, use: ./run.sh"
else
    echo ""
    echo "Build failed. Please check the error messages above."
    echo ""
    echo "If you're missing JavaFX, you can:"
    echo "1. Install it: sudo apt-get install openjfx (on Ubuntu/Debian)"
    echo "2. Or download from: https://openjfx.io/"
    echo "3. Set JAVAFX_PATH environment variable to the lib directory"
    exit 1
fi
