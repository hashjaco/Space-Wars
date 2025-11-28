//
//  MultiplayerManager.swift
//  SpaceCase
//
//  Manages local multiplayer using MultipeerConnectivity
//

import Foundation
import MultipeerConnectivity

protocol MultiplayerManagerDelegate: AnyObject {
    func didReceivePlayerPosition(_ position: CGPoint, from peerID: MCPeerID)
    func didReceiveBullet(_ bullet: BulletNode, from peerID: MCPeerID)
    func didReceivePlayerAction(_ action: String, from peerID: MCPeerID)
    func didConnect(to peer: MCPeerID)
    func didDisconnect(from peer: MCPeerID)
}

class MultiplayerManager: NSObject {
    
    private let serviceType = "spacecase-multiplayer"
    private let myPeerID = MCPeerID(displayName: UIDevice.current.name)
    private var session: MCSession?
    private var serviceAdvertiser: MCNearbyServiceAdvertiser?
    private var serviceBrowser: MCNearbyServiceBrowser?
    
    weak var delegate: MultiplayerManagerDelegate?
    
    var isConnected: Bool {
        return session?.connectedPeers.count ?? 0 > 0
    }
    
    override init() {
        super.init()
        setupSession()
    }
    
    private func setupSession() {
        session = MCSession(peer: myPeerID, securityIdentity: nil, encryptionPreference: .required)
        session?.delegate = self
        
        serviceAdvertiser = MCNearbyServiceAdvertiser(peer: myPeerID, discoveryInfo: nil, serviceType: serviceType)
        serviceAdvertiser?.delegate = self
        serviceAdvertiser?.startAdvertisingPeer()
        
        serviceBrowser = MCNearbyServiceBrowser(peer: myPeerID, serviceType: serviceType)
        serviceBrowser?.delegate = self
        serviceBrowser?.startBrowsingForPeers()
    }
    
    func sendPlayerPosition(_ position: CGPoint) {
        guard let session = session, session.connectedPeers.count > 0 else { return }
        
        let data = try? JSONEncoder().encode(["type": "position", "x": position.x, "y": position.y])
        try? session.send(data ?? Data(), toPeers: session.connectedPeers, with: .reliable)
    }
    
    func sendBullet(_ bullet: BulletNode) {
        guard let session = session, session.connectedPeers.count > 0 else { return }
        
        let data = try? JSONEncoder().encode([
            "type": "bullet",
            "x": bullet.position.x,
            "y": bullet.position.y,
            "isEnemyBullet": bullet.isEnemyBullet,
            "damage": bullet.damage
        ])
        try? session.send(data ?? Data(), toPeers: session.connectedPeers, with: .reliable)
    }
    
    func sendAction(_ action: String) {
        guard let session = session, session.connectedPeers.count > 0 else { return }
        
        let data = try? JSONEncoder().encode(["type": "action", "action": action])
        try? session.send(data ?? Data(), toPeers: session.connectedPeers, with: .reliable)
    }
    
    deinit {
        serviceAdvertiser?.stopAdvertisingPeer()
        serviceBrowser?.stopBrowsingForPeers()
    }
}

extension MultiplayerManager: MCSessionDelegate {
    func session(_ session: MCSession, peer peerID: MCPeerID, didChange state: MCSessionState) {
        switch state {
        case .connected:
            DispatchQueue.main.async {
                self.delegate?.didConnect(to: peerID)
            }
        case .notConnected:
            DispatchQueue.main.async {
                self.delegate?.didDisconnect(from: peerID)
            }
        case .connecting:
            break
        @unknown default:
            break
        }
    }
    
    func session(_ session: MCSession, didReceive data: Data, fromPeer peerID: MCPeerID) {
        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let type = json["type"] as? String else { return }
        
        DispatchQueue.main.async {
            switch type {
            case "position":
                if let x = json["x"] as? CGFloat, let y = json["y"] as? CGFloat {
                    self.delegate?.didReceivePlayerPosition(CGPoint(x: x, y: y), from: peerID)
                }
            case "bullet":
                if let x = json["x"] as? CGFloat,
                   let y = json["y"] as? CGFloat,
                   let isEnemyBullet = json["isEnemyBullet"] as? Bool,
                   let damage = json["damage"] as? Int {
                    let bullet = BulletNode(isEnemyBullet: isEnemyBullet, damage: damage)
                    bullet.position = CGPoint(x: x, y: y)
                    self.delegate?.didReceiveBullet(bullet, from: peerID)
                }
            case "action":
                if let action = json["action"] as? String {
                    self.delegate?.didReceivePlayerAction(action, from: peerID)
                }
            default:
                break
            }
        }
    }
    
    func session(_ session: MCSession, didReceive stream: InputStream, withName streamName: String, fromPeer peerID: MCPeerID) {
        // Handle streams if needed
    }
    
    func session(_ session: MCSession, didStartReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, with progress: Progress) {
        // Handle resource transfer if needed
    }
    
    func session(_ session: MCSession, didFinishReceivingResourceWithName resourceName: String, fromPeer peerID: MCPeerID, at localURL: URL?, withError error: Error?) {
        // Handle resource completion if needed
    }
}

extension MultiplayerManager: MCNearbyServiceAdvertiserDelegate {
    func advertiser(_ advertiser: MCNearbyServiceAdvertiser, didReceiveInvitationFromPeer peerID: MCPeerID, withContext context: Data?, invitationHandler: @escaping (Bool, MCSession?) -> Void) {
        invitationHandler(true, session)
    }
}

extension MultiplayerManager: MCNearbyServiceBrowserDelegate {
    func browser(_ browser: MCNearbyServiceBrowser, foundPeer peerID: MCPeerID, withDiscoveryInfo info: [String : String]?) {
        browser.invitePeer(peerID, to: session!, withContext: nil, timeout: 10)
    }
    
    func browser(_ browser: MCNearbyServiceBrowser, lostPeer peerID: MCPeerID) {
        // Handle peer lost
    }
}
