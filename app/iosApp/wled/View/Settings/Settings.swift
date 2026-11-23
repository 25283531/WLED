//
//  Settings.swift
//  WLED
//
//  Created by Christophe Gagnier on 2025-12-28.
//

import SwiftUI
import Shared

struct Settings: View {
    @Binding var showHiddenDevices: Bool
    @Binding var showOfflineDevicesLast: Bool
    @Binding var theme: ThemeSettings
    @Binding var autoDiscovery: Bool
    var onShowChangelog: (() -> Void)?
    /// Injectable so snapshot tests don't change with every release.
    var appVersion: String = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
    var buildNumber: String = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1.0"

    @AppStorage(AppLanguage.storageKey) private var appLanguage: String = AppLanguage.default.rawValue

    // Environment to dismiss the sheet
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Picker(selection: $appLanguage) {
                        ForEach(AppLanguage.allCases) { lang in
                            Text(verbatim: lang.nativeName).tag(lang.rawValue)
                        }
                    } label: {
                        Label("Language", systemImage: "globe")
                    }

                    Picker(selection: $theme) {
                        ForEach(ThemeSettings.allCases) { option in
                            Text(LocalizedStringKey(option.nameKey))
                                .tag(option)
                        }
                    } label: {
                        Label("Theme", systemImage: "circle.lefthalf.filled")
                    }
                    .pickerStyle(.menu)
                } header: {
                    Text("General")
                }

                Section {
                    Toggle(isOn: $autoDiscovery) {
                        Label("Automatically Discover New Devices", systemImage: "antenna.radiowaves.left.and.right")
                    }

                    Toggle(isOn: $showHiddenDevices) {
                        Label("Show Hidden Devices", systemImage: "eye")
                    }

                    Toggle(isOn: $showOfflineDevicesLast) {
                        Label("Show Offline Devices Last", systemImage: "wifi.slash")
                    }
                } header: {
                    Text("Device List")
                } footer: {
                    Text("When disabled, pull down on the device list to scan manually.")
                }

                Section {
                    if let onShowChangelog {
                        Button {
                            onShowChangelog()
                        } label: {
                            Label("Show Changelog", systemImage: "sparkles")
                        }
                    }

                    if let url = URL(string: "https://kno.wled.ge/") {
                        Link(destination: url) {
                            Label("WLED Documentation", systemImage: "questionmark.circle")
                        }
                    }
                } header: {
                    Text("About")
                } footer: {
                    VStack(alignment: .center) {
                        Text("Made by Moustachauve")
                        Text("Version \(appVersion) (\(buildNumber))")
                    }
                    .frame(maxWidth: .infinity)
                }

            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        dismiss()
                    }
                }
            }
        }
        .presentationDragIndicator(.visible)
    }
}

#Preview {
    Settings(
        showHiddenDevices: .constant(true),
        showOfflineDevicesLast: .constant(true),
        theme: .constant(.auto),
        autoDiscovery: .constant(true)
    )
}
