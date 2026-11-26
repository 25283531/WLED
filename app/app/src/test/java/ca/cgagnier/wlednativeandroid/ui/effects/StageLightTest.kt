package ca.cgagnier.wlednativeandroid.ui.effects

import ca.cgagnier.wlednativeandroid.model.wledapi.Segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * 舞台灯的频段掩码 / 逐频段配色约定。
 *
 * 固件 `mode_stagelight()` 读取 `custom1` 的 bit0..2 与 `col[0..2]`，
 * 这里的断言把 App 侧的默认值（仅低频、白色光）与写入格式钉死，避免两边悄悄错位。
 */
class StageLightTest {

    @Test
    fun currentMask_fallsBackToLowBandForMissingOrInvalidValues() {
        assertEquals(StageLight.DEFAULT_MASK, StageLight.currentMask(null))
        assertEquals(StageLight.DEFAULT_MASK, StageLight.currentMask(Segment(custom1 = 0)))
        assertEquals(StageLight.DEFAULT_MASK, StageLight.currentMask(Segment(custom1 = 0x08)))
        assertEquals(0x05, StageLight.currentMask(Segment(custom1 = 0x05)))
    }

    @Test
    fun toggleBand_keepsAtLeastOneBandLinked() {
        val lowOnly = StageLight.DEFAULT_MASK
        // 取消唯一关联的频段不应生效
        assertEquals(lowOnly, StageLight.toggleBand(lowOnly, 0))
        assertEquals(0x03, StageLight.toggleBand(lowOnly, 1))
        assertEquals(0x01, StageLight.toggleBand(0x03, 1))
    }

    @Test
    fun currentColors_defaultsUnsetSlotsToWhite() {
        val colors = StageLight.currentColors(Segment(colors = listOf(listOf(255, 0, 0))))
        assertEquals(listOf(0xFF0000, 0xFFFFFF, 0xFFFFFF), colors)
    }

    @Test
    fun buildEffectSegment_initializesStageLightDefaultsOnSwitch() {
        val stage = StageLight.effectId
        assertNotNull(stage, "固件灯效目录里应有 Stage Light")
        // 从其它效果切过来时，c1 里可能残留着别的效果的配置
        val segment = Segment(effect = 1, custom1 = 5, custom2 = 90, custom3 = 31)

        val result = buildEffectSegment(segment, stage!!)

        assertEquals(StageLight.DEFAULT_MASK, result.custom1)
        assertEquals(
            List(StageLight.BAND_COUNT) { listOf(255, 255, 255) },
            result.colors,
        )
    }

    @Test
    fun buildEffectSegment_keepsConfiguredBandsWithinStageLight() {
        val stage = StageLight.effectId!!
        val configured = listOf(
            listOf(255, 0, 0),
            listOf(0, 255, 0),
            listOf(0, 0, 255),
        )
        val segment = Segment(effect = stage, custom1 = 0x05, colors = configured)

        val result = buildEffectSegment(segment, stage)

        assertEquals(0x05, result.custom1)
        assertEquals(configured, result.colors)
    }
}
