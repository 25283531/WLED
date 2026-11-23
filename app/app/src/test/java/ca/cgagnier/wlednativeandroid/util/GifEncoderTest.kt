package ca.cgagnier.wlednativeandroid.util

import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * 用 JDK 自带的 GIF 解码器反解回来比对像素 —— 只有解出来的图与输入完全一致，
 * 才能说明 LZW 的码长切换与位打包是对的。
 */
class GifEncoderTest {

    @Test
    fun `encodes a small indexed image without loss`() {
        val width = 9
        val height = 5
        val palette = intArrayOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFFFF)
        val indices = ByteArray(width * height) { (it % palette.size).toByte() }

        val gif = GifEncoder.encode(indices, width, height, palette)
        val decoded = ImageIO.read(ByteArrayInputStream(gif))

        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val expected = palette[indices[y * width + x].toInt()]
                assertEquals(expected, decoded.getRGB(x, y) and 0xFFFFFF, "像素 ($x,$y) 不一致")
            }
        }
    }

    /**
     * 大面积渐变会逼着编码器把字典填满并提升码长，这一步最容易写错：
     * 解码器要读到下一个码才补登表项，编码器必须按"已分配出去的码号"提升位宽。
     */
    @Test
    fun `handles palettes that force longer codes`() {
        val width = 40
        val height = 24
        val palette = IntArray(256) { index ->
            (index shl 16) or ((index * 3 and 0xFF) shl 8) or (255 - index)
        }
        val indices = ByteArray(width * height) { (it * 7 % 256).toByte() }

        assertRoundTrip(indices, width, height, palette)
    }

    /**
     * 噪声图会让字典一路填到 4096 并触发 Clear Code，覆盖码长回退那一段。
     */
    @Test
    fun `handles dictionary overflow and clear code`() {
        val width = 64
        val height = 64
        val palette = IntArray(256) { index -> (index shl 16) or ((255 - index) shl 8) or (index * 5 and 0xFF) }
        val random = java.util.Random(7L)
        val indices = ByteArray(width * height) { random.nextInt(256).toByte() }

        assertRoundTrip(indices, width, height, palette)
    }

    private fun assertRoundTrip(indices: ByteArray, width: Int, height: Int, palette: IntArray) {
        val gif = GifEncoder.encode(indices, width, height, palette)
        val decoded = ImageIO.read(ByteArrayInputStream(gif))

        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        var mismatches = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = indices[y * width + x].toInt() and 0xFF
                if (decoded.getRGB(x, y) and 0xFFFFFF != palette[index] and 0xFFFFFF) mismatches++
            }
        }
        assertEquals(0, mismatches, "解码后有 $mismatches 个像素不一致")
    }
}
