//
//  EnemyNode.swift
//  SpaceCase
//
//  Enemy ship entity with AI
//

import SpriteKit

class EnemyNode: SKSpriteNode {
    
    private var health: Int = 50
    private var maxHealth: Int = 50
    private var lastFireTime: TimeInterval = 0
    private let fireCooldown: TimeInterval = 1.0
    private var target: PlayerNode?
    
    var isDead: Bool {
        return health <= 0
    }
    
    var damage: Int = 10
    
    override init(texture: SKTexture?, color: UIColor, size: CGSize) {
        let texture = SKTexture(imageNamed: "enemyShip")
        super.init(texture: texture, color: .white, size: CGSize(width: 40, height: 40))
        setupPhysics()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    convenience init() {
        self.init(texture: nil, color: .white, size: CGSize(width: 40, height: 40))
    }
    
    private func setupPhysics() {
        physicsBody = SKPhysicsBody(rectangleOf: size)
        physicsBody?.categoryBitMask = PhysicsCategory.enemy
        physicsBody?.contactTestBitMask = PhysicsCategory.bullet
        physicsBody?.collisionBitMask = PhysicsCategory.none
        physicsBody?.isDynamic = true
    }
    
    func update(deltaTime: TimeInterval) {
        // Move downward
        position.y -= 2
        
        // Remove if off screen
        if position.y < -50 {
            removeFromParent()
        }
    }
    
    func engage(target: PlayerNode) {
        self.target = target
        
        // Move towards target horizontally
        let dx = target.position.x - position.x
        if abs(dx) > 5 {
            let moveX = dx > 0 ? 1 : -1
            position.x += CGFloat(moveX)
        }
    }
    
    func canFire() -> Bool {
        guard let target = target else { return false }
        let currentTime = CACurrentMediaTime()
        
        // Only fire if aligned with target and cooldown is ready
        let aligned = abs(target.position.x - position.x) < 10
        let cooldownReady = currentTime - lastFireTime >= fireCooldown
        
        return aligned && cooldownReady
    }
    
    func fire() -> BulletNode {
        lastFireTime = CACurrentMediaTime()
        let bullet = BulletNode(isEnemyBullet: true, damage: damage)
        bullet.position = CGPoint(x: position.x, y: position.y - size.height / 2)
        bullet.zPosition = 1
        return bullet
    }
    
    func takeDamage(_ damage: Int) {
        health = max(0, health - damage)
        
        if health <= 0 {
            // Spawn explosion animation
            spawnExplosion()
            removeFromParent()
        }
    }
    
    private func spawnExplosion() {
        // Create explosion animation
        let explosion = SKSpriteNode(imageNamed: "explosion1")
        explosion.position = position
        explosion.zPosition = 10
        parent?.addChild(explosion)
        
        let textures = (1...25).compactMap { SKTexture(imageNamed: "explosion\($0)") }
        let animation = SKAction.animate(with: textures, timePerFrame: 0.05)
        let remove = SKAction.removeFromParent()
        explosion.run(SKAction.sequence([animation, remove]))
        
        SoundManager.shared.playSound(.explosion)
    }
}
