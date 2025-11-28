# Space Case - iOS Version

## Overview

Space Case is a 2D space shooter game recreated for iOS using Swift and SpriteKit. The game features local multiplayer, story mode, and a data-driven level system.

## Project Structure

```
iOS/SpaceCase/
├── SpaceCase/
│   ├── AppDelegate.swift          # App entry point
│   ├── SceneDelegate.swift        # Scene lifecycle
│   ├── Views/                     # View controllers
│   │   ├── IntroViewController.swift
│   │   ├── MainMenuViewController.swift
│   │   └── SettingsViewController.swift
│   ├── Game/                      # Core game logic
│   │   ├── GameViewController.swift
│   │   ├── GameScene.swift
│   │   ├── GameEngine.swift
│   │   └── Entities/              # Game entities
│   │       ├── PlayerNode.swift
│   │       ├── EnemyNode.swift
│   │       ├── BulletNode.swift
│   │       ├── AsteroidNode.swift
│   │       └── PowerUpNode.swift
│   ├── Multiplayer/               # Multiplayer functionality
│   │   ├── MultiplayerManager.swift
│   │   └── MultiplayerLobbyViewController.swift
│   ├── Levels/                    # Level system
│   │   ├── Level.swift
│   │   └── LevelManager.swift
│   └── Utilities/                 # Utilities
│       └── SoundManager.swift
├── LevelSpecs/                    # Level specification files (JSON)
├── StoryMode/                     # Story scripts
└── Documentation/                 # Documentation
```

## Features

### Core Features
- **SpriteKit-based game engine** for efficient 2D rendering
- **Local multiplayer** using MultipeerConnectivity
- **Story mode** with narrative progression
- **Data-driven level system** using JSON specifications
- **Skippable intro video** support
- **Power-up system** with multiple types
- **Boss battles** with unique attack patterns

### Technical Features
- Modern Swift 5.0+ codebase
- iOS 15.0+ support
- Proper MVC architecture
- Physics-based collision detection
- Efficient entity management
- Sound effect and music support

## Setup Instructions

### Prerequisites
- Xcode 14.0 or later
- iOS 15.0+ deployment target
- Swift 5.0+

### Building the Project

1. Open `SpaceCase.xcodeproj` in Xcode
2. Select your development team in Signing & Capabilities
3. Build and run on a simulator or device

### Adding Assets

Place game assets in the `Assets.xcassets` catalog:
- Sprites (player, enemies, bullets, asteroids, power-ups)
- Background images
- Sound effects (.wav or .mp3)
- Music files

### Level Specifications

Level specs are JSON files located in `LevelSpecs/`. See `LevelSpecs/README.md` for detailed documentation on creating levels.

### Intro Video

Place your AI-generated intro video as `intro_video.mp4` in the app bundle. The intro is skippable and automatically transitions to the main menu.

## Multiplayer Setup

The game uses MultipeerConnectivity for local multiplayer:
- Players must be on the same local network
- Automatic peer discovery
- Real-time synchronization of player positions and actions

## AI Music Integration

See `Documentation/AI_Music_Resources.md` for recommended AI music generation services and implementation guidelines.

## Story Mode

Story scripts are located in `StoryMode/story_scripts.md`. The game includes:
- Pre-level cutscenes with dialogue
- Mid-level radio chatter
- Boss introductions
- Level completion narratives

## Level Generation with AI

The level spec system is designed to work with AI tools. See `LevelSpecs/README.md` for:
- Prompt templates for AI level generation
- Best practices
- Validation guidelines

## Architecture

### Game Engine
- `GameEngine`: Manages game state, entities, and logic
- `GameScene`: SpriteKit scene handling rendering
- Entity system: Modular game objects (Player, Enemy, Bullet, etc.)

### Multiplayer
- `MultiplayerManager`: Handles peer discovery and communication
- JSON-based message protocol
- Real-time synchronization

### Level System
- JSON-based level specifications
- `LevelManager`: Loads and manages levels
- Supports waves, enemies, bosses, and power-ups

## Future Enhancements

- Online multiplayer support
- Cloud save functionality
- Achievement system
- Leaderboards
- Additional enemy types
- More power-up varieties
- Expanded story mode

## License

[Your License Here]

## Credits

- Original Java version: [Original Author]
- iOS port: [Your Name/Team]
- AI Music: Generated using [Service Name]
- Intro Video: Generated using [AI Video Service]
