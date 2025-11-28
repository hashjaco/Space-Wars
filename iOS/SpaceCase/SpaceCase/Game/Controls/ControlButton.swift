//
//  ControlButton.swift
//  SpaceCase
//
//  Virtual button control for game actions
//

import SpriteKit

protocol ControlButtonDelegate: AnyObject {
    func controlButtonPressed(_ button: ControlButton)
    func controlButtonReleased(_ button: ControlButton)
}

class ControlButton: SKNode {
    
    private let buttonNode: SKSpriteNode
    private let labelNode: SKLabelNode?
    private let buttonSize: CGSize
    private var isPressed = false
    
    weak var delegate: ControlButtonDelegate?
    let buttonType: ButtonType
    
    enum ButtonType {
        case fire
        case special
        case bomb
        case shield
        
        var label: String {
            switch self {
            case .fire: return "FIRE"
            case .special: return "SP"
            case .bomb: return "BOMB"
            case .shield: return "SHLD"
            }
        }
    }
    
    init(type: ButtonType, size: CGSize = CGSize(width: 70, height: 70)) {
        self.buttonType = type
        self.buttonSize = size
        
        // Button sprite
        buttonNode = SKSpriteNode(imageNamed: "button_\(type)")
        if buttonNode.texture == nil {
            // Create a simple button if image doesn't exist
            buttonNode.color = UIColor.systemBlue.withAlphaComponent(0.7)
            buttonNode.size = size
        }
        buttonNode.alpha = 0.8
        
        // Label
        labelNode = SKLabelNode(text: type.label)
        labelNode?.fontName = "Avenir-Black"
        labelNode?.fontSize = 18
        labelNode?.fontColor = .white
        labelNode?.verticalAlignmentMode = .center
        labelNode?.horizontalAlignmentMode = .center
        
        super.init()
        
        addChild(buttonNode)
        if let label = labelNode {
            addChild(label)
        }
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    func handleTouchBegan(_ touch: UITouch, location: CGPoint) -> Bool {
        let localLocation = convert(location, from: parent!)
        let distance = sqrt(localLocation.x * localLocation.x + localLocation.y * localLocation.y)
        
        // Check if touch is within button area
        if distance <= buttonSize.width / 2 {
            press()
            return true
        }
        return false
    }
    
    func handleTouchEnded(_ touch: UITouch) {
        release()
    }
    
    private func press() {
        guard !isPressed else { return }
        isPressed = true
        
        // Visual feedback
        let scaleDown = SKAction.scale(to: 0.9, duration: 0.1)
        buttonNode.run(scaleDown)
        buttonNode.alpha = 1.0
        
        delegate?.controlButtonPressed(self)
    }
    
    private func release() {
        guard isPressed else { return }
        isPressed = false
        
        // Visual feedback
        let scaleUp = SKAction.scale(to: 1.0, duration: 0.1)
        buttonNode.run(scaleUp)
        buttonNode.alpha = 0.8
        
        delegate?.controlButtonReleased(self)
    }
    
    func isPointInside(_ point: CGPoint) -> Bool {
        let localPoint = convert(point, from: parent!)
        let distance = sqrt(localPoint.x * localPoint.x + localPoint.y * localPoint.y)
        return distance <= buttonSize.width / 2
    }
}
