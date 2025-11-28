# Integration Guide: Adding Game Source Code to Xcode Project

This guide will help you integrate all the Swift source files into your new Xcode project.

## Step 1: File Structure

Copy all Swift files from `/workspace/iOS/SpaceCase/SpaceCase/` to your new Xcode project maintaining the same folder structure:

```
YourProject/
├── AppDelegate.swift (replace existing)
├── SceneDelegate.swift (add if not exists)
├── Info.plist (update with our settings)
├── Views/
│   ├── IntroViewController.swift
│   ├── MainMenuViewController.swift
│   └── SettingsViewController.swift
├── Game/
│   ├── GameViewController.swift
│   ├── GameScene.swift
│   ├── GameEngine.swift
│   ├── Entities/
│   │   ├── PlayerNode.swift
│   │   ├── EnemyNode.swift
│   │   ├── BulletNode.swift
│   │   ├── AsteroidNode.swift
│   │   └── PowerUpNode.swift
│   └── Controls/
│       ├── JoystickNode.swift
│       ├── ControlButton.swift
│       └── GameControlsView.swift
├── Multiplayer/
│   ├── MultiplayerManager.swift
│   └── MultiplayerLobbyViewController.swift
├── Levels/
│   ├── Level.swift
│   └── LevelManager.swift
└── Utilities/
    └── SoundManager.swift
```

## Step 2: Add Files to Xcode Project

1. **Right-click on your project** in Xcode navigator
2. **Select "Add Files to [ProjectName]"**
3. **Navigate to each folder** and add files:
   - Add all files from `Views/` folder
   - Add all files from `Game/` folder (including subfolders)
   - Add all files from `Multiplayer/` folder
   - Add all files from `Levels/` folder
   - Add all files from `Utilities/` folder
4. **Important**: Make sure "Copy items if needed" is checked
5. **Important**: Make sure your target is selected for all files

## Step 3: Update Info.plist

Add these keys to your Info.plist:

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

## Step 4: Update Build Settings

1. **Deployment Target**: Set to iOS 15.0 or higher
2. **Swift Language Version**: Swift 5.0
3. **Supported Orientations**: Ensure landscape is supported

## Step 5: Add Level Specs (Optional)

Copy the `LevelSpecs/` folder to your project and add it as a folder reference (not a group) so the JSON files are included in the bundle.

## Step 6: Required Frameworks

Ensure these frameworks are linked:
- SpriteKit (for game rendering)
- MultipeerConnectivity (for local multiplayer)
- AVFoundation (for audio/video)

## Step 7: Fix Import Issues

If you see import errors:
1. Make sure all files are added to the correct target
2. Clean build folder (Cmd+Shift+K)
3. Build (Cmd+B)

## Step 8: Assets

You'll need to add these assets (create placeholders if needed):
- Player ship sprite
- Enemy ship sprites
- Bullet sprites
- Asteroid sprites
- Power-up sprites
- Background images
- Sound effects
- Explosion animation frames

## Common Issues

### "Cannot find type in scope"
- Make sure all files are added to the target
- Check that folder structure matches

### "Use of unresolved identifier"
- Clean build folder
- Rebuild project

### Multiplayer not working
- Check Info.plist has NSLocalNetworkUsageDescription
- Ensure MultipeerConnectivity framework is linked

## Next Steps

After integration:
1. Add game assets (sprites, sounds)
2. Test on device or simulator
3. Configure code signing
4. Test multiplayer functionality
