//
//  LevelManager.swift
//  SpaceCase
//
//  Manages level loading and progression
//

import Foundation

class LevelManager {
    
    private var levels: [Int: Level] = [:]
    
    init() {
        loadLevels()
    }
    
    func loadLevel(levelNumber: Int) -> Level? {
        return levels[levelNumber]
    }
    
    private func loadLevels() {
        // Load levels from JSON files
        let fileManager = FileManager.default
        
        guard let levelsPath = Bundle.main.resourcePath?.appending("/LevelSpecs") else {
            // Fallback to default levels
            createDefaultLevels()
            return
        }
        
        do {
            let levelFiles = try fileManager.contentsOfDirectory(atPath: levelsPath)
                .filter { $0.hasSuffix(".json") }
            
            for file in levelFiles {
                let filePath = levelsPath + "/" + file
                if let data = try? Data(contentsOf: URL(fileURLWithPath: filePath)),
                   let level = try? JSONDecoder().decode(Level.self, from: data) {
                    levels[level.levelNumber] = level
                }
            }
        } catch {
            print("Error loading levels: \(error)")
            createDefaultLevels()
        }
    }
    
    private func createDefaultLevels() {
        // Create default level if no spec files found
        let defaultLevel = Level(
            levelNumber: 1,
            name: "First Contact",
            description: "Your first mission into deep space",
            numberOfWaves: 3,
            waves: [
                Level.Wave(
                    waveNumber: 1,
                    enemySpawnRate: 5,
                    asteroidSpawnRate: 10,
                    maxEnemies: 5,
                    maxAsteroids: 10,
                    enemyTypes: [
                        Level.EnemyType(type: "basic", health: 50, damage: 10, speed: 2, spawnWeight: 100)
                    ],
                    powerUpChance: 5,
                    duration: 60
                )
            ],
            backgroundImage: "spaceBackground",
            backgroundMusic: "background_music",
            boss: nil
        )
        levels[1] = defaultLevel
    }
}
