package app.swiftdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.swiftdrop.ui.theme.SwiftDropDesign
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A lightweight regression guard for SwiftMotion's original palette.
 * These checks are not a substitute for TalkBack or rendered UI contrast QA.
 */
class SwiftMotionDesignContractTest {
    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + .05f) / (dark + .05f)
    }

    @Test
    fun primaryActionTextMeetsWcagAa() {
        assertTrue(
            "Signal/white should provide >=4.5:1 text contrast",
            contrast(SwiftDropDesign.Signal, Color.White) >= 4.5f
        )
    }

    @Test
    fun receiveActionTextMeetsWcagAa() {
        assertTrue(
            "Spark/white should provide >=4.5:1 text contrast",
            contrast(SwiftDropDesign.Spark, Color.White) >= 4.5f
        )
    }

    @Test
    fun canvasAndInkHaveStrongTextContrast() {
        assertTrue(
            "Ink/canvas should provide >=7:1 contrast",
            contrast(SwiftDropDesign.Ink, SwiftDropDesign.Background) >= 7f
        )
    }

    @Test
    fun touchTargetsAndScreenInsetsRemainAccessible() {
        assertTrue(SwiftDropDesign.TouchTarget.value >= 48f)
        assertTrue(SwiftDropDesign.ScreenPadding.value >= 16f)
    }
}
