# Files to Integrate into Xcode Project

## Complete File List

All files are located in: `/workspace/iOS/SpaceCase/SpaceCase/`

### Core App Files
- ✅ `AppDelegate.swift` - Replace existing
- ✅ `SceneDelegate.swift` - Add if not exists

### Views (UI Controllers)
- ✅ `Views/IntroViewController.swift`
- ✅ `Views/MainMenuViewController.swift`
- ✅ `Views/SettingsViewController.swift`

### Game Core
- ✅ `Game/GameViewController.swift`
- ✅ `Game/GameScene.swift`
- ✅ `Game/GameEngine.swift`

### Game Entities
- ✅ `Game/Entities/PlayerNode.swift`
- ✅ `Game/Entities/EnemyNode.swift`
- ✅ `Game/Entities/BulletNode.swift`
- ✅ `Game/Entities/AsteroidNode.swift`
- ✅ `Game/Entities/PowerUpNode.swift`

### Game Controls
- ✅ `Game/Controls/JoystickNode.swift`
- ✅ `Game/Controls/ControlButton.swift`
- ✅ `Game/Controls/GameControlsView.swift`

### Multiplayer
- ✅ `Multiplayer/MultiplayerManager.swift`
- ✅ `Multiplayer/MultiplayerLobbyViewController.swift`

### Levels
- ✅ `Levels/Level.swift`
- ✅ `Levels/LevelManager.swift`

### Utilities
- ✅ `Utilities/SoundManager.swift`

### Configuration
- ✅ `Info.plist` - Update with required keys (see below)

## Required Framework Links

Make sure these frameworks are linked in your Xcode project:
1. **SpriteKit** - For 2D game rendering
2. **MultipeerConnectivity** - For local multiplayer
3. **AVFoundation** - For audio/video playback
4. **GameplayKit** - For game logic (optional, but used)

## Info.plist Updates

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

<key>UISupportedInterfaceOrientations</key>
<array>
    <string>UIInterfaceOrientationLandscapeLeft</string>
    <string>UIInterfaceOrientationLandscapeRight</string>
</array>
```

## Build Settings

- **iOS Deployment Target**: 15.0 or higher
- **Swift Language Version**: Swift 5.0
- **Supported Orientations**: Landscape Left & Right

## Integration Steps

1. **Copy all Swift files** maintaining folder structure
2. **Add files to Xcode** using "Add Files to [Project]"
3. **Ensure all files are added to target**
4. **Update Info.plist** with required keys
5. **Link required frameworks**
6. **Clean and build** (Cmd+Shift+K, then Cmd+B)

## Dependencies Between Files

### Core Dependencies:
- `GameScene` depends on: `GameEngine`, `PlayerNode`, `GameControlsView`
- `GameEngine` depends on: All entity nodes, `Level`, `LevelManager`
- `PlayerNode` depends on: `BulletNode`, `GameScene`
- `GameControlsView` depends on: `JoystickNode`, `ControlButton`
- `MultiplayerManager` uses: `MultipeerConnectivity` framework

### Import Statements:
All files import necessary frameworks:
- `UIKit` - For view controllers
- `SpriteKit` - For game rendering
- `GameplayKit` - For game logic
- `MultipeerConnectivity` - For multiplayer
- `AVFoundation` - For audio

## Testing Checklist

After integration, test:
- [ ] Project builds without errors
- [ ] App launches successfully
- [ ] Intro video plays (or skips if no video)
- [ ] Main menu displays
- [ ] Game scene loads
- [ ] Controls respond to touch
- [ ] Multiplayer lobby works
- [ ] No missing framework errors

## Common Issues & Solutions

### "Cannot find type 'X' in scope"
- Ensure all files are added to the target
- Check folder structure matches
- Clean build folder (Cmd+Shift+K)

### "No such module 'SpriteKit'"
- Link SpriteKit framework in project settings
- Check deployment target is iOS 9.0+

### Multiplayer not working
- Verify Info.plist has NSLocalNetworkUsageDescription
- Check MultipeerConnectivity framework is linked
- Ensure both devices are on same network

### Controls not responding
- Verify GameControlsView is added to scene
- Check zPosition is high enough (1000)
- Ensure touch events are forwarded correctly
