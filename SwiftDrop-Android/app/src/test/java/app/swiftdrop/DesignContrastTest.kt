package app.swiftdrop

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import app.swiftdrop.ui.components.actionCaptionColor
import app.swiftdrop.ui.components.musicControlInk
import app.swiftdrop.ui.theme.SwiftDropDesign
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignContrastTest {
    private fun ratio(first:Color,second:Color):Float {
        val a=first.luminance();val b=second.luminance()
        return (maxOf(a,b)+.05f)/(minOf(a,b)+.05f)
    }
    @Test fun blueActionCaptionMeetsSmallTextContrast() {
        val background=SwiftDropDesign.Blue
        assertTrue(ratio(actionCaptionColor(Color.White).compositeOver(background),background)>=4.5f)
    }
    @Test fun transportGlyphIsReadableInBothThemes() {
        for(accent in listOf(Color(0xFFCC3B45),Color(0xFFFF918D)))
            assertTrue(ratio(musicControlInk(accent),accent)>=3f)
    }
}
