// 배치 편집 화면: 게임·키패드·⏸⚙ 아이콘을 끌어서 옮기고, 오른쪽 아래 ● 손잡이로 크기를 바꾼다.
// 좌표 계산은 LayoutEditModel(순수 Kotlin)이 하고, 여기는 그리기와 드래그 입력만 맡는다.
// 에뮬 화면의 안쪽(안전 영역) 상자 안에 그려 실제 게임 화면과 같은 좌표·크기로 편집한다.

package com.parkjeongseop.wipi

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val SELECTED = Color(0xFFFFD54F)
private val UNSELECTED = Color.White.copy(alpha = 0.5f)
private const val HANDLE_DP = 32

/** 편집 대상: 게임, 키패드(번호), 아이콘 */
private sealed interface EditTarget {
    data object Game : EditTarget
    data class Pad(val index: Int) : EditTarget
    data object Icons : EditTarget
}

/**
 * @param initial 편집 출발점(지금 화면의 배치)
 * @param availWdp/availHdp 편집 영역 = 에뮬 화면의 안전 영역 크기(dp)
 * @param onSave 저장(이 화면 크기에 적용) / [onReset] 저장 삭제(자동 배치로) / [onCancel] 바꾸지 않고 닫기(뒤로가기도 같다)
 */
@Composable
fun LayoutEditor(
    initial: CustomLayout,
    availWdp: Float,
    availHdp: Float,
    frame: ImageBitmap,
    hasFrame: Boolean,
    keypadTheme: KeypadTheme,
    onSave: (CustomLayout) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var selected by remember { mutableStateOf<EditTarget>(EditTarget.Game) }
    val w = availWdp
    val h = availHdp

    BackHandler { onCancel() }

    Box(modifier = Modifier.fillMaxSize().background(EMULATOR_BG)) {
        // 게임
        EditableBox(
            rect = draft.game,
            selected = selected == EditTarget.Game,
            onSelect = { selected = EditTarget.Game },
            onMove = { dx, dy -> draft = draft.copy(game = LayoutEdit.move(draft.game, dx, dy, w, h)) },
            onResize = { dx, dy -> draft = draft.copy(game = LayoutEdit.resizeGame(draft.game, dx, dy, w, h)) },
        ) {
            if (hasFrame) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawImage(frame, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None)
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFF303030)))
            }
        }

        // 키패드들
        draft.pads.forEachIndexed { i, pad ->
            EditableBox(
                rect = pad.rect,
                selected = selected == EditTarget.Pad(i),
                onSelect = { selected = EditTarget.Pad(i) },
                onMove = { dx, dy -> draft = draft.updatePad(i) { LayoutEdit.move(it.rect, dx, dy, w, h) } },
                onResize = { dx, dy -> draft = draft.updatePad(i) { LayoutEdit.resizePad(it.rect, it.rows.rows.size, dx, dy, w, h) } },
            ) {
                KeypadPreview(rows = pad.rows.rows, theme = keypadTheme, modifier = Modifier.fillMaxSize().alpha(0.9f))
            }
        }

        // ⏸·⚙ (옮기기만)
        EditableBox(
            rect = draft.icons,
            selected = selected == EditTarget.Icons,
            onSelect = { selected = EditTarget.Icons },
            onMove = { dx, dy -> draft = draft.copy(icons = LayoutEdit.move(draft.icons, dx, dy, w, h)) },
            onResize = null,
        ) {
            Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Pause, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(ICON_TOUCH_DP.dp).padding(12.dp))
                Icon(Icons.Filled.Settings, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(ICON_TOUCH_DP.dp).padding(12.dp))
            }
        }

        // 도구 막대
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row {
                TextButton(onClick = {
                    draft = LayoutEdit.toggleSplit(draft, w, h)
                    selected = EditTarget.Game
                }) { Text(stringResource(if (draft.isSplit) R.string.editor_merge else R.string.editor_split)) }
                TextButton(onClick = onReset) { Text(stringResource(R.string.editor_reset)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.editor_save)) }
            }
            Text(stringResource(R.string.editor_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
    }
}

private fun CustomLayout.updatePad(index: Int, f: (PadSpec) -> RectDp): CustomLayout =
    copy(pads = pads.mapIndexed { i, p -> if (i == index) p.copy(rect = f(p)) else p })

/** 끌어서 옮기는 상자 + (있으면) 오른쪽 아래 크기 손잡이. 드래그 양은 dp로 넘긴다. */
@Composable
private fun EditableBox(
    rect: RectDp,
    selected: Boolean,
    onSelect: () -> Unit,
    onMove: (dxDp: Float, dyDp: Float) -> Unit,
    onResize: ((dxDp: Float, dyDp: Float) -> Unit)?,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current.density
    val select by rememberUpdatedState(onSelect)
    val move by rememberUpdatedState(onMove)
    val resize by rememberUpdatedState(onResize)
    Box(
        modifier = Modifier
            .offset(rect.left.dp, rect.top.dp)
            .size(rect.width.dp, rect.height.dp)
            .border(2.dp, if (selected) SELECTED else UNSELECTED)
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { select() }) { change, amount ->
                    change.consume()
                    move(amount.x / density, amount.y / density)
                }
            },
    ) {
        content()
        if (onResize != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(HANDLE_DP.dp)
                    .padding(4.dp)
                    .background(if (selected) SELECTED else UNSELECTED, CircleShape)
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { select() }) { change, amount ->
                            change.consume()
                            resize?.invoke(amount.x / density, amount.y / density)
                        }
                    },
            )
        }
    }
}
