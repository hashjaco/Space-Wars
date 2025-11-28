//
//  Level.swift
//  SpaceCase
//
//  Level data structure
//

import Foundation

struct Level: Codable {
    let levelNumber: Int
    let name: String
    let description: String
    let numberOfWaves: Int
    let waves: [Wave]
    let backgroundImage: String
    let backgroundMusic: String
    let boss: Boss?
    
    struct Wave: Codable {
        let waveNumber: Int
        let enemySpawnRate: Int // 0-1000, higher = more frequent
        let asteroidSpawnRate: Int // 0-1000
        let maxEnemies: Int
        let maxAsteroids: Int
        let enemyTypes: [EnemyType]
        let powerUpChance: Int // 0-100
        let duration: TimeInterval // seconds
    }
    
    struct EnemyType: Codable {
        let type: String
        let health: Int
        let damage: Int
        let speed: CGFloat
        let spawnWeight: Int // Relative spawn probability
    }
    
    struct Boss: Codable {
        let name: String
        let health: Int
        let damage: Int
        let attackPatterns: [String]
        let spriteName: String
    }
}
