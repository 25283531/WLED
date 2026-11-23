package ca.cgagnier.wlednativeandroid.util

import java.io.ByteArrayOutputStream

/**
 * 最小 GIF89a 编码器（单帧、全局调色板）。
 *
 * 用途：把像素画板画出来的图编码成 GIF 再上传到设备，交给固件的 `Image` 效果显示。
 * WLED 只认 GIF/图片文件，跨平台层没有可用的编码库，所以这里手写一份 ——
 * 索引色 + LZW 的编码逻辑不复杂，比引入一个大依赖可控。
 *
 * 只支持单帧：像素画板本来就是静态图，多帧动画在设备端没有入口能配置。
 */
object GifEncoder {

    private const val MAX_COLORS = 256
    private const val MIN_CODE_SIZE = 8

    /** LZW 码长上限；再大就必须发 Clear Code 重来。 */
    private const val MAX_CODE_SIZE = 12
    private const val MAX_CODES = 1 shl MAX_CODE_SIZE

    /**
     * @param indices 逐像素的调色板下标，长度必须是 [width] * [height]，行优先。
     * @param palette 调色板，ARGB 或 RGB 均可，只用低 24 位；不足 256 色时补黑。
     */
    fun encode(indices: ByteArray, width: Int, height: Int, palette: IntArray): ByteArray {
        require(width > 0 && height > 0) { "尺寸必须为正" }
        require(indices.size == width * height) { "像素数量与尺寸不匹配" }
        require(palette.size <= MAX_COLORS) { "调色板最多 $MAX_COLORS 色" }

        val out = ByteArrayOutputStream()
        // Header + Logical Screen Descriptor
        out.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(out, width)
        writeShort(out, height)
        out.write(0xF7) // 全局调色板、256 色、每像素 8 位
        out.write(0x00) // 背景色索引
        out.write(0x00) // 像素宽高比

        // Global Color Table
        for (index in 0 until MAX_COLORS) {
            val color = palette.getOrElse(index) { 0 }
            out.write((color shr 16) and 0xFF)
            out.write((color shr 8) and 0xFF)
            out.write(color and 0xFF)
        }

        // Image Descriptor
        out.write(0x2C)
        writeShort(out, 0)
        writeShort(out, 0)
        writeShort(out, width)
        writeShort(out, height)
        out.write(0x00)

        out.write(MIN_CODE_SIZE)
        writeSubBlocks(out, lzwEncode(indices))
        out.write(0x3B) // Trailer
        return out.toByteArray()
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write(value and 0xFF)
        out.write((value shr 8) and 0xFF)
    }

    /** GIF 的压缩数据按 ≤255 字节分块，每块前面是长度，最后以 0 结尾。 */
    private fun writeSubBlocks(out: ByteArrayOutputStream, data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val size = minOf(255, data.size - offset)
            out.write(size)
            out.write(data, offset, size)
            offset += size
        }
        out.write(0x00)
    }

    /**
     * GIF 变体的 LZW：码长从 minCodeSize+1 起，字典满 4096 时发 Clear Code 重来。
     * 位数按 LSB 优先打包。
     */
    private fun lzwEncode(indices: ByteArray): ByteArray {
        val clearCode = 1 shl MIN_CODE_SIZE
        val endCode = clearCode + 1

        val out = ByteArrayOutputStream()
        val dictionary = HashMap<Int, Int>(8192)

        var nextCode = endCode + 1
        var codeSize = MIN_CODE_SIZE + 1
        var bitBuffer = 0
        var bitCount = 0

        fun writeCode(code: Int) {
            bitBuffer = bitBuffer or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                out.write(bitBuffer and 0xFF)
                bitBuffer = bitBuffer ushr 8
                bitCount -= 8
            }
        }

        writeCode(clearCode)

        var prefix = indices[0].toInt() and 0xFF
        for (index in 1 until indices.size) {
            val next = indices[index].toInt() and 0xFF
            val key = (prefix shl 8) or next
            val existing = dictionary[key]
            if (existing != null) {
                prefix = existing
                continue
            }
            writeCode(prefix)
            if (nextCode < MAX_CODES) {
                dictionary[key] = nextCode
                // 码长切换用「刚分配出去的码」判定：解码器要读到下一个码才会补登这条表项，
                // 所以编码器必须按已分配的码号提升位宽，不能等 nextCode 自增之后再比。
                if (nextCode >= (1 shl codeSize) && codeSize < MAX_CODE_SIZE) {
                    codeSize++
                }
                nextCode++
            } else {
                writeCode(clearCode)
                dictionary.clear()
                nextCode = endCode + 1
                codeSize = MIN_CODE_SIZE + 1
            }
            prefix = next
        }
        writeCode(prefix)
        writeCode(endCode)
        if (bitCount > 0) {
            out.write(bitBuffer and 0xFF)
        }
        return out.toByteArray()
    }
}
