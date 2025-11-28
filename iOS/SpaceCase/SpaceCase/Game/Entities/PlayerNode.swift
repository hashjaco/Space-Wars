//
//  PlayerNode.swift
//  SpaceCase
//
//  Player ship entity
//

import SpriteKit
import GameplayKit

class PlayerNode: SKSpriteNode {
    
    private var health: Int = 100
    private var maxHealth: Int = 100
    private var lives: Int = 3
    private var score: Int = 0
    private var firePowerLevel: Int = 0
    private var lastFireTime: TimeInterval = 0
    private let fireCooldown: TimeInterval = 0.3
    private var isFiring = false
    private var movementVelocity: CGVector = .zero
    private let movementSpeed: CGFloat = 300.0
    
    var isDead: Bool {
        return health <= 0 && lives <= 0
    }
    
    override init(texture: SKTexture?, color: UIColor, size: CGSize) {
        let texture = SKTexture(imageNamed: "playerShip")
        super.init(texture: texture, color: .white, size: CGSize(width: 50, height: 50))
        setupPhysics()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    convenience init() {
        self.init(texture: nil, color: .white, size: CGSize(width: 50, height: 50))
    }
    
    private func setupPhysics() {
        physicsBody = SKPhysicsBody(rectangleOf: size)
        physicsBody?.categoryBitMask = PhysicsCategory.player
        physicsBody?.contactTestBitMask = PhysicsCategory.enemy | PhysicsCategory.asteroid | PhysicsCategory.enemyBullet
        physicsBody?.collisionBitMask = PhysicsCategory.none
        physicsBody?.isDynamic = true
    }
    
    // MARK: - Joystick Control
    
    func setVelocity(_ velocity: CGVector) {
        movementVelocity = velocity
    }
    
    func updateMovement(deltaTime: TimeInterval) {
        // Apply movement based on joystick velocity
        let moveX = movementVelocity.dx * movementSpeed * CGFloat(deltaTime)
        let moveY = movementVelocity.dy * movementSpeed * CGFloat(deltaTime)
        
        // Clamp position to screen bounds
        let newX = max(size.width / 2, min((scene?.size.width ?? 0) - size.width / 2, position.x + moveX))
        let newY = max(size.height / 2, min((scene?.size.height ?? 0) - size.height / 2, position.y + moveY))
        
        position = CGPoint(x: newX, y: newY)
    }
    
    // MARK: - Fire Control
    
    func startFiring() {
        isFiring = true
        fire() // Fire immediately
    }
    
    func stopFiring() {
        isFiring = false
    }
    
    func updateFiring(deltaTime: TimeInterval) {
        guard isFiring else { return }
        fire()
    }
    
    private func fire() {
        let currentTime = CACurrentMediaTime()
        guard currentTime - lastFireTime >= fireCooldown else { return }
        lastFireTime = currentTime
        
        let bullet = BulletNode(isEnemyBullet: false, damage: 10 + firePowerLevel * 5)
        bullet.position = CGPoint(x: position.x, y: position.y + size.height / 2)
        bullet.zPosition = 1
        
        parent?.addChild(bullet)
        
        // Notify game engine
        if let scene = scene as? GameScene {
            scene.gameEngineRef?.addBullet(bullet)
        }
        
        // Play sound effect
        SoundManager.shared.playSound(.laser)
    }
    
    // MARK: - Special Abilities
    
    func useSpecialAbility() {
        // Implement special ability (e.g., bomb, shield, etc.)
        // For now, fire a powerful burst
        fireBurst()
    }
    
    private func fireBurst() {
        // Fire multiple bullets in a spread pattern
        for i in -1...1 {
            let bullet = BulletNode(isEnemyBullet: false, damage: 15 + firePowerLevel * 5)
            bullet.position = CGPoint(x: position.x + CGFloat(i * 10), y: position.y + size.height / 2)
            bullet.zPosition = 1
            parent?.addChild(bullet)
            
            if let scene = scene as? GameScene {
                scene.gameEngineRef?.addBullet(bullet)
            }
        }
        SoundManager.shared.playSound(.laser)
    }
    
    func takeDamage(_ damage: Int) {
        health = max(0, health - damage)
        
        if health <= 0 {
            lives -= 1
            if lives > 0 {
                respawn()
            } else {
                die()
            }
        }
    }
    
    private func respawn() {
        health = maxHealth
        // Add invincibility period
        alpha = 0.5
        let fadeIn = SKAction.fadeAlpha(to: 1.0, duration: 2.0)
        run(fadeIn)
    }
    
    private func die() {
        // Game over logic
        removeFromParent()
    }
    
    func addScore(_ points: Int) {
        score += points
    }
}

struct PhysicsCategory {
    static let none: UInt32 = 0
    static let player: UInt32 = 0b1
    static let enemy: UInt32 = 0b10
    static let bullet: UInt32 = 0b100
    static let enemyBullet: UInt32 = 0b1000
    static let asteroid: UInt32 = 0b10000
    static let powerUp: UInt32 = 0b100000
}
