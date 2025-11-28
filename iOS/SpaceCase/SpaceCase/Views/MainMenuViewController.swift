//
//  MainMenuViewController.swift
//  SpaceCase
//
//  Main menu following intro video
//

import UIKit

class MainMenuViewController: UIViewController {
    
    private let stackView: UIStackView = {
        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 20
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        return stack
    }()
    
    private let titleLabel: UILabel = {
        let label = UILabel()
        label.text = "SPACE CASE"
        label.font = UIFont.systemFont(ofSize: 48, weight: .bold)
        label.textColor = .white
        label.textAlignment = .center
        return label
    }()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        view.backgroundColor = .black
        
        // Background image/animation can be added here
        let backgroundImageView = UIImageView()
        backgroundImageView.image = UIImage(named: "spaceBackground")
        backgroundImageView.contentMode = .scaleAspectFill
        backgroundImageView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(backgroundImageView)
        
        NSLayoutConstraint.activate([
            backgroundImageView.topAnchor.constraint(equalTo: view.topAnchor),
            backgroundImageView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            backgroundImageView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            backgroundImageView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        
        view.addSubview(stackView)
        stackView.addArrangedSubview(titleLabel)
        
        // Menu buttons
        let storyModeButton = createMenuButton(title: "Story Mode", action: #selector(startStoryMode))
        let multiplayerButton = createMenuButton(title: "Multiplayer", action: #selector(startMultiplayer))
        let settingsButton = createMenuButton(title: "Settings", action: #selector(openSettings))
        
        stackView.addArrangedSubview(storyModeButton)
        stackView.addArrangedSubview(multiplayerButton)
        stackView.addArrangedSubview(settingsButton)
        
        NSLayoutConstraint.activate([
            stackView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stackView.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
    
    private func createMenuButton(title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setTitleColor(.white, for: .normal)
        button.titleLabel?.font = UIFont.systemFont(ofSize: 24, weight: .semibold)
        button.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.7)
        button.layer.cornerRadius = 12
        button.contentEdgeInsets = UIEdgeInsets(top: 15, left: 40, right: 40, bottom: 15)
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }
    
    @objc private func startStoryMode() {
        let gameVC = GameViewController(gameMode: .story)
        gameVC.modalPresentationStyle = .fullScreen
        present(gameVC, animated: true)
    }
    
    @objc private func startMultiplayer() {
        let multiplayerVC = MultiplayerLobbyViewController()
        multiplayerVC.modalPresentationStyle = .fullScreen
        present(multiplayerVC, animated: true)
    }
    
    @objc private func openSettings() {
        let settingsVC = SettingsViewController()
        let navController = UINavigationController(rootViewController: settingsVC)
        present(navController, animated: true)
    }
}
