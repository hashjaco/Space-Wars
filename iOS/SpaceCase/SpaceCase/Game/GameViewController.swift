//
//  GameViewController.swift
//  SpaceCase
//
//  Main game view controller using SpriteKit
//

import UIKit
import SpriteKit

enum GameMode {
    case story
    case multiplayer
    case arcade
}

class GameViewController: UIViewController {
    
    private var gameScene: GameScene?
    private let gameMode: GameMode
    private var multiplayerManager: MultiplayerManager?
    
    init(gameMode: GameMode) {
        self.gameMode = gameMode
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupGameScene()
    }
    
    private func setupGameScene() {
        let skView = SKView(frame: view.bounds)
        skView.ignoresSiblingOrder = true
        skView.showsFPS = true
        skView.showsNodeCount = true
        view.addSubview(skView)
        
        gameScene = GameScene(size: view.bounds.size, gameMode: gameMode)
        gameScene?.scaleMode = .aspectFill
        
        if gameMode == .multiplayer {
            multiplayerManager = MultiplayerManager()
            multiplayerManager?.delegate = gameScene
            gameScene?.multiplayerManager = multiplayerManager
        }
        
        skView.presentScene(gameScene)
    }
    
    override var prefersStatusBarHidden: Bool {
        return true
    }
}
