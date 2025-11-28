#!/bin/bash

# Integration script for Space Case iOS game
# This script helps integrate all source files into your Xcode project

echo "Space Case iOS Integration Script"
echo "================================"
echo ""

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if Xcode project path is provided
if [ -z "$1" ]; then
    echo -e "${YELLOW}Usage: ./integrate_files.sh <path-to-xcode-project>${NC}"
    echo "Example: ./integrate_files.sh ~/Projects/SpaceCase/SpaceCase.xcodeproj"
    echo ""
    echo "Or manually copy files from iOS/SpaceCase/SpaceCase/ to your project"
    exit 1
fi

PROJECT_PATH="$1"
SOURCE_DIR="$(pwd)/iOS/SpaceCase/SpaceCase"

if [ ! -d "$SOURCE_DIR" ]; then
    echo -e "${RED}Error: Source directory not found at $SOURCE_DIR${NC}"
    exit 1
fi

echo -e "${GREEN}Source directory: $SOURCE_DIR${NC}"
echo -e "${GREEN}Target project: $PROJECT_PATH${NC}"
echo ""
echo "This script will help you integrate the files."
echo "Please ensure your Xcode project is closed before proceeding."
echo ""
read -p "Press Enter to continue..."

# List all files that need to be copied
echo ""
echo "Files to integrate:"
echo "==================="
find "$SOURCE_DIR" -name "*.swift" -o -name "*.plist" | while read file; do
    echo "  - $file"
done

echo ""
echo -e "${YELLOW}Manual Integration Steps:${NC}"
echo "1. Open your Xcode project"
echo "2. Right-click on your project in the navigator"
echo "3. Select 'Add Files to [ProjectName]'"
echo "4. Navigate to: $SOURCE_DIR"
echo "5. Add the following folders (with 'Create groups' selected):"
echo "   - Views/"
echo "   - Game/ (including all subfolders)"
echo "   - Multiplayer/"
echo "   - Levels/"
echo "   - Utilities/"
echo "6. Replace AppDelegate.swift and SceneDelegate.swift if they exist"
echo "7. Update Info.plist with the required keys"
echo ""
echo "See INTEGRATION_GUIDE.md for detailed instructions."
