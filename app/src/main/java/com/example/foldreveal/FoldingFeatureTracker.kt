package com.example.foldreveal

import android.content.Context
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

import kotlinx.coroutines.launch

/**
 * 기기가 실제로 보고하는 접힘선(FoldingFeature)을 추적합니다.
 *
 * 왜 필요한가:
 *  셰이더에서 힌지를 "화면 정중앙(x=0.5)"으로 가정하면, 폴드8 같은 와이드 폼팩터나
 *  플렉스 모드 상태에서 접힘선이 실제 위치와 어긋납니다. Jetpack WindowManager 는
 *  접힘선의 실제 경계(bounds), 방향(가로/세로), 상태(FLAT/HALF_OPENED)를 알려주므로
 *  이 값을 셰이더에 그대로 먹이면 기기마다 정확히 들어맞습니다.
 *
 * 값을 못 받는 기기/상태에서는 [hingePositionRatio] 가 0.5(중앙), [isVertical] 이 true 로
 * 남아서 기존 동작과 동일하게 폴백됩니다.
 */
class FoldingFeatureTracker(private val context: Context) {

    /** 접힘선의 정규화 위치 (0.0 ~ 1.0). 세로 힌지면 x 비율, 가로 힌지면 y 비율. */
    @Volatile
    var hingePositionRatio: Float = 0.5f
        private set

    /** 접힘선이 세로선인지 (폴드 시리즈는 보통 true, 플립 시리즈는 false) */
    @Volatile
    var isVertical: Boolean = true
        private set

    /** 접힘선의 두께 (px). 0이면 두께 없는 선. */
    @Volatile
    var hingeThicknessPx: Float = 0f
        private set

    /** 기기가 실제로 접힘 정보를 보고하고 있는지 */
    @Volatile
    var hasFoldingFeature: Boolean = false
        private set

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job != null) return
        job = scope.launch {
            try {
                WindowInfoTracker.getOrCreate(context)
                    .windowLayoutInfo(context)
                    .collect { layoutInfo ->
                        val fold = layoutInfo.displayFeatures
                            .filterIsInstance<FoldingFeature>()
                            .firstOrNull()

                        if (fold == null) {
                            hasFoldingFeature = false
                            return@collect
                        }

                        hasFoldingFeature = true
                        val bounds = fold.bounds

                        if (fold.orientation == FoldingFeature.Orientation.VERTICAL) {
                            isVertical = true
                            hingeThicknessPx = (bounds.right - bounds.left).toFloat()
                            val metrics = context.resources.displayMetrics
                            val center = (bounds.left + bounds.right) / 2f
                            hingePositionRatio =
                                (center / metrics.widthPixels.toFloat()).coerceIn(0f, 1f)
                        } else {
                            isVertical = false
                            hingeThicknessPx = (bounds.bottom - bounds.top).toFloat()
                            val metrics = context.resources.displayMetrics
                            val center = (bounds.top + bounds.bottom) / 2f
                            hingePositionRatio =
                                (center / metrics.heightPixels.toFloat()).coerceIn(0f, 1f)
                        }
                    }
            } catch (e: Exception) {
                // 접힘 정보를 못 받는 환경에서는 기본값(중앙/세로)으로 계속 동작합니다.
                hasFoldingFeature = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
