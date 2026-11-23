"""为四个语言资源文件补齐新增字符串。

用法: python tools/add_android_strings.py
重复执行是幂等的：已存在的 key 会被替换而不是重复插入。
"""

from __future__ import annotations

import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "app", "src", "main", "res")

STRINGS = {
    # 底部导航
    "nav_devices": ("Devices", "设备", "Appareils", "Geräte"),
    "nav_effects": ("Effects", "灯效", "Effets", "Effekte"),
    "nav_control": ("Control", "控制", "Contrôle", "Steuerung"),
    "nav_favorites": ("Favorites", "收藏", "Favoris", "Favoriten"),
    # 灯效库
    "effects_title": ("Effects library", "灯效库", "Bibliothèque d'effets", "Effektbibliothek"),
    "effects_search_hint": ("Search effects", "搜索灯效", "Rechercher des effets", "Effekte suchen"),
    "effects_search_clear": ("Clear search", "清空搜索", "Effacer la recherche", "Suche löschen"),
    "effects_empty": ("No matching effects", "没有匹配的灯效", "Aucun effet correspondant", "Keine passenden Effekte"),
    "effects_no_device": ("Connect a light first", "请先连接一台灯", "Connectez d'abord une lampe", "Zuerst eine Lampe verbinden"),
    "effects_apply_failed": ("Could not send, check the connection", "下发失败，请检查连接", "Échec de l'envoi, vérifiez la connexion", "Senden fehlgeschlagen, Verbindung prüfen"),
    "effects_favorite_add": ("Add to favorites", "收藏", "Ajouter aux favoris", "Zu Favoriten hinzufügen"),
    "effects_favorite_remove": ("Remove from favorites", "取消收藏", "Retirer des favoris", "Aus Favoriten entfernen"),
    "effects_no_preview": ("No preview", "暂无预览", "Pas d'aperçu", "Keine Vorschau"),
    "effects_filter_all": ("All", "全部", "Tout", "Alle"),
    "effects_filter_popular": ("Popular", "常用", "Populaires", "Beliebt"),
    "effects_filter_one_d": ("1D", "1D", "1D", "1D"),
    "effects_filter_two_d": ("2D", "2D", "2D", "2D"),
    "effects_filter_art": ("Art & text", "字画", "Image et texte", "Bild & Text"),
    # 控制页
    "control_title": ("Control", "控制", "Contrôle", "Steuerung"),
    "control_no_device": ("No light online", "没有在线的灯", "Aucune lampe en ligne", "Keine Lampe online"),
    "control_no_device_hint": (
        "Make sure the light is on and on the same network",
        "请确认灯已开机并连到同一网络",
        "Vérifiez que la lampe est allumée et sur le même réseau",
        "Lampe einschalten und im selben Netzwerk verbinden",
    ),
    "control_power_on": ("On", "已开启", "Allumée", "An"),
    "control_power_off": ("Off", "已关闭", "Éteinte", "Aus"),
    "control_brightness": ("Brightness", "亮度", "Luminosité", "Helligkeit"),
    "control_current_effect": ("Effect: %1$s", "当前灯效：%1$s", "Effet : %1$s", "Effekt: %1$s"),
    "control_effect": ("Effect", "灯效", "Effet", "Effekt"),
    "control_tuning": ("Effect tuning", "效果调节", "Réglage de l'effet", "Effekteinstellung"),
    "control_speed": ("Speed", "速度", "Vitesse", "Geschwindigkeit"),
    "control_intensity": ("Intensity", "强度", "Intensité", "Intensität"),
    "control_color": ("Color", "颜色", "Couleur", "Farbe"),
    "control_sync": ("Sync lights", "多灯同步", "Synchroniser les lampes", "Lampen synchronisieren"),
    "control_sync_desc": (
        "Applies to %1$d lights online",
        "同时对 %1$d 台在线灯生效",
        "S'applique à %1$d lampes en ligne",
        "Gilt für %1$d online Lampen",
    ),
    "control_timer": ("Sleep timer", "定时关灯", "Minuteur", "Ausschalttimer"),
    "control_timer_idle": ("Not set", "未设置", "Non défini", "Nicht gesetzt"),
    "control_timer_remaining": ("Turns off in %1$s", "将在 %1$s 后关闭", "S'éteint dans %1$s", "Schaltet in %1$s aus"),
    "control_timer_set": ("Set timer", "设置定时", "Régler le minuteur", "Timer einstellen"),
    "control_timer_cancel": ("Cancel timer", "取消定时", "Annuler le minuteur", "Timer abbrechen"),
    "control_timer_minutes": ("%1$d min", "%1$d 分钟", "%1$d min", "%1$d Min."),
    "control_timer_pending": ("Turning off soon", "即将关闭", "Extinction imminente", "Schaltet bald aus"),
    # 收藏页
    "favorites_title": ("Favorites", "收藏", "Favoris", "Favoriten"),
    "favorites_remove": ("Remove favorite", "移除收藏", "Retirer le favori", "Favorit entfernen"),
    "favorites_speed": ("Speed %1$d", "速度 %1$d", "Vitesse %1$d", "Tempo %1$d"),
    "favorites_intensity": ("Intensity %1$d", "强度 %1$d", "Intensité %1$d", "Intensität %1$d"),
    "favorites_default_params": ("Default settings", "使用默认参数", "Réglages par défaut", "Standardeinstellungen"),
    "favorites_empty": ("No favorites yet", "还没有收藏", "Aucun favori", "Noch keine Favoriten"),
    "favorites_empty_hint": (
        "Tap the heart in the effects library to save one",
        "在灯效库里点 ♥ 即可收藏",
        "Touchez le cœur dans la bibliothèque d'effets",
        "Tippe auf das Herz in der Effektbibliothek",
    ),
    # 设备
    "device_web_settings": ("Web settings", "网页设置", "Réglages web", "Web-Einstellungen"),
    "device_selector_no_device": ("No device selected", "未选择设备", "Aucun appareil sélectionné", "Kein Gerät gewählt"),
    "device_selector_switch": ("Switch device", "切换设备", "Changer d'appareil", "Gerät wechseln"),
    "device_spec_unknown": ("Spec unknown", "规格未知", "Spécifications inconnues", "Spezifikation unbekannt"),
    "device_spec_led_count": ("%1$d LEDs", "共 %1$d 颗", "%1$d LED", "%1$d LEDs"),
    "device_spec_matrix": ("%1$d LEDs · %2$d×%3$d", "共 %1$d 颗 · %2$d×%3$d", "%1$d LED · %2$d×%3$d", "%1$d LEDs · %2$d×%3$d"),
    # 设置
    "settings_language_current": ("Current: %1$s", "当前：%1$s", "Actuel : %1$s", "Aktuell: %1$s"),
    "settings_language_follow_system": ("Follow system language", "跟随系统语言", "Suivre la langue du système", "Systemsprache folgen"),
    "settings_language_follow_system_description": (
        "Chinese, English, French and German are supported; other system languages fall back to Chinese",
        "支持中文、英语、法语、德语；系统语言为其它的将回落到中文",
        "Chinois, anglais, français et allemand sont pris en charge ; les autres langues reviennent au chinois",
        "Chinesisch, Englisch, Französisch und Deutsch werden unterstützt; andere Sprachen fallen auf Chinesisch zurück",
    ),
}

FILES = {
    "values": 0,
    "values-zh": 1,
    "values-fr": 2,
    "values-de": 3,
}


def escape(value: str) -> str:
    return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def main() -> int:
    for folder, index in FILES.items():
        path = os.path.join(RES, folder, "strings.xml")
        with open(path, encoding="utf-8") as handle:
            content = handle.read()

        lines = []
        for key, translations in STRINGS.items():
            value = escape(translations[index])
            lines.append(f'    <string name="{key}">{value}</string>')
        block = "\n".join(lines)

        # 幂等：先删掉已存在的同名 key
        for key in STRINGS:
            content = re.sub(rf'\n?[ \t]*<string name="{key}">.*?</string>', "", content)

        content = content.replace("\n</resources>", "\n" + block + "\n</resources>")
        if not content.rstrip().endswith("</resources>"):
            print(f"警告: {path} 结构异常", flush=True)
            return 1
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(content)
        print(f"更新 {path}: +{len(STRINGS)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
