// 게임 화면 필터: 선명(nearest) / 부드럽게(bilinear) / 스캔라인 / LCD 격자 / 업스케일(EPX = Scale2x 규칙, GPU 셰이더).
// - 업스케일은 AGSL RuntimeShader(API 33+)로 그린다. 그 미만이거나 셰이더 생성이 실패하면 선명하게 그린다.
// - [Epx]는 셰이더와 같은 규칙의 순수 Kotlin 구현(단위 테스트·문서용). 둘을 바꿀 때는 함께 바꾼다.

package com.parkjeongseop.wipi

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.roundToInt

enum class ScreenFilter { SHARP, SMOOTH, SCANLINES, LCD, UPSCALE }

/** 스캔라인·LCD 격자 어둡기(0~1) — 원본 픽셀 한 줄(칸)의 아래(오른쪽) [LINE_FRACTION]만큼을 이만큼 어둡게 */
private const val LINE_ALPHA = 0.35f
private const val LINE_FRACTION = 0.3f

/**
 * EPX(Scale2x) 규칙: 원본 픽셀 P를 2×2로 나눈 각 칸에서, 그 칸 쪽 두 이웃이 같고 반대쪽 이웃과 다르면 그 이웃 색.
 * 계단 모양 대각선이 매끈해지고 글자·가로세로 선은 그대로다. 화면 배율이 정수가 아니어도 출력 픽셀마다
 * "원본 픽셀 안의 어느 사분면인가"로 고른다(셰이더와 같은 방식).
 */
object Epx {
    /** 원본 좌표(x, y — 픽셀 단위 실수)에서의 색. [src]는 w×h ARGB. 가장자리 밖은 가장자리 픽셀(clamp). */
    fun sample(src: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val ix = floor(x).toInt().coerceIn(0, w - 1)
        val iy = floor(y).toInt().coerceIn(0, h - 1)
        fun at(px: Int, py: Int) = src[py.coerceIn(0, h - 1) * w + px.coerceIn(0, w - 1)]
        val p = at(ix, iy)
        val a = at(ix, iy - 1) // 위
        val b = at(ix + 1, iy) // 오른쪽
        val c = at(ix - 1, iy) // 왼쪽
        val d = at(ix, iy + 1) // 아래
        val left = x - floor(x) < 0.5f
        val top = y - floor(y) < 0.5f
        return when {
            top && left -> if (c == a && c != d && a != b) a else p
            top -> if (a == b && a != c && b != d) b else p
            left -> if (d == c && d != b && c != a) c else p
            else -> if (b == d && b != a && d != c) d else p
        }
    }

    /** 전체를 outW×outH로 (테스트·미리보기용 — 화면에는 셰이더가 같은 일을 한다) */
    fun render(src: IntArray, w: Int, h: Int, outW: Int, outH: Int): IntArray = IntArray(outW * outH) { i ->
        val ox = i % outW
        val oy = i / outW
        sample(src, w, h, (ox + 0.5f) * w / outW, (oy + 0.5f) * h / outH)
    }

    /** [Epx.sample]과 같은 규칙의 AGSL. image = 원본 비트맵(BitmapShader, CLAMP), 좌표는 원본 픽셀 단위로 샘플한다. */
    const val AGSL = """
uniform shader image;
uniform float2 srcSize;
uniform float2 dstSize;

bool same(half4 a, half4 b) {
    half4 d = abs(a - b);
    return d.r + d.g + d.b + d.a < 0.01;
}

half4 main(float2 fragCoord) {
    float2 p = fragCoord / dstSize * srcSize;
    float2 ip = floor(p);
    float2 f = p - ip;
    float2 c = ip + float2(0.5, 0.5);
    half4 P = image.eval(c);
    half4 A = image.eval(c + float2(0.0, -1.0));
    half4 B = image.eval(c + float2(1.0, 0.0));
    half4 C = image.eval(c + float2(-1.0, 0.0));
    half4 D = image.eval(c + float2(0.0, 1.0));
    bool left = f.x < 0.5;
    bool top = f.y < 0.5;
    if (top && left) {
        if (same(C, A) && !same(C, D) && !same(A, B)) { return A; }
    } else if (top) {
        if (same(A, B) && !same(A, C) && !same(B, D)) { return B; }
    } else if (left) {
        if (same(D, C) && !same(D, B) && !same(C, A)) { return C; }
    } else {
        if (same(B, D) && !same(B, A) && !same(D, C)) { return D; }
    }
    return P;
}
"""
}

/**
 * 필터별 게임 그리기. 셰이더는 한 번만 만들고(실패하면 null → 선명하게) 프레임마다 크기 uniform만 바꾼다.
 * 메인 스레드(그리기)에서만 쓴다.
 */
class GameRenderer(private val bitmap: Bitmap) {
    private val upscalePaint: Paint? by lazy { createUpscalePaint() }
    private var shader: RuntimeShader? = null

    val upscaleSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private fun createUpscalePaint(): Paint? {
        if (!upscaleSupported) return null
        return try {
            val s = RuntimeShader(Epx.AGSL)
            s.setInputShader("image", BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            s.setFloatUniform("srcSize", bitmap.width.toFloat(), bitmap.height.toFloat())
            shader = s
            Paint().apply { this.shader = s }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "upscale shader unavailable, falling back to sharp", e)
            null
        }
    }

    /** [frame]은 [bitmap]의 ImageBitmap 뷰. DrawScope 크기 전체에 그린다. */
    fun DrawScope.draw(frame: ImageBitmap, filter: ScreenFilter) {
        val dst = IntSize(size.width.roundToInt(), size.height.roundToInt())
        when (filter) {
            ScreenFilter.SMOOTH -> drawImage(frame, dstSize = dst, filterQuality = FilterQuality.Low)
            ScreenFilter.UPSCALE -> {
                val paint = upscalePaint
                if (paint == null) {
                    drawImage(frame, dstSize = dst, filterQuality = FilterQuality.None)
                } else {
                    shader?.setFloatUniform("dstSize", size.width, size.height)
                    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) }
                }
            }
            else -> drawImage(frame, dstSize = dst, filterQuality = FilterQuality.None)
        }
        if (filter == ScreenFilter.SCANLINES || filter == ScreenFilter.LCD) {
            val rowH = size.height / bitmap.height
            drawRect(lineBrush(rowH, vertical = true), size = size)
            if (filter == ScreenFilter.LCD) {
                val colW = size.width / bitmap.width
                drawRect(lineBrush(colW, vertical = false), size = size)
            }
        }
    }

    /** 원본 픽셀 한 칸([cell] px)마다 끝부분 [LINE_FRACTION]을 어둡게 하는 반복 그라디언트(한 번의 drawRect) */
    private fun lineBrush(cell: Float, vertical: Boolean): Brush {
        val dark = Color.Black.copy(alpha = LINE_ALPHA)
        val stops = arrayOf(
            0f to Color.Transparent,
            1f - LINE_FRACTION to Color.Transparent,
            1f - LINE_FRACTION to dark,
            1f to dark,
        )
        return if (vertical) {
            Brush.verticalGradient(*stops, startY = 0f, endY = cell, tileMode = TileMode.Repeated)
        } else {
            Brush.horizontalGradient(*stops, startX = 0f, endX = cell, tileMode = TileMode.Repeated)
        }
    }
}
