//
//  BulletNode.swift
//  SpaceCase
//
//  Bullet entity
//

import SpriteKit

class BulletNode: SKSpriteNode {
    
    let isEnemyBullet: Bool
    let damage: Int
    private let speed: CGFloat = 500
    
    init(isEnemyBullet: Bool, damage: Int) {
        self.isEnemyBullet = isEnemyBullet
        self.damage = damage
        
        let texture = isEnemyBullet ? 
            SKTexture(imageNamed: "enemyBullet") : 
            SKTexture(imageNamed: "playerBullet")
        
        super.init(texture: texture, color: .white, size: CGSize(width: 10, height: 20))
        setupPhysics()
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupPhysics() {
        physicsBody = SKPhysicsBody(rectangleOf: size)
        physicsBody?.categoryBitMask = isEnemyBullet ? PhysicsCategory.enemyBullet : PhysicsCategory.bullet
        physicsBody?.contactTestBitMask = isEnemyBullet ? PhysicsCategory.player : PhysicsCategory.enemy | PhysicsCategory.asteroid
        physicsBody?.collisionBitMask = PhysicsCategory.none
        physicsBody?.isDynamic = true
    }
    
    func update(deltaTime: TimeInterval) {
        let direction: CGFloat = isEnemyBullet ? 1 : -1
        position.y += speed * CGFloat(deltaTime) * direction
    }
}
