package ca.cgagnier.wlednativeandroid.ui.create

import android.content.Context
import android.graphics.Bitmap
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
import java.io.FileOutputStream
import java.util.UUID

/** 上传到设备时的文件名前缀，创作页投送与设备页调用共用。 */
private const val GRAPHIC_FILE_PREFIX = "isle_"

/**
 * 按 GIF 内容生成设备端文件名。
 *
 * 固件 `Image` 效果要求段落名是以 `.gif` 结尾的文件名，并且只在名字变化时才重新加载 ——
 * 用内容哈希拼出唯一名字，既满足后缀要求，也让重复投送/重新调用能真正刷新画面。
 */
fun customGraphicFileName(bytes: ByteArray): String =
    "$GRAPHIC_FILE_PREFIX${bytes.size}_${bytes.contentHashCode().toUInt().toString(16)}.gif"

/** 一条保存在手机本地的自定义图文收藏。 */
@Serializable
data class CustomGraphicFavorite(
    val id: String,
    /** 快捷卡片上显示的名字。 */
    val name: String,
    /** `custom_graphics/` 下的 GIF 文件名。 */
    val gifFile: String,
    /** 预览 PNG 的绝对路径，卡片直接解码显示。 */
    val previewPath: String,
    /** 上传到设备后使用的文件名，固定由 GIF 内容推导。 */
    val deviceFileName: String,
    /** 保存时的滚动速度百分比，调用时写回 `seg.sx`。 */
    val speedPercent: Int,
    val createdAt: Long,
)

@Serializable
private data class CustomGraphicDocument(
    val graphics: List<CustomGraphicFavorite> = emptyList(),
)

/**
 * 自定义图文收藏仓库。
 *
 * 只存在手机里（`filesDir/custom_graphics/`），不写 Room：
 * 灯效收藏表按 effectId 唯一，而所有自定义图文都指向同一个 `Image` 效果，
 * 并且每条收藏还需要附带 GIF 与预览图，放在本地文件里更合适。
 *
 * 设备页调用时才把 GIF 上传到设备，因此换一台灯也能调出同一份收藏。
 */
class CustomGraphicStore(context: Context) {
    private val root = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
    private val indexFile = File(root, INDEX_FILE)
    private val json = Json { ignoreUnknownKeys = true }
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favorites = MutableStateFlow<List<CustomGraphicFavorite>>(emptyList())
    val favorites: StateFlow<List<CustomGraphicFavorite>> = _favorites.asStateFlow()

    private var document = CustomGraphicDocument()

    init {
        document = runCatching {
            json.decodeFromString<CustomGraphicDocument>(indexFile.readText())
        }.getOrDefault(CustomGraphicDocument())
        // 文件被清理后索引里可能残留死条目，加载时顺手剔除
        _favorites.value = document.graphics.filter { File(root, it.gifFile).isFile }
    }

    /** 保存一份图文收藏，GIF 与预览图都落到本地。 */
    fun add(gif: ByteArray, preview: Bitmap, speedPercent: Int, name: String): CustomGraphicFavorite {
        val id = UUID.randomUUID().toString()
        File(root, "$id.gif").writeBytes(gif)
        val previewFile = File(root, "$id.png")
        FileOutputStream(previewFile).use { out ->
            preview.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val record = CustomGraphicFavorite(
            id = id,
            name = name,
            gifFile = "$id.gif",
            previewPath = previewFile.absolutePath,
            deviceFileName = customGraphicFileName(gif),
            speedPercent = speedPercent.coerceIn(0, 100),
            createdAt = System.currentTimeMillis(),
        )
        synchronized(this) {
            document = document.copy(graphics = listOf(record) + document.graphics)
            publish()
        }
        return record
    }

    fun remove(id: String) {
        synchronized(this) {
            val record = document.graphics.firstOrNull { it.id == id } ?: return
            File(root, record.gifFile).delete()
            File(record.previewPath).delete()
            document = document.copy(graphics = document.graphics.filterNot { it.id == id })
            publish()
        }
    }

    /** 读取收藏对应的 GIF 内容，文件缺失时返回 null。 */
    fun readGif(record: CustomGraphicFavorite): ByteArray? {
        val file = File(root, record.gifFile)
        return if (file.isFile) runCatching { file.readBytes() }.getOrNull() else null
    }

    private fun publish() {
        _favorites.value = document.graphics
        val snapshot = json.encodeToString(document)
        writeScope.launch {
            runCatching { indexFile.writeText(snapshot) }
        }
    }

    private companion object {
        const val DIR_NAME = "custom_graphics"
        const val INDEX_FILE = "index.json"
    }
}
