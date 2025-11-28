//
//  MultiplayerLobbyViewController.swift
//  SpaceCase
//
//  Lobby for finding and connecting to nearby players
//

import UIKit
import MultipeerConnectivity

class MultiplayerLobbyViewController: UIViewController {
    
    private var multiplayerManager: MultiplayerManager?
    private let statusLabel = UILabel()
    private let connectedPeersLabel = UILabel()
    private let startButton = UIButton(type: .system)
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        setupMultiplayer()
    }
    
    private func setupUI() {
        view.backgroundColor = .black
        
        statusLabel.text = "Searching for nearby players..."
        statusLabel.textColor = .white
        statusLabel.textAlignment = .center
        statusLabel.font = UIFont.systemFont(ofSize: 18)
        statusLabel.translatesAutoresizingMaskIntoConstraints = false
        
        connectedPeersLabel.text = "Connected: 0"
        connectedPeersLabel.textColor = .white
        connectedPeersLabel.textAlignment = .center
        connectedPeersLabel.font = UIFont.systemFont(ofSize: 16)
        connectedPeersLabel.translatesAutoresizingMaskIntoConstraints = false
        
        startButton.setTitle("Start Game", for: .normal)
        startButton.setTitleColor(.white, for: .normal)
        startButton.backgroundColor = .systemBlue
        startButton.layer.cornerRadius = 12
        startButton.isEnabled = false
        startButton.addTarget(self, action: #selector(startGame), for: .touchUpInside)
        startButton.translatesAutoresizingMaskIntoConstraints = false
        
        let backButton = UIButton(type: .system)
        backButton.setTitle("Back", for: .normal)
        backButton.setTitleColor(.white, for: .normal)
        backButton.addTarget(self, action: #selector(goBack), for: .touchUpInside)
        backButton.translatesAutoresizingMaskIntoConstraints = false
        
        view.addSubview(statusLabel)
        view.addSubview(connectedPeersLabel)
        view.addSubview(startButton)
        view.addSubview(backButton)
        
        NSLayoutConstraint.activate([
            statusLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            statusLabel.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 100),
            
            connectedPeersLabel.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            connectedPeersLabel.topAnchor.constraint(equalTo: statusLabel.bottomAnchor, constant: 20),
            
            startButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            startButton.topAnchor.constraint(equalTo: connectedPeersLabel.bottomAnchor, constant: 40),
            startButton.widthAnchor.constraint(equalToConstant: 200),
            startButton.heightAnchor.constraint(equalToConstant: 50),
            
            backButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            backButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20)
        ])
    }
    
    private func setupMultiplayer() {
        multiplayerManager = MultiplayerManager()
        multiplayerManager?.delegate = self
    }
    
    @objc private func startGame() {
        guard multiplayerManager?.isConnected == true else { return }
        
        let gameVC = GameViewController(gameMode: .multiplayer)
        gameVC.modalPresentationStyle = .fullScreen
        present(gameVC, animated: true)
    }
    
    @objc private func goBack() {
        dismiss(animated: true)
    }
}

extension MultiplayerLobbyViewController: MultiplayerManagerDelegate {
    func didConnect(to peer: MCPeerID) {
        DispatchQueue.main.async {
            self.statusLabel.text = "Connected to \(peer.displayName)"
            self.connectedPeersLabel.text = "Connected: 1"
            self.startButton.isEnabled = true
        }
    }
    
    func didDisconnect(from peer: MCPeerID) {
        DispatchQueue.main.async {
            self.statusLabel.text = "Disconnected from \(peer.displayName)"
            self.connectedPeersLabel.text = "Connected: 0"
            self.startButton.isEnabled = false
        }
    }
    
    func didReceivePlayerPosition(_ position: CGPoint, from peerID: MCPeerID) {
        // Handle in game scene
    }
    
    func didReceiveBullet(_ bullet: BulletNode, from peerID: MCPeerID) {
        // Handle in game scene
    }
    
    func didReceivePlayerAction(_ action: String, from peerID: MCPeerID) {
        // Handle in game scene
    }
}
