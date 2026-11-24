"""把光屿新版界面的文案写入四种语言的 strings.xml。

key -> (en, zh, fr, de)。幂等：已存在的 key 直接替换内容，不新增重复项。

用法:
    python tools/add_isle_strings.py
"""

from __future__ import annotations

import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "app", "src", "main", "res")

STRINGS: dict[str, tuple[str, str, str, str]] = {
    "isle_tab_device": ("Device", "设备", "Appareil", "Gerät"),
    "isle_tab_library": ("Effects", "灯效库", "Effets", "Effekte"),
    "isle_tab_create": ("Create", "创作", "Créer", "Erstellen"),
    "isle_tab_settings": ("Settings", "设置", "Réglages", "Einstellungen"),

    "isle_status_connected": ("Lamp connected", "设备已连接", "Lampe connectée", "Lampe verbunden"),
    "isle_status_offline": ("Lamp offline", "设备离线", "Lampe hors ligne", "Lampe offline"),
    "isle_status_no_device": ("No lamp connected", "未连接设备", "Aucune lampe connectée", "Keine Lampe verbunden"),
    "isle_device_count": ("%1$d lamps", "%1$d 台设备", "%1$d lampes", "%1$d Lampen"),

    "isle_sync": ("Sync", "多灯同步", "Synchro", "Sync"),
    "isle_sync_title": ("Sync all lamps", "全部灯同步", "Synchroniser les lampes", "Alle Lampen synchron"),
    "isle_sync_desc": (
        "Every command is sent to all lamps that are online",
        "每条指令都会同时下发给所有在线灯",
        "Chaque commande va à toutes les lampes en ligne",
        "Jeder Befehl geht an alle online Lampen",
    ),
    "isle_timer": ("Timer", "定时", "Minuteur", "Timer"),
    "isle_nightlight_off": ("Turn off after", "定时关灯", "Éteindre après", "Ausschalten nach"),
    "isle_minutes": ("%1$d min", "%1$d 分钟", "%1$d min", "%1$d Min."),
    "isle_cancel_nightlight": ("Cancel the timer", "取消定时", "Annuler le minuteur", "Timer abbrechen"),

    "isle_devices": ("Lamps", "设备管理", "Appareils", "Lampen"),
    "isle_add_device": ("Add a lamp", "添加设备", "Ajouter une lampe", "Lampe hinzufügen"),
    "isle_brightness": ("Brightness", "亮度", "Luminosité", "Helligkeit"),
    "isle_rhythm_mode": ("Rhythm mode", "律动模式", "Mode rythmique", "Rhythmusmodus"),
    "isle_quick_effects": ("Quick effects", "快捷灯效", "Effets rapides", "Schnelleffekte"),

    "isle_mood_aurora": ("Aurora", "幻彩极光", "Aurore", "Aurora"),
    "isle_mood_music": ("Music", "音乐律动", "Musique", "Musik"),
    "isle_mood_text": ("Captions", "动态字幕", "Texte", "Text"),
    "isle_sleep_light": ("Sleep light", "睡眠夜灯", "Veilleuse", "Nachtlicht"),
    "isle_sleep_light_applied": (
        "Switched to a warm sleep light", "已切换到睡眠夜灯暖光",
        "Lumière douce de veilleuse activée", "Warmes Nachtlicht aktiviert",
    ),
    "isle_nightlight_remaining": ("%1$s left", "剩余 %1$s", "%1$s restantes", "Noch %1$s"),
    "isle_nightlight_started": (
        "Will turn off in %1$d min", "已设置 %1$d 分钟后关灯",
        "Extinction dans %1$d min", "Ausschalten in %1$d Min.",
    ),
    "isle_nightlight_cancelled": ("Timer cancelled", "已取消定时关灯", "Minuterie annulée", "Timer abgebrochen"),
    "isle_nightlight_failed": (
        "Could not send the timer. Check the connection.", "定时指令发送失败，请检查连接",
        "Envoi de la minuterie impossible. Vérifiez la connexion.",
        "Timer konnte nicht gesendet werden. Verbindung prüfen.",
    ),
    "isle_nightlight_light_off": (
        "The light is off, so the timer will not take effect.", "灯当前是关闭状态，定时关灯不会生效",
        "La lampe est éteinte, la minuterie ne fera rien.",
        "Die Lampe ist aus, der Timer hat keine Wirkung.",
    ),

    "isle_favorite_add": ("Add to favorites", "收藏", "Ajouter aux favoris", "Zu Favoriten hinzufügen"),
    "isle_favorite_remove": ("Remove from favorites", "取消收藏", "Retirer des favoris", "Aus Favoriten entfernen"),

    "isle_library_title": ("Effects library", "灯效库", "Bibliothèque d'effets", "Effektbibliothek"),
    "isle_search": ("Search effects", "搜索灯效", "Rechercher un effet", "Effekte suchen"),
    "isle_search_empty": ("No matching effect", "没有匹配的灯效", "Aucun effet trouvé", "Kein Effekt gefunden"),

    "isle_create_title": ("Create studio", "创作工坊", "Atelier créatif", "Kreativstudio"),
    "isle_create_color": ("Color wheel", "色轮调色", "Roue chromatique", "Farbrad"),
    "isle_create_pixel": ("Pixel art", "自定义图文", "Pixel art", "Pixelkunst"),
    "isle_save_favorite": ("Save", "保存收藏", "Enregistrer", "Speichern"),
    "isle_apply_colors": ("Apply to lamp", "同步到灯管", "Appliquer à la lampe", "Auf Lampe anwenden"),
    "isle_send_text": ("Send caption", "投送字幕", "Envoyer le texte", "Text senden"),
    "isle_upload_art": ("Upload art", "上传图案", "Envoyer l'image", "Bild senden"),
    "isle_color_tuning": ("Color tuning", "色彩调节", "Réglage des couleurs", "Farbabstimmung"),
    "isle_flow_speed": ("Flow speed", "流动速度", "Vitesse du flux", "Fließgeschwindigkeit"),
    "isle_saturation": ("Saturation", "色彩饱和度", "Saturation", "Sättigung"),
    "isle_pixel_title": ("Caption / pixel canvas", "自定义文字 / 像素画板", "Texte / canevas", "Text / Leinwand"),
    "isle_text_hint": (
        "Caption text (32 chars max)", "输入字幕（最多 32 字）",
        "Texte (32 caractères max)", "Text (max. 32 Zeichen)",
    ),
    "isle_brush": ("Brush", "画笔", "Pinceau", "Pinsel"),
    "isle_eraser": ("Eraser", "橡皮擦", "Gomme", "Radierer"),
    "isle_picker": ("Picker", "取色器", "Pipette", "Pipette"),
    "isle_clear": ("Clear", "清除", "Effacer", "Löschen"),
    "isle_color_hint": (
        "Both anchors are sent to the lamp together; the diffuser blends them into one glow.",
        "两个锚点会一起下发，透过扩散膜混成一片光。",
        "Les deux points sont envoyés ensemble ; le diffuseur les mélange en une seule lueur.",
        "Beide Punkte werden gemeinsam gesendet; der Diffusor mischt sie zu einem Leuchten.",
    ),
    "isle_pixel_hint": (
        "The canvas follows your lamp's matrix. Draw, then upload — the firmware shows it with the Image effect.",
        "画布跟随灯的矩阵尺寸。画好后上传，固件用「自定义图片」效果显示。",
        "Le canevas suit la matrice de la lampe. Dessinez puis envoyez : le firmware l'affiche avec l'effet Image.",
        "Die Leinwand folgt der Matrix der Lampe. Zeichnen und senden — die Firmware zeigt es mit dem Image-Effekt.",
    ),

    "isle_settings_ecosystem": ("Smart home", "智能生态", "Maison connectée", "Smart Home"),
    "isle_settings_hardware": ("Hardware and input", "硬件与交互", "Matériel et commandes", "Hardware und Bedienung"),
    "isle_settings_system": ("System", "系统与支持", "Système", "System"),
    "isle_matter": ("Matter / 米家 / HomeKit", "Matter / 米家 / HomeKit", "Matter / 米家 / HomeKit", "Matter / 米家 / HomeKit"),
    "isle_wifi": ("Wi-Fi network", "Wi-Fi 网络配置", "Réseau Wi-Fi", "WLAN"),
    "isle_mic": ("Mic sensitivity", "麦克风拾音灵敏度", "Sensibilité du micro", "Mikrofonempfindlichkeit"),
    "isle_keys": ("Top touch keys", "顶部按键设置", "Touches tactiles", "Tasten oben"),
    "isle_boot_state": ("Power-on state", "开机默认状态", "État au démarrage", "Startzustand"),
    "isle_firmware": ("Firmware update", "固件升级", "Mise à jour du firmware", "Firmware-Update"),
    "isle_not_supported": ("Not in firmware", "固件未开放", "Non disponible", "Nicht verfügbar"),
    "isle_not_supported_short": ("N/A", "未开放", "N/D", "N/V"),
    "isle_not_supported_toast": (
        "This needs firmware support that is not in the current build.",
        "该能力需要固件支持，当前固件版本还没开放。",
        "Cette fonction nécessite une prise en charge du firmware.",
        "Diese Funktion erfordert Firmware-Unterstützung.",
    ),
    "isle_open_web": ("Open web page", "打开网页", "Ouvrir la page web", "Webseite öffnen"),
    "isle_open_web_failed": (
        "Cannot open the lamp page", "无法打开灯的网页",
        "Impossible d'ouvrir la page de la lampe", "Lampenseite nicht erreichbar",
    ),
    "isle_language": ("Language", "语言", "Langue", "Sprache"),
    "isle_follow_system": ("Follow system", "跟随系统", "Suivre le système", "Systemsprache"),
    "isle_settings_footer": (
        "Hardware-level settings still live in the lamp's built-in web page; this app drives light only.",
        "硬件级设置仍在灯自带的网页里，App 只负责控光。",
        "Les réglages matériels restent dans la page web de la lampe ; cette application pilote la lumière.",
        "Hardware-Einstellungen bleiben in der Weboberfläche der Lampe; diese App steuert nur das Licht.",
    ),
    "isle_wifi_current_network": ("Current network", "当前网络", "Réseau actuel", "Aktuelles Netzwerk"),
    "isle_wifi_unknown": ("Unknown", "未读取到", "Inconnu", "Unbekannt"),
    "isle_wifi_nearby": ("Nearby networks", "附近网络", "Réseaux à proximité", "Netzwerke in der Nähe"),
    "isle_wifi_scanning": ("Scanning", "正在扫描", "Analyse", "Suche läuft"),
    "isle_wifi_rescan": ("Scan again", "重新扫描", "Rescanner", "Neu suchen"),
    "isle_wifi_no_networks": (
        "No networks found. Move closer to the router and try again.",
        "没有发现网络，请靠近路由器后重试",
        "Aucun réseau trouvé. Rapprochez-vous du routeur puis réessayez.",
        "Keine Netzwerke gefunden. Geh näher an den Router und versuche es erneut.",
    ),
    "isle_wifi_connected": ("Connected", "已连接", "Connecté", "Verbunden"),
    "isle_wifi_open_network": ("Open", "开放", "Ouvert", "Offen"),
    "isle_wifi_secured": ("Secured", "加密", "Sécurisé", "Gesichert"),
    "isle_wifi_channel": ("CH %1$d", "CH %1$d", "CH %1$d", "CH %1$d"),
    "isle_wifi_signal": ("%1$d dBm", "%1$d dBm", "%1$d dBm", "%1$d dBm"),
    "isle_wifi_connect_title": ("Connect to %1$s", "连接到 %1$s", "Se connecter à %1$s", "Mit %1$s verbinden"),
    "isle_wifi_password": ("Wi-Fi password", "WiFi 密码", "Mot de passe Wi-Fi", "WLAN-Passwort"),
    "isle_wifi_connect": ("Connect", "连接", "Connecter", "Verbinden"),
    "isle_wifi_connect_submitted": (
        "New network saved. The device is restarting.",
        "已保存新网络，设备正在重启",
        "Nouveau réseau enregistré, redémarrage en cours",
        "Neues Netzwerk gespeichert, Gerät startet neu",
    ),
    "isle_wifi_reboot_hint": (
        "If the IP address changes, search for the lamp again in the device list.",
        "重启后若 IP 变化，请回到设备列表重新搜索",
        "Si la nouvelle adresse IP est différente, recherchez la lampe dans la liste des appareils.",
        "Ändert sich die IP-Adresse, suche die Lampe erneut in der Geräteliste.",
    ),
    "isle_wifi_scan_failed": ("Scan failed", "扫描失败", "Analyse impossible", "Suche fehlgeschlagen"),
    "isle_wifi_scan_failed_hint": (
        "Make sure the lamp is online and try again.",
        "请确认设备在线后重试",
        "Vérifiez que la lampe est en ligne puis réessayez.",
        "Prüfe, ob die Lampe online ist, und versuche es erneut.",
    ),
    "isle_wifi_connect_failed": (
        "Settings not applied", "配置未生效", "Réglages non appliqués", "Einstellungen nicht übernommen",
    ),
    "isle_wifi_connect_failed_hint": (
        "Check the connection. A device PIN must be removed first.",
        "请检查设备连接；设备若设置过 PIN 需先解除",
        "Vérifiez la connexion. Un code PIN doit être retiré au préalable.",
        "Prüfe die Verbindung. Ein Geräte-PIN muss vorher entfernt werden.",
    ),
}

FOLDERS = ("values", "values-zh", "values-fr", "values-de")


def escape(value: str) -> str:
    return (
        value.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "\\'")
        .replace('"', "\\\"")
    )


def main() -> int:
    for index, folder in enumerate(FOLDERS):
        path = os.path.join(RES, folder, "strings.xml")
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
        added = replaced = 0
        for key, values in STRINGS.items():
            body = escape(values[index])
            node = f'    <string name="{key}">{body}</string>'
            pattern = re.compile(r'[ \t]*<string name="%s"[^>]*>.*?</string>' % re.escape(key), re.S)
            if pattern.search(text):
                text = pattern.sub(node, text, count=1)
                replaced += 1
            else:
                text = text.replace("</resources>", node + "\n</resources>", 1)
                added += 1
        with open(path, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(text)
        print(f"{folder}: 新增 {added}，替换 {replaced}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
