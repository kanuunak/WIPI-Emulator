// 레이아웃 HUD (명세 §5.3, 가이드 Phase 2-5). ⚙ 길게 누르기로 켜고 끈다(릴리스·디버그 모두, 기본 꺼짐).
// 실기기의 창 크기·인셋·계산 결과를 스크린샷 한 장으로 전달받기 위한 화면 — 값은 Log.i(LOG_TAG)에도 남긴다.

package com.parkjeongseop.wipi

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

const val LOG_TAG = "FoldRetro"
val EMULATOR_BG = Color(0xFF202020)

/** HUD 표시 줄과 로그 한 줄. data class라 값이 바뀔 때만 로그가 다시 남는다. */
data class LayoutMetrics(val lines: List<String>, val logLine: String)

/** 인셋(dp, L,T,R,B)을 읽어 레이아웃 계산 결과와 합친다. 인셋 변화도 구독한다. */
@Composable
fun layoutMetrics(windowWdp: Float, windowHdp: Float, layout: StackLayout): LayoutMetrics {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val config = LocalConfiguration.current

    fun dp(px: Int): Int = (px / density.density).roundToInt()
    fun insets(i: WindowInsets): String =
        "${dp(i.getLeft(density, direction))},${dp(i.getTop(density))},${dp(i.getRight(density, direction))},${dp(i.getBottom(density))}"

    val named = listOf(
        "safe" to insets(WindowInsets.safeDrawing),
        "status" to insets(WindowInsets.statusBars),
        "nav" to insets(WindowInsets.navigationBars),
        "cutout" to insets(WindowInsets.displayCutout),
        "gest" to insets(WindowInsets.systemGestures),
        "mgest" to insets(WindowInsets.mandatorySystemGestures),
    )
    val orientation = if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) "land" else "port"
    val window = "win ${windowWdp.roundToInt()}x${windowHdp.roundToInt()}dp sw${config.smallestScreenWidthDp} $orientation"
    val scale = "d ${density.density} fs ${density.fontScale}"
    val g = layout.game
    val px = layout.gamePx

    val lines = buildList {
        add(window)
        add(scale)
        add("insets dp L,T,R,B")
        named.forEach { (name, value) -> add("${name.padEnd(6)} $value") }
        add("avail ${layout.availWdp.roundToInt()}x${layout.availHdp.roundToInt()}dp")
        add("keyH ${layout.keyH.roundToInt()}dp gap ${layout.gap.roundToInt()}dp")
        add("game ${g.width.roundToInt()}x${g.height.roundToInt()}dp")
        add("     ${px.width}x${px.height}px x${"%.2f".format(java.util.Locale.ROOT, layout.gameScale)}")
        add("keypad ${layout.keypad.width.roundToInt()}x${layout.keypad.height.roundToInt()}dp")
        if (!layout.fits) add("OVERFLOW: keypad > avail")
    }
    val logLine = "layout $window $scale " + named.joinToString(" ") { (n, v) -> "$n=$v" } + " " + layout.summary()
    return LayoutMetrics(lines, logLine)
}

/** 화면 왼쪽 위(안전 영역 안)에 크게 표시. 입력을 받지 않아 아래 키·게임 터치를 막지 않는다. */
@Composable
fun LayoutHud(metrics: LayoutMetrics, modifier: Modifier = Modifier) {
    Column(modifier = modifier.background(Color.Black.copy(alpha = 0.75f)).padding(6.dp)) {
        metrics.lines.forEach {
            Text(it, color = Color(0xFF7CFC00), fontSize = 14.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
