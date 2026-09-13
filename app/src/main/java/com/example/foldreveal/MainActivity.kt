package com.example.foldreveal

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * 컨트롤 화면.
 *
 * 1) "다른 앱 위에 표시" 권한 요청 → 어떤 앱을 쓰든 오버레이를 그릴 수 있게 함
 * 2) "화면 캡처 시작" 권한 요청 → MediaProjection 동의를 받고 OverlayService 시작
 * 3) 아래쪽엔 이 액티비티 안에서만 도는 미리보기(1단계 데모)도 남겨뒀습니다.
 */
class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(OverlayService.EXTRA_RESULT_DATA, result.data)
                }
                startForegroundService(serviceIntent)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    ControlScreen(
                        hasOverlayPermission = { hasOverlayPermission() },
                        onRequestOverlayPermission = { requestOverlayPermission() },
                        onStartSystemWideEffect = { requestScreenCaptureAndStart() },
                        onStopSystemWideEffect = {
                            stopService(Intent(this, OverlayService::class.java))
                        },
                    )
                }
            }
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName"),
        )
        startActivity(intent)
    }

    private fun requestScreenCaptureAndStart() {
        if (!hasOverlayPermission()) {
            requestOverlayPermission()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }
}

@Composable
fun ControlScreen(
    hasOverlayPermission: () -> Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartSystemWideEffect: () -> Unit,
    onStopSystemWideEffect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "듀오 스타일 펼침 효과",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "1) 오버레이 권한을 먼저 켜고, 2) 화면 캡처를 시작하면 " +
                "어떤 앱을 쓰고 있어도 기기를 펼치고 접을 때 전환 효과가 나타납니다. " +
                "(화면 캡처 중에는 시스템이 알림/아이콘을 표시합니다 — OS 정책이라 끌 수 없어요.)",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )

        Button(onClick = onRequestOverlayPermission) {
            Text(if (hasOverlayPermission()) "오버레이 권한: 허용됨" else "1. 오버레이 권한 요청")
        }
        Button(onClick = onStartSystemWideEffect) {
            Text("2. 화면 캡처 권한 요청 + 전체 앱 효과 시작")
        }
        Button(onClick = onStopSystemWideEffect) {
            Text("전체 앱 효과 중지")
        }

        Text(
            "아래는 이 화면 안에서만 도는 간단 미리보기예요 (실제 서비스와 별개):",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 12.sp,
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            PreviewFoldRevealScreen()
        }
    }
}

// ---- 아래는 1단계에서 만든 액티비티 내부 미리보기 (그대로 유지) ----

@Composable
fun PreviewFoldRevealScreen() {
    val context = LocalContext.current
    val hingeSensor = remember { HingeAngleSensor(context) }
    val animatedAngle = remember { Animatable(0f) }
    var rawAngle by remember { mutableFloatStateOf(0f) }
    val sensorAvailable = remember { hingeSensor.isAvailable }

    DisposableEffect(Unit) {
        hingeSensor.start { angle -> rawAngle = angle }
        onDispose { hingeSensor.stop() }
    }

    LaunchedEffect(rawAngle) {
        animatedAngle.animateTo(rawAngle, animationSpec = tween(durationMillis = 90))
    }

    PreviewHingeRevealVisual(angleDegrees = animatedAngle.value, sensorAvailable = sensorAvailable)
}

@Composable
private fun PreviewHingeRevealVisual(angleDegrees: Float, sensorAvailable: Boolean) {
    val t = (angleDegrees / 180f).coerceIn(0f, 1f)
    val smoothT = smoothstep(t)

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val coverAlpha = 1f - smoothstep(min(t / 0.18f, 1f))
        if (coverAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = coverAlpha }
                    .background(Brush.verticalGradient(listOf(Color(0xFF1B1B1F), Color(0xFF303038)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("커버 화면", color = Color.White, fontSize = 18.sp)
            }
        }

        val innerAlpha = smoothstep(min(t / 0.5f, 1f))
        if (innerAlpha > 0f) {
            Row(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = innerAlpha }) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .graphicsLayer {
                            cameraDistance = 24f * density
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            rotationY = lerp(78f, 0f, smoothT)
                        }
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF264653), Color(0xFF2A9D8F)),
                                start = Offset(0f, 0f),
                                end = Offset(400f, 800f),
                            ),
                        ),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .graphicsLayer {
                            cameraDistance = 24f * density
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            rotationY = lerp(-78f, 0f, smoothT)
                        }
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2A9D8F), Color(0xFFE9C46A)),
                                start = Offset(0f, 0f),
                                end = Offset(400f, 800f),
                            ),
                        ),
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.TopCenter) {
            Text(
                text = if (sensorAvailable) "힌지 각도: ${angleDegrees.toInt()}°" else "힌지 센서를 찾을 수 없음",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
            )
        }
    }
}

private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
