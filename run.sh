#!/bin/bash

# Space Wars Run Script
# This script runs the compiled Space Wars game

echo "Starting Space Wars..."
echo "======================"

# Check if build has been run
if [ ! -d "out" ]; then
    echo "Error: 'out' directory not found. Please run ./build.sh first."
    exit 1
fi

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
    echo "Attempting to run without explicit JavaFX path..."
    echo "If this fails, please install JavaFX or set JAVAFX_PATH environment variable"
    echo ""
    
    # Try to run without JavaFX path (if it's in the classpath)
    java --add-modules javafx.controls,javafx.media,javafx.graphics \
         -cp out:sources:. \
         sources.Main
else
    echo "Found JavaFX at: $JAVAFX_PATH"
    echo ""
    
    # Run with JavaFX
    java --module-path "$JAVAFX_PATH" \
         --add-modules javafx.controls,javafx.media,javafx.graphics \
         -cp out:sources:. \
         sources.Main
fi

# Check if run was successful
if [ $? -ne 0 ]; then
    echo ""
    echo "Failed to start the game. Please check:"
    echo "1. Java is installed (java -version)"
    echo "2. JavaFX is installed and JAVAFX_PATH is set correctly"
    echo "3. The game was built successfully (./build.sh)"
    exit 1
fi
