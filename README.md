# Space Wars

Original single or multiplayer space wars game with scrolling background, stimulating music, and enemy AI. Destroy asteroids, shoot up enemy ships, and defeat bosses!

Space Wars is a 2D shooter developed using the JavaFX library. This game features a complete start menu, two-player gameplay, and an engaging space combat experience.

## Prerequisites

Before building and running Space Wars, ensure you have:

- **Java Development Kit (JDK) 11 or higher** - Check with `java -version`
- **JavaFX SDK** - Required for the GUI components

### Installing JavaFX

**On Ubuntu/Debian:**
```bash
sudo apt-get update
sudo apt-get install openjfx
```

**On macOS (using Homebrew):**
```bash
brew install openjfx
```

**On Windows or Manual Installation:**
1. Download JavaFX SDK from [https://openjfx.io/](https://openjfx.io/)
2. Extract it to a location like `~/javafx-sdk` or `C:\javafx-sdk`
3. Set the `JAVAFX_PATH` environment variable to point to the `lib` directory

## Building the Game

### Quick Build (Recommended)

Simply run the provided build script:

```bash
./build.sh
```

This script will:
- Create an `out` directory for compiled classes
- Automatically detect JavaFX installation
- Compile all Java source files
- Handle JavaFX module dependencies

### Manual Build

If you prefer to build manually:

```bash
# Create output directory
mkdir -p out

# Compile (adjust JAVAFX_PATH to your installation)
javac --module-path /usr/share/openjfx/lib \
      --add-modules javafx.controls,javafx.media,javafx.graphics \
      -d out \
      -sourcepath . \
      sources/*.java
```

## Running the Game

### Quick Run (Recommended)

After building, simply run:

```bash
./run.sh
```

### Manual Run

If you prefer to run manually:

```bash
java --module-path /usr/share/openjfx/lib \
     --add-modules javafx.controls,javafx.media,javafx.graphics \
     -cp out:sources \
     sources.Main
```

## Game Features

### Start Menu
- **START GAME** - Begin playing immediately
- **INSTRUCTIONS** - View game controls and objectives
- **EXIT** - Quit the game

### Gameplay
- Two-player split-screen gameplay
- Scrolling space background
- Enemy AI that targets players
- Asteroid destruction
- Power-ups and special weapons
- Score tracking
- Health and lives system

## Controls

### Player 1:
- **W** - Move Up
- **A** - Move Left
- **S** - Move Down
- **D** - Move Right
- **SHIFT** - Fire

### Player 2:
- **UP Arrow** - Move Up
- **LEFT Arrow** - Move Left
- **DOWN Arrow** - Move Down
- **RIGHT Arrow** - Move Right
- **COMMA (,)** - Fire

### General:
- **ESCAPE** - Pause/Resume Game

## Game Objective

Survive as long as possible by:
- Destroying asteroids (10 points each)
- Defeating enemy ships (20 points each)
- Avoiding collisions with enemies and their projectiles
- Collecting power-ups to enhance your ship

## Troubleshooting

### Build Fails with "JavaFX not found"
- Install JavaFX using your package manager (see Prerequisites)
- Or download JavaFX SDK manually and set `JAVAFX_PATH` environment variable

### Game won't start
- Ensure you've run `./build.sh` first
- Check that Java version is 11 or higher: `java -version`
- Verify JavaFX is properly installed

### Resources not loading (images/sounds)
- Ensure you're running from the project root directory
- Check that `sources/Sprites/` and `sources/Sounds/` directories exist

## Project Structure

```
Space-Wars/
├── sources/           # Java source files
│   ├── Sprites/      # Game sprite images
│   └── Sounds/       # Game sound effects
├── out/              # Compiled classes (created by build)
├── build.sh          # Build script
├── run.sh            # Run script
└── README.md         # This file
```

## Development Notes

This game was originally developed 5 years ago and has been updated to:
- Include a functional start menu
- Fix compilation issues
- Add proper build and run scripts
- Improve resource loading

The game uses JavaFX for rendering and requires JavaFX modules to be included at both compile and runtime.

## License

This is a personal project. Feel free to explore and learn from the code!
