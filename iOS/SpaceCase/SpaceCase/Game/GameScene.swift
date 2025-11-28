//
//  GameScene.swift
//  SpaceCase
//
//  Main game scene using SpriteKit
//

import SpriteKit
import GameplayKit

class GameScene: SKScene {
    
    private var gameEngine: GameEngine?
    private var player: PlayerNode?
    private var background: SKSpriteNode?
    private var lastUpdateTime: TimeInterval = 0
    private var deltaTime: TimeInterval = 0
    
    var multiplayerManager: MultiplayerManager?
    let gameMode: GameMode
    
    // Expose gameEngine for entities
    var gameEngineRef: GameEngine? {
        return gameEngine
    }
    
    init(size: CGSize, gameMode: GameMode) {
        self.gameMode = gameMode
        super.init(size: size)
    }
    
    required init?(coder aDecoder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func didMove(to view: SKView) {
        setupScene()
    }
    
    private func setupScene() {
        backgroundColor = .black
        
        // Setup background
        setupBackground()
        
        // Initialize game engine
        gameEngine = GameEngine(scene: self, gameMode: gameMode)
        
        // Setup player
        if gameMode == .multiplayer {
            setupMultiplayerPlayers()
        } else {
            setupSinglePlayer()
        }
        
        // Setup physics world
        physicsWorld.gravity = CGVector.zero
        physicsWorld.contactDelegate = self
    }
    
    private func setupBackground() {
        // Scrolling background
        background = SKSpriteNode(imageNamed: "spaceBackground")
        background?.size = size
        background?.position = CGPoint(x: size.width / 2, y: size.height / 2)
        background?.zPosition = -1
        background?.name = "background"
        addChild(background!)
        
        // Create second background for seamless scrolling
        let background2 = SKSpriteNode(imageNamed: "spaceBackground")
        background2.size = size
        background2.position = CGPoint(x: size.width / 2, y: size.height * 1.5)
        background2.zPosition = -1
        background2.name = "background"
        addChild(background2)
    }
    
    private func setupSinglePlayer() {
        player = PlayerNode()
        player?.position = CGPoint(x: size.width / 2, y: 100)
        addChild(player!)
        gameEngine?.addPlayer(player!)
    }
    
    private func setupMultiplayerPlayers() {
        // Player 1
        let player1 = PlayerNode()
        player1.position = CGPoint(x: size.width / 4, y: 100)
        player1.name = "player1"
        addChild(player1)
        gameEngine?.addPlayer(player1)
        
        // Player 2
        let player2 = PlayerNode()
        player2.position = CGPoint(x: size.width * 3 / 4, y: 100)
        player2.name = "player2"
        addChild(player2)
        gameEngine?.addPlayer(player2)
    }
    
    override func update(_ currentTime: TimeInterval) {
        if lastUpdateTime == 0 {
            lastUpdateTime = currentTime
        }
        
        deltaTime = currentTime - lastUpdateTime
        lastUpdateTime = currentTime
        
        gameEngine?.update(deltaTime: deltaTime)
        scrollBackground()
    }
    
    private func scrollBackground() {
        enumerateChildNodes(withName: "background") { node, _ in
            node.position.y -= 3
            if node.position.y < -self.size.height {
                node.position.y = self.size.height * 1.5
            }
        }
    }
    
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let location = touch.location(in: self)
        
        // Handle touch for player movement or shooting
        player?.handleTouch(at: location)
    }
    
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        guard let touch = touches.first else { return }
        let location = touch.location(in: self)
        player?.moveTo(location)
    }
}

extension GameScene: SKPhysicsContactDelegate {
    func didBegin(_ contact: SKPhysicsContact) {
        gameEngine?.handleCollision(contact: contact)
    }
}
