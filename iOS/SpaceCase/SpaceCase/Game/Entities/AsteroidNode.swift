//
//  AsteroidNode.swift
//  SpaceCase
//
//  Asteroid entity
//

import SpriteKit

class AsteroidNode: SKSpriteNode {
    
    private var health: Int = 20
    private var maxHealth: Int = 20
    
    var isDead: Bool {
        return health <= 0
    }
    
    var damage: Int = 10
    
    override init(texture: SKTexture?, color: UIColor, size: CGSize) {
        let texture = SKTexture(imageNamed: "asteroid")
        super.init(texture: texture, color: .white, size: CGSize(width: 60, height: 60))
        setupPhysics()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    convenience init() {
        self.init(texture: nil, color: .white, size: CGSize(width: 60, height: 60))
    }
    
    private func setupPhysics() {
        physicsBody = SKPhysicsBody(circleOfRadius: size.width / 2)
        physicsBody?.categoryBitMask = PhysicsCategory.asteroid
        physicsBody?.contactTestBitMask = PhysicsCategory.player | PhysicsCategory.bullet
        physicsBody?.collisionBitMask = PhysicsCategory.none
        physicsBody?.isDynamic = true
        
        // Random rotation
        let rotate = SKAction.rotate(byAngle: CGFloat.random(in: -CGFloat.pi...CGFloat.pi), duration: 1.0)
        run(SKAction.repeatForever(rotate))
    }
    
    func update(deltaTime: TimeInterval) {
        // Move downward with slight horizontal drift
        position.y -= 3
        position.x += CGFloat.random(in: -1...1)
    }
    
    func takeDamage(_ damage: Int) {
        health = max(0, health - damage)
        
        if health <= 0 {
            spawnExplosion()
            removeFromParent()
        }
    }
    
    private func spawnExplosion() {
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
