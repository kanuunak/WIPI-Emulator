// 스킨 화면: 폰 그림 + 화면 자리에 게임 + 키 영역 누르기(TouchKeypad와 같은 handleKeypadPointers).
// 배치 계산은 SkinPack.place(순수 Kotlin). 눌린 키는 눌린 그림(있으면) 또는 pressTint로 그 키 모양만 덮는다.
// WipiNative를 직접 부르지 않는다(허브·게임 그리기를 주입).

package com.parkjeongseop.wipi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 스킨의 배치 하나와 그 그림(읽어 둔 것) */
class LoadedSkin(val layout: SkinLayoutSpec, val body: ImageBitmap, val pressed: ImageBitmap?)

/**
 * @param drawGame 게임 화면 자리(정수 px 사각형)에 게임을 그린다 — 크기 = DrawScope 크기
 * @param showKeyAreas 키 영역 테두리·코드 표시(스킨 만들 때 확인용)
 * @param fillBackground false면 그림 밖 여백을 칠하지 않는다(뒤에 사용자 배경 이미지가 있을 때)
 * @param icons ⏸⚙ 묶음([iconsWdp]×[iconsHdp]) — 스킨이 정한 자리(없으면 그림 오른쪽 위)에 놓는다
 */
@Composable
fun SkinScreen(
    skin: LoadedSkin,
    hub: KeyInputHub,
    onFeedback: () -> Unit,
    onTrace: (String) -> Unit,
    showKeyAreas: Boolean,
    fillBackground: Boolean,
    drawGame: DrawScope.() -> Unit,
    iconsWdp: Float,
    iconsHdp: Float,
    icons: @Composable () -> Unit,
) {
    val feedback by rememberUpdatedState(onFeedback)
    val trace by rememberUpdatedState(onTrace)
    var pressed by remember { mutableStateOf(emptySet<String>()) }
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current.density
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val placement = remember(skin, w, h, density) {
            SkinPack.place(skin.layout, w, h, density, iconsWdp * density, iconsHdp * density)
        }
        // 뒤로가기 제스처 제외: 방향키 둘레(세로 200dp 한도는 시스템이 자른다)
        val exclusion = remember(placement) {
            val dpad = placement.keys.filter { it.code in DPAD_CODES }
            if (dpad.isEmpty()) null else Rect(0f, dpad.minOf { it.box.top }, w, dpad.maxOf { it.box.bottom })
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .systemGestureExclusion { exclusion ?: Rect.Zero }
                .pointerInput(placement, hub) {
                    handleKeypadPointers(placement.keypad, hub, onPressed = { pressed = it }, feedback = { feedback() }, trace = { trace(it) })
                },
        ) {
            val layout = placement.layout
            if (fillBackground) drawRect(Color(layout.background))
            val img = placement.image
            val dstOffset = IntOffset(img.left.roundToInt(), img.top.roundToInt())
            val dstSize = IntSize(img.width.roundToInt(), img.height.roundToInt())
            drawImage(skin.body, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Medium)
            val scr = placement.screen
            drawRect(Color(layout.screenColor), topLeft = Offset(scr.left, scr.top), size = Size(scr.width, scr.height))

            // 눌린 키: 그 키 모양 안만 눌린 그림으로, 없으면 색으로 덮는다
            for (k in placement.keys) {
                if (k.code !in pressed) continue
                val path = keyPath(k)
                val pressedImage = skin.pressed
                if (pressedImage != null) {
                    clipPath(path) { drawImage(pressedImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Medium) }
                } else {
                    drawPath(path, Color(layout.pressTint))
                }
            }

            if (showKeyAreas) {
                val stroke = Stroke(2.dp.toPx())
                drawRect(Color.Cyan, Offset(scr.left, scr.top), Size(scr.width, scr.height), style = stroke)
                for (k in placement.keys) {
                    drawPath(keyPath(k), Color.Magenta, style = stroke)
                    val label = textMeasurer.measure(k.code, TextStyle(color = Color.Magenta, fontSize = 11.sp, background = Color.White.copy(alpha = 0.7f)))
                    drawText(label, topLeft = Offset(k.box.left + 2f, k.box.top + 2f))
                }
            }
        }

        // 게임: draw 단계에서만 프레임을 읽는다(drawGame) → 매 프레임 재구성 없음
        val g = placement.game
        Canvas(
            modifier = Modifier
                .offset { IntOffset(g.left.roundToInt(), g.top.roundToInt()) }
                .size((g.width / density).dp, (g.height / density).dp),
        ) { drawGame() }

        Box(modifier = Modifier.offset { IntOffset(placement.iconsX.roundToInt(), placement.iconsY.roundToInt()) }) {
            icons()
        }
    }
}

private val DPAD_CODES = setOf("UP", "DOWN", "LEFT", "RIGHT", "OK")

private fun keyPath(k: SkinKey): Path = Path().apply {
    val r = Rect(k.box.left, k.box.top, k.box.right, k.box.bottom)
    when (k.shape) {
        SkinKeyShape.RECT -> addRect(r)
        SkinKeyShape.OVAL -> addOval(r)
        SkinKeyShape.ROUND -> addRoundRect(RoundRect(r, CornerRadius(k.radius)))
    }
}
