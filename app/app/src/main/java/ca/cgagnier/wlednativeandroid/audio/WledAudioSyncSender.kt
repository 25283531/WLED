package ca.cgagnier.wlednativeandroid.audio

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WLED Audio Sync v2 的 44 字节包编码。
 *
 * 包结构来自固件 `audio_reactive.cpp` 的 `audioSyncPacket`（44 字节、packed）：
 *
 * ```
 * offset 0  char[6]  "00002\0"
 * offset 6  uint8[2] reserved
 * offset 8  float    sampleRaw
 * offset 12 float    sampleSmth
 * offset 16 uint8    samplePeak
 * offset 17 uint8    reserved
 * offset 18 uint8[16] fftResult
 * offset 34 uint16   reserved
 * offset 36 float    FFT_Magnitude
 * offset 40 float    FFT_MajorPeak
 * ```
 *
 * UDP 与 BLE 收到的都是同一个包，编码只保留这一份实现，避免两处偏移各自漂移。
 */
object WledAudioSyncPacket {

    const val SIZE = 44

    private const val GEQ_CHANNELS = 16
    private const val PEAK_ON: Byte = 1
    private const val PEAK_OFF: Byte = 0
    private const val RESERVED_SHORT: Short = 0
    private val HEADER = "00002".toByteArray(Charsets.US_ASCII) + byteArrayOf(0)
    private val RESERVED = ByteArray(2)
    private const val RESERVED_ONE: Byte = 0

    fun encode(frame: AudioFrame): ByteArray {
        val buffer = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(HEADER)
        buffer.put(RESERVED)
        buffer.putFloat(frame.sampleRaw)
        buffer.putFloat(frame.sampleSmth)
        buffer.put(if (frame.peak) PEAK_ON else PEAK_OFF)
        buffer.put(RESERVED_ONE)
        val geq = ByteArray(GEQ_CHANNELS)
        System.arraycopy(frame.geq, 0, geq, 0, minOf(frame.geq.size, GEQ_CHANNELS))
        buffer.put(geq)
        buffer.putShort(RESERVED_SHORT)
        buffer.putFloat(frame.magnitude)
        buffer.putFloat(frame.majorPeak)
        return buffer.array()
    }
}

/**
 * WLED Audio Sync v2 发送器（UDP 11988）。
 *
 * 固件用 `fftUdp.beginMulticast(IPAddress(239, 0, 0, 1), port)` 接收（见
 * `audio_reactive.cpp:1531`），所以**组播是主路径**；单播/广播只作为兜底。
 * UDP 没有回执，发送成功不代表设备收到，"设备不可达"只能由发送异常推断。
 */
class WledAudioSyncSender(
    private val host: String,
    private val port: Int = DEFAULT_PORT,
) : Closeable {

    private val socket = DatagramSocket().apply {
        broadcast = true
    }

    /** 最近一次发送是否成功（异常时为 false），供服务判断是否需要提示设备不可达。 */
    var lastSendFailed: Boolean = false
        private set

    fun send(frame: AudioFrame): Boolean {
        val bytes = WledAudioSyncPacket.encode(frame)
        lastSendFailed = false
        // 设备只加入了 239.0.0.1 组播组，组播必须是首选，单播放在后面兜底
        if (sendTo(bytes, MULTICAST_HOST)) return true
        if (sendTo(bytes, host)) return true
        if (sendTo(bytes, BROADCAST_HOST)) return true
        lastSendFailed = true
        return false
    }

    private fun sendTo(bytes: ByteArray, address: String): Boolean = try {
        val target = InetAddress.getByName(address)
        socket.send(DatagramPacket(bytes, bytes.size, target, port))
        true
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        // 网络边界：网卡切换、地址暂时不可解析都只意味着这次没发出去
        false
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        const val DEFAULT_PORT = 11_988
        const val MULTICAST_HOST = "239.0.0.1"
        const val BROADCAST_HOST = "255.255.255.255"
    }
}
