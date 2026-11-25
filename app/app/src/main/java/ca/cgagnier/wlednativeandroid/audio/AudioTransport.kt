package ca.cgagnier.wlednativeandroid.audio

/** 手机侧向设备发送音频包的通道。 */
enum class AudioTransportType {
    /** 蓝牙 GATT（仅 C01 机型）。 */
    BLE,

    /** Wi-Fi 组播/单播（界面上的「直通」）。 */
    UDP,
}

/**
 * 音频传输层。
 *
 * 服务只关心「能否开始」「这一帧是否发出」，两种通道的发现与重连细节各自封装。
 */
interface AudioTransport {
    val type: AudioTransportType

    /** 准备通道；返回 false 表示当前设备不可用（权限或硬件缺失）。 */
    fun start(): Boolean

    /** 发送一帧；返回 false 表示当前不可达（未连接或写入失败）。 */
    fun send(frame: AudioFrame): Boolean

    fun stop()
}

/** UDP 传输：组播优先，单播/广播兜底（细节见 [WledAudioSyncSender]）。 */
class UdpAudioTransport(host: String) : AudioTransport {

    private val sender = WledAudioSyncSender(host)

    override val type: AudioTransportType = AudioTransportType.UDP

    override fun start(): Boolean = true

    override fun send(frame: AudioFrame): Boolean = sender.send(frame)

    override fun stop() {
        sender.close()
    }
}
