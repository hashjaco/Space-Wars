//
//  SettingsViewController.swift
//  SpaceCase
//
//  Settings and configuration
//

import UIKit

class SettingsViewController: UIViewController {
    
    private let tableView = UITableView(frame: .zero, style: .grouped)
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        title = "Settings"
        view.backgroundColor = .systemBackground
        
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(dismissSettings)
        )
        
        tableView.delegate = self
        tableView.dataSource = self
        tableView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(tableView)
        
        NSLayoutConstraint.activate([
            tableView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            tableView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    @objc private func dismissSettings() {
        dismiss(animated: true)
    }
}

extension SettingsViewController: UITableViewDelegate, UITableViewDataSource {
    func numberOfSections(in tableView: UITableView) -> Int {
        return 3
    }
    
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        switch section {
        case 0: return 2 // Audio
        case 1: return 2 // Graphics
        case 2: return 1 // Other
        default: return 0
        }
    }
    
    func tableView(_ tableView: UITableView, titleForHeaderInSection section: Int) -> String? {
        switch section {
        case 0: return "Audio"
        case 1: return "Graphics"
        case 2: return "Other"
        default: return nil
        }
    }
    
    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = UITableViewCell(style: .value1, reuseIdentifier: nil)
        
        switch indexPath.section {
        case 0:
            if indexPath.row == 0 {
                cell.textLabel?.text = "Music Volume"
                cell.detailTextLabel?.text = "50%"
            } else {
                cell.textLabel?.text = "Sound Effects"
                cell.detailTextLabel?.text = "75%"
            }
        case 1:
            if indexPath.row == 0 {
                cell.textLabel?.text = "Show FPS"
                let switchView = UISwitch()
                switchView.isOn = true
                cell.accessoryView = switchView
            } else {
                cell.textLabel?.text = "Particle Effects"
                let switchView = UISwitch()
                switchView.isOn = true
                cell.accessoryView = switchView
            }
        case 2:
            cell.textLabel?.text = "About"
            cell.accessoryType = .disclosureIndicator
        default:
            break
        }
        
        return cell
    }
    
    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        
        if indexPath.section == 2 && indexPath.row == 0 {
            // Show about screen
            let alert = UIAlertController(
                title: "Space Case",
                message: "Version 1.0\n\nA space shooter game for iOS.",
                preferredStyle: .alert
            )
            alert.addAction(UIAlertAction(title: "OK", style: .default))
            present(alert, animated: true)
        }
    }
}
