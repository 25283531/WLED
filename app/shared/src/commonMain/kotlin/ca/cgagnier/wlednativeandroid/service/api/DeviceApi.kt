package ca.cgagnier.wlednativeandroid.service.api

import ca.cgagnier.wlednativeandroid.model.Device
import ca.cgagnier.wlednativeandroid.model.wledapi.Info
import ca.cgagnier.wlednativeandroid.model.wledapi.JsonPost
import ca.cgagnier.wlednativeandroid.model.wledapi.NetworkScan
import ca.cgagnier.wlednativeandroid.model.wledapi.State
import ca.cgagnier.wlednativeandroid.model.wledapi.WledConfig
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.FileSystem
import okio.Path
import kotlin.random.Random
import ca.cgagnier.wlednativeandroid.shared.fileSystem as defaultFileSystem

/**
 * HTTP API of a single WLED device.
 *
 * Non-2xx responses are reported through [ApiResponse]. Transport failures (timeouts, unreachable
 * host, malformed JSON, ...) are thrown. Every member is annotated with `@Throws(Exception::class)`
 * so that these failures surface as Swift errors on iOS instead of terminating the process, which
 * is what Kotlin/Native does for undeclared exceptions crossing into Objective-C/Swift.
 */
interface DeviceApi {
    @Throws(Exception::class)
    suspend fun getInfo(): ApiResponse<Info>

    @Throws(Exception::class)
    suspend fun getState(): ApiResponse<State>

    /**
     * Effect names as advertised by the firmware (`GET /json/effects`).
     *
     * The array index is the effect id used in `seg.fx`, so the returned list doubles as the
     * id -> name table for the device. Entries are never null; reserved slots surface as `RSVD`.
     */
    @Throws(Exception::class)
    suspend fun getEffects(): ApiResponse<List<String>>

    @Throws(Exception::class)
    suspend fun postJson(state: JsonPost): ApiResponse<State>

    /**
     * 读取设备配置（`GET /json/cfg`），返回 App 关心的片段。
     */
    @Throws(Exception::class)
    suspend fun getConfig(): ApiResponse<WledConfig>

    /**
     * 写入设备配置（`POST /json/cfg`）。
     *
     * 固件把请求体合并进 cfg.json；`WledConfig.rb = true` 时保存后立即重启。
     * 设备启用了设置 PIN 时固件返回 401，调用方需要按 [ApiResponse.code] 区分。
     */
    @Throws(Exception::class)
    suspend fun postConfig(config: WledConfig): ApiResponse<String>

    /**
     * 触发一次 WiFi 扫描并返回当前可见网络（`GET /json/net`）。
     *
     * 固件扫描是异步的：扫描中返回空列表，调用方需要轮询。
     */
    @Throws(Exception::class)
    suspend fun scanNetworks(): ApiResponse<NetworkScan>

    @Throws(Exception::class)
    suspend fun updateDevice(fileData: ByteArray, fileName: String): ApiResponse<String>

    /**
     * Upload a file to the device filesystem (`POST /upload`), used for custom images and
     * playlists. The firmware expects the multipart field name `file`.
     */
    @Throws(Exception::class)
    suspend fun uploadFile(fileData: ByteArray, fileName: String): ApiResponse<String>
}

@Throws(Exception::class)
suspend fun DeviceApi.updateDevice(
    binaryPath: Path,
    fs: FileSystem = defaultFileSystem,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ApiResponse<String> {
    val bytes = withContext(ioDispatcher) {
        fs.read(binaryPath) { readByteArray() }
    }
    return updateDevice(bytes, binaryPath.name)
}

class KtorDeviceApi(private val baseUrl: String, private val httpClient: HttpClient) : DeviceApi {

    private fun normalizeUrl(path: String): String {
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val relative = if (path.startsWith("/")) path.drop(1) else path
        return "$base$relative"
    }

    @Throws(Exception::class)
    override suspend fun getInfo(): ApiResponse<Info> {
        val response = httpClient.get(normalizeUrl("json/info"))
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<Info>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    @Throws(Exception::class)
    override suspend fun getState(): ApiResponse<State> {
        val response = httpClient.get(normalizeUrl("json/state"))
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<State>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    @Throws(Exception::class)
    override suspend fun getEffects(): ApiResponse<List<String>> {
        val response = httpClient.get(normalizeUrl("json/effects"))
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<List<String>>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    @Throws(Exception::class)
    override suspend fun postJson(state: JsonPost): ApiResponse<State> {
        val response = httpClient.post(normalizeUrl("json/state")) {
            contentType(ContentType.Application.Json)
            setBody(state)
        }
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<State>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    @Throws(Exception::class)
    override suspend fun getConfig(): ApiResponse<WledConfig> {
        val response = httpClient.get(normalizeUrl("json/cfg"))
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<WledConfig>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    @Throws(Exception::class)
    override suspend fun postConfig(config: WledConfig): ApiResponse<String> {
        val response = httpClient.post(normalizeUrl("json/cfg")) {
            contentType(ContentType.Application.Json)
            setBody(config)
        }
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = text)
        } else {
            ApiResponse(code = response.status.value, errorBody = text)
        }
    }

    @Throws(Exception::class)
    override suspend fun scanNetworks(): ApiResponse<NetworkScan> {
        val response = httpClient.get(normalizeUrl("json/net"))
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = response.body<NetworkScan>())
        } else {
            ApiResponse(code = response.status.value, errorBody = response.bodyAsText())
        }
    }

    @Throws(Exception::class)
    override suspend fun updateDevice(fileData: ByteArray, fileName: String): ApiResponse<String> =
        postMultipart("update", fieldName = FILE_FIELD_NAME, fileName = fileName, fileData = fileData)

    @Throws(Exception::class)
    override suspend fun uploadFile(fileData: ByteArray, fileName: String): ApiResponse<String> =
        postMultipart("upload", fieldName = FILE_FIELD_NAME, fileName = fileName, fileData = fileData)

    @Throws(Exception::class)
    private suspend fun postMultipart(
        path: String,
        fieldName: String,
        fileName: String,
        fileData: ByteArray,
    ): ApiResponse<String> {
        val boundary = generateMultipartBoundary()
        val bodyBytes = buildMultipartBody(boundary, fieldName, fileName, fileData)
        // Use ByteArrayContent instead of streaming WriteChannelContent so Darwin sends an NSData
        // body with an explicit Content-Length rather than chunked Transfer-Encoding (unsupported by ESP32).
        val response = httpClient.post(normalizeUrl(path)) {
            setBody(
                ByteArrayContent(
                    bytes = bodyBytes,
                    contentType = ContentType.MultiPart.FormData.withParameter("boundary", boundary),
                ),
            )
        }
        val text = response.bodyAsText()
        return if (response.status.isSuccess()) {
            ApiResponse(code = response.status.value, body = text)
        } else {
            ApiResponse(code = response.status.value, errorBody = text)
        }
    }

    private fun generateMultipartBoundary(): String = buildString {
        append("----WledFormBoundary")
        append(Random.nextLong().toULong().toString(HEX_RADIX))
        append(Random.nextLong().toULong().toString(HEX_RADIX))
    }

    private fun buildMultipartBody(
        boundary: String,
        fieldName: String,
        fileName: String,
        fileData: ByteArray,
    ): ByteArray {
        // ESPAsyncWebServer reads filename="..." up to the next quote without unescaping
        // backslashes, so strip CR/LF and quotes rather than backslash-escaping.
        val safeFileName = fileName.filterNot { it == '\r' || it == '\n' || it == '"' }
        return Buffer().apply {
            writeUtf8("--$boundary\r\n")
            writeUtf8("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$safeFileName\"\r\n")
            writeUtf8("Content-Type: application/octet-stream\r\n")
            writeUtf8("\r\n")
            write(fileData)
            writeUtf8("\r\n--$boundary--\r\n")
        }.readByteArray()
    }

    private companion object {
        const val HEX_RADIX = 16

        /** Multipart field name expected by both `/update` and `/upload` on the firmware. */
        const val FILE_FIELD_NAME = "file"
    }
}

val defaultJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/**
 * Factory for creating instances of DeviceApi.
 *
 * Since the base URL is dynamic per device, we can't provide a singleton instance.
 * Instead, we provide this factory to create a new DeviceApi on-demand.
 *
 * @param defaultHttpClient The HttpClient instance to use for underlying HTTP transport.
 */
class DeviceApiFactory(private val defaultHttpClient: HttpClient) {

    /**
     * Create a new DeviceApi instance from a device address.
     *
     * @param address The address of a device to create the API for.
     */
    fun create(address: String): DeviceApi {
        val baseUrl = normalizeAddress(address)
        return KtorDeviceApi(baseUrl, defaultHttpClient)
    }

    /**
     * Create a new DeviceApi instance for a device.
     *
     * @param device The device to create the API for.
     */
    fun create(device: Device): DeviceApi = create(device.getDeviceUrl())

    /**
     * Create a new DeviceApi instance with a custom timeout.
     *
     * HttpClient.config shares the parent client's engine, connection pool, and threads
     * while applying custom timeout configuration.
     *
     * @param address The address of a device to create the API for.
     * @param timeout The custom timeout in seconds.
     */
    fun create(address: String, timeout: Long): DeviceApi {
        val timeoutMillis = timeout * MILLIS_PER_SECOND
        val customHttpClient = defaultHttpClient.config {
            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }
        }
        return KtorDeviceApi(normalizeAddress(address), customHttpClient)
    }

    /**
     * Create a new DeviceApi instance with a custom timeout.
     *
     * @param device The device to create the API for.
     * @param timeout The custom timeout in seconds.
     */
    fun create(device: Device, timeout: Long): DeviceApi = create(device.getDeviceUrl(), timeout)

    private fun normalizeAddress(address: String): String =
        if (!address.startsWith("http://") && !address.startsWith("https://")) {
            "http://$address/"
        } else {
            address
        }

    companion object {
        const val MILLIS_PER_SECOND = 1000L
        const val PING_INTERVAL_SECONDS = 30

        /** Default connect/read timeout for device requests, shared by the Android and iOS clients. */
        const val DEFAULT_TIMEOUT_SECONDS = 30L
    }
}
