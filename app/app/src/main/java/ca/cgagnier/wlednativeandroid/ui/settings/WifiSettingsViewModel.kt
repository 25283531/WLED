package ca.cgagnier.wlednativeandroid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ca.cgagnier.wlednativeandroid.model.wledapi.NetworkConfig
import ca.cgagnier.wlednativeandroid.model.wledapi.ScannedNetwork
import ca.cgagnier.wlednativeandroid.model.wledapi.WifiEntry
import ca.cgagnier.wlednativeandroid.model.wledapi.WledConfig
import ca.cgagnier.wlednativeandroid.service.api.DeviceApi
import ca.cgagnier.wlednativeandroid.service.api.DeviceApiFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SCAN_ATTEMPTS = 8
private const val SCAN_INTERVAL_MS = 1_500L

/**
 * 设备 WiFi 原生页。
 *
 * 数据全部来自设备本身：`GET /json/cfg` 读当前 SSID，`GET /json/net` 取扫描结果，
 * `POST /json/cfg` 写回新网络并带 `rb = true`，由固件保存后自行重启。
 *
 * 固件扫描是异步的（扫描中返回空列表），所以要轮询；一旦拿到结果就停，避免反复触发扫描。
 */
class WifiSettingsViewModel(
    private val deviceApiFactory: DeviceApiFactory,
) : ViewModel() {

    data class UiState(
        val currentSsid: String? = null,
        val networks: List<ScannedNetwork> = emptyList(),
        val scanning: Boolean = true,
        /** 正在提交连接请求的 SSID，用于禁用重复点击。 */
        val connectingSsid: String? = null,
        /** 配置已写入、设备即将重启。 */
        val rebootPending: Boolean = false,
        val error: Error? = null,
    ) {
        enum class Error { SCAN, CONNECT }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var loadedAddress: String? = null
    private var scanJob: Job? = null

    /**
     * 每次进入页面都刷新一次。
     *
     * ViewModel 跟着 Activity 存活，离开页面不会销毁；如果同地址直接复用旧状态，
     * 上次连接留下的「设备正在重启」提示和旧扫描结果会一直挂在页面上。
     */
    fun load(address: String) {
        val knownSsid = if (address == loadedAddress) _uiState.value.currentSsid else null
        loadedAddress = address
        _uiState.value = UiState(currentSsid = knownSsid)
        loadCurrentNetwork(address)
        rescan()
    }

    fun rescan() {
        val address = loadedAddress ?: return
        scanJob?.cancel()
        _uiState.update { it.copy(scanning = true, error = null) }
        scanJob = viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            val found = try {
                pollNetworks(api)
            } catch (cancellation: CancellationException) {
                // 重新扫描会取消上一轮；这里的取消不能当成扫描失败
                throw cancellation
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // 网络边界：超时/不可达/解析失败都按「扫描失败」处理
                _uiState.update { it.copy(scanning = false, error = UiState.Error.SCAN) }
                return@launch
            }
            _uiState.update {
                it.copy(
                    networks = found,
                    scanning = false,
                    error = null,
                )
            }
        }
    }

    /**
     * 连接新网络：写 `nw.ins[0]` 并请求重启。
     *
     * 设备启用设置 PIN 时固件返回 401，这里统一按失败处理，由界面提示检查连接。
     */
    fun connect(ssid: String, password: String) {
        val address = loadedAddress ?: return
        if (_uiState.value.connectingSsid != null) return
        _uiState.update { it.copy(connectingSsid = ssid, error = null) }
        viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            val accepted = try {
                val response = api.postConfig(
                    WledConfig(
                        nw = NetworkConfig(ins = listOf(WifiEntry(ssid = ssid, psk = password))),
                        rb = true,
                    ),
                )
                response.code in 200..299
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // 网络边界：连接中断与超时都视为未提交成功
                false
            }
            _uiState.update {
                it.copy(
                    connectingSsid = null,
                    rebootPending = accepted || it.rebootPending,
                    currentSsid = if (accepted) ssid else it.currentSsid,
                    error = if (accepted) null else UiState.Error.CONNECT,
                )
            }
        }
    }

    private suspend fun pollNetworks(api: DeviceApi): List<ScannedNetwork> {
        var found: List<ScannedNetwork> = emptyList()
        for (attempt in 0 until SCAN_ATTEMPTS) {
            val response = api.scanNetworks()
            found = response.body?.networks.orEmpty().filterNot { it.ssid.isNullOrBlank() }
            if (found.isNotEmpty()) break
            if (attempt < SCAN_ATTEMPTS - 1) delay(SCAN_INTERVAL_MS)
        }
        // 同一 SSID 可能有多个热点（2.4G/5G 或 mesh），只保留信号最强的那条
        return found
            .sortedByDescending { it.rssi ?: Int.MIN_VALUE }
            .distinctBy { it.ssid }
    }

    private fun loadCurrentNetwork(address: String) {
        viewModelScope.launch {
            val api = deviceApiFactory.create(address)
            // 读不到就用「未知网络」展示，不阻塞扫描
            val config = runCatching { api.getConfig().body }.getOrNull()
            val ssid = config?.nw?.ins?.firstOrNull()?.ssid
            if (ssid != null) {
                _uiState.update { it.copy(currentSsid = ssid) }
            }
        }
    }
}
