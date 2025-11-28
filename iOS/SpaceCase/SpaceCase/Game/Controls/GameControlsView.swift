//
//  GameControlsView.swift
//  SpaceCase
//
//  Container for all game controls (joystick and buttons)
//

import SpriteKit

class GameControlsView: SKNode {
    
    private let joystick: JoystickNode
    private let fireButton: ControlButton
    private let specialButton: ControlButton
    
    weak var controlsDelegate: GameControlsDelegate?
    
    init(size: CGSize) {
        // Joystick on the left side
        joystick = JoystickNode()
        
        // Buttons arranged in diamond shape on the right
        // Diamond arrangement: fire button on top, special button on bottom
        fireButton = ControlButton(type: .fire)
        specialButton = ControlButton(type: .special)
        
        super.init()
        
        // Setup joystick
        joystick.delegate = self
        addChild(joystick)
        
        // Setup buttons
        fireButton.delegate = self
        specialButton.delegate = self
        addChild(fireButton)
        addChild(specialButton)
        
        // Position controls based on screen size (landscape orientation)
        updateControlPositions(size: size)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func updateControlPositions(size: CGSize) {
        // In landscape mode: width > height
        let isLandscape = size.width > size.height
        
        if isLandscape {
            // Joystick on left side, vertically centered
            joystick.position = CGPoint(x: 100, y: size.height / 2)
            
            // Buttons in diamond shape on right side
            // Fire button on top-right
            fireButton.position = CGPoint(x: size.width - 100, y: size.height / 2 + 60)
            // Special button on bottom-right
            specialButton.position = CGPoint(x: size.width - 100, y: size.height / 2 - 60)
        } else {
            // Portrait mode fallback
            joystick.position = CGPoint(x: 100, y: 100)
            fireButton.position = CGPoint(x: size.width - 100, y: size.height / 2 + 50)
            specialButton.position = CGPoint(x: size.width - 100, y: size.height / 2 - 50)
        }
    }
    
    func handleTouchBegan(_ touch: UITouch, location: CGPoint) -> Bool {
        // Check joystick first
        if joystick.handleTouchBegan(touch, location: location) {
            return true
        }
        
        // Check buttons
        if fireButton.handleTouchBegan(touch, location: location) {
            return true
        }
        if specialButton.handleTouchBegan(touch, location: location) {
            return true
        }
        
        return false
    }
    
    func handleTouchMoved(_ touch: UITouch, location: CGPoint) {
        joystick.handleTouchMoved(touch, location: location)
    }
    
    func handleTouchEnded(_ touch: UITouch) {
        joystick.handleTouchEnded(touch)
        fireButton.handleTouchEnded(touch)
        specialButton.handleTouchEnded(touch)
    }
    
    func updateControlPositions(size: CGSize) {
        // In landscape mode: width > height
        let isLandscape = size.width > size.height
        
        if isLandscape {
            // Joystick on left side, vertically centered
            joystick.position = CGPoint(x: 100, y: size.height / 2)
            
            // Buttons in diamond shape on right side
            // Fire button on top-right
            fireButton.position = CGPoint(x: size.width - 100, y: size.height / 2 + 60)
            // Special button on bottom-right
            specialButton.position = CGPoint(x: size.width - 100, y: size.height / 2 - 60)
        } else {
            // Portrait mode fallback
            joystick.position = CGPoint(x: 100, y: 100)
            fireButton.position = CGPoint(x: size.width - 100, y: size.height / 2 + 50)
            specialButton.position = CGPoint(x: size.width - 100, y: size.height / 2 - 50)
        }
    }
}

extension GameControlsView: JoystickDelegate {
    func joystick(_ joystick: JoystickNode, didUpdateVelocity velocity: CGVector) {
        controlsDelegate?.joystickDidUpdate(velocity: velocity)
    }
}

extension GameControlsView: ControlButtonDelegate {
    func controlButtonPressed(_ button: ControlButton) {
        switch button.buttonType {
        case .fire:
            controlsDelegate?.fireButtonPressed()
        case .special:
            controlsDelegate?.specialButtonPressed()
        default:
            break
        }
    }
    
    func controlButtonReleased(_ button: ControlButton) {
        switch button.buttonType {
        case .fire:
            controlsDelegate?.fireButtonReleased()
        case .special:
            controlsDelegate?.specialButtonReleased()
        default:
            break
        }
    }
}

protocol GameControlsDelegate: AnyObject {
    func joystickDidUpdate(velocity: CGVector)
    func fireButtonPressed()
    func fireButtonReleased()
    func specialButtonPressed()
    func specialButtonReleased()
}
