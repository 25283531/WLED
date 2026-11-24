package ca.cgagnier.wlednativeandroid.util

import kotlin.math.roundToInt

/**
 * WLED 通道刻度。
 *
 * WLED 协议里，亮度（`bri`）、灯效速度（`seg.sx`）、灯效强度（`seg.ix`）用的是
 * **同一个 0..255 的整数刻度**；百分比（0..100）和比例（0..1）都只是 UI 表征。
 * 这三个换算方向集中在这里，避免各页面各写一遍 —— 历史上就因为同一段
 * 「×255/100」被写了两遍，导致速度滑块实际只能输出 0..7。
 */
const val CHANNEL_MAX = 255

/**
 * 百分比刻度上限（0..100）。
 */
const val CHANNEL_PERCENT_MAX = 100

/**
 * 比例（0..1）→ 通道值（0..255）。
 *
 * 这是滑块提交的唯一换算入口：滑块内部只保留比例，提交时才落到通道刻度。
 *
 * @param normalized 归一化比例，超出 0..1 会被钳位
 * @return 四舍五入后的通道值
 */
fun normalizedToChannel(normalized: Float): Int =
    (normalized.coerceIn(0f, 1f) * CHANNEL_MAX).roundToInt()

/**
 * 通道值（0..255）→ 比例（0..1）。
 *
 * @param channel 通道值，超出 0..255 会被钳位
 */
fun channelToNormalized(channel: Int): Float =
    channel.coerceIn(0, CHANNEL_MAX) / CHANNEL_MAX.toFloat()

/**
 * 通道值（0..255）→ 百分比（0..100）。
 *
 * @param channel 通道值，超出 0..255 会被钳位
 */
fun channelToPercent(channel: Int): Int =
    (channel.coerceIn(0, CHANNEL_MAX) * CHANNEL_PERCENT_MAX.toFloat() / CHANNEL_MAX).roundToInt()

/**
 * 百分比（0..100）→ 通道值（0..255）。
 *
 * @param percent 百分比，超出 0..100 会被钳位
 */
fun percentToChannel(percent: Float): Int =
    (percent.coerceIn(0f, CHANNEL_PERCENT_MAX.toFloat()) / CHANNEL_PERCENT_MAX * CHANNEL_MAX).roundToInt()

/**
 * 比例（0..1）→ 百分比（0..100），仅用于界面显示。
 *
 * @param normalized 归一化比例，超出 0..1 会被钳位
 */
fun normalizedToPercent(normalized: Float): Int =
    (normalized.coerceIn(0f, 1f) * CHANNEL_PERCENT_MAX).roundToInt()
