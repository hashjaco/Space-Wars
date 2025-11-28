//
//  JoystickNode.swift
//  SpaceCase
//
//  Virtual joystick control for player movement
//

import SpriteKit

protocol JoystickDelegate: AnyObject {
    func joystick(_ joystick: JoystickNode, didUpdateVelocity velocity: CGVector)
}

class JoystickNode: SKNode {
    
    private let baseNode: SKSpriteNode
    private let stickNode: SKSpriteNode
    private let radius: CGFloat = 60.0
    private var isActive = false
    private var currentTouch: UITouch?
    
    weak var delegate: JoystickDelegate?
    
    var velocity: CGVector = .zero {
        didSet {
            delegate?.joystick(self, didUpdateVelocity: velocity)
        }
    }
    
    override init() {
        // Base (outer circle)
        baseNode = SKSpriteNode(imageNamed: "joystick_base")
        if baseNode.texture == nil {
            // Create a simple base if image doesn't exist
            baseNode.color = UIColor.white.withAlphaComponent(0.3)
            baseNode.size = CGSize(width: radius * 2, height: radius * 2)
        }
        baseNode.alpha = 0.5
        
        // Stick (inner circle)
        stickNode = SKSpriteNode(imageNamed: "joystick_stick")
        if stickNode.texture == nil {
            // Create a simple stick if image doesn't exist
            stickNode.color = UIColor.white.withAlphaComponent(0.6)
            stickNode.size = CGSize(width: radius * 0.6, height: radius * 0.6)
        }
        stickNode.alpha = 0.8
        
        super.init()
        
        addChild(baseNode)
        addChild(stickNode)
        
        // Reset stick position
        stickNode.position = .zero
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    func handleTouchBegan(_ touch: UITouch, location: CGPoint) -> Bool {
        let localLocation = convert(location, from: parent!)
        let distance = sqrt(localLocation.x * localLocation.x + localLocation.y * localLocation.y)
        
        // Check if touch is within joystick area
        if distance <= radius * 1.5 {
            isActive = true
            currentTouch = touch
            updateStickPosition(location: localLocation)
            return true
        }
        return false
    }
    
    func handleTouchMoved(_ touch: UITouch, location: CGPoint) {
        guard isActive, currentTouch == touch else { return }
        let localLocation = convert(location, from: parent!)
        updateStickPosition(location: localLocation)
    }
    
    func handleTouchEnded(_ touch: UITouch) {
        guard isActive, currentTouch == touch else { return }
        isActive = false
        currentTouch = nil
        resetStick()
    }
    
    private func updateStickPosition(location: CGPoint) {
        let distance = sqrt(location.x * location.x + location.y * location.y)
        let angle = atan2(location.y, location.x)
        
        // Clamp stick to radius
        let clampedDistance = min(distance, radius)
        stickNode.position = CGPoint(
            x: cos(angle) * clampedDistance,
            y: sin(angle) * clampedDistance
        )
        
        // Calculate normalized velocity (-1 to 1)
        let normalizedX = clampedDistance > 0 ? cos(angle) * (clampedDistance / radius) : 0
        let normalizedY = clampedDistance > 0 ? sin(angle) * (clampedDistance / radius) : 0
        
        velocity = CGVector(dx: normalizedX, dy: normalizedY)
    }
    
    private func resetStick() {
        let resetAction = SKAction.move(to: .zero, duration: 0.2)
        resetAction.timingMode = .easeOut
        stickNode.run(resetAction)
        velocity = .zero
    }
}
