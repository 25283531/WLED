package ca.cgagnier.wlednativeandroid.ui.effects

import android.content.Context
import ca.cgagnier.wlednativeandroid.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.UUID

/** 一个自定义多色调色板模板（保存在手机本地）。 */
@Serializable
data class PaletteTemplate(
    val id: String,
    val name: String,
    /** 有序色标，`0xRRGGBB`。 */
    val colors: List<Int>,
)

@Serializable
private data class PaletteTemplateDocument(
    val templates: List<PaletteTemplate> = emptyList(),
    /** mac -> (templateId -> 设备自定义调色板槽位)。 */
    val slots: Map<String, Map<String, Int>> = emptyMap(),
)

/**
 * 多色模板仓库。
 *
 * 模板本身只存在手机里（`filesDir/palette_templates.json`），不依赖设备存储；
 * 只有在「设备页 → 灯效参数 → 调色板」里点用某个模板时，才把它的色标上传成设备的
 * 自定义调色板，并记住每台设备分配到的槽位，避免重复占用设备槽位。
 */
class PaletteTemplateStore(context: Context) {
    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, FILE_NAME)
    private val json = Json { ignoreUnknownKeys = true }
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _templates = MutableStateFlow<List<PaletteTemplate>>(emptyList())
    val templates: StateFlow<List<PaletteTemplate>> = _templates.asStateFlow()

    private val _slots = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())

    /** 每台设备已分配的模板槽位，界面用它判断哪个模板当前被选中。 */
    val slots: StateFlow<Map<String, Map<String, Int>>> = _slots.asStateFlow()

    private var document = PaletteTemplateDocument()

    init {
        document = runCatching {
            json.decodeFromString<PaletteTemplateDocument>(file.readText())
        }.getOrDefault(PaletteTemplateDocument())
        _templates.value = document.templates
        _slots.value = document.slots
    }

    /** 保存一个新模板，返回带自动命名的结果；颜色数量必须在 [MIN_COLORS]..[MAX_COLORS]。 */
    fun add(colors: List<Int>): PaletteTemplate? {
        val cleaned = colors.map { it and 0xFFFFFF }.take(MAX_COLORS)
        if (cleaned.size < MIN_COLORS) return null
        synchronized(this) {
            val template = PaletteTemplate(
                id = UUID.randomUUID().toString(),
                name = nextName(),
                colors = cleaned,
            )
            document = document.copy(templates = document.templates + template)
            publish()
            return template
        }
    }

    fun remove(id: String) {
        synchronized(this) {
            val slots = document.slots.mapValues { (_, byTemplate) -> byTemplate - id }
            document = document.copy(
                templates = document.templates.filterNot { it.id == id },
                slots = slots,
            )
            publish()
        }
    }

    fun slotFor(macAddress: String, templateId: String): Int? =
        document.slots[macAddress]?.get(templateId)

    /**
     * 取该模板在这台设备上的槽位；没有就追加一个新槽位并记住。
     *
     * [deviceCustomPaletteCount] 是设备当前自定义调色板数量（`info.cpalcount`），
     * 取它与本地已用槽位的较大值，避免并发或设备侧新增调色板时覆盖别人的数据。
     */
    fun allocateSlot(macAddress: String, templateId: String, deviceCustomPaletteCount: Int?): Int {
        synchronized(this) {
            slotFor(macAddress, templateId)?.let { return it }
            val usedMax = document.slots[macAddress]?.values?.maxOrNull() ?: -1
            val slot = maxOf(deviceCustomPaletteCount ?: 0, usedMax + 1)
                .coerceIn(0, MAX_SLOT)
            val deviceSlots = (document.slots[macAddress] ?: emptyMap()) + (templateId to slot)
            document = document.copy(slots = document.slots + (macAddress to deviceSlots))
            publish()
            return slot
        }
    }

    private fun publish() {
        _templates.value = document.templates
        _slots.value = document.slots
        val snapshot = document
        writeScope.launch {
            runCatching { file.writeText(json.encodeToString(snapshot)) }
        }
    }

    private fun nextName(): String {
        val used = document.templates.map { it.name }.toSet()
        var index = document.templates.size + 1
        while (appContext.getString(R.string.isle_palette_template_name, index) in used) index++
        return appContext.getString(R.string.isle_palette_template_name, index)
    }

    companion object {
        private const val FILE_NAME = "palette_templates.json"
        const val MIN_COLORS = 2
        const val MAX_COLORS = 6
        private const val MAX_SLOT = 128
    }
}

/** 固件自定义调色板 id 基数：槽位 0 → 200，槽位 1 → 199 …… */
const val CUSTOM_PALETTE_ID_BASE = 200

/**
 * 生成固件 `/paletteN.json` 的内容。
 *
 * 色标按顺序等距分布在 0..255 上，固件会在相邻色标之间做线性渐变。
 */
fun buildPaletteFileJson(colors: List<Int>): String {
    val safe = colors.map { it and 0xFFFFFF }.take(PaletteTemplateStore.MAX_COLORS)
    val last = (safe.size - 1).coerceAtLeast(1)
    val entries = safe.mapIndexed { index, color ->
        val position = if (safe.size <= 1) 0 else index * 255 / last
        String.format(Locale.US, "%d,\"%06X\"", position, color)
    }
    return "{\"palette\":[${entries.joinToString(",")}]}"
}
