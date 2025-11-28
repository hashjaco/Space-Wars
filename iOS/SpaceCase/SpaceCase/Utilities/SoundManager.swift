//
//  SoundManager.swift
//  SpaceCase
//
//  Manages sound effects and music
//

import AVFoundation
import SpriteKit

enum SoundEffect {
    case laser
    case explosion
    case powerUp
    case hit
}

class SoundManager {
    static let shared = SoundManager()
    
    private var backgroundMusicPlayer: AVAudioPlayer?
    private var soundEffects: [SoundEffect: SKAction] = [:]
    
    private init() {
        setupSoundEffects()
    }
    
    private func setupSoundEffects() {
        // Load sound effects
        if let laserSound = SKAction.playSoundFileNamed("lazer.wav", waitForCompletion: false) {
            soundEffects[.laser] = laserSound
        }
        
        if let explosionSound = SKAction.playSoundFileNamed("explosion_x.wav", waitForCompletion: false) {
            soundEffects[.explosion] = explosionSound
        }
    }
    
    func playSound(_ sound: SoundEffect) {
        guard let soundAction = soundEffects[sound] else { return }
        // Sound will be played by the scene that calls this
        // This is a placeholder - actual implementation would use SKAction.run
    }
    
    func playBackgroundMusic(filename: String) {
        guard let url = Bundle.main.url(forResource: filename, withExtension: nil) else { return }
        
        do {
            backgroundMusicPlayer = try AVAudioPlayer(contentsOf: url)
            backgroundMusicPlayer?.numberOfLoops = -1
            backgroundMusicPlayer?.play()
        } catch {
            print("Failed to play background music: \(error)")
        }
    }
    
    func stopBackgroundMusic() {
        backgroundMusicPlayer?.stop()
    }
}
