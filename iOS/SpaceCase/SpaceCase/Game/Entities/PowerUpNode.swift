//
//  PowerUpNode.swift
//  SpaceCase
//
//  Power-up entity
//

import SpriteKit

enum PowerUpType {
    case health
    case firePower
    case shield
    case speed
}

class PowerUpNode: SKSpriteNode {
    
    let type: PowerUpType
    
    init(type: PowerUpType) {
        self.type = type
        
        let imageName: String
        switch type {
        case .health:
            imageName = "healthPU"
        case .firePower:
            imageName = "firePowerPU"
        case .shield:
            imageName = "shieldPU"
        case .speed:
            imageName = "speedPU"
        }
        
        let texture = SKTexture(imageNamed: imageName)
        super.init(texture: texture, color: .white, size: CGSize(width: 30, height: 30))
        setupPhysics()
        
        // Pulsing animation
        let pulseUp = SKAction.scale(to: 1.2, duration: 0.5)
        let pulseDown = SKAction.scale(to: 1.0, duration: 0.5)
        run(SKAction.repeatForever(SKAction.sequence([pulseUp, pulseDown])))
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupPhysics() {
        physicsBody = SKPhysicsBody(circleOfRadius: size.width / 2)
        physicsBody?.categoryBitMask = PhysicsCategory.powerUp
        physicsBody?.contactTestBitMask = PhysicsCategory.player
        physicsBody?.collisionBitMask = PhysicsCategory.none
        physicsBody?.isDynamic = false
    }
    
    func update(deltaTime: TimeInterval) {
        position.y -= 2
    }
}
