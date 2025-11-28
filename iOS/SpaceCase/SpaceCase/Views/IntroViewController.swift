//
//  IntroViewController.swift
//  SpaceCase
//
//  Handles skippable intro video playback
//

import UIKit
import AVKit
import AVFoundation

class IntroViewController: UIViewController {
    
    private var player: AVPlayer?
    private var playerViewController: AVPlayerViewController?
    private var skipButton: UIButton?
    private var hasSkipped = false
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupIntroVideo()
    }
    
    private func setupIntroVideo() {
        // Placeholder for AI-generated intro video
        // In production, replace with actual video file path
        // Video should be created using Sora, Runway, or similar AI video generation
        
        guard let videoPath = Bundle.main.path(forResource: "intro_video", ofType: "mp4") else {
            // If video doesn't exist, skip to menu
            presentMainMenu()
            return
        }
        
        let videoURL = URL(fileURLWithPath: videoPath)
        player = AVPlayer(url: videoURL)
        playerViewController = AVPlayerViewController()
        playerViewController?.player = player
        playerViewController?.showsPlaybackControls = false
        
        // Add skip button
        skipButton = UIButton(type: .system)
        skipButton?.setTitle("Skip", for: .normal)
        skipButton?.setTitleColor(.white, for: .normal)
        skipButton?.backgroundColor = UIColor.black.withAlphaComponent(0.5)
        skipButton?.layer.cornerRadius = 8
        skipButton?.addTarget(self, action: #selector(skipIntro), for: .touchUpInside)
        
        if let playerVC = playerViewController {
            addChild(playerVC)
            view.addSubview(playerVC.view)
            playerVC.view.frame = view.bounds
            playerVC.didMove(toParent: self)
            
            if let skipBtn = skipButton {
                view.addSubview(skipBtn)
                skipBtn.translatesAutoresizingMaskIntoConstraints = false
                NSLayoutConstraint.activate([
                    skipBtn.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
                    skipBtn.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
                    skipBtn.widthAnchor.constraint(equalToConstant: 80),
                    skipBtn.heightAnchor.constraint(equalToConstant: 40)
                ])
            }
        }
        
        // Observe when video ends
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(videoDidEnd),
            name: .AVPlayerItemDidPlayToEndTime,
            object: player?.currentItem
        )
        
        player?.play()
    }
    
    @objc private func skipIntro() {
        guard !hasSkipped else { return }
        hasSkipped = true
        player?.pause()
        presentMainMenu()
    }
    
    @objc private func videoDidEnd() {
        presentMainMenu()
    }
    
    private func presentMainMenu() {
        let mainMenuVC = MainMenuViewController()
        mainMenuVC.modalPresentationStyle = .fullScreen
        present(mainMenuVC, animated: true)
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
}
