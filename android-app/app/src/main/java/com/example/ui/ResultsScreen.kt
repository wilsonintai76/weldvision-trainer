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
fun ResultsScreen(
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
            Text(
                text = "SESSION EVALUATION COMPLETED",
                color = WarningAmber,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace
            )
            
            // Segmented Tab Selector for Current Run vs Past History Log
            Row(
                modifier = Modifier
                    .background(ContainerGrey, RoundedCornerShape(8.dp))
                    .border(1.dp, BorderGrey, RoundedCornerShape(8.dp))
                    .padding(2.dp)
            ) {
                listOf("CURRENT RUN DETAILS", "PAST PRACTICE HISTORY").forEachIndexed { index, label ->
                    val isSelected = state.activeResultsTab == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) AccentCyan else Color.Transparent)
                            .clickable { viewModel.setResultsTab(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) OnPrimary else MutedText,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        if (state.activeResultsTab == 0) {
            // CURRENT RUN DETAILS
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Score card badge
                Card(
                    colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxHeight(),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Overall Run Grade", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(100.dp)
                                .background(WarningAmber.copy(alpha = 0.05f), CircleShape)
                                .border(4.dp, WarningAmber, CircleShape)
                        ) {
                            Text(
                                text = "${state.lastGrade}%",
                                color = WarningAmber,
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Text("Process: ${state.currentProcess.abbrev} ${state.currentJoint.label}", color = MutedText, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Middle Technique Breakdowns
                Card(
                    colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(5f)
                        .fillMaxHeight(),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Performance Analytics", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        // Progress Bar 1
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Arc Length Stability", color = MutedText, fontSize = 9.sp)
                                Text("${state.lastArcLengthStability}%", color = AlertEmerald, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { state.lastArcLengthStability / 100f },
                                color = AlertEmerald,
                                trackColor = BorderGrey,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Progress Bar 2
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Travel Speed Uniformity", color = MutedText, fontSize = 9.sp)
                                Text("${state.lastTravelSpeedUniformity}%", color = WarningAmber, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { state.lastTravelSpeedUniformity / 100f },
                                color = WarningAmber,
                                trackColor = BorderGrey,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }

                        // Progress Bar 3
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Angle Orientations", color = MutedText, fontSize = 9.sp)
                                Text("${state.lastAngleOrientationStability}%", color = AlertEmerald, fontSize = 9.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { state.lastAngleOrientationStability / 100f },
                                color = AlertEmerald,
                                trackColor = BorderGrey,
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                            )
                        }
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().background(DeepSpaceBlue, RoundedCornerShape(8.dp)).padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Defects Detected", color = MutedText, fontSize = 8.sp)
                                Text("${state.defectCount}", color = if (state.defectCount == 0) AlertEmerald else AlertRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("Porosity Risk", color = MutedText, fontSize = 8.sp)
                                Text(state.porosityRisk, color = if (state.porosityRisk == "Low") AlertEmerald else WarningAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                
                // Right Skill Progression
                Card(
                    colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(4f)
                        .fillMaxHeight(),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Skill Progression", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(70.dp)
                                .background(AccentCyan.copy(alpha = 0.1f), CircleShape)
                                .border(2.dp, AccentCyan, CircleShape)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("LVL", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("${state.userLevel}", color = AccentCyan, fontSize = 24.sp, fontWeight = FontWeight.Black)
                            }
                        }

                        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("XP: ${state.experiencePoints}", color = AccentCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("+150 XP", color = AlertEmerald, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            LinearProgressIndicator(
                                progress = { (state.experiencePoints % 1000) / 1000f },
                                color = AccentCyan,
                                trackColor = BorderGrey,
                                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                            val remainingXp = 2000 - state.experiencePoints
                            Text(if (state.userLevel >= 4) "TIG Basics Unlocked!" else "$remainingXp XP to Level 4 (TIG Basics Unlock)", color = MutedText, fontSize = 8.sp)
                        }
                    }
                }
            }

            // Bottom AI Coaching Prompt box outputs
            Card(
                colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                        Text(
                            text = "MASTER INSTRUCTOR ASSISTANT COOPERATIVE",
                            color = AccentCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = "\"${state.lastCoachingPhrase}\"",
                        color = MutedText,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        } else {
            // PAST PRACTICE HISTORY LOG
            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.sessionHistory.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No historic practice sessions recorded yet.",
                                color = MutedText,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    items(state.sessionHistory) { session ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, BorderGrey.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(WarningAmber.copy(alpha = 0.1f), CircleShape)
                                                .border(1.dp, WarningAmber, CircleShape)
                                        ) {
                                            Text(
                                                text = "${session.grade}%",
                                                color = WarningAmber,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = "${session.process} - ${session.joint}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Material: ${session.material} | ${session.timestamp}",
                                                color = MutedText,
                                                fontSize = 9.sp
                                            )
                                        }
                                    }
                                    
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (session.defectCount == 0) AlertEmerald.copy(alpha = 0.1f) else AlertRed.copy(alpha = 0.1f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(1.dp, if (session.defectCount == 0) AlertEmerald.copy(alpha = 0.4f) else AlertRed.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (session.defectCount == 0) "PASSED" else "DEFECTS: ${session.defectCount}",
                                                color = if (session.defectCount == 0) AlertEmerald else AlertRed,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (session.porosityRisk.startsWith("Low")) AlertEmerald.copy(alpha = 0.1f) else WarningAmber.copy(alpha = 0.1f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(1.dp, if (session.porosityRisk.startsWith("Low")) AlertEmerald.copy(alpha = 0.4f) else WarningAmber.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "POROSITY: ${session.porosityRisk.uppercase()}",
                                                color = if (session.porosityRisk.startsWith("Low")) AlertEmerald else WarningAmber,
                                                fontSize = 7.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                                
                                // Stability Metrics Progress Displays
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Arc Stability", color = MutedText, fontSize = 8.sp)
                                            Text("${session.arcLengthStability}%", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        LinearProgressIndicator(
                                            progress = { session.arcLengthStability / 100f },
                                            color = AlertEmerald,
                                            trackColor = BorderGrey,
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Speed Uniformity", color = MutedText, fontSize = 8.sp)
                                            Text("${session.travelSpeedUniformity}%", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        LinearProgressIndicator(
                                            progress = { session.travelSpeedUniformity / 100f },
                                            color = WarningAmber,
                                            trackColor = BorderGrey,
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Angles Orientation", color = MutedText, fontSize = 8.sp)
                                            Text("${session.angleOrientationStability}%", color = Color.White, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        LinearProgressIndicator(
                                            progress = { session.angleOrientationStability / 100f },
                                            color = AlertEmerald,
                                            trackColor = BorderGrey,
                                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                        )
                                    }
                                }
                                
                                Text(
                                    text = "\"${session.coachingPhrase}\"",
                                    color = MutedText,
                                    fontSize = 9.sp,
                                    lineHeight = 12.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Retry Action Triggers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { viewModel.resetSession() },
                colors = ButtonDefaults.buttonColors(containerColor = WarningAmber),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(40.dp)
            ) {
                Text("RETRY PRACTICE SESSION", color = OnPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}
