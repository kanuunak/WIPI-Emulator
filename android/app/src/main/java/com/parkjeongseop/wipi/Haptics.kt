// 게임의 vibrate(duration_ms, intensity) 요청을 Vibrator로 재생 (iOS Haptics와 대칭).
// WIPI API의 level(0~10)을 세기에 반영하고, 사용자 배율(scale)을 곱한다.
// 낮은 값이 안 느껴지는 문제만 0.5 + 0.5x 선형 보정으로 바닥을 올린다.

package com.parkjeongseop.wipi

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** durationMs 동안 intensity(0~100)로 진동. scale은 설정의 배율(0~1.5). 0이면 진동 없음/끄기 신호라 건너뜀. */
    fun play(durationMs: Long, intensity: Int, scale: Float) {
        val vibrator = vibrator ?: return
        if (durationMs <= 0 || intensity <= 0 || scale <= 0f) return

        val level = intensity / 100f
        val base = 0.5f + 0.5f * level // level에 비례, 최소 0.55 보장 (개발 의도)
        val amplitude = ((base * scale).coerceIn(0f, 1f) * 255).toInt().coerceIn(1, 255)
        val duration = durationMs.coerceAtMost(5000) // 상한 5초 (안전)

        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(duration, amplitude)
        } else {
            // 세기 조절 불가 기기 — 실제 피처폰 편심모터처럼 ON/OFF만
            VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    /**
     * 터치 키패드 누름·슬라이드 피드백. View.performHapticFeedback(VIRTUAL_KEY)는 기기(삼성)에서 거의 안 느껴져
     * 진동기의 표준 클릭 효과를 직접 쓴다(API 29+). 그 아래는 짧은 원샷.
     */
    fun keyTap() {
        val vibrator = vibrator ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
        } else {
            VibrationEffect.createOneShot(KEY_TAP_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    }

    private companion object {
        const val KEY_TAP_MS = 20L
    }
}
