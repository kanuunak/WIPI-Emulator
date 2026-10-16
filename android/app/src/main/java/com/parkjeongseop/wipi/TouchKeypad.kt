// 터치 키패드 (명세 §5.2.3). 루트 pointerInput 하나가 모든 손가락을 처리한다 — 멀티터치, 같은 클러스터 안 슬라이드.
// 키 사각형은 KeypadModel이 계산하고 그리기와 히트테스트가 같은 값을 쓴다.
// WipiNative를 직접 부르지 않는다: 허브를 주입받는다(Preview·테스트 가능).

package com.parkjeongseop.wipi

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** 몸체(판) 모서리 상한 */
private const val BODY_CORNER_MAX_DP = 20f

/**
 * 7행×3열 터치 키패드. 크기(바깥 여백 포함)는 호출자가 정한다 — 에뮬 화면은 [StackLayout.keypad]
 * (높이 = [keypadHeight](keyH))를 그대로 넘기므로 행 높이 = keyH가 된다.
 *
 * - 손가락마다 source `touch:<pointerId>`로 허브에 press / move / release.
 * - 뗌은 `change.pressed == false`로 판정(소비 여부 무시). 포인터 루프가 끝나면(취소·재구성) finally에서 전부 release.
 * - 방향키 블록(1~3행)은 시스템 뒤로가기 제스처에서 제외한다(세로 200dp 한도 이내).
 *
 * @param onFeedback 누름·슬라이드로 키가 바뀔 때 호출(햅틱). 설정 확인은 호출자 몫.
 * @param onTrace 진단 기록(키 누름·슬라이드·뗌과 이유, 좌표 dp). 레이아웃 HUD에 표시된다.
 * @param theme 키 색·모양·투명도·라벨 크기(설정 > 화면 > 키패드 테마)
 */
@Composable
fun TouchKeypad(
    hub: KeyInputHub,
    onFeedback: () -> Unit,
    modifier: Modifier = Modifier,
    rows: List<List<KeyDef>> = KeypadModel.ROWS,
    theme: KeypadTheme = KeypadTheme.DEFAULT,
    onTrace: (String) -> Unit = {},
) {
    val feedback by rememberUpdatedState(onFeedback)
    val trace by rememberUpdatedState(onTrace)
    val textMeasurer = rememberTextMeasurer()

    // 지금 이 키패드의 손가락이 누르고 있는 키 코드. Canvas draw 단계에서만 읽어 눌림 변화는 다시 그리기만 한다
    var pressed by remember { mutableStateOf(emptySet<String>()) }

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val layout = remember(widthPx, heightPx, density, rows) {
            KeypadModel.layout(widthPx, heightPx, KeypadMetrics(density), rows)
        }
        // 뒤로가기 제스처 제외: 방향키 블록. 숫자만 있는 키패드(좌우 분할의 오른쪽)는 화면 오른쪽 가장자리에 붙으므로 전체
        val exclusionBottom = if (layout.dpadBlockBottom > 0f) layout.dpadBlockBottom else heightPx
        val labels = remember(layout, textMeasurer, theme) { measureLabels(textMeasurer, layout, theme) }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .systemGestureExclusion { Rect(0f, 0f, it.size.width.toFloat(), exclusionBottom) }
                .pointerInput(layout, hub) {
                    handleKeypadPointers(layout, hub, onPressed = { pressed = it }, feedback = { feedback() }, trace = { trace(it) })
                },
        ) {
            drawKeys(layout, labels, pressed, theme)
        }
    }
}

/**
 * 키패드 터치 처리(명세 §5.2.3) — 터치 키패드와 스킨이 같이 쓴다. 손가락마다 source `touch:<pointerId>`로 허브에
 * press / move / release, 뗌은 `change.pressed == false`, 루프가 끝나면 finally에서 전부 release.
 * [layout]의 사각형으로 누르기·슬라이드를 판정하고 [onPressed]로 지금 눌린 키 코드들을 알린다(그리기용).
 */
suspend fun PointerInputScope.handleKeypadPointers(
    layout: KeypadLayout,
    hub: KeyInputHub,
    onPressed: (Set<String>) -> Unit,
    feedback: () -> Unit,
    trace: (String) -> Unit,
) {
    fun at(x: Float, y: Float) = "${(x / density).roundToInt()},${(y / density).roundToInt()}"

    awaitPointerEventScope {
        val active = HashMap<PointerId, KeyDef>()
        fun source(id: PointerId) = "touch:${id.value}"
        try {
            while (true) {
                val event = awaitPointerEvent()
                for (change in event.changes) {
                    val id = change.id
                    val x = change.position.x
                    val y = change.position.y
                    var current = active[id]
                    // 허브가 이미 놓은 손가락(일시정지·포커스 상실의 releaseAll)은 떼기 전까지 무시
                    if (current != null && !hub.holds(source(id))) {
                        trace("#${id.value} ${current.code} dropped (hub released)")
                        active.remove(id)
                        current = null
                    }
                    when {
                        !change.pressed -> if (current != null) {
                            active.remove(id)
                            hub.release(source(id))
                            // Compose의 취소 이벤트는 시각이 직전과 같다(실제 뗌은 시각이 흐름)
                            val why = if (change.uptimeMillis == change.previousUptimeMillis) "CANCEL" else "up"
                            trace("#${id.value} ${current.code} $why @${at(x, y)}")
                        }
                        current == null -> if (change.changedToDownIgnoreConsumed()) {
                            val key = layout.hitTestInitial(x, y)
                            if (key != null) {
                                active[id] = key
                                hub.press(source(id), key.code)
                                feedback()
                            }
                            trace("#${id.value} down ${key?.code ?: "-"} @${at(x, y)}")
                        }
                        layout.isBeyondLeave(x, y) -> {
                            active.remove(id)
                            hub.release(source(id))
                            trace("#${id.value} ${current.code} left area @${at(x, y)}")
                        }
                        else -> {
                            val next = layout.hitTestMove(current, x, y)
                            if (next != current) {
                                active[id] = next
                                hub.move(source(id), next.code)
                                feedback()
                                trace("#${id.value} ${current.code}>${next.code} @${at(x, y)}")
                            }
                        }
                    }
                    change.consume()
                }
                onPressed(active.values.mapTo(HashSet()) { it.code })
            }
        } finally {
            if (active.isNotEmpty()) trace("loop end: release ${active.values.joinToString { it.code }}")
            active.keys.forEach { hub.release(source(it)) }
            active.clear()
            onPressed(emptySet())
        }
    }
}

private fun measureLabels(textMeasurer: TextMeasurer, layout: KeypadLayout, theme: KeypadTheme): List<TextLayoutResult> {
    val shadow = if (theme.labelShadow) Shadow(Color.Black.copy(alpha = 0.8f), Offset(0f, 1f), blurRadius = 4f) else null
    return layout.keys.map {
        val style = TextStyle(color = Color(theme.labelColor(it.key.cluster)), fontSize = theme.labelSize.sp.sp, shadow = shadow)
        textMeasurer.measure(it.key.label, style)
    }
}

/** 키 그리기(터치 키패드·배치 편집 미리보기·설정의 테마 미리보기가 같이 쓴다). 눌린 키는 [KeypadTheme.pressedColor]로 */
private fun DrawScope.drawKeys(layout: KeypadLayout, labels: List<TextLayoutResult>, down: Set<String>, theme: KeypadTheme) {
    val palette = theme.palette
    val first = layout.keys.firstOrNull() ?: return
    val keyCorner = theme.cornerRadiusPx(first.right - first.left, first.bottom - first.top, density)
    palette.body?.let { body ->
        val margin = KEYPAD_MARGIN_DP.dp.toPx()
        drawRoundRect(
            color = Color(body),
            cornerRadius = CornerRadius(minOf(keyCorner + margin, BODY_CORNER_MAX_DP.dp.toPx())),
            alpha = theme.opacity,
        )
    }
    val strokePx = 1.dp.toPx()
    layout.keys.forEachIndexed { i, r ->
        val pressed = r.key.code in down
        val corner = CornerRadius(theme.cornerRadiusPx(r.right - r.left, r.bottom - r.top, density))
        val topLeft = Offset(r.left, r.top)
        val size = Size(r.right - r.left, r.bottom - r.top)
        drawRoundRect(
            color = Color(if (pressed) theme.pressedColor(r.key.cluster) else theme.keyColor(r.key.cluster)),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
            alpha = theme.fillAlpha(pressed),
        )
        palette.border?.let { border ->
            val half = strokePx / 2f
            drawRoundRect(
                color = Color(border),
                topLeft = Offset(r.left + half, r.top + half),
                size = Size(size.width - strokePx, size.height - strokePx),
                cornerRadius = CornerRadius((corner.x - half).coerceAtLeast(0f)),
                style = Stroke(strokePx),
                alpha = theme.borderAlpha,
            )
        }
        val label = labels[i]
        drawText(
            label,
            topLeft = Offset(r.centerX - label.size.width / 2f, r.centerY - label.size.height / 2f),
            alpha = theme.labelAlpha,
        )
    }
}

/** 입력을 받지 않는 키패드 그림(배치 편집·테마 미리보기). 크기는 호출자가 정한다. [down]은 눌린 모습으로 그릴 키 */
@Composable
fun KeypadPreview(
    rows: List<List<KeyDef>>,
    modifier: Modifier = Modifier,
    theme: KeypadTheme = KeypadTheme.DEFAULT,
    down: Set<String> = emptySet(),
) {
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current.density
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val layout = remember(widthPx, heightPx, density, rows) { KeypadModel.layout(widthPx, heightPx, KeypadMetrics(density), rows) }
        val labels = remember(layout, textMeasurer, theme) { measureLabels(textMeasurer, layout, theme) }
        Canvas(modifier = Modifier.fillMaxSize()) { drawKeys(layout, labels, down, theme) }
    }
}

@Preview(name = "TouchKeypad — Fold5 커버 (344dp, keyH 48)", widthDp = 344, heightDp = 376)
@Composable
private fun TouchKeypadPreview() {
    // 가짜 허브: sink는 아무것도 안 하고, 지연 실행은 즉시 실행
    val hub = remember {
        KeyInputHub(
            sink = object : KeySink {
                override fun down(key: String) {}
                override fun up(key: String) {}
            },
            clock = { 0L },
            schedule = { _, action -> action() },
        )
    }
    TouchKeypad(
        hub = hub,
        onFeedback = {},
        modifier = Modifier
            .background(EMULATOR_BG)
            .width(344.dp)
            .height(keypadHeight(48f).dp),
    )
}

@Preview(name = "키패드 테마 프리셋 (▲ 눌림)", widthDp = 344, heightDp = 640)
@Composable
private fun KeypadThemePresetsPreview() {
    androidx.compose.foundation.layout.Column(modifier = Modifier.background(EMULATOR_BG)) {
        KeypadPreset.entries.forEach { preset ->
            KeypadPreview(
                rows = KeypadModel.UPPER_ROWS,
                theme = preset.defaults(),
                down = setOf("UP"),
                modifier = Modifier.width(344.dp).height(160.dp),
            )
        }
    }
}
