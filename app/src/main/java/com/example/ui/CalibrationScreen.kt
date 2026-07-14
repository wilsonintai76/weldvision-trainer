package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.Application
import com.example.data.*
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import android.os.Vibrator
import android.os.VibrationEffect
import android.content.Context
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager


@Composable
fun CalibrationScreen(
    state: WeldVisionState,
    viewModel: WeldVisionUiViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BRACKET MOUNT REGISTRATION",
                    color = WarningAmber,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text("Pivot Calibration Sequence", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .background(
                        if (state.isCalibrating) WarningAmber.copy(alpha = 0.2f) 
                        else if (state.isCalibrated) AlertEmerald.copy(alpha = 0.2f)
                        else AlertEmerald.copy(alpha = 0.15f),
                        RoundedCornerShape(4.dp)
                    )
                    .border(1.dp, 
                        if (state.isCalibrating) WarningAmber 
                        else if (state.isCalibrated) AlertEmerald 
                        else BorderGrey, 
                        RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (state.isCalibrating) "CALIBRATING..." 
                           else if (state.isCalibrated) "CALIBRATED ✓" 
                           else "NOT CALIBRATED",
                    color = if (state.isCalibrating) WarningAmber 
                           else if (state.isCalibrated) AlertEmerald
                           else MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Live Camera Feed for AprilTag alignment
            Box(
                modifier = Modifier
                    .weight(5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, AccentCyan, RoundedCornerShape(16.dp))
                    .background(Color.Black)
            ) {
                CameraPreviewView(
                    modifier = Modifier.fillMaxSize(),
                    state = state,
                    showArOverlay = false,
                    onArcTracked = { x, y, isTracked ->
                        viewModel.updateTrackedArc(x, y, isTracked)
                    }
                )
                // Center crosshair overlay
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2
                    val cy = size.height / 2
                    // Crosshair lines
                    drawLine(AccentCyan, Offset(cx - 30f, cy), Offset(cx + 30f, cy), strokeWidth = 1.5f)
                    drawLine(AccentCyan, Offset(cx, cy - 30f), Offset(cx, cy + 30f), strokeWidth = 1.5f)
                    // Outer ring
                    drawCircle(AccentCyan.copy(alpha = 0.4f), radius = 60f, center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
                Text(
                    text = "Align AprilTag on crosshair",
                    color = AccentCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                )
            }

            // Progression Meter Controls
            Column(
                modifier = Modifier.weight(7f).fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Center the AprilTag on the crosshair. Tap INITIATE SWEEP, then slowly tilt the phone in a cone arc to collect calibration poses.",
                    color = MutedText,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    maxLines = 3
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ContainerGrey, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pose Inversion Tracking Convergence", color = MutedText, fontSize = 10.sp)
                        Text(
                            text = "${state.calibrationProgress}%",
                            color = if (state.isCalibrated) AlertEmerald else WarningAmber,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { state.calibrationProgress / 100f },
                        color = if (state.isCalibrated) AlertEmerald else WarningAmber,
                        trackColor = BorderGrey,
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    )

                    // Calibrated offset display
                    AnimatedVisibility(visible = state.isCalibrated, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .border(0.5.dp, AlertEmerald.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("BRACKET OFFSET (t_C^T):", color = AlertEmerald, fontSize = 8.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            Text("TX: ${String.format("%.1f", state.calibrationOffsetX)} mm", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("TY: ${String.format("%.1f", state.calibrationOffsetY)} mm", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("TZ: ${String.format("%.1f", state.calibrationOffsetZ)} mm", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("Calibration locked. Recalibrate if mount shifts.", color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                // Calibrate / Recalibrate button
                Button(
                    onClick = { viewModel.runCalibration() },
                    enabled = !state.isCalibrating,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isCalibrated) AccentCyan.copy(alpha = 0.7f) else AccentCyan,
                        disabledContainerColor = ContainerGrey
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = if (state.isCalibrating) MutedText else OnPrimary)
                        Text(
                            if (state.isCalibrated) "RECALIBRATE" else "INITIATE SWEEP ROUTINE",
                            color = if (state.isCalibrating) MutedText else OnPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp
                        )
                    }
                }

                // Reset calibration button (only when calibrated)
                AnimatedVisibility(visible = state.isCalibrated, enter = fadeIn(), exit = fadeOut()) {
                    OutlinedButton(
                        onClick = { viewModel.resetCalibration() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertRed),
                        border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("RESET CALIBRATION", color = AlertRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
