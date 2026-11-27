package ca.cgagnier.wlednativeandroid.ui.create

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.FavoriteEffect
import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import ca.cgagnier.wlednativeandroid.repository.FavoriteEffectDao
import ca.cgagnier.wlednativeandroid.service.DeviceConnectionManager
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import ca.cgagnier.wlednativeandroid.service.websocket.DeviceWithState
import ca.cgagnier.wlednativeandroid.ui.effects.EffectCatalog
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteTemplate
import ca.cgagnier.wlednativeandroid.ui.effects.PaletteTemplateStore
import ca.cgagnier.wlednativeandroid.ui.effects.packRgb
import ca.cgagnier.wlednativeandroid.ui.isle.SelectedDeviceStore
import ca.cgagnier.wlednativeandroid.util.GifEncoder
import ca.cgagnier.wlednativeandroid.util.normalizedToChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** 创作工坊的三个分段：色轮调色 / 自定义图文 / 多色模板。 */
enum class CreateMode { COLOR, PIXEL, TEMPLATE }

/** 一条操作结果，交给界面弹提示。 */
data class CreateStatus(val message: String, val isError: Boolean)

/**
 * 按固件英文名反查灯效 id，取不到直接崩。
 *
 * 创作工坊把自定义图文编码成 GIF 上传，再用 `Image` 效果显示。
 * 这个 id 曾经被写死成数字，而固件调整效果槽位后，写死的数字会静默指向另一个效果 ——
 * 38 一度是 Image，槽位对齐后变成 Aurora（极光），表现是「上传的图案显示成极光」，
 * 而且不报错、很难联想到是 id 漂移。改成按名反查，槽位变更自动跟随。
 */
private fun requireEffectId(name: String): Int =
    EffectCatalog.byName(name)?.id
        ?: error("固件灯效目录缺少「$name」，请重新运行 tools/gen_effect_catalog.py")

private val IMAGE_EFFECT = requireEffectId("Image")

private const val COLOR_SLOT_COUNT = 3
private const val DEFAULT_GRID_COLS = 16
private const val DEFAULT_GRID_ROWS = 10
private const val MAX_GRID_SIDE = 40

/** 预览图每个灯珠放大成 6×6 像素，卡片上放大显示时不至于糊成一团。 */
private const val PREVIEW_CELL_SCALE = 6

/** 收藏名截断长度，过长的文字不适合显示在快捷卡片上。 */
private const val FAVORITE_NAME_MAX_LENGTH = 12

/** 自定义图文里文字/时间层可选的文字颜色。 */
val GRAPHIC_COLORS: List<Int> = listOf(
    0xFFFFFF, 0x5FE3E4, 0x8C7BFF,
    0xFF7AD0, 0x3EE08F, 0xFFC46B, 0xFF5A5A,
)

/** 自定义文字里可一键插入的常用表情（App 侧栅格化，不依赖固件字库）。 */
val GRAPHIC_EMOJIS: List<String> = listOf("😀", "😄", "😊", "😍", "😎", "😢", "😡", "🤖", "❤️", "⭐")

/** 上游 PixelForge 支持的时间函数占位符，点一下追加到文字末尾。 */
val GRAPHIC_TOKENS: List<String> = listOf(
    "#TIME", "#HHMM", "#DATE", "#DDMM", "#MMDD", "#YYYY", "#YY",
    "#HH", "#MM", "#SS", "#MO", "#DD", "#MON", "#MONL", "#DAY", "#DDDD",
)

/** 多色模板「添加颜色」时的候选色，尽量不与已有色重复。 */
private val TEMPLATE_ADD_COLORS = listOf(0xFFB46B, 0x3EE08F, 0xFF7AD0, 0xFFFFFF, 0x5FE3E4)

/**
 * 创作工坊。
 *
 * 色轮调色：两个锚点取色，饱和度统一压缩后再下发，避免选到过曝的纯色。
 * 自定义图文：文字 / 时间 / 图片按设备灯珠矩阵在 App 侧栅格化成 GIF 上传，
 * 用固件的 `Image` 效果播放，因此中文与 Emoji 不依赖固件字库。
 */
class CreateViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
    private val favoriteEffectDao: FavoriteEffectDao,
    private val selectedDeviceStore: SelectedDeviceStore,
    private val deviceApiFactory: DeviceApiFactory,
    private val paletteTemplateStore: PaletteTemplateStore,
    private val customGraphicStore: CustomGraphicStore,
) : ViewModel() {

    val devices: StateFlow<List<DeviceWithState>> = deviceConnectionManager.allDevicesWithState
    val selectedMacAddress: StateFlow<String?> = selectedDeviceStore.macAddress

    private val _mode = MutableStateFlow(CreateMode.COLOR)
    val mode: StateFlow<CreateMode> = _mode.asStateFlow()

    private val _primary = MutableStateFlow(0x5FE3E4)
    val primary: StateFlow<Int> = _primary.asStateFlow()

    private val _secondary = MutableStateFlow(0x8C7BFF)
    val secondary: StateFlow<Int> = _secondary.asStateFlow()

    private val _saturation = MutableStateFlow(0.9f)
    val saturation: StateFlow<Float> = _saturation.asStateFlow()

    private val _speed = MutableStateFlow(0.6f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /**
     * 自定义图文的叠加内容。默认放一条文字层，用户可继续叠加时间与图片。
     * 发送时按设备矩阵栅格化，不再区分「自定义文字 / 像素画板」。
     */
    private val _layers = MutableStateFlow(
        listOf(
            GraphicLayer(
                id = 1L,
                type = GraphicLayerType.TEXT,
                text = "LumiIsle",
                color = 0xFFFFFF,
                sizePercent = 45,
            ),
        ),
    )
    val layers: StateFlow<List<GraphicLayer>> = _layers.asStateFlow()

    private var nextLayerId = 2L

    /** 反向滚动（对应 PixelForge 的 Reverse）。 */
    private val _reverse = MutableStateFlow(false)
    val reverse: StateFlow<Boolean> = _reverse.asStateFlow()

    /** 拖尾残影 0..90（对应 Trail）。 */
    private val _trailPercent = MutableStateFlow(0)
    val trailPercent: StateFlow<Int> = _trailPercent.asStateFlow()

    /** 暗部裁剪阈值 0..255（对应 Dark Pixel Cutoff）。 */
    private val _darkCutoff = MutableStateFlow(0)
    val darkCutoff: StateFlow<Int> = _darkCutoff.asStateFlow()

    /** 输出背景色（对应 Background Color）。 */
    private val _background = MutableStateFlow(0x000000)
    val background: StateFlow<Int> = _background.asStateFlow()

    private val _gridCols = MutableStateFlow(DEFAULT_GRID_COLS)
    val gridCols: StateFlow<Int> = _gridCols.asStateFlow()

    private val _gridRows = MutableStateFlow(DEFAULT_GRID_ROWS)
    val gridRows: StateFlow<Int> = _gridRows.asStateFlow()

    /** 0 表示该格不发光；由 [refreshPreview] 按当前图层栅格化后写入。 */
    private val _grid = MutableStateFlow(List(DEFAULT_GRID_COLS * DEFAULT_GRID_ROWS) { 0 })
    val grid: StateFlow<List<Int>> = _grid.asStateFlow()

    private val _frameCount = MutableStateFlow(1)
    /** 发送时会生成的 GIF 帧数，>1 表示内容超宽、会上跑马灯。 */
    val frameCount: StateFlow<Int> = _frameCount.asStateFlow()

    private val _status = MutableStateFlow<CreateStatus?>(null)
    val status: StateFlow<CreateStatus?> = _status.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 手机本地保存的多色模板。 */
    val paletteTemplates: StateFlow<List<PaletteTemplate>> = paletteTemplateStore.templates

    /** 正在编辑的模板色标（`0xRRGGBB`），默认给出三色，用户可增减。 */
    private val _templateColors = MutableStateFlow(listOf(0x5FE3E4, 0x8C7BFF, 0xFF7AD0))
    val templateColors: StateFlow<List<Int>> = _templateColors.asStateFlow()

    fun setTemplateColor(index: Int, packed: Int) {
        val current = _templateColors.value
        if (index !in current.indices) return
        _templateColors.value = current.toMutableList().apply {
            this[index] = packed and 0xFFFFFF
        }
    }

    fun addTemplateColor() {
        val current = _templateColors.value
        if (current.size >= PaletteTemplateStore.MAX_COLORS) return
        val candidate = TEMPLATE_ADD_COLORS.firstOrNull { it !in current } ?: 0xFFFFFF
        _templateColors.value = current + candidate
    }

    fun removeTemplateColor(index: Int) {
        val current = _templateColors.value
        if (current.size <= PaletteTemplateStore.MIN_COLORS || index !in current.indices) return
        _templateColors.value = current.filterIndexed { i, _ -> i != index }
    }

    /** 保存当前色标为模板；成功返回 true，交给界面弹提示。 */
    fun saveTemplate(): Boolean = paletteTemplateStore.add(_templateColors.value) != null

    fun deleteTemplate(id: String) {
        paletteTemplateStore.remove(id)
    }

    val favorites: StateFlow<List<FavoriteEffect>> = favoriteEffectDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    fun selectMode(mode: CreateMode) {
        _mode.value = mode
    }

    fun setPrimary(packedRgb: Int) {
        _primary.value = packedRgb
    }

    fun setSecondary(packedRgb: Int) {
        _secondary.value = packedRgb
    }

    fun setSaturation(value: Float) {
        _saturation.value = value.coerceIn(0f, 1f)
    }

    fun setSpeed(value: Float) {
        _speed.value = value.coerceIn(0f, 1f)
    }

    fun setReverse(value: Boolean) {
        _reverse.value = value
    }

    fun setTrailPercent(value: Int) {
        _trailPercent.value = value.coerceIn(0, 90)
    }

    fun setDarkCutoff(value: Int) {
        _darkCutoff.value = value.coerceIn(0, 255)
    }

    fun setBackground(packed: Int) {
        _background.value = packed and 0xFFFFFF
    }

    fun addLayer(type: GraphicLayerType) {
        val layer = when (type) {
            GraphicLayerType.TEXT -> GraphicLayer(
                id = nextLayerId++,
                type = type,
                text = "文字",
                sizePercent = 45,
                startYPercent = 35,
                stopYPercent = 65,
            )
            GraphicLayerType.TIME -> GraphicLayer(
                id = nextLayerId++,
                type = type,
                text = "{time}",
                sizePercent = 45,
                startYPercent = 10,
                stopYPercent = 35,
            )
            GraphicLayerType.IMAGE -> GraphicLayer(
                id = nextLayerId++,
                type = type,
                sizePercent = 70,
            )
        }
        _layers.value = _layers.value + layer
    }

    fun updateLayer(id: Long, transform: (GraphicLayer) -> GraphicLayer) {
        _layers.value = _layers.value.map { if (it.id == id) transform(it) else it }
    }

    fun removeLayer(id: Long) {
        _layers.value = _layers.value.filterNot { it.id == id }
    }

    fun moveLayer(id: Long, delta: Int) {
        val current = _layers.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        val target = index + delta
        if (index < 0 || target !in current.indices) return
        val item = current.removeAt(index)
        current.add(target, item)
        _layers.value = current
    }

    /**
     * 画布尺寸跟随设备矩阵：圆形灯管是竖长条，画布也应该是竖的，
     * 否则上传后画面会被压扁。
     */
    fun configureGrid(device: DeviceWithState?) {
        val leds = device?.stateInfo?.info?.leds ?: return
        val cols = leds.width.coerceIn(4, MAX_GRID_SIDE)
        val rows = leds.height.coerceIn(4, MAX_GRID_SIDE)
        if (cols == _gridCols.value && rows == _gridRows.value) return
        _gridCols.value = cols
        _gridRows.value = rows
    }

    /** 把当前图层叠加结果重新栅格化，供预览画布显示。 */
    fun refreshPreview(context: Context) {
        val frames = GraphicComposer.renderFrames(
            context = context,
            cols = _gridCols.value,
            rows = _gridRows.value,
            layers = _layers.value,
            options = graphicOptions(),
        )
        _frameCount.value = frames.size.coerceAtLeast(1)
        _grid.value = GraphicComposer.renderPreview(
            context = context,
            cols = _gridCols.value,
            rows = _gridRows.value,
            layers = _layers.value,
            options = graphicOptions(),
        )
    }

    private fun graphicOptions(): GraphicOptions = GraphicOptions(
        // 滑杆 0..100% 映射到原来的 0..20%，曲线见 graphicSpeedFraction
        scrollSpeed = graphicSpeedFraction(_speed.value),
        reverse = _reverse.value,
        trailPercent = _trailPercent.value,
        darkCutoff = _darkCutoff.value,
        background = _background.value,
    )

    fun dismissStatus() {
        _status.value = null
    }

    /** 把色轮选中的两种颜色下发到灯上，同时带上流动速度。 */
    fun applyColors(device: DeviceWithState) {
        val colors = listOf(
            applySaturation(_primary.value),
            applySaturation(_secondary.value),
            listOf(0, 0, 0),
        )
        viewModelScope.launch {
            val ok = deviceConnectionManager.sendSegmentPatch(device.device.macAddress) { segment ->
                segment.copy(
                    colors = colors,
                    effectSpeed = normalizedToChannel(_speed.value),
                )
            }
            _status.value = CreateStatus(
                message = if (ok) "已同步到灯管" else "下发失败，请检查连接",
                isError = !ok,
            )
        }
    }

    /**
     * 自定义图文 -> 多帧 GIF -> 上传 -> 切到 Image 效果。
     *
     * 固件只认设备文件系统里的图片文件，所以必须先上传；上传成功后再把段落名设成文件名。
     * 内容比矩阵宽时生成多帧跑马灯，中文与 Emoji 都能显示。
     */
    fun uploadGraphic(device: DeviceWithState, context: Context) {
        if (_layers.value.isEmpty()) {
            _status.value = CreateStatus("请先添加文字、时间或图片", isError = true)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val outcome = runCatching {
                val bytes = withContext(Dispatchers.Default) {
                    val frames = GraphicComposer.renderFrames(
                        context = context,
                        cols = _gridCols.value,
                        rows = _gridRows.value,
                        layers = _layers.value,
                        options = graphicOptions(),
                    )
                    require(frames.any { frame -> frame.colors.any { it != 0 } }) { "内容还没有点亮任何灯珠" }
                    buildGif(frames, _gridCols.value, _gridRows.value)
                }
                // 内容哈希保证不同图文落到不同文件名：固件按文件名判断是否重新加载，
                // 名字不变的话再次投送会继续显示上一张图。
                val fileName = customGraphicFileName(bytes)
                val api = deviceApiFactory.create(device.device)
                api.uploadFile(bytes, fileName) to fileName
            }
            val result = outcome.getOrNull()
            val uploaded = result != null && result.first.code in 200..299
            val applied = uploaded && deviceConnectionManager.sendSegmentPatch(device.device.macAddress) {
                // 固件 Image 的帧间隔 = GIF 延迟 × (2 - sx/128)，
                // sx 不写回就会沿用上一个效果的强度，导致滚动速度调节失灵。
                it.copy(
                    effect = IMAGE_EFFECT,
                    name = result?.second,
                    effectSpeed = normalizedToChannel(graphicSpeedFraction(_speed.value)),
                )
            }
            _busy.value = false
            _status.value = when {
                !uploaded -> CreateStatus(
                    outcome.exceptionOrNull()?.message ?: "上传失败：设备可能关闭了文件上传",
                    isError = true,
                )
                !applied -> CreateStatus("图片已上传，但切换效果失败", isError = true)
                else -> CreateStatus("自定义图文已投送", isError = false)
            }
        }
    }

    /**
     * 把当前创作存成收藏。
     *
     * 色轮调色存进灯效收藏表；自定义图文存进本地图文仓库（带 GIF 与预览图），
     * 之后可以在设备页的「动态字幕」快捷卡片里调用。
     */
    fun saveFavorite(device: DeviceWithState, context: Context) {
        when (_mode.value) {
            CreateMode.TEMPLATE -> return
            CreateMode.PIXEL -> saveGraphicFavorite(context)
            CreateMode.COLOR -> saveColorFavorite(device)
        }
    }

    private fun saveColorFavorite(device: DeviceWithState) {
        val effectId = currentSegment(device)?.effect ?: 0
        val name = "自定义配色"
        viewModelScope.launch {
            val existing = favoriteEffectDao.findByEffectId(effectId)
            if (existing != null) favoriteEffectDao.delete(existing)
            favoriteEffectDao.insert(
                FavoriteEffect(
                    effectId = effectId,
                    effectName = name,
                    speed = normalizedToChannel(_speed.value),
                    intensity = null,
                    palette = currentSegment(device)?.palette,
                    color1 = applyPacked(_primary.value),
                    color2 = applyPacked(_secondary.value),
                    color3 = 0,
                ),
            )
            _status.value = CreateStatus(
                message = "已收藏「${EffectCatalog.find(effectId)?.nameZh ?: name}」",
                isError = false,
            )
        }
    }

    private fun saveGraphicFavorite(context: Context) {
        if (_layers.value.isEmpty()) {
            _status.value = CreateStatus("请先添加文字、时间或图片", isError = true)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val outcome = runCatching {
                withContext(Dispatchers.Default) {
                    val cols = _gridCols.value
                    val rows = _gridRows.value
                    val frames = GraphicComposer.renderFrames(
                        context = context,
                        cols = cols,
                        rows = rows,
                        layers = _layers.value,
                        options = graphicOptions(),
                    )
                    require(frames.any { frame -> frame.colors.any { it != 0 } }) { "内容还没有点亮任何灯珠" }
                    val preview = GraphicComposer.renderPreview(
                        context = context,
                        cols = cols,
                        rows = rows,
                        layers = _layers.value,
                        options = graphicOptions(),
                    )
                    customGraphicStore.add(
                        gif = buildGif(frames, cols, rows),
                        preview = previewBitmap(preview, cols, rows),
                        speedPercent = (_speed.value * 100f).roundToInt(),
                        name = graphicFavoriteName(),
                    )
                }
            }
            _busy.value = false
            _status.value = outcome.fold(
                onSuccess = {
                    CreateStatus("已收藏自定义图文，可在设备页「动态字幕」中调用", isError = false)
                },
                onFailure = {
                    CreateStatus(it.message ?: "收藏失败", isError = true)
                },
            )
        }
    }

    /** 收藏名优先取第一层文字，便于在快捷卡片上分辨。 */
    private fun graphicFavoriteName(): String {
        val text = _layers.value
            .firstOrNull { it.type == GraphicLayerType.TEXT }
            ?.text
            ?.trim()
            .orEmpty()
        return if (text.isNotEmpty()) text.take(FAVORITE_NAME_MAX_LENGTH) else "自定义图文"
    }

    /** 把预览网格放大成 PNG，卡片解码后直接铺在缩略图上。 */
    private fun previewBitmap(colors: List<Int>, cols: Int, rows: Int): Bitmap {
        val width = (cols * PREVIEW_CELL_SCALE).coerceAtLeast(1)
        val height = (rows * PREVIEW_CELL_SCALE).coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val row = (y / PREVIEW_CELL_SCALE) * cols
            for (x in 0 until width) {
                val packed = colors.getOrElse(row + x / PREVIEW_CELL_SCALE) { 0 }
                pixels[y * width + x] = packed or 0xFF000000.toInt()
            }
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun currentSegment(device: DeviceWithState): Segment? =
        device.stateInfo?.state?.segment?.firstOrNull()

    private fun applySaturation(packed: Int): List<Int> {
        val hsv = FloatArray(3)
        AndroidColor.RGBToHSV(
            (packed shr 16) and 0xFF,
            (packed shr 8) and 0xFF,
            packed and 0xFF,
            hsv,
        )
        val rgb = AndroidColor.HSVToColor(
            floatArrayOf(hsv[0], hsv[1] * _saturation.value, 1f.coerceAtMost(hsv[2] + 0.1f)),
        )
        return listOf((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
    }

    private fun applyPacked(packed: Int): Int {
        val rgb = applySaturation(packed)
        return packRgb(rgb[0], rgb[1], rgb[2]) ?: packed
    }

    private fun buildGif(frames: List<GraphicFrame>, cols: Int, rows: Int): ByteArray {
        // GIF 只有一张全局调色板：把所有帧的像素统一压到 RGB332（≤256 色）后再建索引。
        val palette = LinkedHashMap<Int, Int>()
        palette[0] = 0
        fun quantize(rgb: Int): Int {
            val r = (rgb shr 16) and 0xE0
            val g = (rgb shr 8) and 0xE0
            val b = rgb and 0xC0
            return (r shl 16) or (g shl 8) or b
        }
        val encoded = frames.map { frame ->
            val indices = ByteArray(frame.colors.size) { index ->
                val q = quantize(frame.colors[index] and 0xFFFFFF)
                val slot = palette.getOrPut(q) { palette.size }
                slot.toByte()
            }
            GifEncoder.Frame(indices, frame.delayCentis)
        }
        return GifEncoder.encodeFrames(encoded, cols, rows, palette.keys.toIntArray())
    }
}
