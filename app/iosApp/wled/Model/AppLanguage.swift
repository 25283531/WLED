import Foundation

/// 应用内语言，默认简体中文。
enum AppLanguage: String, CaseIterable, Identifiable {
    case chinese = "zh-Hans"
    case english = "en"
    case french = "fr-CA"
    case german = "de"

    static let storageKey = "appLanguage"
    static let `default`: AppLanguage = .chinese

    var id: String { rawValue }

    var nativeName: String {
        switch self {
        case .chinese: return "简体中文"
        case .english: return "English"
        case .french: return "Français"
        case .german: return "Deutsch"
        }
    }

    var locale: Locale { Locale(identifier: rawValue) }

    static var current: AppLanguage {
        AppLanguage(rawValue: UserDefaults.standard.string(forKey: storageKey) ?? "") ?? .default
    }

    /// SwiftUI 文本通过 `.environment(\.locale)` 即时切换；
    /// 同时写入 AppleLanguages，让 Bundle 级别的字符串在下次启动时也使用该语言。
    func apply() {
        UserDefaults.standard.set(rawValue, forKey: Self.storageKey)
        UserDefaults.standard.set([rawValue], forKey: "AppleLanguages")
    }
}
