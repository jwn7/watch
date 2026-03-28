package com.issr.watch.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Text
import com.issr.watch.presentation.theme.IssrwatchTheme

/**
 * Main watch screen: single start/stop button.
 *
 * Per D-06: 세션 시작/중지 버튼 1개짜리 최소 Composable 화면.
 * Per D-07: Composable 구조로 작성 — 이후 등급 표시/경과 시간 등 추가 가능.
 *
 * DEFERRED (out of scope for Phase 3):
 * - Current grade text display
 * - Elapsed time display
 */
@Composable
fun MainScreen(
    isRunning: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    IssrwatchTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isRunning) {
                Button(
                    onClick = onStopClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE74C3C)  // Red — STOP
                    )
                ) {
                    Text("중지")
                }
            } else {
                Button(
                    onClick = onStartClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2ECC71)  // Green — START
                    )
                ) {
                    Text("시작")
                }
            }
        }
    }
}
