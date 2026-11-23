import Shared
import SwiftUI

@main
struct WLEDNativeApp: App {
    /// Set by xcodebuild when the app is launched as a unit test host. In that case the real UI is skipped so
    /// tests don't open live device connections, run discovery, or fetch releases from GitHub.
    private static let isRunningUnitTests = ProcessInfo.processInfo.environment["XCTestConfigurationFilePath"] != nil

    @AppStorage(AppLanguage.storageKey) private var appLanguage: String = AppLanguage.default.rawValue

    init() {
        // 首次启动默认中文
        AppLanguage.current.apply()
    }

    private var language: AppLanguage { AppLanguage(rawValue: appLanguage) ?? .default }

    var body: some Scene {
        WindowGroup {
            if Self.isRunningUnitTests {
                EmptyView()
            } else {
                DeviceListView(database: .shared)
                    .environment(\.locale, language.locale)
                    .id(language)
                    .onChange(of: appLanguage) { _, _ in language.apply() }
                    .task {
                        await AppDatabase.shared.refreshVersionsIfNeeded()
                    }
            }
        }
    }
}
