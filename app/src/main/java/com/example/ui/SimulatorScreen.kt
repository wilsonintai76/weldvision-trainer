package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
fun SimulatorScreen(state: WeldVisionState, viewModel: WeldVisionUiViewModel) {
    WeldVoiceFeedbackHandler(state = state)
    WeldHapticFeedbackHandler(state = state)
    var showModulesOverlay by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. Real-time Camera Feed (replacing the simulated drawing)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(24.dp))
                .border(2.dp, BorderGrey, RoundedCornerShape(24.dp))
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        if (state.isInstructorJoined) {
                            viewModel.addInstructorAnnotation(offset)
                        }
                    }
                }
        ) {
            CameraPreviewView(
                modifier = Modifier.fillMaxSize(),
                state = state,
                onArcTracked = { x, y, isTracked ->
                    viewModel.updateTrackedArc(x, y, isTracked)
                },
                onTagPose = { tagPos ->
                    viewModel.updateTagPose(tagPos)
                }
            )

            // ── Real-time weld bead overlay (disabled during debug) ──
            // if (state.isPracticeRunActive && !state.isSimulationPaused) {
            //     BeadOverlay(modifier = Modifier.fillMaxSize().padding(16.dp), grid = viewModel.beadGrid)
            // }
        }

        // 2. HUD: Battery Level Indicator (Top Right Corner)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp)
        ) {
            BatteryIndicator(level = state.batteryLevel)
        }

        // 2. Overlay Layer: Telemetry display HUD
        // Instructor Annotations Drawing Layer
        if (state.isInstructorJoined) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                state.instructorAnnotations.forEach { offset ->
                    drawCircle(
                        color = AccentCyan.copy(alpha = 0.5f),
                        radius = 20f,
                        center = offset
                    )
                    drawCircle(
                        color = AccentCyan,
                        radius = 4f,
                        center = offset
                    )
                }
            }
        }

        // ── Weld Path Progress Overlay (real AprilTag-based travel tracking) ──
        AnimatedVisibility(
            visible = state.isPracticeRunActive && !state.isSimulationPaused,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(4.dp)
                        .background(ContainerGrey.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.travelProgress.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(
                                if (state.tagVisible) AccentCyan else WarningAmber,
                                RoundedCornerShape(2.dp)
                            )
                    )
                }
                // Travel stats
                Row(
                    modifier = Modifier.padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (state.tagVisible) "TRAVEL ${(state.travelProgress * 100).toInt()}%" else "TAG LOST",
                        color = if (state.tagVisible) AccentCyan else WarningAmber,
                        fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                    )
                    if (state.tagVisible) {
                        Text(
                            "${String.format("%.0f", state.travelDistanceMm)}mm",
                            color = Color.White.copy(alpha = 0.7f), fontSize = 7.sp, fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "${String.format("%.1f", state.trackerTravelSpeed)}mm/s",
                            color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace
                        )
                    }
                    if (!state.isOnPath && state.tagVisible) {
                        Text("OFF-PATH", color = AlertRed, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        // Telemetry HUD — hidden in minimal mode unless alert
        val showTelemetry = !state.isMinimalHud || !(state.gyroTravelAngle in 5f..15f && state.gyroWorkAngle in 85f..95f)
        AnimatedVisibility(
            visible = showTelemetry,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
        ) {
        Row(
            modifier = Modifier
                .background(ContainerGrey.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .border(1.dp, BorderGrey.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Derive travel speed from gyro angular velocity (°/s → mm/s)
            val estTravelSpeed = (state.gyroAngularSpeed / 2f).coerceIn(0.5f, 10f)
            val isSpeedOk = estTravelSpeed in (state.targetSpeed - 1.5f)..(state.targetSpeed + 1.5f)
            val isTravelOk = state.gyroTravelAngle in 5f..15f
            val isWorkOk = state.gyroWorkAngle in 85f..95f
            val gapFromShake = (3f + state.gyroAngularSpeed / 20f).coerceIn(1.5f, 6f)
            val isGapOk = gapFromShake in (state.targetGap - 1f)..(state.targetGap + 1f)

            // Speed — derived from phone movement (°/s → mm/s)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPEED", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${String.format("%.1f", estTravelSpeed)}mm/s", color = if (isSpeedOk) AlertEmerald else AlertRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            // Travel angle — torch angle relative to travel direction
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TR. ANGLE", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${String.format("%.1f", state.gyroTravelAngle)}°", color = if (isTravelOk) AlertEmerald else AlertRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            // Work angle — torch angle relative to workpiece
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("WORK ANGLE", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${String.format("%.1f", state.gyroWorkAngle)}°", color = if (isWorkOk) AlertEmerald else AlertRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            // Arc gap — derived from gyro stability (shaky = wide gap)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ARC GAP", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                Text("${String.format("%.1f", gapFromShake)}mm", color = if (isGapOk) AlertEmerald else AlertRed, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            // Gyro reference
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    if (state.isGyroActive) Box(Modifier.size(5.dp).background(AlertEmerald, CircleShape))
                    Text("GYRO", color = if (state.isGyroActive) AlertEmerald else MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }
                Text("${state.gyroAngularSpeed.toInt()}°/s", color = WarningAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
            
            // Voice feedback quick toggle button (Non-essential: Dimmed under Power Save Mode)
            IconButton(
                onClick = { viewModel.toggleVoiceFeedback() },
                modifier = Modifier
                    .size(28.dp)
                    .alpha(if (state.isPowerSaveEnabled) 0.35f else 1.0f)
            ) {
                Icon(
                    imageVector = if (state.isVoiceFeedbackEnabled) Icons.AutoMirrored.Filled.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = "Voice Feedback Toggle",
                    tint = if (state.isVoiceFeedbackEnabled) WarningAmber else MutedText,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Haptic feedback quick toggle button (Non-essential: Dimmed under Power Save Mode)
            IconButton(
                onClick = { viewModel.toggleHapticFeedback() },
                modifier = Modifier
                    .size(28.dp)
                    .alpha(if (state.isPowerSaveEnabled) 0.35f else 1.0f)
            ) {
                Icon(
                    imageVector = Icons.Default.Vibration,
                    contentDescription = "Haptic Feedback Toggle",
                    tint = if (state.isHapticFeedbackEnabled) WarningAmber else MutedText,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Quick Access Onboarding Guide Help
            IconButton(
                onClick = { viewModel.setOnboardingOverlayVisible(true) },
                modifier = Modifier
                    .size(28.dp)
                    .alpha(if (state.isPowerSaveEnabled) 0.35f else 1.0f)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Positioning Help Guide",
                    tint = AccentCyan,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Collab toggle inline (Non-essential: Dimmed under Power Save Mode)
            Row(
                modifier = Modifier
                    .background(Color.Transparent, RoundedCornerShape(8.dp))
                    .border(1.dp, if (state.isInstructorJoined) AccentCyan else BorderGrey, RoundedCornerShape(8.dp))
                    .clickable { viewModel.toggleInstructor() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .alpha(if (state.isPowerSaveEnabled) 0.35f else 1.0f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.People, 
                    contentDescription = "Collab", 
                    tint = if (state.isInstructorJoined) AccentCyan else MutedText,
                    modifier = Modifier.size(12.dp)
                )
                Text(if (state.isInstructorJoined) "LIVE" else "INVITE", color = if (state.isInstructorJoined) Color.White else MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }

            // Minimal HUD toggle (eye icon)
            IconButton(
                onClick = { viewModel.toggleMinimalHud() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (state.isMinimalHud) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Toggle HUD",
                    tint = if (state.isMinimalHud) MutedText else AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Weld mode toggle (straight ↔ weaving)
            IconButton(
                onClick = { viewModel.toggleWeldMode() },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (state.weldMode == WeldMode.WEAVING) Icons.Default.Timeline else Icons.Default.ShowChart,
                    contentDescription = "Toggle Weld Mode",
                    tint = if (state.weldMode == WeldMode.WEAVING) AccentCyan else MutedText,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        } // End AnimatedVisibility

        // ── Weave Metrics Bar (visible when weaving + practice run active) ──
        AnimatedVisibility(
            visible = state.weldMode == WeldMode.WEAVING && state.isPracticeRunActive && !state.isSimulationPaused,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp)
        ) {
            val weaveTarget = WeaveTargets.byId(state.activeWeaveTargetId)
            val isPassing = state.weaveQualityScore >= (weaveTarget?.minQualityScore ?: 55f)
            Row(
                modifier = Modifier
                    .background(ContainerGrey.copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                    .border(1.dp, if (isPassing) AlertEmerald.copy(alpha = 0.6f) else AlertRed.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pattern type badge
                Text(state.weavePatternType, color = AccentCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                // Weave quality ring
                Text("Q:${state.weaveQualityScore.toInt()}%", color = if (isPassing) AlertEmerald else AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                // Width
                Text("W:${String.format("%.1f", state.weaveWidthDeg)}°", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                // Frequency
                Text("F:${String.format("%.1f", state.weaveFrequencyHz)}Hz", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                // Symmetry
                Text("S:${(state.weaveSymmetry * 100).toInt()}%", color = Color.White.copy(alpha = 0.8f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                // Target
                if (weaveTarget != null) {
                    Text("→${weaveTarget.name}", color = MutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        // ── Weave Target Selector (visible when weaving + idle) ──
        AnimatedVisibility(
            visible = state.weldMode == WeldMode.WEAVING && !state.isPracticeRunActive,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 68.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(ContainerGrey.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("TARGET:", color = MutedText, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                WeaveTargets.all().forEach { target ->
                    val isSelected = state.activeWeaveTargetId == target.id
                    Text(
                        target.name,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AccentCyan.copy(alpha = 0.25f) else Color.Transparent)
                            .border(0.5.dp, if (isSelected) AccentCyan else BorderGrey, RoundedCornerShape(6.dp))
                            .clickable { viewModel.selectWeaveTarget(target.id) }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        color = if (isSelected) AccentCyan else Color.White.copy(alpha = 0.7f),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Voice & Haptic Warnings HUD (Bottom)
        val isGapOk = state.arcLength in (state.targetGap - 1.0f)..(state.targetGap + 1.0f)
        val isSpeedOk = state.travelSpeed in (state.targetSpeed - 1.0f)..(state.targetSpeed + 1.0f)
        val isTravelOk = state.travelAngle in 5..15
        val isWorkOk = state.workAngle in 85..95
        val hasAlert = !isGapOk || !isSpeedOk || !isTravelOk || !isWorkOk

        AnimatedVisibility(
            visible = hasAlert,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 64.dp)
        ) {
            Row(
                modifier = Modifier
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .background(Color(0xFF370001), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFFFDAD6), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (!isSpeedOk || !isTravelOk || !isWorkOk) Icons.Default.Vibration else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = Color(0xFFFFB4AB),
                    modifier = Modifier.size(20.dp)
                )
                Column {
                    Text(
                        text = when {
                            state.arcLength > state.targetGap + 1.0f -> "TIGHTEN ARC!"
                            state.arcLength < state.targetGap - 1.0f -> "WIDEN ARC!"
                            state.travelSpeed > state.targetSpeed + 1.0f -> "SLOW DOWN!"
                            state.travelSpeed < state.targetSpeed - 1.0f -> "SPEED UP!"
                            state.travelAngle > 15 -> "REDUCE TRAVEL ANGLE!"
                            state.travelAngle < 5 -> "INCREASE TRAVEL ANGLE!"
                            state.workAngle > 95 -> "CORRECT WORK ANGLE (TILT LEFT)!"
                            state.workAngle < 85 -> "CORRECT WORK ANGLE (TILT RIGHT)!"
                            else -> "CORRECT TECHNIQUE"
                        },
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = when {
                            !state.isHapticFeedbackEnabled -> "Haptics muted"
                            !isSpeedOk -> "Haptic feedback pulsing: SPEED DEVIATION"
                            !isTravelOk || !isWorkOk -> "Haptic feedback pulsing: ANGLE DEVIATION"
                            else -> "Haptic controller active"
                        },
                        color = Color(0xFFFFB4AB),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 2a. Real-time Practice Run Coroutine Simulation Loop
        if (state.isPracticeRunActive && !state.isSimulationPaused) {
            LaunchedEffect(state.weldProgress) {
                kotlinx.coroutines.delay(120)
                
                // ── Live scoring: angle STABILITY is key, not absolute position ──
                val baseScore = 100

                // Gap: derived from gyro shake (stable phone = tight consistent gap)
                val estGap = (3f + state.gyroAngularSpeed / 20f).coerceIn(1.5f, 6f)
                val gapError = kotlin.math.abs(estGap - state.targetGap).coerceIn(0f, 5f)

                // Speed: prefer real AprilTag travel speed, fall back to gyro-derived
                val estSpeed = if (state.tagVisible && state.trackerTravelSpeed > 0.3f) {
                    state.trackerTravelSpeed.coerceIn(0.5f, 15f)
                } else {
                    (state.gyroAngularSpeed / 2f).coerceIn(0.5f, 10f)
                }
                val speedError = kotlin.math.abs(estSpeed - state.targetSpeed).coerceIn(0f, 8f)

                // Travel angle stability: penalty for DEVIATING from your own running average
                val travelAvg = if (state.travelAngleCount > 0)
                    state.travelAngleSum / state.travelAngleCount else state.gyroTravelAngle
                val travelVariance = kotlin.math.abs(state.gyroTravelAngle - travelAvg)
                val travelError = travelVariance.coerceIn(0f, 10f)

                // Work angle stability: penalty for DEVIATING from your own running average  
                val workAvg = if (state.workAngleCount > 0)
                    state.workAngleSum / state.workAngleCount else state.gyroWorkAngle
                val workVariance = kotlin.math.abs(state.gyroWorkAngle - workAvg)
                val workError = workVariance.coerceIn(0f, 10f)

                val instPenalty = (gapError * 10f + speedError * 8f + travelError * 4f + workError * 4f).toInt()
                var tickScore = (baseScore - instPenalty).coerceIn(20, 100)

                // Weave mode: blend in weave quality score
                if (state.weldMode == WeldMode.WEAVING && state.weaveQualityScore > 0f) {
                    tickScore = ((tickScore * 0.4f) + (state.weaveQualityScore * 0.6f)).toInt().coerceIn(20, 100)
                }

                // Update running angle stats
                viewModel.updateAngleStats(state.gyroTravelAngle, state.gyroWorkAngle)

                viewModel.addPracticeScoreTick(tickScore)

                // ── Bead physics: simulate weld bead growth ──
                val torchX = if (state.tagVisible) state.tagTxMm else (state.weldProgress / 100f) * viewModel.beadGrid.physicalWidthMm
                val torchZ = if (state.tagVisible) state.tagTzMm else 0f
                viewModel.beadPhysics.tick(torchX, torchZ, state.gyroWorkAngle, estSpeed)
                
                // Advance progress: use real AprilTag position when visible, timer otherwise
                val nextProg = if (state.tagVisible) {
                    (state.travelProgress * 100f).coerceAtLeast(state.weldProgress + 0.1f)
                } else {
                    state.weldProgress + 1f  // fallback timer when tag lost
                }
                if (nextProg >= 100f) {
                    val finalAvgGrade = if (state.scoreTicks > 0) state.scoreSum / state.scoreTicks else 50

                    // Stability metrics from live gyro data
                    val gyroVariation = state.gyroAngularSpeed.coerceIn(0f, 50f)
                    val travelDev = kotlin.math.abs(state.gyroTravelAngle - 10f).coerceIn(0f, 20f)
                    val workDev = kotlin.math.abs(state.gyroWorkAngle - 90f).coerceIn(0f, 20f)
                    val speedDev = kotlin.math.abs(estSpeed - state.targetSpeed)

                    val arcLengthStability = (100 - gyroVariation * 2f).toInt().coerceIn(30, 100)
                    val travelSpeedUniformity = (100 - speedDev * 8f).toInt().coerceIn(30, 100)
                    val angleOrientationStability = (100 - (travelDev + workDev) * 2.5f).toInt().coerceIn(30, 100)
                    
                    val defCount = if (finalAvgGrade < 70) 2 else if (finalAvgGrade < 84) 1 else 0
                    val isWindy = state.currentEnvironment == EnvironmentFactor.WINDY
                    val porosity = when {
                        isWindy -> "High (Gas Shield Blown)"
                        finalAvgGrade < 72 -> "High"
                        finalAvgGrade < 85 -> "Medium"
                        else -> "Low"
                    }
                    
                    val coachMsg = when {
                        finalAvgGrade >= 85 -> "Outstanding visual consistency! Your arc gap stability (${arcLengthStability}%) and speed uniformity (${travelSpeedUniformity}%) were exceptionally high, forming a pristine, uniform bead profile."
                        finalAvgGrade >= 70 -> "Solid fillet weld bead. You maintained good alignment, but travel speed uniformity (${travelSpeedUniformity}%) had minor spikes. Practice sliding without wrist adjustments."
                        else -> "Weld profile needs improvement. Significant deviations in arc gap and travel speeds led to heat build-up. Maintain a steady 15-30cm distance and guide the torch evenly."
                    }
                    
                    viewModel.completePracticeRun(
                        avgGrade = finalAvgGrade,
                        arcLengthStability = arcLengthStability,
                        travelSpeedUniformity = travelSpeedUniformity,
                        angleOrientationStability = angleOrientationStability,
                        defectCount = defCount,
                        porosityRisk = porosity,
                        coachingPhrase = coachMsg
                    )
                } else {
                    viewModel.updatePracticeProgress(nextProg)
                }
            }
        }

        // 2b. Glowing electric arc simulator visual effect
        if (state.isPracticeRunActive && !state.isSimulationPaused) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(24.dp))
            ) {
                val progressFraction = state.weldProgress / 100f
                val paddingX = 80.dp.toPx()
                val totalWidth = size.width - (paddingX * 2)
                
                val arcX = paddingX + (totalWidth * progressFraction)
                val arcY = size.height * 0.72f
                
                val pulseScale = 1.0f + 0.15f * kotlin.math.sin(state.weldProgress * 1.5f)
                
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFFFFFF),
                            Color(0xFFFFEA7A).copy(alpha = 0.85f),
                            Color(0xFFFF9800).copy(alpha = 0.45f),
                            Color.Transparent
                        ),
                        center = Offset(arcX, arcY),
                        radius = 48.dp.toPx() * pulseScale
                    ),
                    center = Offset(arcX, arcY),
                    radius = 48.dp.toPx() * pulseScale
                )
                
                drawCircle(
                    color = Color.White,
                    radius = 10.dp.toPx() * pulseScale,
                    center = Offset(arcX, arcY)
                )
                
                val random = java.util.Random((state.weldProgress * 1000).toLong())
                for (i in 0..7) {
                    val angle = random.nextFloat() * 2 * kotlin.math.PI
                    val distance = (12 + random.nextInt(36)).dp.toPx()
                    val sparkX = arcX + (distance * kotlin.math.cos(angle)).toFloat()
                    val sparkY = arcY + (distance * kotlin.math.sin(angle)).toFloat()
                    
                    drawCircle(
                        color = Color(0xFFFFB300),
                        radius = 1.8.dp.toPx(),
                        center = Offset(sparkX, sparkY)
                    )
                }
            }
        }

        // 2c. Compact Practice Run Controller (pill design)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
                .background(ContainerGrey.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                .border(0.5.dp, if (state.isPracticeRunActive) AccentCyan.copy(alpha = 0.4f) else BorderGrey.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            if (!state.isPracticeRunActive) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.startPracticeRun() },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = OnPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("START WELD", color = OnPrimary, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                    Text("Tap to begin recording", color = MutedText, fontSize = 9.sp)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Pulsing red dot + REC
                    Box(modifier = Modifier.size(7.dp).background(AlertRed, CircleShape))
                    Text("REC", color = AlertRed, fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    // Thin progress indicator
                    Box(Modifier.width(80.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(BorderGrey)) {
                        Box(Modifier.fillMaxWidth(state.weldProgress / 100f).height(3.dp).background(AccentCyan))
                    }
                    val instScore = if (state.scoreTicks > 0) state.scoreSum / state.scoreTicks else 90
                    Text("${state.weldProgress.toInt()}%", color = MutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    Text("Score: $instScore%", color = if (instScore >= 80) AlertEmerald else if (instScore >= 70) WarningAmber else AlertRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { viewModel.cancelPracticeRun() }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, "Abort", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // 3. FULLSCREEN PAUSE OVERLAY (When Proximity Sensor detects user is too far)
        AnimatedVisibility(
            visible = state.isSimulationPaused,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {}, // Prevent interaction behind the overlay
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .width(420.dp)
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .background(AlertRed.copy(alpha = 0.1f), CircleShape)
                                .border(1.dp, AlertRed.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Warning",
                                tint = AlertRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "SESSION AUTOMATICALLY PAUSED",
                                color = AlertRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Head / device moved too far from simulated weld point.",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Move your head closer to the screen / weld line to automatically resume practice.",
                                color = MutedText,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        if (state.isProximitySimulated) {
                            Button(
                                onClick = { viewModel.setProximityFar(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertEmerald),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "SIMULATE: MOVE HEAD CLOSE",
                                    color = OnPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- INTERACTIVE TRAINING MODULES OVERLAY & FLOATING SELECTOR ---
        
        // Floating Vertical Tab to open menu
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        ) {
            if (!showModulesOverlay) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                        .background(ContainerGrey.copy(alpha = 0.92f))
                        .border(
                            BorderStroke(1.dp, Brush.horizontalGradient(listOf(AccentCyan, Color.Transparent))),
                            RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        )
                        .clickable { showModulesOverlay = true }
                        .padding(horizontal = 10.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Open Modules",
                        tint = AccentCyan,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "MODULES",
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                    
                    val currentModule = preDefinedModules.firstOrNull { it.id == state.selectedModuleId }
                    if (currentModule != null) {
                        Box(
                            modifier = Modifier
                                .background(AlertEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = currentModule.process.abbrev,
                                color = AlertEmerald,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }

        // Sliding Panel Drawer from Left
        AnimatedVisibility(
            visible = showModulesOverlay,
            enter = androidx.compose.animation.slideInHorizontally(
                initialOffsetX = { -it }
            ) + fadeIn(),
            exit = androidx.compose.animation.slideOutHorizontally(
                targetOffsetX = { -it }
            ) + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(340.dp)
                .padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = ContainerGrey.copy(alpha = 0.96f)),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, AccentCyan),
                modifier = Modifier
                    .fillMaxHeight()
                    .shadow(12.dp, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Header Area
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = "TRAINING PATHWAYS",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "Tap a pathway to load target guide specs",
                                    color = MutedText,
                                    fontSize = 8.sp
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = { showModulesOverlay = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close overlay",
                                tint = MutedText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Divider(color = BorderGrey.copy(alpha = 0.5f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Scrollable List
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        preDefinedModules.forEach { module ->
                            val isActive = module.id == state.selectedModuleId
                            val isLocked = state.userLevel < module.levelRequired
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isActive) AccentCyan.copy(alpha = 0.08f)
                                        else Color.Black.copy(alpha = 0.4f)
                                    )
                                    .border(
                                        width = if (isActive) 1.5.dp else 1.dp,
                                        color = when {
                                            isActive -> AlertEmerald
                                            isLocked -> BorderGrey.copy(alpha = 0.4f)
                                            else -> BorderGrey
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.selectTrainingModule(module.id)
                                    }
                                    .padding(10.dp)
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = module.title,
                                            color = if (isActive) AccentCyan else Color.White,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.weight(1f)
                                        )
                                        
                                        when {
                                            isActive -> {
                                                Box(
                                                    modifier = Modifier
                                                        .background(AlertEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Box(modifier = Modifier.size(4.dp).background(AlertEmerald, CircleShape))
                                                        Text("ACTIVE", color = AlertEmerald, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                            }
                                            isLocked -> {
                                                Box(
                                                    modifier = Modifier
                                                        .background(Color(0xFF332000), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                                    ) {
                                                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(8.dp))
                                                        Text("LVL ${module.levelRequired}", color = Color(0xFFFFB300), fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                    }
                                                }
                                            }
                                            else -> {
                                                Box(
                                                    modifier = Modifier
                                                        .background(BorderGrey.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                                ) {
                                                    Text("READY", color = MutedText, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                                }
                                            }
                                        }
                                    }
                                    
                                    Text(
                                        text = module.description,
                                        color = MutedText.copy(alpha = 0.85f),
                                        fontSize = 8.sp,
                                        lineHeight = 11.sp
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        ModuleSpecBadge(label = module.process.abbrev, value = "")
                                        ModuleSpecBadge(label = "Gap", value = "${module.targetGap}mm")
                                        ModuleSpecBadge(label = "Speed", value = "${module.targetSpeed}mm/s")
                                        ModuleSpecBadge(label = "Joint", value = module.joint.name)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. STEP-BY-STEP ONBOARDING TOOLTIP OVERLAY (Before practice starts)
        OnboardingOverlay(state = state, viewModel = viewModel)
    }
}

// ── Helper composables ──

@Composable
fun WeldVoiceFeedbackHandler(state: WeldVisionState) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        tts = TextToSpeech(context) { status -> if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US }
        onDispose { tts?.shutdown() }
    }
    LaunchedEffect(state.arcLength, state.travelSpeed, state.travelAngle, state.workAngle) {
        if (!state.isVoiceFeedbackEnabled || !state.isPracticeRunActive || state.isSimulationPaused) return@LaunchedEffect
        val msg = when {
            state.arcLength > state.targetGap + 1.0f -> "Tighten arc"
            state.arcLength < state.targetGap - 1.0f -> "Widen arc"
            state.travelSpeed > state.targetSpeed + 1.0f -> "Slow down"
            state.travelSpeed < state.targetSpeed - 1.0f -> "Speed up"
            state.travelAngle > 15 -> "Reduce travel angle"
            state.travelAngle < 5 -> "Increase travel angle"
            state.workAngle > 95 -> "Tilt left"
            state.workAngle < 85 -> "Tilt right"
            else -> null
        }
        msg?.let { tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "weld_feedback") }
    }
}

@Composable
fun WeldHapticFeedbackHandler(state: WeldVisionState) {
    val context = LocalContext.current
    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    LaunchedEffect(state.travelSpeed, state.travelAngle, state.workAngle, state.weaveQualityScore, state.weldMode) {
        if (!state.isHapticFeedbackEnabled || !state.isPracticeRunActive || state.isSimulationPaused) return@LaunchedEffect
        if (vibrator?.hasVibrator() != true) return@LaunchedEffect

        // Weave mode: rhythmic haptic pulse at weave frequency
        if (state.weldMode == WeldMode.WEAVING && state.weaveFrequencyHz > 0f) {
            val pulseIntervalMs = (1000f / state.weaveFrequencyHz).toLong().coerceIn(200, 1000)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(40)
            }
            return@LaunchedEffect
        }

        // Standard haptic for issues
        val hasIssue = state.travelSpeed !in (state.targetSpeed - 1.0f)..(state.targetSpeed + 1.0f) ||
                       state.travelAngle !in 5..15 || state.workAngle !in 85..95
        if (hasIssue) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(80)
            }
        }
    }
}

@Composable
fun BatteryIndicator(level: Float) {
    val color = when { level > 50f -> AlertEmerald; level > 20f -> WarningAmber; else -> AlertRed }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Icon(Icons.Default.BatterySaver, null, tint = color, modifier = Modifier.size(12.dp))
        Text("${level.toInt()}%", color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun ModuleSpecBadge(label: String, value: String) {
    Box(Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(if (value.isNotEmpty()) "$label: $value" else label, color = MutedText, fontSize = 7.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

