//
//  GameEngine.swift
//  SpaceCase
//
//  Core game engine managing game state, entities, and logic
//

import SpriteKit
import GameplayKit

class GameEngine {
    
    private weak var scene: SKScene?
    private var players: [PlayerNode] = []
    private var enemies: [EnemyNode] = []
    private var bullets: [BulletNode] = []
    private var asteroids: [AsteroidNode] = []
    private var powerUps: [PowerUpNode] = []
    
    private var currentLevel: Level?
    private var levelManager: LevelManager
    private var score: Int = 0
    private var waveNumber: Int = 0
    private var currentWave: Level.Wave?
    
    let gameMode: GameMode
    
    init(scene: SKScene, gameMode: GameMode) {
        self.scene = scene
        self.gameMode = gameMode
        self.levelManager = LevelManager()
        
        if gameMode == .story {
            loadStoryLevel(levelNumber: 1)
        }
    }
    
    func addPlayer(_ player: PlayerNode) {
        players.append(player)
    }
    
    func update(deltaTime: TimeInterval) {
        // Update all game entities
        updateEnemies(deltaTime: deltaTime)
        updateBullets(deltaTime: deltaTime)
        updateAsteroids(deltaTime: deltaTime)
        updatePowerUps(deltaTime: deltaTime)
        spawnEntities()
        checkWaveCompletion()
    }
    
    private func updateEnemies(deltaTime: TimeInterval) {
        enemies.forEach { enemy in
            enemy.update(deltaTime: deltaTime)
            
            // Enemy AI: engage nearest player
            if let nearestPlayer = findNearestPlayer(to: enemy.position) {
                enemy.engage(target: nearestPlayer)
                
                // Enemy shooting logic
                if enemy.canFire() {
                    let bullet = enemy.fire()
                    bullets.append(bullet)
                    scene?.addChild(bullet)
                }
            }
        }
        
        // Remove dead enemies
        enemies.removeAll { enemy in
            if enemy.isDead {
                enemy.removeFromParent()
                return true
            }
            return false
        }
    }
    
    private func updateBullets(deltaTime: TimeInterval) {
        bullets.forEach { bullet in
            bullet.update(deltaTime: deltaTime)
        }
        
        // Remove bullets that are off screen
        bullets.removeAll { bullet in
            if bullet.position.y < 0 || bullet.position.y > (scene?.size.height ?? 0) {
                bullet.removeFromParent()
                return true
            }
            return false
        }
    }
    
    private func updateAsteroids(deltaTime: TimeInterval) {
        asteroids.forEach { asteroid in
            asteroid.update(deltaTime: deltaTime)
        }
        
        asteroids.removeAll { asteroid in
            if asteroid.isDead || asteroid.position.y < 0 {
                asteroid.removeFromParent()
                return true
            }
            return false
        }
    }
    
    private func updatePowerUps(deltaTime: TimeInterval) {
        powerUps.forEach { powerUp in
            powerUp.update(deltaTime: deltaTime)
        }
        
        powerUps.removeAll { powerUp in
            if powerUp.position.y < 0 {
                powerUp.removeFromParent()
                return true
            }
            return false
        }
    }
    
    private func spawnEntities() {
        guard let level = currentLevel, let wave = currentWave else { return }
        
        // Spawn enemies based on level spec
        if enemies.count < wave.maxEnemies {
            if Int.random(in: 0...1000) < wave.enemySpawnRate {
                spawnEnemy(from: wave)
            }
        }
        
        // Spawn asteroids
        if asteroids.count < wave.maxAsteroids {
            if Int.random(in: 0...1000) < wave.asteroidSpawnRate {
                spawnAsteroid()
            }
        }
        
        // Spawn power-ups
        if Int.random(in: 0...100) < wave.powerUpChance {
            spawnPowerUp()
        }
    }
    
    private func spawnEnemy(from wave: Level.Wave) {
        guard let scene = scene else { return }
        
        // Select enemy type based on spawn weights
        let totalWeight = wave.enemyTypes.reduce(0) { $0 + $1.spawnWeight }
        var random = Int.random(in: 0..<totalWeight)
        
        var selectedType: Level.EnemyType?
        for enemyType in wave.enemyTypes {
            if random < enemyType.spawnWeight {
                selectedType = enemyType
                break
            }
            random -= enemyType.spawnWeight
        }
        
        guard let type = selectedType else { return }
        
        let enemy = EnemyNode()
        enemy.position = CGPoint(
            x: CGFloat.random(in: 50...(scene.size.width - 50)),
            y: scene.size.height + 50
        )
        // Apply enemy type properties
        enemy.damage = type.damage
        enemies.append(enemy)
        scene.addChild(enemy)
    }
    
    private func spawnAsteroid() {
        guard let scene = scene else { return }
        let asteroid = AsteroidNode()
        asteroid.position = CGPoint(
            x: CGFloat.random(in: 50...(scene.size.width - 50)),
            y: scene.size.height + 50
        )
        asteroids.append(asteroid)
        scene.addChild(asteroid)
    }
    
    private func spawnPowerUp() {
        guard let scene = scene else { return }
        let types: [PowerUpType] = [.health, .firePower, .shield, .speed]
        let randomType = types.randomElement() ?? .health
        let powerUp = PowerUpNode(type: randomType)
        powerUp.position = CGPoint(
            x: CGFloat.random(in: 50...(scene.size.width - 50)),
            y: scene.size.height + 50
        )
        powerUps.append(powerUp)
        scene.addChild(powerUp)
    }
    
    private func findNearestPlayer(to position: CGPoint) -> PlayerNode? {
        return players.min { player1, player2 in
            let dist1 = distance(from: position, to: player1.position)
            let dist2 = distance(from: position, to: player2.position)
            return dist1 < dist2
        }
    }
    
    private func distance(from: CGPoint, to: CGPoint) -> CGFloat {
        let dx = to.x - from.x
        let dy = to.y - from.y
        return sqrt(dx * dx + dy * dy)
    }
    
    func handleCollision(contact: SKPhysicsContact) {
        let bodyA = contact.bodyA
        let bodyB = contact.bodyB
        
        // Player-Bullet collisions
        if let player = bodyA.node as? PlayerNode,
           let bullet = bodyB.node as? BulletNode,
           bullet.isEnemyBullet {
            player.takeDamage(bullet.damage)
            bullet.removeFromParent()
            bullets.removeAll { $0 == bullet }
        } else if let bullet = bodyA.node as? BulletNode,
                  let player = bodyB.node as? PlayerNode,
                  bullet.isEnemyBullet {
            player.takeDamage(bullet.damage)
            bullet.removeFromParent()
            bullets.removeAll { $0 == bullet }
        }
        
        // Bullet-Enemy collisions
        if let bullet = bodyA.node as? BulletNode,
           let enemy = bodyB.node as? EnemyNode,
           !bullet.isEnemyBullet {
            enemy.takeDamage(bullet.damage)
            bullet.removeFromParent()
            bullets.removeAll { $0 == bullet }
            if enemy.isDead {
                score += 20
                // Award score to player who shot
                if let player = players.first {
                    player.addScore(20)
                }
            }
        } else if let enemy = bodyA.node as? EnemyNode,
                  let bullet = bodyB.node as? BulletNode,
                  !bullet.isEnemyBullet {
            enemy.takeDamage(bullet.damage)
            bullet.removeFromParent()
            bullets.removeAll { $0 == bullet }
            if enemy.isDead {
                score += 20
                if let player = players.first {
                    player.addScore(20)
                }
            }
        }
        
        // Bullet-Asteroid collisions
        if let bullet = bodyA.node as? BulletNode,
           let asteroid = bodyB.node as? AsteroidNode,
           !bullet.isEnemyBullet {
            asteroid.takeDamage(bullet.damage)
            bullet.removeFromParent()
            bullets.removeAll { $0 == bullet }
            if asteroid.isDead {
                score += 10
                if let player = players.first {
                    player.addScore(10)
                }
            }
        } else if let asteroid = bodyA.node as? AsteroidNode,
                  let bullet = bodyB.node as? BulletNode,
                  !bullet.isEnemyBullet {
            asteroid.takeDamage(bullet.damage)
            bullet.removeFromParent()
            bullets.removeAll { $0 == bullet }
            if asteroid.isDead {
                score += 10
                if let player = players.first {
                    player.addScore(10)
                }
            }
        }
        
        // Player-Asteroid collisions
        if let player = bodyA.node as? PlayerNode,
           let asteroid = bodyB.node as? AsteroidNode {
            player.takeDamage(asteroid.damage)
            asteroid.removeFromParent()
            asteroids.removeAll { $0 == asteroid }
        } else if let asteroid = bodyA.node as? AsteroidNode,
                  let player = bodyB.node as? PlayerNode {
            player.takeDamage(asteroid.damage)
            asteroid.removeFromParent()
            asteroids.removeAll { $0 == asteroid }
        }
        
        // Player-PowerUp collisions
        if let player = bodyA.node as? PlayerNode,
           let powerUp = bodyB.node as? PowerUpNode {
            applyPowerUp(powerUp.type, to: player)
            powerUp.removeFromParent()
            powerUps.removeAll { $0 == powerUp }
        } else if let powerUp = bodyA.node as? PowerUpNode,
                  let player = bodyB.node as? PlayerNode {
            applyPowerUp(powerUp.type, to: player)
            powerUp.removeFromParent()
            powerUps.removeAll { $0 == powerUp }
        }
    }
    
    private func applyPowerUp(_ type: PowerUpType, to player: PlayerNode) {
        switch type {
        case .health:
            // Restore health (implementation depends on PlayerNode)
            break
        case .firePower:
            // Increase fire power (implementation depends on PlayerNode)
            break
        case .shield:
            // Add temporary shield
            break
        case .speed:
            // Increase movement speed
            break
        }
    }
    
    private func checkWaveCompletion() {
        guard let level = currentLevel else { return }
        
        if enemies.isEmpty && asteroids.isEmpty && currentWave != nil {
            waveNumber += 1
            if waveNumber >= level.numberOfWaves {
                completeLevel()
            } else {
                startNextWave()
            }
        }
    }
    
    private func startNextWave() {
        guard let level = currentLevel, waveNumber < level.waves.count else { return }
        currentWave = level.waves[waveNumber]
    }
    
    private func completeLevel() {
        // Level completion logic
        if gameMode == .story {
            // Load next story level or show completion screen
            print("Level completed!")
        }
    }
    
    func loadStoryLevel(levelNumber: Int) {
        currentLevel = levelManager.loadLevel(levelNumber: levelNumber)
        waveNumber = 0
        if let level = currentLevel, !level.waves.isEmpty {
            currentWave = level.waves[0]
        }
    }
    
    func addBullet(_ bullet: BulletNode) {
        bullets.append(bullet)
    }
}
