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
fun ProfileScreen(
    state: WeldVisionState,
    viewModel: WeldVisionUiViewModel
) {
    var activeProfileTab by remember { mutableStateOf(0) } // 0 = Trends, 1 = Achievements

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpaceBlue)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Panel: Identity & Level Profile Card
        Card(
            modifier = Modifier
                .width(280.dp)
                .fillMaxHeight(),
            colors = CardDefaults.cardColors(containerColor = ContainerGrey),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title
                Text(
                    text = "OPERATOR IDENTITY PROFILE",
                    color = MutedText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Avatar Placeholder Icon
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(DeepSpaceBlue)
                        .border(1.5.dp, WarningAmber, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Avatar",
                        tint = WarningAmber,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Name and Editing Field
                if (state.isEditingName) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        OutlinedTextField(
                            value = state.editedName,
                            onValueChange = { viewModel.updateEditedName(it) },
                            textStyle = LocalTextStyle.current.copy(
                                fontSize = 13.sp,
                                color = Color.White,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = BorderGrey,
                                focusedContainerColor = DeepSpaceBlue,
                                unfocusedContainerColor = DeepSpaceBlue
                            ),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextButton(onClick = { viewModel.cancelEditingName() }) {
                                Text("CANCEL", color = AlertRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                            Button(
                                onClick = { viewModel.saveProfileName() },
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                                shape = RoundedCornerShape(4.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("SAVE", color = DeepSpaceBlue, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.profileName.uppercase(),
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { viewModel.startEditingName() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Name",
                                tint = AccentCyan,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Divider(color = BorderGrey, thickness = 0.5.dp)

                // Level Progress Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LEVEL ${state.userLevel}", color = WarningAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("${state.experiencePoints} XP", color = MutedText, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    }

                    val progress = remember(state.experiencePoints) {
                        val base = 1000
                        val target = 2000
                        if (state.experiencePoints >= target) 1.0f 
                        else ((state.experiencePoints - base).coerceAtLeast(0).toFloat() / (target - base).toFloat()).coerceIn(0f, 1f)
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = WarningAmber,
                        trackColor = DeepSpaceBlue
                    )
                    Text(
                        text = "Next level at 2,000 XP (+150 XP per Practice)",
                        color = MutedText,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                val totalSeconds = state.gmawWeldTime + state.gtawWeldTime + state.smawWeldTime
                val formattedTotalTime = remember(totalSeconds) {
                    val h = totalSeconds / 3600
                    val m = (totalSeconds % 3600) / 60
                    val s = totalSeconds % 60
                    if (h > 0) "${h}h ${m}m" else "${m}m ${s}s"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSpaceBlue),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timeline,
                            contentDescription = "Total Practice",
                            tint = AlertEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text("ACCUMULATED WELD TIME", color = MutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text(formattedTotalTime, color = AlertEmerald, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        // Right Panel: Process Performance Trends or Achievements & Badges
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tab Switcher Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(ContainerGrey)
                        .border(1.dp, BorderGrey, RoundedCornerShape(6.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (activeProfileTab == 0) DeepSpaceBlue else Color.Transparent)
                            .border(1.dp, if (activeProfileTab == 0) BorderGrey else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { activeProfileTab = 0 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "PERFORMANCE TRENDS",
                            color = if (activeProfileTab == 0) WarningAmber else MutedText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (activeProfileTab == 1) DeepSpaceBlue else Color.Transparent)
                            .border(1.dp, if (activeProfileTab == 1) BorderGrey else Color.Transparent, RoundedCornerShape(4.dp))
                            .clickable { activeProfileTab = 1 }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Star",
                                tint = if (activeProfileTab == 1) WarningAmber else MutedText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "ACHIEVEMENTS & BADGES",
                                color = if (activeProfileTab == 1) WarningAmber else MutedText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }

            if (activeProfileTab == 0) {
                val processes = listOf(
                    WeldProcessDetail("GMAW", "MIG / Gas Metal Arc", state.gmawWeldTime, AlertEmerald),
                    WeldProcessDetail("GTAW", "TIG / Gas Tungsten Arc", state.gtawWeldTime, AccentCyan),
                    WeldProcessDetail("SMAW", "Stick / Shielded Metal Arc", state.smawWeldTime, WarningAmber)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    processes.forEach { processDetail ->
                        val filteredSessions = state.sessionHistory.filter { it.process == processDetail.abbrev }
                        ProcessTrendCard(
                            processDetail = processDetail,
                            sessions = filteredSessions
                        )
                    }
                }
            } else {
                AchievementsTabContent(state = state)
            }
        }
    }
}

@Composable
fun AchievementsTabContent(state: WeldVisionState) {
    val totalSeconds = state.gmawWeldTime + state.gtawWeldTime + state.smawWeldTime
    val sessionHistory = state.sessionHistory

    val maxGrade = if (sessionHistory.isEmpty()) 0 else sessionHistory.maxOf { it.grade }
    val maxArcStability = if (sessionHistory.isEmpty()) 0 else sessionHistory.maxOf { it.arcLengthStability }
    val maxSpeedUniformity = if (sessionHistory.isEmpty()) 0 else sessionHistory.maxOf { it.travelSpeedUniformity }
    val hasGmaw = sessionHistory.any { it.process == "GMAW" }
    val hasGtaw = sessionHistory.any { it.process == "GTAW" }
    val hasSmaw = sessionHistory.any { it.process == "SMAW" }

    val achievements = listOf(
        AchievementItem(
            title = "PERFECT WELD ACCURACY",
            description = "Achieve an overall grade score of 90% or higher on any practice run.",
            isUnlocked = maxGrade >= 90,
            icon = Icons.Default.Star,
            statText = "Personal Best: $maxGrade%",
            badgeColor = AlertEmerald
        ),
        AchievementItem(
            title = "VETERAN ARC OPERATOR",
            description = "Log a total of 10+ minutes (600 seconds) of physical weld time across all processes.",
            isUnlocked = totalSeconds >= 600,
            icon = Icons.Default.Schedule,
            statText = "Logged: ${totalSeconds / 60}m ${totalSeconds % 60}s / 10m",
            badgeColor = WarningAmber
        ),
        AchievementItem(
            title = "PRISTINE BEAD",
            description = "Complete any practice run with 0 detected visual or volumetric defects.",
            isUnlocked = sessionHistory.any { it.defectCount == 0 },
            icon = Icons.Default.CheckCircle,
            statText = if (sessionHistory.any { it.defectCount == 0 }) "Status: Flawless" else "Status: No defect-free run yet",
            badgeColor = AccentCyan
        ),
        AchievementItem(
            title = "PROCESS MULTI-SPECIALIST",
            description = "Complete at least one practice run with MIG (GMAW), TIG (GTAW), and Stick (SMAW).",
            isUnlocked = hasGmaw && hasGtaw && hasSmaw,
            icon = Icons.Default.Build,
            statText = "Completed: ${(if (hasGmaw) 1 else 0) + (if (hasGtaw) 1 else 0) + (if (hasSmaw) 1 else 0)}/3 Processes",
            badgeColor = Color(0xFFE57373)
        ),
        AchievementItem(
            title = "ARC LENGTH MASTER",
            description = "Achieve 90% or higher Arc Length Stability on a practice run.",
            isUnlocked = maxArcStability >= 90,
            icon = Icons.Default.FlashOn,
            statText = "Max Stability: $maxArcStability%",
            badgeColor = WarningAmber
        ),
        AchievementItem(
            title = "STEADY-HAND OPERATOR",
            description = "Achieve 85% or higher Travel Speed Uniformity on a practice run.",
            isUnlocked = maxSpeedUniformity >= 85,
            icon = Icons.Default.Speed,
            statText = "Max Uniformity: $maxSpeedUniformity%",
            badgeColor = AccentCyan
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Card with total stats
        val unlockedCount = achievements.count { it.isUnlocked }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = ContainerGrey),
            border = BorderStroke(1.dp, BorderGrey),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "ACCREDITATION MILESTONES",
                        color = WarningAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Earn badges to unlock Operator Certifications",
                        color = MutedText,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = DeepSpaceBlue),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, BorderGrey)
                ) {
                    Text(
                        text = "$unlockedCount / ${achievements.size} BADGES EARNED",
                        color = WarningAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        // Achievements Grid or list
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            achievements.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            AchievementBadgeCard(item = item)
                        }
                    }
                    if (rowItems.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

data class AchievementItem(
    val title: String,
    val description: String,
    val isUnlocked: Boolean,
    val icon: ImageVector,
    val statText: String,
    val badgeColor: Color
)

@Composable
fun AchievementBadgeCard(item: AchievementItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isUnlocked) ContainerGrey else ContainerGrey.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (item.isUnlocked) item.badgeColor.copy(alpha = 0.5f) else BorderGrey.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Visual Badge Circular Icon with lock state overlay
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isUnlocked) item.badgeColor.copy(alpha = 0.12f)
                        else Color.White.copy(alpha = 0.03f)
                    )
                    .border(
                        1.5.dp,
                        if (item.isUnlocked) item.badgeColor else BorderGrey.copy(alpha = 0.5f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (item.isUnlocked) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = "Badge",
                        tint = item.badgeColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked Badge",
                        tint = MutedText.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = if (item.isUnlocked) Color.White else MutedText.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (item.isUnlocked) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = item.badgeColor.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(2.dp)
                        ) {
                            Text(
                                text = "ACTIVE",
                                color = item.badgeColor,
                                fontSize = 7.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = item.description,
                    color = if (item.isUnlocked) MutedText else MutedText.copy(alpha = 0.4f),
                    fontSize = 8.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 11.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.statText,
                    color = if (item.isUnlocked) item.badgeColor.copy(alpha = 0.8f) else MutedText.copy(alpha = 0.3f),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

data class WeldProcessDetail(
    val abbrev: String,
    val label: String,
    val weldTimeSeconds: Int,
    val themeColor: Color
)

@Composable
fun ProcessTrendCard(
    processDetail: WeldProcessDetail,
    sessions: List<WeldSession>
) {
    val runCount = sessions.size
    val avgGrade = if (runCount > 0) sessions.map { it.grade }.average().toInt() else 0
    val avgArcStability = if (runCount > 0) sessions.map { it.arcLengthStability }.average().toInt() else 0
    val avgSpeedUniformity = if (runCount > 0) sessions.map { it.travelSpeedUniformity }.average().toInt() else 0
    val avgAngleStability = if (runCount > 0) sessions.map { it.angleOrientationStability }.average().toInt() else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = ContainerGrey),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(processDetail.themeColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Process",
                            tint = processDetail.themeColor,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Column {
                        Text(
                            text = processDetail.abbrev,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = processDetail.label,
                            color = MutedText,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (avgGrade >= 80) AlertEmerald.copy(alpha = 0.12f)
                                        else if (avgGrade >= 70) WarningAmber.copy(alpha = 0.12f)
                                        else AlertRed.copy(alpha = 0.12f)
                    ),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (runCount > 0) "$avgGrade AVG SCORE" else "NO RUNS YET",
                        color = if (avgGrade >= 80) AlertEmerald
                                else if (avgGrade >= 70) WarningAmber
                                else AlertRed,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Divider(color = BorderGrey.copy(alpha = 0.5f), thickness = 0.5.dp)

            // Stats row (Weld time and total runs)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = "Time",
                        tint = MutedText,
                        modifier = Modifier.size(13.dp)
                    )
                    Column {
                        Text("WELD TIME", color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        val m = processDetail.weldTimeSeconds / 60
                        val s = processDetail.weldTimeSeconds % 60
                        Text(
                            text = "${String.format("%02d", m)}m ${String.format("%02d", s)}s",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = "Runs",
                        tint = MutedText,
                        modifier = Modifier.size(13.dp)
                    )
                    Column {
                        Text("COMPLETED RUNS", color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        Text(
                            text = "$runCount runs",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (runCount > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MetricTrendBar("Arc Length Stability", avgArcStability, processDetail.themeColor)
                    MetricTrendBar("Travel Speed Uniformity", avgSpeedUniformity, processDetail.themeColor)
                    MetricTrendBar("Angle Orientation Stability", avgAngleStability, processDetail.themeColor)
                }
            }
        }
    }
}

@Composable
fun MetricTrendBar(
    label: String,
    value: Int,
    barColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = MutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            Text("$value%", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
        LinearProgressIndicator(
            progress = { value.toFloat() / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = DeepSpaceBlue
        )
    }
}
