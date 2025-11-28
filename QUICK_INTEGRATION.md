# Quick Integration Guide

## Overview
All game source code is ready in `/workspace/iOS/SpaceCase/SpaceCase/`. Follow these steps to integrate into your new Xcode project.

## Step-by-Step Integration

### 1. Copy Files to Your Xcode Project

Copy these folders from `/workspace/iOS/SpaceCase/SpaceCase/` to your Xcode project:

```
YourProject/
├── AppDelegate.swift          ← Replace existing
├── SceneDelegate.swift        ← Add if missing
├── Info.plist                ← Update (see Step 3)
├── Views/                    ← Add folder
│   ├── IntroViewController.swift
│   ├── MainMenuViewController.swift
│   └── SettingsViewController.swift
├── Game/                     ← Add folder
│   ├── GameViewController.swift
│   ├── GameScene.swift
│   ├── GameEngine.swift
│   ├── Entities/             ← Add subfolder
│   │   ├── PlayerNode.swift
│   │   ├── EnemyNode.swift
│   │   ├── BulletNode.swift
│   │   ├── AsteroidNode.swift
│   │   └── PowerUpNode.swift
│   └── Controls/             ← Add subfolder
│       ├── JoystickNode.swift
│       ├── ControlButton.swift
│       └── GameControlsView.swift
├── Multiplayer/              ← Add folder
│   ├── MultiplayerManager.swift
│   └── MultiplayerLobbyViewController.swift
├── Levels/                   ← Add folder
│   ├── Level.swift
│   └── LevelManager.swift
└── Utilities/                ← Add folder
    └── SoundManager.swift
```

### 2. Add Files in Xcode

1. **Open your Xcode project**
2. **Right-click** on your project root in the navigator
3. **Select "Add Files to [YourProject]"**
4. **Navigate** to each folder and add:
   - Select `Views/` folder → **Create groups** → Add to target
   - Select `Game/` folder → **Create groups** → Add to target
   - Select `Multiplayer/` folder → **Create groups** → Add to target
   - Select `Levels/` folder → **Create groups** → Add to target
   - Select `Utilities/` folder → **Create groups** → Add to target
5. **Replace** `AppDelegate.swift` and `SceneDelegate.swift` if they exist

### 3. Update Info.plist

Add these keys to your `Info.plist`:

```xml
<key>NSLocalNetworkUsageDescription</key>
<string>Space Case uses local network to connect with nearby players for multiplayer mode.</string>

<key>NSBonjourServices</key>
<array>
    <string>_spacecase-multiplayer._tcp</string>
</array>

<key>UIBackgroundModes</key>
<array>
    <string>audio</string>
</array>
```

### 4. Link Required Frameworks

In Xcode project settings → **General** → **Frameworks, Libraries, and Embedded Content**, add:

- ✅ **SpriteKit.framework**
- ✅ **MultipeerConnectivity.framework**
- ✅ **AVFoundation.framework**
- ✅ **GameplayKit.framework** (optional but recommended)

### 5. Update Build Settings

- **iOS Deployment Target**: Set to **15.0** or higher
- **Swift Language Version**: **Swift 5.0**
- **Supported Orientations**: Ensure **Landscape Left** and **Landscape Right** are enabled

### 6. Verify All Files Are in Target

1. Select your project in navigator
2. Select your target
3. Go to **Build Phases** → **Compile Sources**
4. Verify all `.swift` files are listed
5. If any are missing, add them manually

### 7. Clean and Build

1. **Clean Build Folder**: `Cmd + Shift + K`
2. **Build**: `Cmd + B`
3. Fix any import errors (usually means files aren't in target)

## File Count Summary

- **Total Swift Files**: 22
- **View Controllers**: 3
- **Game Core**: 3
- **Game Entities**: 5
- **Controls**: 3
- **Multiplayer**: 2
- **Levels**: 2
- **Utilities**: 1
- **App Files**: 2

## Required Frameworks

1. **SpriteKit** - 2D game rendering
2. **MultipeerConnectivity** - Local multiplayer
3. **AVFoundation** - Audio/video
4. **GameplayKit** - Game logic (optional)

## Common Issues

### ❌ "Cannot find type 'X' in scope"
**Solution**: Ensure all files are added to your target in Build Phases → Compile Sources

### ❌ "No such module 'SpriteKit'"
**Solution**: Link SpriteKit framework in project settings

### ❌ Build errors about missing types
**Solution**: Clean build folder (Cmd+Shift+K) and rebuild

### ❌ Multiplayer not working
**Solution**: Verify Info.plist has `NSLocalNetworkUsageDescription` key

## Testing Checklist

After integration:
- [ ] Project builds without errors
- [ ] App launches
- [ ] Intro screen appears (or skips if no video)
- [ ] Main menu displays
- [ ] Can navigate to game
- [ ] Game scene loads
- [ ] Controls respond
- [ ] No console errors

## Next Steps

1. Add game assets (sprites, sounds)
2. Test on device/simulator
3. Configure code signing
4. Test multiplayer functionality

## Need Help?

See `INTEGRATION_GUIDE.md` for detailed troubleshooting and `FILES_TO_INTEGRATE.md` for complete file list.
