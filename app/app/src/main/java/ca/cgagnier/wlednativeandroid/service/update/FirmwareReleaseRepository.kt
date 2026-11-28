package ca.cgagnier.wlednativeandroid.service.update

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest

/** 统一固件分发通道的根地址，固件与 manifest 都放在这里的 /firmware 目录下。 */
const val FIRMWARE_CHANNEL_BASE_URL = "https://alist.250886.xyz:32123"

private const val MANIFEST_PATH = "/firmware/manifest.json"
private const val ALIST_FILE_API = "/api/fs/get"
private const val DOWNLOAD_BUFFER_SIZE = 64 * 1024

/**
 * manifest.json 里的 firmware 节点。
 *
 * versionCode 与固件 `wled.h` 的 VERSION 一致，设备 /json/info 的 vid 会原样上报，
 * App 侧用整数比较判断是否有新版本，避免语义化版本解析带来的歧义。
 */
@Serializable
data class FirmwareRelease(
    val version: String = "",
    val versionCode: Int = 0,
    val model: String? = null,
    val file: String = "",
    val size: Long = 0L,
    val sha256: String? = null,
    val notes: String? = null,
    val publishedAt: String? = null,
)

@Serializable
data class FirmwareManifest(
    val schema: Int = 1,
    val channel: String? = null,
    val updatedAt: String? = null,
    val firmware: FirmwareRelease? = null,
)

open class FirmwareChannelException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 下载内容与 manifest 声明的 sha256 不一致。 */
class FirmwareChecksumException(message: String) : FirmwareChannelException(message)

@Serializable
private data class AlistFileEntry(
    val name: String? = null,
    val size: Long = 0L,
    @SerialName("raw_url") val rawUrl: String? = null,
)

@Serializable
private data class AlistResponse(
    val code: Int = 0,
    val message: String? = null,
    val data: AlistFileEntry? = null,
)

/**
 * AList 固件通道。
 *
 * AList 的 `/d/` 直链需要登录，但 `/api/fs/get` 可以匿名拿到带签名的 `raw_url`，
 * 因此这里总是先用 API 换取 raw_url 再下载，不在代码里硬编码 storage 名。
 */
class FirmwareReleaseRepository(
    private val httpClient: OkHttpClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    suspend fun fetchLatestFirmware(): FirmwareRelease = withContext(ioDispatcher) {
        val rawUrl = resolveRawUrl(MANIFEST_PATH)
        val text = runCatching { httpGetText(rawUrl) }
            .getOrElse { throw FirmwareChannelException("manifest download failed: ${it.message}", it) }
        val manifest = runCatching { json.decodeFromString<FirmwareManifest>(text) }
            .getOrElse { throw FirmwareChannelException("manifest parse failed: ${it.message}", it) }
        manifest.firmware?.takeIf { it.versionCode > 0 && it.file.isNotBlank() }
            ?: throw FirmwareChannelException("manifest has no usable firmware entry")
    }

    /**
     * 下载固件到 [destDir]，边下边算 sha256，校验通过后才返回文件。
     */
    suspend fun downloadFirmware(
        release: FirmwareRelease,
        destDir: File,
        onProgress: (Int) -> Unit = {},
    ): File = withContext(ioDispatcher) {
        val remotePath = "/firmware/${release.file.trimStart('/')}"
        val rawUrl = resolveRawUrl(remotePath)
        val dest = File(destDir, "fw-ota-${release.versionCode}.bin")
        val digest = MessageDigest.getInstance("SHA-256")
        var written = 0L

        try {
            val request = Request.Builder().url(rawUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw FirmwareChannelException("firmware download failed: HTTP ${response.code}")
                }
                val stream = response.body.byteStream()
                dest.outputStream().use { output ->
                    val buffer = ByteArray(DOWNLOAD_BUFFER_SIZE)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read
                        val total = release.size.takeIf { it > 0 } ?: response.body.contentLength()
                        if (total > 0) {
                            onProgress(((written * 100) / total).toInt().coerceIn(0, 100))
                        }
                    }
                }
            }

            if (release.size > 0 && written != release.size) {
                throw FirmwareChannelException("firmware size mismatch: $written != ${release.size}")
            }
            val expected = release.sha256?.lowercase()?.trim()
            if (!expected.isNullOrEmpty()) {
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
                if (actual != expected) {
                    throw FirmwareChecksumException("firmware checksum mismatch")
                }
            }
            onProgress(100)
            dest
        } catch (e: CancellationException) {
            dest.delete()
            throw e
        } catch (e: Throwable) {
            dest.delete()
            if (e is FirmwareChannelException) throw e
            throw FirmwareChannelException("firmware download failed: ${e.message}", e)
        }
    }

    /** 通过 AList API 换取带签名的直链；签名有时效，所以每次请求都重新获取。 */
    private fun resolveRawUrl(path: String): String {
        val payload = buildJsonObject { put("path", path) }.toString()
        val request = Request.Builder()
            .url(FIRMWARE_CHANNEL_BASE_URL + ALIST_FILE_API)
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .header("Accept", "application/json")
            .build()
        val text = httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw FirmwareChannelException("AList API HTTP ${response.code}")
            }
            response.body.string()
        }
        val parsed = runCatching { json.decodeFromString<AlistResponse>(text) }
            .getOrElse { throw FirmwareChannelException("AList response parse failed", it) }
        if (parsed.code != 200) {
            throw FirmwareChannelException("AList ${parsed.code}: ${parsed.message.orEmpty()}")
        }
        return parsed.data?.rawUrl?.takeIf { it.isNotBlank() }
            ?: throw FirmwareChannelException("AList did not return a raw_url for $path")
    }

    private fun httpGetText(url: String): String {
        val request = Request.Builder().url(url).get().build()
        return httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw FirmwareChannelException("HTTP ${response.code} for $url")
            }
            response.body.string()
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
