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
		'WiFi Signal': 'WiFi 信号',

		// —— 页面标题 ——
		'WLED Settings': 'WLED 设置',
		'UI Settings': '界面设置',
		'LED Settings': 'LED 设置',
		'2D Set-up': '2D 设置',
		'DMX Settings': 'DMX 设置',
		'Sync Settings': '同步设置',
		'Time Settings': '时间设置',
		'WiFi Settings': 'WiFi 设置',
		'Security & Update Setup': '安全与更新设置',
		'Usermod Settings': '用户模块设置',
		'WLED Update': 'WLED 更新',

		// —— 主界面残留 ——
		'⚠ DEVELOPMENT BUILD — WORK IN PROGRESS — NOT FOR PRODUCTION USE':
			'⚠ 开发版本 — 正在开发中 — 请勿用于生产环境',
		'Loading WLED UI...': '正在加载 WLED 界面…',
		'Sorry, WLED UI needs JavaScript!': '抱歉，WLED 界面需要启用 JavaScript！',
		'Made with': '由',
		'by': '制作，',
		'and the': '与',
		'WLED community': 'WLED 社区',
		'To use built-in effects, use an override button below.':
			'如需使用内置灯效，请点击下方的覆盖按钮。',
		'You can return to realtime mode by pressing the star in the top left corner.':
			'点击左上角的星标即可返回实时模式。',
		'Search': '搜索',
		'not used': '未使用',

		// —— 设置：用户界面 ——
		'✔ Local UI settings saved!': '✔ 本地界面设置已保存！',
		'⚠ Could not access local storage. Make sure it is enabled in your browser.':
			'⚠ 无法访问本地存储，请确认浏览器已启用本地存储。',
		'Device Name:': '设备名称：',
		'Enable simplified UI:': '启用简化界面：',
		'The following UI customization settings are unique both to the WLED device and this browser.':
			'以下界面自定义设置同时属于该 WLED 设备和当前浏览器。',
		'You will need to set them again if using a different browser, device or WLED IP address.':
			'更换浏览器、设备或 WLED IP 地址后需要重新设置。',
		'Refresh the main UI to apply changes.': '刷新主界面后生效。',
		'Loading settings...': '正在加载设置…',
		'UI Appearance': '界面外观',
		'I hate dark mode:': '我不喜欢深色模式：',
		'Why would you?': '为什么呢？',
		'Custom CSS:': '自定义 CSS：',
		'UI Background': '界面背景',
		'BG image:': '背景图片：',
		'Random BG image settings': '随机背景图片设置',
		'Holidays:': '节日列表：',
		'Clear local storage': '清除本地存储',
		'Show button labels': '显示按钮标签',
		'Color selection methods': '颜色选择方式',
		'Color Wheel': '色轮',
		'RGB sliders': 'RGB 滑块',
		'Quick color selectors': '快速颜色选择器',
		'HEX color input': 'HEX 颜色输入',
		'Show bottom tab bar in PC mode': 'PC 模式下显示底部标签栏',
		'Show preset IDs': '显示预设 ID',
		'Set segment length instead of stop LED': '设置分段长度而非结束灯珠',
		'Hide segment power & brightness': '隐藏分段电源和亮度',
		'Always expand first segment': '始终展开第一个分段',
		'Enable custom CSS': '启用自定义 CSS',
		'Enable custom Holidays list': '启用自定义节日列表',
		'Use effect default parameters': '使用灯效默认参数',
		'Power button preset override for On': '电源键开灯时的预设覆盖',
		'Power button preset override for Off': '电源键关灯时的预设覆盖',
		'Sort presets by ID': '按 ID 排序预设',
		'Background opacity': '背景不透明度',
		'Button opacity': '按钮不透明度',
		'BG image URL': '背景图片 URL',
		'Random BG image': '随机背景图片',
		'BG HEX color': '背景 HEX 颜色',
		'Cleared.': '已清除。',
		'⚠ Settings JSON parsing failed. (': '⚠ 设置 JSON 解析失败（',
		'⚠ Settings JSON saving failed. (': '⚠ 设置 JSON 保存失败（',

		// —— 设置：LED ——
		'LED setup': 'LED 配置',
		'Total LEDs:': 'LED 总数：',
		'Recommended power supply for brightest white:': '最亮白光时推荐的电源功率：',
		'Global brightness factor:': '全局亮度系数：',
		'Enable automatic brightness limiter:': '启用自动亮度限制：',
		'Automatically limits brightness to stay close to the limit.':
			'自动限制亮度以接近设定上限。',
		'Keep at <1A if powering LEDs directly from the ESP 5V pin!':
			'若直接从 ESP 的 5V 引脚供电，请保持在 1A 以下！',
		'If using multiple outputs it is recommended to use per-output limiter.':
			'使用多个输出时建议启用按输出限制。',
		'Analog (PWM) and virtual LEDs cannot use automatic brightness limiter.':
			'模拟（PWM）和虚拟 LED 无法使用自动亮度限制。',
		'Maximum PSU Current:': '电源最大电流：',
		'Use per-output limiter:': '使用按输出限制：',
		'Make sure you enter correct value for each LED output.':
			'请确保为每个 LED 输出填写正确数值。',
		'If using multiple outputs with only one PSU, distribute its power proportionally amongst outputs.':
			'多个输出共用一台电源时，请按比例分配功率。',
		'⚠ Your power supply provides high current.': '⚠ 您的电源输出电流较高。',
		'To improve the safety of your setup,': '为提升安全性，',
		'please use thick cables,': '请使用粗线缆、',
		'multiple power injection points and a fuse!': '多个供电注入点和保险丝！',
		'LED outputs:': 'LED 输出：',
		'LED memory usage:': 'LED 内存占用：',
		'⚠ You might run into stability or lag issues.': '⚠ 可能遇到稳定性或卡顿问题。',
		'Use less than': '请使用少于',
		'800 LEDs per output': '每个输出 800 颗灯珠',
		'for the best experience!': '以获得最佳体验！',
		'Show Advanced Settings': '显示高级设置',
		'Make a segment for each output:': '为每个输出创建分段：',
		'Custom bus start indices:': '自定义总线起始索引：',
		'Color Order Override:': '覆盖颜色顺序：',
		'Color & White': '颜色与白光',
		'Use Gamma correction for color:': '颜色启用伽马校正：',
		'(strongly recommended)': '（强烈推荐）',
		'Use Gamma correction for brightness:': '亮度启用伽马校正：',
		'(not recommended)': '（不推荐）',
		'Use Gamma value:': '伽马值：',
		'White Balance correction:': '白平衡校正：',
		'Global override for Auto-calculate white:': '自动计算白光的全局覆盖：',
		'Calculate CCT from RGB:': '由 RGB 计算 CCT：',
		'CCT IC used (Athom 15W):': '使用的 CCT 芯片（Athom 15W）：',
		'CCT blending (±100%):': 'CCT 混合（±100%）：',
		'Positive: additive blend, Negative: exclusive blend': '正值：加色混合，负值：排他混合',
		'Set to 0 when using 2-wire (reverse polarity) CCT strips':
			'使用两线（反极性）CCT 灯带时设为 0',
		'Hardware setup': '硬件设置',
		'Disable internal pull-up/down:': '禁用内部上/下拉：',
		'Touch threshold:': '触摸阈值：',
		'IR Remote': '红外遥控',
		'IR GPIO:': '红外 GPIO：',
		'Remote disabled': '遥控已禁用',
		'24-key RGB': '24 键 RGB',
		'24-key with CT': '24 键带色温',
		'40-key blue': '40 键蓝色',
		'44-key RGB': '44 键 RGB',
		'21-key RGB': '21 键 RGB',
		'6-key black': '6 键黑色',
		'9-key red': '9 键红色',
		'JSON remote': 'JSON 遥控',
		'Apply IR change to main segment only:': '红外指令仅作用于主分段：',
		'JSON file:': 'JSON 文件：',
		'IR info': '红外信息',
		'Relay GPIO:': '继电器 GPIO：',
		'Open drain': '开漏输出',
		'Delay:': '延迟：',
		'General settings': '通用设置',
		'Power up': '上电行为',
		'Turn LEDs on after power up/reset:': '上电/复位后点亮 LED：',
		'Bootup brightness:': '开机亮度：',
		'Apply preset': '应用预设',
		'at boot (0 = none)': '开机时（0 = 不应用）',
		'Default transition time:': '默认过渡时间：',
		'Random Palettes': '随机调色板',
		'Use harmonic colors in': '在随机调色板中使用和谐色',
		'Random palettes': '随机调色板',
		'Random Palette': '随机调色板',
		'Cycle Time:': '循环时间：',
		'Timed light': '定时灯',
		'Default duration:': '默认时长：',
		'Default target brightness:': '默认目标亮度：',
		'Mode:': '模式：',
		'Wait and set': '等待并设置',
		'Fade Color': '渐变色',
		'Palette wrapping:': '调色板环绕：',
		'Linear (wrap if moving)': '线性（移动时环绕）',
		'Linear (always wrap)': '线性（始终环绕）',
		'Linear (never wrap)': '线性（不环绕）',
		'None (not recommended)': '无（不推荐）',
		'None': '无',
		'Target refresh rate:': '目标刷新率：',
		'⚠ Unlimited FPS Mode is experimental ⚠': '⚠ 无限 FPS 模式为实验性功能 ⚠',
		'⚠ High FPS Mode is experimental.': '⚠ 高 FPS 模式为实验性功能。',
		'WLED configuration and presets first!': 'WLED 配置和预设！',
		'Config template:': '配置模板：',
		'Invalid Bus-config': '总线配置无效',
		'HUB75 changes require a reboot': 'HUB75 修改需要重启',
		"Too many LEDs! Can't handle that!": 'LED 数量过多，无法处理！',
		'Memory usage is high, reboot recommended! Set transitions to 0 to save memory.':
			'内存占用较高，建议重启！将过渡时间设为 0 以节省内存。',
		'GPIO:': 'GPIO：',
		'GPIOs:': 'GPIO：',
		'Off Refresh': '关闭时刷新',
		'IP address:': 'IP 地址：',
		'Config:': '配置：',
		'Panel (width x height):': '面板（宽 x 高）：',
		'No. of Panels:': '面板数量：',
		'rows x cols:': '行 x 列：',
		'Inverted output': '反相输出',
		'Index:': '索引：',
		'Start:': '起始：',
		'80% of max LED memory': '已达最大 LED 内存的 80%',
		'A power supply with total of': '所需电源总电流约为',
		'A is required.': 'A。',
		'(for most effects, ~': '（多数灯效约',
		'A is enough)<br>': 'A 即可）',
		'W & B': '白+蓝',
		'W & G': '白+绿',
		'W & R': '白+红',
		'Slowest': '最慢',
		'Slow': '慢',
		'Normal': '正常',
		'Fast': '快',
		'Unused': '未使用',
		"The file API isn't supported on this browser yet.": '此浏览器尚不支持文件 API。',
		"This browser doesn't support the `files` property of file inputs.":
			'此浏览器不支持文件输入的 `files` 属性。',
		'Please select a JSON file first!': '请先选择一个 JSON 文件！',
		'Invalid configuration JSON:': '无效的配置 JSON：',
		'Configuration has more than 36 LED outputs.': '配置包含超过 36 个 LED 输出。',
		'(R/O)': '（只读）',

		// —— 设置：2D ——
		'2D setup': '2D 配置',
		'Strip or panel:': '灯带或面板：',
		'1D Strip': '1D 灯带',
		'2D Matrix': '2D 矩阵',
		'Matrix Generator': '矩阵生成器',
		'Panel dimensions (WxH):': '面板尺寸（宽x高）：',
		'Horizontal panels:': '水平面板数：',
		'Vertical panels:': '垂直面板数：',
		'panel:': '面板：',
		'Orientation:': '方向：',
		'Serpentine:': '蛇形走线：',
		'Pressing Populate will create LED panel layout with pre-arranged matrix.':
			'点击「生成」将按预设矩阵创建 LED 面板布局。',
		'Values above': '上述数值',
		'will not': '不会',
		'affect final layout.': '影响最终布局。',
		'WARNING: You may need to update each panel parameters after they are generated.':
			'警告：生成后可能需要逐个更新每个面板的参数。',
		'Panel set-up': '面板设置',
		'Number of panels:': '面板数量：',
		'A matrix is made of 1 or more physical LED panels.':
			'一个矩阵由一个或多个物理 LED 面板组成。',
		'Each panel can be of different size and/or have different LED orientation and/or starting point and/or layout.':
			'每个面板可以有不同尺寸、不同 LED 方向、不同起始点和不同布局。',
		'LED panel layout': 'LED 面板布局',
		'Gap file:': '间隙文件：',
		'Note: Gap file is a': '注意：间隙文件是一个',
		'file containing an array with number of elements equal to the matrix size.':
			'文件，其数组元素数量与矩阵尺寸相同。',
		'A value of -1 means that pixel at that position is missing, a value of 0 means never paint that pixel, and 1 means regular pixel.':
			'-1 表示该位置缺少灯珠，0 表示该像素永不点亮，1 表示正常像素。',

		// —— 设置：DMX ——
		'Imma firin ma lazer (if it has DMX support)': '我要发射激光（如果支持 DMX）',
		'Proxy Universe': '代理 Universe',
		'from E1.31 to DMX (0=disabled)': '从 E1.31 到 DMX（0 = 禁用）',
		'This will disable the LED data output to DMX configurable below':
			'启用后将关闭下方配置的 DMX LED 数据输出',
		'Number of fixtures is taken from LED config page': '灯具数量取自 LED 配置页面',
		'Channels per fixture (15 max):': '每个灯具的通道数（最多 15）：',
		'Start channel:': '起始通道：',
		'Spacing between start channels:': '起始通道间距：',
		'WARNING: Channel gap is lower than channels per fixture.':
			'警告：通道间距小于每灯具通道数。',
		'This will cause overlap.': '这会造成通道重叠。',
		'DMX Map': 'DMX 映射',
		'DMX fixtures start LED:': 'DMX 灯具起始 LED：',
		'Channel functions': '通道功能',

		// —— 设置：同步 ——
		'Sync setup': '同步配置',
		'WLED Broadcast': 'WLED 广播',
		'UDP Port:': 'UDP 端口：',
		'2nd Port:': '第二端口：',
		'Disabled. Enable ESP-NOW in WiFi settings.': '已禁用。请在 WiFi 设置中启用 ESP-NOW。',
		'Use ESP-NOW sync:': '使用 ESP-NOW 同步：',
		'(in AP mode or no WiFi)': '（AP 模式或无 WiFi 时）',
		'Sync groups': '同步组',
		'Send:': '发送：',
		'Receive:': '接收：',
		'Brightness,': '亮度、',
		'Color,': '颜色、',
		'Effects,': '灯效、',
		'Segment options,': '分段选项、',
		'and': '',
		'Palette': '调色板',
		'bounds': '边界',
		'Enable Sync on start:': '启动时启用同步：',
		'Send notifications on direct change:': '直接变更时发送通知：',
		'Send notifications on button press or IR:': '按键或红外操作时发送通知：',
		'Send Alexa notifications:': '发送 Alexa 通知：',
		'Send Philips Hue change notifications:': '发送 Philips Hue 变更通知：',
		'UDP packet retransmissions:': 'UDP 数据包重传次数：',
		'Reboot required to apply changes.': '需要重启后生效。',
		'Enable instance list:': '启用实例列表：',
		'Make this instance discoverable:': '允许发现此实例：',
		'Receive UDP realtime:': '接收 UDP 实时数据：',
		'Use main segment only:': '仅使用主分段：',
		'Respect LED Maps:': '遵循 LED 映射：',
		'Network DMX input': '网络 DMX 输入',
		'Type:': '类型：',
		'Custom port': '自定义端口',
		'Port:': '端口：',
		'Multicast:': '组播：',
		'Start universe:': '起始 Universe：',
		'Reboot required.': '需要重启。',
		'Check out': '参见',
		'Skip out-of-sequence packets:': '跳过乱序数据包：',
		'DMX start address:': 'DMX 起始地址：',
		'DMX segment spacing:': 'DMX 分段间距：',
		'E1.31 port priority:': 'E1.31 端口优先级：',
		'DMX mode:': 'DMX 模式：',
		'Single RGB': '单灯 RGB',
		'Single DRGB': '单灯 DRGB',
		'Effect + White': '灯效 + 白光',
		'Effect Segment': '灯效分段',
		'Effect Segment + White': '灯效分段 + 白光',
		'Multi RGB': '多灯 RGB',
		'Dimmer + Multi RGB': '调光 + 多灯 RGB',
		'Multi RGBW': '多灯 RGBW',
		'E1.31 info': 'E1.31 信息',
		'Timeout:': '超时：',
		'Force max brightness:': '强制最大亮度：',
		'Disable realtime gamma correction:': '禁用实时伽马校正：',
		'Realtime LED offset:': '实时 LED 偏移：',
		'Wired DMX Input': '有线 DMX 输入',
		'DMX RX Pin:': 'DMX 接收引脚：',
		'DMX TX Pin:': 'DMX 发送引脚：',
		'DMX Enable Pin:': 'DMX 使能引脚：',
		'DMX Port:': 'DMX 端口：',
		'This firmware build does not include DMX Input support.':
			'此固件版本不包含 DMX 输入支持。',
		'This firmware build does not include DMX output support.':
			'此固件版本不包含 DMX 输出支持。',
		'Alexa Voice Assistant': 'Alexa 语音助手',
		'This firmware build does not include Alexa support.':
			'此固件版本不包含 Alexa 支持。',
		'Emulate Alexa device:': '模拟 Alexa 设备：',
		'Alexa invocation name:': 'Alexa 唤醒名称：',
		'Also emulate devices to call the first': '同时模拟设备以调用前',
		'MQTT and Hue sync connect to external hosts!': 'MQTT 和 Hue 同步会连接外部主机！',
		'This may impact the responsiveness of WLED.': '这可能会影响 WLED 的响应速度。',
		'For best results, only use one of these services at a time.':
			'为获得最佳效果，建议一次只使用其中一种服务。',
		'(alternatively, connect a second ESP to them and use the UDP sync)':
			'（或者用第二个 ESP 连接它们并使用 UDP 同步）',
		'This firmware build does not include MQTT support.':
			'此固件版本不包含 MQTT 支持。',
		'Enable MQTT:': '启用 MQTT：',
		'Broker:': '服务器：',
		'The MQTT credentials are sent over an unsecured connection.':
			'MQTT 凭据通过未加密连接发送。',
		'Never use the MQTT password for another service!': '切勿将 MQTT 密码用于其他服务！',
		'Username:': '用户名：',
		'Password:': '密码：',
		'Client ID:': '客户端 ID：',
		'Device Topic:': '设备主题：',
		'Group Topic:': '组主题：',
		'Publish on button press:': '按键时发布：',
		'Retain brightness & color messages:': '保留亮度和颜色消息：',
		'MQTT info': 'MQTT 信息',
		'This firmware build does not include Philips Hue support.':
			'此固件版本不包含 Philips Hue 支持。',
		"You can find the bridge IP and the light number in the 'About' section of the hue app.":
			'可在 Hue 应用的「关于」中查看桥接器 IP 和灯的编号。',
		'Poll Hue light': '轮询 Hue 灯',
		'every': '每',
		'ms:': '毫秒：',
		'Then, receive': '然后接收',
		'On/Off,': '开关、',
		'Brightness, and': '亮度和',
		'Hue Bridge IP:': 'Hue 桥接器 IP：',
		'Press the pushlink button on the bridge, after that save this page!':
			'请按下桥接器上的连接按钮，然后保存本页！',
		'(when first connecting)': '（首次连接时）',
		'Hue status:': 'Hue 状态：',
		'Disabled in this build': '此版本中已禁用',
		'This firmware build does not support Serial interface.':
			'此固件版本不支持串口接口。',
		'Baud rate:': '波特率：',
		'Keep at 115200 to use Improv. Some boards may not support high rates.':
			'使用 Improv 请保持 115200。部分开发板可能不支持高波特率。',

		// —— 设置：时间 ——
		'Time setup': '时间配置',
		'Get time from NTP server:': '从 NTP 服务器获取时间：',
		'Use 24h format:': '使用 24 小时制：',
		'Time zone:': '时区：',
		'CST (AWST, PHST)': 'CST（AWST、PHST）',
		'JST (KST)': 'JST（KST）',
		'North Korea': '朝鲜',
		'IST (India)': 'IST（印度）',
		'HST (Hawaii)': 'HST（夏威夷）',
		'NOVT (Novosibirsk)': 'NOVT（新西伯利亚）',
		'AKST/AKDT (Anchorage)': 'AKST/AKDT（安克雷奇）',
		'PKT (Pakistan)': 'PKT（巴基斯坦）',
		'BRT (Brasília)': 'BRT（巴西利亚）',
		'AWST (Perth)': 'AWST（珀斯）',
		'UTC offset:': 'UTC 偏移：',
		'seconds (max. 18 hours)': '秒（最多 18 小时）',
		'Current local time is': '当前本地时间为',
		'Latitude:': '纬度：',
		'Longitude:': '经度：',
		'Get location': '获取位置',
		'(opens new tab, only works in browser)': '（会打开新标签页，仅在浏览器中可用）',
		'Analog Clock overlay:': '模拟时钟叠加：',
		'First LED:': '首颗 LED：',
		'Last LED:': '末颗 LED：',
		'12h LED:': '12 点 LED：',
		'Show 5min marks:': '显示 5 分钟刻度：',
		'Seconds (as trail):': '秒（拖尾）：',
		'Show clock overlay only if all LEDs are solid black:':
			'仅当所有 LED 全黑时显示时钟叠加：',
		'Countdown Mode:': '倒计时模式：',
		'Countdown Goal:': '倒计时目标：',
		'Date:': '日期：',
		'Macro Presets': '宏预设',
		'Presets can be used as macros for both JSON and HTTP API commands.':
			'预设可作为 JSON 和 HTTP API 命令的宏使用。',
		'Enter the preset ID below.': '在下方输入预设 ID。',
		'Use 0 for the default action instead of a preset': '填 0 表示使用默认动作而非预设',
		'Timer & Alexa Presets': '定时器与 Alexa 预设',
		'Countdown-Over Preset:': '倒计时结束预设：',
		'Timed-Light-Over Presets:': '定时灯结束预设：',
		'Alexa On/Off Preset:': 'Alexa 开关预设：',
		'Button Action Presets': '按键动作预设',
		'Analog Button setup': '模拟按键设置',
		'Time-Controlled Presets': '时间控制预设',
		'Please visit the main UI first to load presets': '请先访问主界面以加载预设',
		'Select preset': '选择预设',
		'Default Action (0)': '默认动作 (0)',
		'En.': '启用',
		'Type': '类型',
		'Hour': '时',
		'Minute': '分',
		'Analog Functions': '模拟功能',
		'Analog Segment Opacity': '模拟分段不透明度',
		'Push inverted': '按下反相',
		'PIR sensor': '人体红外传感器',
		'Analog inverted': '模拟反相',
		'Touch (switch)': '触摸（开关）',

		// —— 设置：WiFi ——
		'Save & Connect': '保存并连接',
		'WiFi & Network Settings': 'WiFi 与网络设置',
		'Wireless network': '无线网络',
		'Ethernet Type': '以太网类型',
		'DNS & mDNS': 'DNS 与 mDNS',
		'DNS server address:': 'DNS 服务器地址：',
		'mDNS address (leave empty for no mDNS):': 'mDNS 地址（留空则不启用 mDNS）：',
		'Client IP:': '客户端 IP：',
		'Not connected': '未连接',
		'Configure Access Point': '配置接入点',
		'AP SSID (leave empty for no AP):': 'AP SSID（留空则不开启 AP）：',
		'Hide AP name:': '隐藏 AP 名称：',
		'AP password (leave empty for open):': 'AP 密码（留空为开放网络）：',
		'Access Point WiFi channel:': '接入点 WiFi 信道：',
		'AP opens:': 'AP 开启时机：',
		'No connection after boot': '开机后无连接时',
		'Disconnected': '断开连接时',
		'Always': '总是',
		'Never (not recommended)': '从不（不推荐）',
		'Temporary (no connection after boot)': '临时（开机后无连接时）',
		'AP IP:': 'AP IP：',
		'Not active': '未启用',
		'WiFi Power': 'WiFi 功率',
		'Force 802.11g mode (ESP8266 only):': '强制 802.11g 模式（仅 ESP8266）：',
		'Disable WiFi sleep:': '禁用 WiFi 休眠：',
		'Disabling WiFi sleep increases power consumption': '禁用 WiFi 休眠会增加功耗，',
		'but can help with connectivity issues and sync.':
			'但有助于改善连接问题和同步。',
		'WiFi band:': 'WiFi 频段：',
		'2.4 GHz only': '仅 2.4 GHz',
		'5 GHz only': '仅 5 GHz',
		'Auto (both)': '自动（两者）',
		'Max. TX power:': '最大发射功率：',
		'WARNING: Modifying TX power may render device unreachable.':
			'警告：修改发射功率可能导致设备失联。',
		'ESP-NOW Wireless': 'ESP-NOW 无线',
		'This firmware build does not include ESP-NOW support.':
			'此固件版本不包含 ESP-NOW 支持。',
		'Enable ESP-NOW:': '启用 ESP-NOW：',
		'Listen for events over ESP-NOW': '通过 ESP-NOW 监听事件',
		'Keep disabled if not using a remote or ESP-NOW sync, increases power consumption.':
			'不使用遥控或 ESP-NOW 同步时请保持禁用，否则会增加功耗。',
		'Last device seen:': '最近发现的设备：',
		'Linked MACs (10 max):': '已绑定 MAC（最多 10 个）：',
		'Scanning...': '扫描中…',
		'Other network...': '其他网络…',
		', empty to not connect': '，留空则不连接',
		'Also used by Ethernet': '也用于以太网',

		// —— 设置：安全与更新 ——
		'Settings PIN:': '设置 PIN 码：',
		'⚠ Unencrypted transmission. Be prudent when selecting PIN, do NOT use your banking, door, SIM, etc. pin!':
			'⚠ 传输未加密。设置 PIN 时请谨慎，不要使用银行卡、门禁、SIM 卡等密码！',
		'Lock wireless (OTA) software update:': '锁定无线（OTA）软件更新：',
		'Passphrase:': '口令：',
		'To enable OTA, for security reasons you need to also enter the correct password!':
			'出于安全考虑，启用 OTA 时还需要输入正确密码！',
		'The password should be changed when OTA is enabled.': '启用 OTA 后应修改密码。',
		'Disable OTA when not in use, otherwise an attacker can reflash device software!':
			'不使用时请关闭 OTA，否则攻击者可重刷设备固件！',
		'Settings on this page are only changeable if OTA lock is disabled!':
			'仅当 OTA 锁定已关闭时才能修改本页设置！',
		'Deny access to WiFi settings if locked:': '锁定时拒绝访问 WiFi 设置：',
		'Factory reset:': '恢复出厂设置：',
		'All settings and presets will be erased.': '所有设置和预设都将被清除。',
		'⚠ Unencrypted transmission. An attacker on the same network can intercept form data!':
			'⚠ 传输未加密。同一网络中的攻击者可截获表单数据！',
		'Software Update': '软件更新',
		'Enable ArduinoOTA:': '启用 ArduinoOTA：',
		'Only allow update from same network/WiFi:': '仅允许同一网络/WiFi 内更新：',
		'⚠ If you are using multiple VLANs (i.e. IoT or guest network) either set PIN or disable this option.':
			'⚠ 如果使用多个 VLAN（如 IoT 或访客网络），请设置 PIN 或关闭此选项。',
		'Disabling this option will make your device less secure.':
			'关闭此选项会降低设备安全性。',
		'Backup & Restore': '备份与恢复',
		'⚠ Restoring presets/configuration will OVERWRITE your current presets/configuration.':
			'⚠ 恢复预设/配置将覆盖当前的预设/配置。',
		'Incorrect upload or configuration may require a factory reset or re-flashing of your ESP.':
			'上传或配置错误可能需要恢复出厂设置或重新刷写 ESP。',
		'For security reasons, passwords are not backed up.': '出于安全考虑，密码不会被备份。',
		'Backup presets': '备份预设',
		'Restore presets': '恢复预设',
		'Backup configuration': '备份配置',
		'Restore configuration': '恢复配置',
		'version ##VERSION##': '版本 ##VERSION##',
		'Contributors, dependencies and special thanks': '贡献者、依赖与特别致谢',
		'A huge thank you to everyone who helped me create WLED!':
			'衷心感谢每一位帮助我打造 WLED 的人！',
		'(c) 2016-2026 Christian Schwinne and individual WLED contributors':
			'（c）2016-2026 Christian Schwinne 及 WLED 各位贡献者',
		'Licensed under the': '基于',
		'EUPL v1.2 license': 'EUPL v1.2 许可证',
		'Installed version:': '已安装版本：',

		// —— 设置：用户模块 ——
		'✔ Configuration saved!': '✔ 配置已保存！',
		'⚠ Could not load configuration.': '⚠ 无法加载配置。',
		'Usermod Setup': '用户模块配置',
		'Global I': '全局 I',
		'C & SPI': 'C 与 SPI',
		'C GPIOs (HW)': 'C GPIO（硬件）',
		'SPI GPIOs (HW)': 'SPI GPIO（硬件）',
		'only changable on ESP32': '仅在 ESP32 上可修改',
		'change requires reboot!': '修改需要重启！',
		'Reboot after save?': '保存后重启？',

		// —— 设置：PIN 与引脚信息 ——
		'PIN required': '需要 PIN 码',
		'Please enter settings PIN code': '请输入设置 PIN 码',
		'Submit': '提交',
		'No pins available.': '没有可用引脚。',
		'Pin': '引脚',
		'Used by': '使用者',
		'Pin Notes': '引脚说明',
		'Available': '可用',
		'(PU)': '（上拉）',
		'(No PU)': '（无上拉）',
		'Error loading pin info': '加载引脚信息出错',

		// —— 更新页 ——
		'WLED Software Update': 'WLED 软件更新',
		'Release:': '发行版：',
		'Latest release: Checking...': '最新版本：检查中…',
		'Latest version:': '最新版本：',
		'Auto update': '自动更新',
		'Manual upload': '手动上传',
		'Ignore firmware validation': '忽略固件校验',
		'Revert update': '回滚更新',
		'Bootloader Update': 'Bootloader 更新',
		'Warning:': '警告：',
		'Only upload verified ESP32 bootloader files!':
			'只能上传经过验证的 ESP32 bootloader 文件！',
		'Update Bootloader': '更新 Bootloader',
		'Updating...': '更新中…',
		'Please do not close or refresh the page :)': '请勿关闭或刷新页面 :)',
		'Revert!': '回滚！',
		'Could not fetch device info:': '无法获取设备信息：',
		'No release repository available for this build.':
			'此固件版本没有可用的发行仓库。',
		'Checking GitHub for latest release...': '正在从 GitHub 检查最新版本…',
		'Auto-update failed:': '自动更新失败：',
		'Current bootloader SHA256:': '当前 bootloader SHA256：',

		// —— 欢迎页 ——
		'Welcome!': '欢迎！',
		'Welcome to WLED!': '欢迎使用 WLED！',
		'A versatile tool for controlling LEDs': '一款多功能的 LED 控制工具',
		'Find out more at wled.me': '更多信息请访问 wled.me',
		'Next steps:': '后续步骤：',
		'Connect to your local WiFi here!': '在此连接本地 WiFi！',
		'Just trying this out in AP mode?': '只是在 AP 模式下试用？',
		'To the controls!': '前往控制界面！',
		'WiFi settings': 'WiFi 设置',

		// —— 主界面动态提示 ——
		'Connection to light failed!': '连接灯光失败！',
		'Copied to clipboard!': '已复制到剪贴板！',
		'Could not load Node list!': '无法加载节点列表！',
		'No segments!': '没有分段！',
		'Empty response': '空响应',
		'Timer deactivated.': '定时器已停用。',
		'Other lights in the network will now sync to this one.':
			'网络中的其他灯光现在将同步到此设备。',
		'This light and other lights in the network will no longer sync.':
			'此设备与网络中的其他灯光将不再同步。',
		'You need at least 2 presets to make a playlist!':
			'创建播放列表至少需要 2 个预设！',
		'You need to have multiple segments to delete one!':
			'需要有多个分段才能删除分段！',
		'Loading preset': '正在加载预设',
		'Saving': '正在保存',
		'You will not be asked again.': '不会再询问。',
		'Reporting upgrade...': '正在上报升级…',
		'Thank you! Future upgrades will be reported automatically.':
			'谢谢！后续升级将自动上报。',
		'Thank you for reporting!': '感谢反馈！',
		'Report failed. Please try again later.': '上报失败，请稍后再试。',
		'Report failed': '上报失败',
		'🎉 Thank you for installing WLED!': '🎉 感谢安装 WLED！',
		'🎉 WLED Upgrade Detected!': '🎉 检测到 WLED 升级！',
		'Buffer locked!': '缓冲区已锁定！',
		'No RAM for buffer!': '内存不足，无法分配缓冲区！',
		'Effect RAM depleted!': '灯效内存已耗尽！',
		'JSON parsing error!': 'JSON 解析错误！',
		'Could not mount filesystem!': '无法挂载文件系统！',
		'Not enough space to save preset!': '空间不足，无法保存预设！',
		'File Mode. Please enter WLED IP!': '文件模式：请输入 WLED IP！',
		'Playlist entries': '播放列表条目',
		'Quick load label:': '快速加载标签：',
		'API command': 'API 命令',
		'Duration': '时长',
		'Repeat': '重复',
		'Test': '测试',
		'Stop': '停止',

		// —— 补充遗漏 ——
		'Time:': '时间：',
		'(input only)': '（仅输入）',

		// —— 运行时拼装文案 ——
		'Color Wheel:': '色轮：',
		'RGB sliders:': 'RGB 滑块：',
		'Quick color selectors:': '快速颜色选择器：',
		'HEX color input:': 'HEX 颜色输入：',
		'Upload': '上传',
		'Remove': '移除',
		'Color Order:': '颜色顺序：',
		'Reversed': '反向',
		'Skip first LEDs:': '跳过前几颗 LED：',
		'mA/LED:': 'mA/灯珠：',
		'max. mA/LED:': '最大 mA/灯珠：',
		'PSU:': '电源：',
		'Swap:': '交换：',
		'Custom': '自定义',
		'55mA (typ. 5V WS281x)': '55mA（典型 5V WS281x）',
		'35mA (eco WS2812)': '35mA（节能 WS2812）',
		'30mA (typ. 12V)': '30mA（典型 12V）',
		'12mA (WS2815)': '12mA（WS2815）',
		'15mA (seed/fairy pixels)': '15mA（种子/仙女灯珠）',
		'Clock': '时钟',
		'Realtime': '实时数据',
		'Network name (SSID, empty to not connect):': '网络名称（SSID，留空则不连接）：',
		'Network name (SSID):': '网络名称（SSID）：',
		'Network password:': '网络密码：',
		'BSSID (optional):': 'BSSID（可选）：',
		'Static IP (leave at 0.0.0.0 for DHCP):': '静态 IP（留 0.0.0.0 使用 DHCP）：',
		'Also used by Ethernet:': '也用于以太网：',
		'Static gateway:': '静态网关：',
		'Static subnet mask:': '静态子网掩码：',
		'Short press': '短按',
		'Long press': '长按',
		'Double press': '双击',
		'Pushbutton': '按钮',
		'Switch': '开关',
		'Touch': '触摸',
		'Analog': '模拟',
		'Disabled': '已禁用',
		'Regular': '常规',

		// —— 2D 与 LED 引脚 ——
		'Data GPIO:': '数据 GPIO：',
		'Clock GPIO:': '时钟 GPIO：',
		'1st LED:': '首颗 LED：',
		'Bottom': '底部',
		'Top': '顶部',
		'Left': '左侧',
		'Right': '右侧',
		'Vertical': '纵向',
		'Horizontal': '横向',
		'Dimensions (WxH):': '尺寸（宽x高）：',
		'Offset X:': 'X 偏移：',
		'Offset Y:': 'Y 偏移：',
		'(offset from top-left corner in # LEDs)': '（以灯珠数为单位，从左上角偏移）',
		'Static IP (leave at 0.0.0.0 for DHCP)': '静态 IP（留 0.0.0.0 使用 DHCP）',
		'Color palette not used': '未使用调色板',
		'LED:': 'LED：',
		'Y:': 'Y：'
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

	/** 含数值/编号的运行时拼装文案：正则 → 替换模板（$1… 为捕获组）。 */
	var PATTERNS = [
		[/^A power supply with total of ([\d.]+)A is required\.$/, '所需电源总电流约为 $1A。'],
		[/^\(for most effects, ~([\d.]+)A is enough\)$/, '（多数灯效约 $1A 即可）'],
		[/^Hardware channels used: (.+)$/, '已用硬件通道：$1'],
		[/^#(\d+) GPIO:$/, '#$1 GPIO：'],
		[/^Panel (\d+)$/, '面板 $1'],
		[/^Matrix Dimensions \(W\*H=LC\): (.+)$/, '矩阵尺寸（宽×高=长）：$1'],
		[/^Segment (\d+) opacity$/, '分段 $1 不透明度']
	];

	/** 命中字典时返回带原始首尾空白的译文，否则返回 null（不改动）。 */
	function translate(value) {
		if (!value) return null;
		var key = normalize(value);
		if (!key) return null;
		if (Object.prototype.hasOwnProperty.call(DICT, key)) {
			var lead = value.match(/^\s*/)[0];
			var trail = value.match(/\s*$/)[0];
			return lead + DICT[key] + trail;
		}
		// “Button 0 - Pushbutton” 这类组合文案：编号保留，类型回查字典
		var btn = key.match(/^Button (\d+) - (.+)$/);
		if (btn) {
			var leadB = value.match(/^\s*/)[0];
			var trailB = value.match(/\s*$/)[0];
			var typeName = Object.prototype.hasOwnProperty.call(DICT, btn[2]) ? DICT[btn[2]] : btn[2];
			return leadB + '按键 ' + btn[1] + ' - ' + typeName + trailB;
		}
		for (var i = 0; i < PATTERNS.length; i++) {
			if (PATTERNS[i][0].test(key)) {
				var lead2 = value.match(/^\s*/)[0];
				var trail2 = value.match(/\s*$/)[0];
				return lead2 + key.replace(PATTERNS[i][0], PATTERNS[i][1]) + trail2;
			}
		}
		return null;
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
		var btns = root.querySelectorAll('input[type=button][value],input[type=submit][value],input[type=reset][value]');
		for (var j = 0; j < btns.length; j++) {
			translateAttr(btns[j], 'value');
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
