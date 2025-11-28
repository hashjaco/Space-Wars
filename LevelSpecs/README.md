# Level Specification System

## Overview

The Level Specification system allows for data-driven level design using JSON files. This system enables AI-assisted level generation and easy content creation without code changes.

## Level Spec File Format

Each level is defined in a JSON file following this structure:

```json
{
  "levelNumber": 1,
  "name": "Level Name",
  "description": "Level description",
  "numberOfWaves": 3,
  "waves": [
    {
      "waveNumber": 1,
      "enemySpawnRate": 5,
      "asteroidSpawnRate": 10,
      "maxEnemies": 5,
      "maxAsteroids": 10,
      "enemyTypes": [
        {
          "type": "basic",
          "health": 50,
          "damage": 10,
          "speed": 2.0,
          "spawnWeight": 100
        }
      ],
      "powerUpChance": 5,
      "duration": 60.0
    }
  ],
  "backgroundImage": "spaceBackground",
  "backgroundMusic": "background_music",
  "boss": {
    "name": "Boss Name",
    "health": 500,
    "damage": 25,
    "attackPatterns": ["pattern1", "pattern2"],
    "spriteName": "boss_sprite"
  }
}
```

## Field Descriptions

### Level Fields
- `levelNumber`: Unique identifier for the level
- `name`: Display name for the level
- `description`: Story/context description
- `numberOfWaves`: Total number of waves in the level
- `waves`: Array of wave configurations
- `backgroundImage`: Asset name for background
- `backgroundMusic`: Asset name for background music
- `boss`: Optional boss configuration (null if no boss)

### Wave Fields
- `waveNumber`: Wave index (1-based)
- `enemySpawnRate`: Spawn probability per tick (0-1000, higher = more frequent)
- `asteroidSpawnRate`: Asteroid spawn probability (0-1000)
- `maxEnemies`: Maximum concurrent enemies
- `maxAsteroids`: Maximum concurrent asteroids
- `enemyTypes`: Array of enemy type configurations
- `powerUpChance`: Percentage chance for power-up spawn (0-100)
- `duration`: Wave duration in seconds

### Enemy Type Fields
- `type`: Enemy type identifier
- `health`: Enemy hit points
- `damage`: Damage dealt to player
- `speed`: Movement speed multiplier
- `spawnWeight`: Relative spawn probability (higher = more likely)

### Boss Fields
- `name`: Boss display name
- `health`: Boss hit points
- `damage`: Damage dealt to player
- `attackPatterns`: Array of attack pattern identifiers
- `spriteName`: Asset name for boss sprite

## AI-Assisted Level Generation

### Using AI to Generate Levels

You can use AI tools (like ChatGPT, Claude, or specialized game design AIs) to generate level specifications. Here's a prompt template:

```
Generate a level specification JSON for a space shooter game with the following requirements:
- Level number: [X]
- Difficulty: [Easy/Medium/Hard]
- Theme: [e.g., "Asteroid Field", "Enemy Base", "Boss Battle"]
- Number of waves: [X]
- Include a boss: [Yes/No]

Generate waves with increasing difficulty, varied enemy types, and appropriate spawn rates.
```

### Example AI Prompts

1. **Progressive Difficulty:**
   ```
   Create a level spec for level 3 that starts easy and gets progressively harder. 
   Wave 1 should have low spawn rates, Wave 2 should increase, and Wave 3 should be 
   challenging with a boss at the end.
   ```

2. **Themed Levels:**
   ```
   Generate a level spec for an "Asteroid Field" themed level. Focus on high 
   asteroid spawn rates with fewer enemies. Include environmental hazards.
   ```

3. **Boss Battle:**
   ```
   Create a boss battle level spec. Include 2 warm-up waves, then a challenging 
   boss with multiple attack patterns.
   ```

### AI Tools Recommendations

1. **ChatGPT/Claude:** Use structured prompts with the JSON schema
2. **GitHub Copilot:** Generate level specs inline while coding
3. **Custom AI Tools:** Train on existing level data for consistency

### Validation

After generating a level spec with AI:
1. Validate JSON syntax
2. Check spawn rates are reasonable (0-1000)
3. Ensure enemy types exist in the game
4. Test the level in-game for balance

## Best Practices

1. **Progressive Difficulty:** Each wave should be slightly harder than the previous
2. **Spawn Rates:** Start conservative and increase gradually
3. **Enemy Variety:** Mix different enemy types within waves
4. **Power-Up Placement:** Strategic power-up chances maintain engagement
5. **Boss Design:** Bosses should feel challenging but fair

## File Naming Convention

Level spec files should be named: `level_[number].json`

Example: `level_1.json`, `level_2.json`, etc.

## Integration

Levels are automatically loaded at game startup from the `LevelSpecs` directory. The game engine reads these files and constructs level objects for gameplay.
