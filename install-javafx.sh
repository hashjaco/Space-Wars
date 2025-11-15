#!/bin/bash

# Space Wars - JavaFX Installation Helper
# This script helps install JavaFX for the Space Wars game

echo "Space Wars - JavaFX Installation Helper"
echo "========================================"
echo ""

# Detect Java version
JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ -z "$JAVA_VERSION" ]; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
fi

echo "Detected Java version: $JAVA_VERSION"
echo ""

# Check if running on Ubuntu/Debian
if [ -f /etc/debian_version ]; then
    echo "Detected Ubuntu/Debian system"
    echo ""
    echo "Installing JavaFX via package manager..."
    echo "You may be prompted for your password."
    echo ""
    
    sudo apt-get update
    sudo apt-get install -y openjfx
    
    if [ $? -eq 0 ]; then
        echo ""
        echo "JavaFX installed successfully!"
        echo ""
        echo "You can now run: ./build.sh"
    else
        echo ""
        echo "Installation failed. You may need to install manually."
        echo "See README.md for manual installation instructions."
    fi
else
    echo "This script currently only supports Ubuntu/Debian systems."
    echo ""
    echo "For other systems, please install JavaFX manually:"
    echo ""
    echo "1. Visit https://openjfx.io/"
    echo "2. Download JavaFX SDK for Java $JAVA_VERSION"
    echo "3. Extract to ~/javafx-sdk"
    echo "4. Set environment variable:"
    echo "   export JAVAFX_PATH=~/javafx-sdk/lib"
    echo "5. Run: ./build.sh"
    echo ""
fi
