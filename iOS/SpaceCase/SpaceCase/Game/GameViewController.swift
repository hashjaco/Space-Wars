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
        
        // Ensure landscape size
        let landscapeSize = CGSize(
            width: max(view.bounds.width, view.bounds.height),
            height: min(view.bounds.width, view.bounds.height)
        )
        
        gameScene = GameScene(size: landscapeSize, gameMode: gameMode)
        gameScene?.scaleMode = .aspectFill
        
        if gameMode == .multiplayer {
            multiplayerManager = MultiplayerManager()
            multiplayerManager?.delegate = gameScene
            gameScene?.multiplayerManager = multiplayerManager
        }
        
        skView.presentScene(gameScene)
    }
    
    override func viewWillLayoutSubviews() {
        super.viewWillLayoutSubviews()
        
        // Update scene size when layout changes
        if let skView = view.subviews.first as? SKView,
           let scene = gameScene {
            let landscapeSize = CGSize(
                width: max(view.bounds.width, view.bounds.height),
                height: min(view.bounds.width, view.bounds.height)
            )
            scene.size = landscapeSize
        }
    }
    
    override var prefersStatusBarHidden: Bool {
        return true
    }
    
    // MARK: - Orientation Support
    
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        return .landscape
    }
    
    override var preferredInterfaceOrientationForPresentation: UIInterfaceOrientation {
        return .landscapeLeft
    }
    
    override var shouldAutorotate: Bool {
        return false
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        
        // Force landscape orientation
        if #available(iOS 16.0, *) {
            setNeedsUpdateOfSupportedInterfaceOrientations()
        } else {
            // For iOS 15 and earlier
            let value = UIInterfaceOrientation.landscapeLeft.rawValue
            UIDevice.current.setValue(value, forKey: "orientation")
        }
    }
}
