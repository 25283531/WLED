package ca.cgagnier.wlednativeandroid.audio

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import java.util.UUID

private const val TAG = "BleAudioSyncSender"
private val logger = Logger.withTag(TAG)

/**
 * 通过 BLE GATT 把 Audio Sync v2 包写给 C01 灯带。
 *
 * 固件只接受无回执写入，所以这里连接完成后主动把 MTU 提到 185：
 * 一个 44 字节的包需要 MTU ≥ 47 才能整包写入，更小的 MTU 会先不发送并重试一次。
 * 断开后按固定退避重扫重连，生命周期完全由 [AudioStreamService] 控制。
 *
 * 回调和写入可能来自不同线程，所有 GATT 状态都经 [gattLock] 串行化；
 * 回调线程上只做状态更新与投递重连，不做任何阻塞操作。
 */
@SuppressLint("MissingPermission")
class BleAudioSyncSender(context: Context) : AudioTransport {

    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val handler = Handler(Looper.getMainLooper())
    private val gattLock = Any()

    @Volatile
    private var running = false

    private var gatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null
    private var mtuReady = false
    private var mtuRetried = false

    @Volatile
    private var scanning = false
    private var bestNamedResult: ScanResult? = null
    private var bestOtherResult: ScanResult? = null

    override val type: AudioTransportType = AudioTransportType.BLE

    override fun start(): Boolean {
        if (running) return true
        if (!isSupported(appContext) || !hasPermissions(appContext)) {
            logger.w { "BLE audio unavailable (supported=${isSupported(appContext)})" }
            return false
        }
        val adapter = bluetoothManager?.adapter ?: return false
        if (!runCatching { adapter.isEnabled }.getOrDefault(false)) return false
        running = true
        handler.post { startScan() }
        return true
    }

    override fun send(frame: AudioFrame): Boolean {
        val payload = WledAudioSyncPacket.encode(frame)
        synchronized(gattLock) {
            if (!running || !mtuReady) return false
            val activeGatt = gatt ?: return false
            val characteristic = rxCharacteristic ?: return false
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    activeGatt.writeCharacteristic(
                        characteristic,
                        payload,
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE,
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    writeLegacy(activeGatt, characteristic, payload)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // 连接在写入瞬间被系统回收属于正常竞争，这一帧丢掉即可
                false
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun writeLegacy(
        activeGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ): Boolean {
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        characteristic.value = payload
        return activeGatt.writeCharacteristic(characteristic)
    }

    override fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        resetConnection()
    }

    // region 扫描与连接

    @SuppressLint("MissingPermission")
    private fun startScan() {
        if (!running || scanning) return
        val scanner = runCatching {
            bluetoothManager?.adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        }.getOrNull()
        if (scanner == null) {
            scheduleReconnect()
            return
        }
        synchronized(gattLock) {
            bestNamedResult = null
            bestOtherResult = null
        }
        val started = runCatching {
            scanner.startScan(listOf(SCAN_FILTER), SCAN_SETTINGS, scanCallback)
        }.isSuccess
        if (!started) {
            scheduleReconnect()
            return
        }
        scanning = true
        handler.removeCallbacks(scanWindowRunnable)
        handler.postDelayed(scanWindowRunnable, SCAN_WINDOW_MS)
    }

    private val scanWindowRunnable = Runnable { finishScan() }

    private fun finishScan() {
        if (!running || !scanning) return
        stopScan()
        val target = synchronized(gattLock) { bestNamedResult ?: bestOtherResult }
        if (target == null) {
            scheduleReconnect()
            return
        }
        connect(target.device)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        handler.removeCallbacks(scanWindowRunnable)
        runCatching { bluetoothManager?.adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!running) return
        resetConnection()
        val newGatt = runCatching {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull()
        if (newGatt == null) {
            scheduleReconnect()
            return
        }
        synchronized(gattLock) {
            gatt = newGatt
            mtuReady = false
            mtuRetried = false
        }
    }

    private fun scheduleReconnect() {
        if (!running) return
        handler.removeCallbacks(reconnectRunnable)
        handler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
    }

    private val reconnectRunnable = Runnable { startScan() }

    /** 丢弃当前连接并让下一次扫描从干净状态开始。 */
    private fun resetConnection() {
        val previous = synchronized(gattLock) {
            mtuReady = false
            rxCharacteristic = null
            val current = gatt
            gatt = null
            current
        }
        previous?.let { connection ->
            runCatching { connection.disconnect() }
            runCatching { connection.close() }
        }
    }

    // endregion

    // region 回调

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            collectCandidate(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::collectCandidate)
        }

        override fun onScanFailed(errorCode: Int) {
            logger.w { "BLE scan failed: $errorCode" }
            scanning = false
            handler.removeCallbacks(scanWindowRunnable)
            scheduleReconnect()
        }
    }

    private fun collectCandidate(result: ScanResult) {
        val name = result.scanRecord?.deviceName
            ?: runCatching { result.device.name }.getOrNull()
        synchronized(gattLock) {
            if (name?.startsWith(DEVICE_NAME_PREFIX) == true) {
                if (bestNamedResult == null || result.rssi > bestNamedResult!!.rssi) {
                    bestNamedResult = result
                }
            } else if (bestOtherResult == null || result.rssi > bestOtherResult!!.rssi) {
                bestOtherResult = result
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(connection: BluetoothGatt, status: Int, newState: Int) {
            if (!isCurrent(connection)) return
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                val discovering = synchronized(gattLock) {
                    runCatching { connection.discoverServices() }.getOrDefault(false)
                }
                if (!discovering) {
                    logger.w { "discoverServices rejected" }
                    resetIfCurrent(connection)
                    scheduleReconnect()
                }
                return
            }
            if (newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                resetIfCurrent(connection)
                scheduleReconnect()
            }
        }

        override fun onServicesDiscovered(connection: BluetoothGatt, status: Int) {
            if (!isCurrent(connection)) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                resetIfCurrent(connection)
                scheduleReconnect()
                return
            }
            val characteristic = connection
                .getService(SERVICE_UUID)
                ?.getCharacteristic(RX_CHARACTERISTIC_UUID)
            if (characteristic == null) {
                logger.w { "RX characteristic not found" }
                resetIfCurrent(connection)
                scheduleReconnect()
                return
            }
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            synchronized(gattLock) {
                rxCharacteristic = characteristic
                mtuReady = false
                mtuRetried = false
            }
            // MTU 协商结果通过 onMtuChanged 回来，成功之前 send() 一直返回 false
            val requested = synchronized(gattLock) {
                runCatching { connection.requestMtu(MTU_REQUEST) }.getOrDefault(false)
            }
            if (!requested) {
                resetIfCurrent(connection)
                scheduleReconnect()
            }
        }

        override fun onMtuChanged(connection: BluetoothGatt, mtu: Int, status: Int) {
            if (!isCurrent(connection)) return
            if (status == BluetoothGatt.GATT_SUCCESS && mtu >= MIN_USABLE_MTU) {
                synchronized(gattLock) { mtuReady = true }
                return
            }
            val retry = synchronized(gattLock) {
                if (mtuRetried) {
                    false
                } else {
                    mtuRetried = true
                    true
                }
            }
            val retried = retry && synchronized(gattLock) {
                runCatching { connection.requestMtu(MTU_REQUEST) }.getOrDefault(false)
            }
            if (retried) return
            logger.w { "MTU negotiation failed (mtu=$mtu, status=$status)" }
            resetIfCurrent(connection)
            scheduleReconnect()
        }
    }

    private fun isCurrent(connection: BluetoothGatt): Boolean =
        synchronized(gattLock) { gatt === connection }

    private fun resetIfCurrent(connection: BluetoothGatt) {
        if (isCurrent(connection)) resetConnection()
    }

    // endregion

    companion object {
        /** 固件在广播里携带的自定义服务 UUID。 */
        val SERVICE_UUID: UUID = UUID.fromString("6b1d0001-3c2a-4f5e-9d8b-0a1b2c3d4e5f")

        /** 音频包写入特征。 */
        val RX_CHARACTERISTIC_UUID: UUID = UUID.fromString("6b1d0002-3c2a-4f5e-9d8b-0a1b2c3d4e5f")

        private const val DEVICE_NAME_PREFIX = "C01-"
        private const val MTU_REQUEST = 185
        private const val MIN_USABLE_MTU = 47
        private const val SCAN_WINDOW_MS = 2_000L
        private const val RECONNECT_DELAY_MS = 2_000L

        private val SCAN_FILTER = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        private val SCAN_SETTINGS = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        /** 蓝牙音频只在 Android 10+ 的 C01 机型上可用。 */
        fun isSupported(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)

        /**
         * 扫描/连接所需权限：
         * - Android 12+：BLUETOOTH_SCAN + BLUETOOTH_CONNECT
         * - Android 10/11：定位权限（系统把 BLE 扫描算作定位）
         */
        fun hasPermissions(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                return isGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    isGranted(context, Manifest.permission.BLUETOOTH_CONNECT)
            }
            return isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        }

        private fun isGranted(context: Context, permission: String): Boolean =
            ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
