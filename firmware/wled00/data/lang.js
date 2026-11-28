/*
 * 光屿 LumiIsle Web UI 语言层
 *
 * 固件自带页面本来是纯英文、没有 i18n 钩子。这里用「英文原文 -> 译文」字典
 * 在 DOM 上做替换，避免改动每个页面里成百上千个静态节点。
 *
 * 语言模式：
 *   auto  跟随浏览器/系统语言（navigator.language 以 zh 开头即中文）
 *   zh    强制中文
 *   en    强制英文
 * 选择结果保存在 localStorage.wledLang，切换后整页刷新。
 */
(function () {
	if (window.__wledLangLoaded) return;
	window.__wledLangLoaded = true;

	var STORAGE_KEY = 'wledLang';
	var MODES = ['auto', 'zh', 'en'];

	var DICT = {
		// —— 主界面 ——
		'Power': '电源',
		'Timer': '定时',
		'Sync': '同步',
		'Peek': '预览',
		'Info': '信息',
		'Nodes': '节点',
		'Config': '设置',
		'PC Mode': 'PC 模式',
		'Brightness': '亮度',
		'Colors': '颜色',
		'Effects': '效果',
		'Segments': '分段',
		'Presets': '预设',
		'Color palette': '调色板',
		'Effect mode': '灯效模式',
		'Reset segments': '重置分段',
		'Transition:': '过渡：',
		'Hue': '色相',
		'Saturation': '饱和度',
		'Value/Brightness': '明度',
		'Kelvin/Temperature': '色温',
		'Red channel': '红色通道',
		'Green channel': '绿色通道',
		'Blue channel': '蓝色通道',
		'White channel': '白色通道',
		'White balance': '白平衡',
		'Random': '随机',
		'Select slot': '选择颜色槽',
		'Hex RGB': '十六进制颜色',
		'Freeze': '冻结',
		'Effect speed': '灯效速度',
		'Toggle labels': '切换标签',
		'Effect intensity': '灯效强度',
		'Custom 1': '自定义 1',
		'Custom 2': '自定义 2',
		'Custom 3': '自定义 3',
		'Check 1': '选项 1',
		'Check 2': '选项 2',
		'Check 3': '选项 3',
		'File editor': '文件编辑器',
		'PixelForge': '像素工坊',
		'Custom palettes': '自定义调色板',
		'Files': '文件',
		'Palettes': '调色板',
		'Uses palette': '使用调色板',
		'Single pixel': '单像素',
		'Volume': '音量',
		'Frequency': '频率',
		'Loading...': '加载中…',
		'Refresh': '刷新',
		'Instance List': '实例列表',
		'Update WLED': '更新固件',
		'Reboot WLED': '重启设备',
		'WLED instances': 'WLED 实例',
		'Override once': '覆盖一次',
		'Override until reboot': '覆盖直到重启',
		'For best performance, it is recommended to turn off the streaming source when not in use.':
			'为获得最佳效果，不使用时建议关闭推流源。',

		// —— 分段过渡 ——
		'Fade': '渐变',
		'Fairy Dust': '星尘',
		'Swipe right': '向右滑动',
		'Swipe left': '向左滑动',
		'Push right': '向右推动',
		'Push left': '向左推动',
		'Outside-in': '由外向内',
		'Inside-out': '由内向外',
		'Swipe up': '向上滑动',
		'Swipe down': '向下滑动',
		'Open H': '水平展开',
		'Open V': '垂直展开',
		'Push up': '向上推动',
		'Push down': '向下推动',
		'Swipe TL': '左上滑动',
		'Swipe TR': '右上滑动',
		'Swipe BR': '右下滑动',
		'Swipe BL': '左下滑动',
		'Circular Out': '圆形展开',
		'Circular In': '圆形收缩',

		// —— 设置菜单 ——
		'Settings': '设置',
		'Language': '语言',
		'Auto (follow system)': '跟随系统',
		'WiFi & Network': 'WiFi 与网络',
		'LED & Hardware': 'LED 与硬件',
		'Pin Info': '引脚信息',
		'2D Configuration': '2D 配置',
		'User Interface': '用户界面',
		'DMX Output': 'DMX 输出',
		'Sync Interfaces': '同步接口',
		'Time & Macros': '时间与宏',
		'Usermods': '用户模块',
		'Security & Updates': '安全与更新',

		// —— 通用控件与设置页 ——
		'Save': '保存',
		'Save & Reboot': '保存并重启',
		'Back': '返回',
		'Close': '关闭',
		'Apply': '应用',
		'Cancel': '取消',
		'Delete': '删除',
		'Edit': '编辑',
		'Add': '添加',
		'Reset': '重置',
		'Reboot': '重启',
		'Update': '更新',
		'Firmware': '固件',
		'Version': '版本',
		'Build': '构建',
		'About': '关于',
		'Security': '安全',
		'WiFi setup': 'WiFi 设置',
		'Networks': '网络',
		'Scan': '扫描',
		'Static IP': '静态 IP',
		'DHCP': 'DHCP',
		'Hidden': '隐藏',
		'Welcome': '欢迎',
		'Access point': '接入点',
		'Use AP mode': '使用 AP 模式',
		'Time zone': '时区',
		'NTP Server': 'NTP 服务器',
		'Macros': '宏',
		'Button': '按键',
		'Relay': '继电器',
		'UDP Port': 'UDP 端口',
		'Multicast': '组播',
		'Receive': '接收',
		'Send': '发送',
		'Color': '颜色',
		'Effect': '灯效',
		'Preset': '预设',
		'Playlist': '播放列表',
		'LED count': 'LED 数量',
		'LED Preferences': 'LED 偏好',
		'Color Order': '颜色顺序',
		'Startup Preset': '开机预设',
		'Boot Preset': '启动预设',
		'Automatic Brightness Limiter': '自动亮度限制',
		'Max current': '最大电流',
		'Recommended': '推荐值',
		'Estimated current': '估计电流',
		'Enable Arduino OTA': '启用 Arduino OTA',

		// —— 信息面板 ——
		'IP Address': 'IP 地址',
		'MAC Address': 'MAC 地址',
		'Signal Strength': '信号强度',
		'Uptime': '运行时间',
		'Free heap': '空闲内存',
		'Flash Size': '闪存容量',
		'Filesystem': '文件系统',
		'Chip': '芯片',
		'WiFi Signal': 'WiFi 信号'
	};

	function normalize(value) {
		return String(value).replace(/\s+/g, ' ').trim();
	}

	function systemLang() {
		var nav = (navigator.language || navigator.userLanguage || 'en').toLowerCase();
		return nav.indexOf('zh') === 0 ? 'zh' : 'en';
	}

	function readMode() {
		var stored = null;
		try {
			stored = localStorage.getItem(STORAGE_KEY);
		} catch (e) {
			stored = null;
		}
		return MODES.indexOf(stored) >= 0 ? stored : 'auto';
	}

	function activeLang() {
		var mode = readMode();
		return mode === 'auto' ? systemLang() : mode;
	}

	var lang = activeLang();

	/** 命中字典时返回带原始首尾空白的译文，否则返回 null（不改动）。 */
	function translate(value) {
		if (!value) return null;
		var key = normalize(value);
		if (!key || !Object.prototype.hasOwnProperty.call(DICT, key)) return null;
		var lead = value.match(/^\s*/)[0];
		var trail = value.match(/\s*$/)[0];
		return lead + DICT[key] + trail;
	}

	function translateTextNode(node) {
		if (!node || node.nodeType !== 3) return;
		var tag = node.parentNode && node.parentNode.nodeName;
		if (!tag || tag === 'SCRIPT' || tag === 'STYLE' || tag === 'TEXTAREA') return;
		var out = translate(node.nodeValue);
		if (out !== null) node.nodeValue = out;
	}

	function walkText(root) {
		if (!root) return;
		if (root.nodeType === 3) {
			translateTextNode(root);
			return;
		}
		if (root.nodeType !== 1 && root.nodeType !== 9) return;
		var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, null, false);
		var node;
		while ((node = walker.nextNode())) {
			translateTextNode(node);
		}
	}

	function translateAttr(el, name) {
		if (!el.getAttribute) return;
		var value = el.getAttribute(name);
		if (!value) return;
		var out = translate(value);
		if (out !== null) el.setAttribute(name, out);
	}

	function walkAttrs(root) {
		if (!root || !root.querySelectorAll) return;
		var els = root.querySelectorAll('[title],[placeholder],[aria-label]');
		for (var i = 0; i < els.length; i++) {
			translateAttr(els[i], 'title');
			translateAttr(els[i], 'placeholder');
			translateAttr(els[i], 'aria-label');
		}
	}

	function syncSelect() {
		var select = document.getElementById('wledLangSel');
		if (select) select.value = readMode();
	}

	function wireSelect() {
		var select = document.getElementById('wledLangSel');
		if (!select || select.__wledLangWired) return;
		select.__wledLangWired = true;
		select.value = readMode();
		select.addEventListener('change', function () {
			try {
				localStorage.setItem(STORAGE_KEY, select.value);
			} catch (e) {
				/* 隐私模式下写不进去就只影响本次会话 */
			}
			location.reload();
		});
	}

	function applyAll() {
		if (lang !== 'zh') return;
		var body = document.body;
		if (!body) return;
		walkText(body);
		walkAttrs(body);
		var title = translate(document.title);
		if (title !== null) document.title = title;
		document.documentElement.lang = 'zh-CN';
		syncSelect();
	}

	var pendingNodes = [];
	var pendingTimer = null;

	function flushPending() {
		pendingTimer = null;
		var nodes = pendingNodes;
		pendingNodes = [];
		for (var i = 0; i < nodes.length; i++) {
			walkText(nodes[i]);
			walkAttrs(nodes[i]);
		}
	}

	/** 只处理新增节点，避免每次刷新都重扫整页。 */
	function onMutations(records) {
		if (lang !== 'zh') return;
		for (var i = 0; i < records.length; i++) {
			var added = records[i].addedNodes;
			for (var j = 0; j < added.length; j++) {
				var node = added[j];
				if (node.nodeType === 1) {
					pendingNodes.push(node);
				} else if (node.nodeType === 3) {
					translateTextNode(node);
				}
			}
		}
		if (!pendingNodes.length || pendingTimer) return;
		pendingTimer = setTimeout(flushPending, 100);
	}

	function init() {
		applyAll();
		wireSelect();
		if (!document.body || typeof MutationObserver === 'undefined') return;
		// 页面内容是 JS 动态生成的，监听新增节点即可；纯数字变化（characterData）不需要翻译
		var observer = new MutationObserver(onMutations);
		observer.observe(document.body, { childList: true, subtree: true });
	}

	if (document.readyState === 'loading') {
		document.addEventListener('DOMContentLoaded', init);
	} else {
		init();
	}

	window.wledLang = {
		mode: readMode,
		lang: activeLang,
		translate: translate,
		apply: applyAll
	};
})();
