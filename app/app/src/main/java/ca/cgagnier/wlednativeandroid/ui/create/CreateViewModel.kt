package ca.cgagnier.wlednativeandroid.ui.create

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
import ca.cgagnier.wlednativeandroid.ui.effects.unpackRgb
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

/** 创作工坊的三个分段：色轮调色 / 自定义图文 / 多色模板。 */
enum class CreateMode { COLOR, PIXEL, TEMPLATE }

/** 像素画板的工具。 */
enum class PixelTool { BRUSH, ERASER, PICKER }

/** 一条操作结果，交给界面弹提示。 */
data class CreateStatus(val message: String, val isError: Boolean)

/**
 * 按固件英文名反查灯效 id，取不到直接崩。
 *
 * 创作工坊依赖两个灯效：Image（显示上传的 GIF）和 Scrolling Text（渲染滚动字幕）。
 * 这两个 id 曾经被写死成数字，而固件调整效果槽位后，写死的数字会静默指向另一个效果 ——
 * 38 一度是 Image，槽位对齐后变成 Aurora（极光）；123 一度是 Scrolling Text，现在是
 * Drift Rose（玫瑰漂移）。表现是「上传的图案显示成极光」「投送的字幕变成玫瑰漂移」，
 * 而且不报错、很难联想到是 id 漂移。改成按名反查，槽位变更自动跟随。
 */
private fun requireEffectId(name: String): Int =
    EffectCatalog.byName(name)?.id
        ?: error("固件灯效目录缺少「$name」，请重新运行 tools/gen_effect_catalog.py")

private val IMAGE_EFFECT = requireEffectId("Image")
private val SCROLLING_TEXT_EFFECT = requireEffectId("Scrolling Text")

/** `seg.c2` = 255 时固件映射到 font 4（5×12），并启用加粗笔画。 */
private const val SCROLLING_TEXT_BIG_FONT = 255

/**
 * `seg.c3` 的字幕朝向。固件 `map(custom3, 0, 31, -2, 2)`：0 → -2（上下颠倒）、
 * 16 → 0（正常）、24 → 1（逆时针 90°）。之前写 0，实体灯上的字幕就是倒的。
 */
private const val SCROLLING_TEXT_ROTATE_NORMAL = 16
private const val COLOR_SLOT_COUNT = 3
private const val DEFAULT_GRID_COLS = 16
private const val DEFAULT_GRID_ROWS = 10
private const val MAX_GRID_SIDE = 40
private const val UPLOAD_FILE_NAME = "isle.gif"

/** 上传到设备后，`Image` 效果按段落名找文件，所以名字两边必须一致。 */
private const val UPLOAD_BASENAME = "isle"

/** 像素画板的默认色板（与设计稿一致：黑白 + 青紫粉绿）。 */
val PIXEL_PALETTE: List<Int> = listOf(
    0xFFFFFF, 0x000000, 0x5FE3E4, 0x8C7BFF,
    0xFF7AD0, 0x3EE08F, 0xFFC46B, 0xFF5A5A,
)

/** 多色模板「添加颜色」时的候选色，尽量不与已有色重复。 */
private val TEMPLATE_ADD_COLORS = listOf(0xFFB46B, 0x3EE08F, 0xFF7AD0, 0xFFFFFF, 0x5FE3E4)

/**
 * 创作工坊。
 *
 * 色轮调色：两个锚点取色，饱和度统一压缩后再下发，避免选到过曝的纯色。
 * 自定义图文：文字直接写进 `seg.n` 交给固件的滚动字幕效果；
 * 像素画板则编码成 GIF 上传到设备，用 `Image` 效果播放。
 */
class CreateViewModel(
    private val deviceConnectionManager: DeviceConnectionManager,
    private val favoriteEffectDao: FavoriteEffectDao,
    private val selectedDeviceStore: SelectedDeviceStore,
    private val deviceApiFactory: DeviceApiFactory,
    private val paletteTemplateStore: PaletteTemplateStore,
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

    private val _text = MutableStateFlow("LumiIsle")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _tool = MutableStateFlow(PixelTool.BRUSH)
    val tool: StateFlow<PixelTool> = _tool.asStateFlow()

    private val _brushColor = MutableStateFlow(PIXEL_PALETTE[0])
    val brushColor: StateFlow<Int> = _brushColor.asStateFlow()

    private val _gridCols = MutableStateFlow(DEFAULT_GRID_COLS)
    val gridCols: StateFlow<Int> = _gridCols.asStateFlow()

    private val _gridRows = MutableStateFlow(DEFAULT_GRID_ROWS)
    val gridRows: StateFlow<Int> = _gridRows.asStateFlow()

    /** 0 表示该格不发光。 */
    private val _grid = MutableStateFlow(List(DEFAULT_GRID_COLS * DEFAULT_GRID_ROWS) { 0 })
    val grid: StateFlow<List<Int>> = _grid.asStateFlow()

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

    fun setText(value: String) {
        _text.value = value.take(32)
    }

    fun setTool(tool: PixelTool) {
        _tool.value = tool
    }

    fun setBrushColor(packedRgb: Int) {
        _brushColor.value = packedRgb
        if (_tool.value == PixelTool.ERASER) _tool.value = PixelTool.BRUSH
    }

    /**
     * 画板尺寸跟随设备矩阵：圆形灯管是竖长条，画布也应该是竖的，
     * 否则上传后画面会被压扁。
     */
    fun configureGrid(device: DeviceWithState?) {
        val leds = device?.stateInfo?.info?.leds ?: return
        val cols = leds.width.coerceIn(4, MAX_GRID_SIDE)
        val rows = leds.height.coerceIn(4, MAX_GRID_SIDE)
        if (cols == _gridCols.value && rows == _gridRows.value) return
        _gridCols.value = cols
        _gridRows.value = rows
        _grid.value = List(cols * rows) { 0 }
    }

    fun paint(index: Int) {
        val current = _grid.value
        if (index !in current.indices) return
        val target = when (_tool.value) {
            PixelTool.BRUSH -> _brushColor.value and 0xFFFFFF
            PixelTool.ERASER -> 0
            PixelTool.PICKER -> current[index]
        }
        if (_tool.value == PixelTool.PICKER) {
            if (target != 0) {
                _brushColor.value = target
                _tool.value = PixelTool.BRUSH
            }
            return
        }
        if (current[index] == target) return
        _grid.value = current.toMutableList().apply { this[index] = target }
    }

    fun clearGrid() {
        _grid.value = List(_grid.value.size) { 0 }
    }

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

    /** 滚动字幕：文字写进段落名，固件用它渲染。 */
    fun sendText(device: DeviceWithState) {
        val value = _text.value.trim()
        if (value.isEmpty()) {
            _status.value = CreateStatus("请先输入字幕内容", isError = true)
            return
        }
        viewModelScope.launch {
            val ok = deviceConnectionManager.sendSegmentPatch(device.device.macAddress) { segment ->
                segment.copy(
                    effect = SCROLLING_TEXT_EFFECT,
                    name = value,
                    // 纯黑底 + 单色高亮字：透过熏黑亚克力罩仍能读清
                    colors = listOf(listOf(255, 255, 255), listOf(0, 0, 0), listOf(0, 0, 0)),
                    effectSpeed = normalizedToChannel(_speed.value),
                    // c2 = 255 -> 固件选中 font 4（5×12 大字），并自动启用 2 像素加粗笔画
                    custom2 = SCROLLING_TEXT_BIG_FONT,
                    custom3 = SCROLLING_TEXT_ROTATE_NORMAL,
                )
            }
            _status.value = CreateStatus(
                message = if (ok) "字幕已投送" else "投送失败，请检查连接",
                isError = !ok,
            )
        }
    }

    /**
     * 像素画板 -> GIF -> 上传 -> 切到 Image 效果。
     *
     * 固件只认设备文件系统里的图片文件，所以必须先上传；上传成功后再把段落名设成文件名。
     */
    fun uploadPixelArt(device: DeviceWithState) {
        val cols = _gridCols.value
        val rows = _gridRows.value
        val grid = _grid.value
        if (grid.all { it == 0 }) {
            _status.value = CreateStatus("画板还是空的", isError = true)
            return
        }
        _busy.value = true
        viewModelScope.launch {
            val result = runCatching {
                val bytes = withContext(Dispatchers.Default) {
                    buildGif(grid, cols, rows)
                }
                val api = deviceApiFactory.create(device.device)
                api.uploadFile(bytes, UPLOAD_FILE_NAME)
            }.getOrNull()
            val uploaded = result != null && result.code in 200..299
            val applied = uploaded && deviceConnectionManager.sendSegmentPatch(device.device.macAddress) {
                it.copy(effect = IMAGE_EFFECT, name = UPLOAD_BASENAME)
            }
            _busy.value = false
            _status.value = when {
                !uploaded -> CreateStatus("上传失败：设备可能关闭了文件上传", isError = true)
                !applied -> CreateStatus("图片已上传，但切换效果失败", isError = true)
                else -> CreateStatus("像素图已上传并显示", isError = false)
            }
        }
    }

    /** 把当前创作存成收藏，之后在首页快捷卡片里可以直接调出。 */
    fun saveFavorite(device: DeviceWithState) {
        if (_mode.value == CreateMode.TEMPLATE) return
        val effectId = when (_mode.value) {
            CreateMode.COLOR -> currentSegment(device)?.effect ?: 0
            CreateMode.PIXEL -> IMAGE_EFFECT
            CreateMode.TEMPLATE -> return
        }
        val name = when (_mode.value) {
            CreateMode.COLOR -> "自定义配色"
            CreateMode.PIXEL -> "像素画 ${_gridCols.value}×${_gridRows.value}"
            CreateMode.TEMPLATE -> return
        }
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

    private fun buildGif(grid: List<Int>, cols: Int, rows: Int): ByteArray {
        val palette = buildList {
            add(0x000000)
            grid.forEach { if (it != 0 && it !in this) add(it) }
        }
        val indices = ByteArray(grid.size) { index ->
            palette.indexOf(grid[index]).coerceAtLeast(0).toByte()
        }
        return GifEncoder.encode(indices, cols, rows, palette.toIntArray())
    }
}
