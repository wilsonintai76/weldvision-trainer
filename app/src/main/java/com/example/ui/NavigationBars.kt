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
fun LandscapeTopStatusBar(state: WeldVisionState, onPowerSaveToggle: () -> Unit) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepSpaceBlue)
            .padding(start = 16.dp, end = 16.dp, top = statusBarHeight + 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        if (state.isPowerSaveEnabled) WarningAmber.copy(alpha = 0.1f) else WarningAmber.copy(alpha = 0.2f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = state.currentProcess.abbrev,
                    color = if (state.isPowerSaveEnabled) WarningAmber.copy(alpha = 0.6f) else WarningAmber,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                text = "Spec: Lap-Joint Fillet 2G (Steel)",
                color = if (state.isPowerSaveEnabled) MutedText.copy(alpha = 0.4f) else MutedText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Power Save Mode Toggle Pill Button
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (state.isPowerSaveEnabled) AlertEmerald.copy(alpha = 0.2f) else Color.Transparent,
                        RoundedCornerShape(4.dp)
                    )
                    .border(
                        1.dp,
                        if (state.isPowerSaveEnabled) AlertEmerald else BorderGrey,
                        RoundedCornerShape(4.dp)
                    )
                    .clickable { onPowerSaveToggle() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.BatterySaver,
                    contentDescription = "Power Save Mode",
                    tint = if (state.isPowerSaveEnabled) AlertEmerald else MutedText,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = if (state.isPowerSaveEnabled) "POWER SAVE: ON" else "POWER SAVE",
                    color = if (state.isPowerSaveEnabled) Color.White else MutedText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Color.Black, RoundedCornerShape(4.dp))
                    .border(1.dp, BorderGrey, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(if (state.isBleSynced) AlertEmerald else AlertRed, CircleShape)
                )
                Text(
                    text = if (state.isBleSynced) "TORCH SYNCED" else "NO SIGNAL",
                    color = if (state.isPowerSaveEnabled) MutedText.copy(alpha = 0.5f) else MutedText,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "13:37",
                color = if (state.isPowerSaveEnabled) MutedText.copy(alpha = 0.4f) else MutedText,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AndroidBottomNavBar(
    currentScreen: AppScreen,
    isPowerSaveEnabled: Boolean = false,
    onNavigate: (AppScreen) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(DeepSpaceBlue)
            .border(1.dp, BorderGrey)
            .padding(horizontal = 24.dp)
            .alpha(if (isPowerSaveEnabled) 0.45f else 1.0f),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BottomTabItem(
            icon = Icons.Default.GpsFixed,
            label = "Calibrate",
            isSelected = currentScreen == AppScreen.CALIBRATE,
            onClick = { onNavigate(AppScreen.CALIBRATE) }
        )

        BottomTabItem(
            icon = Icons.Default.Bolt,
            label = "Run Simulator",
            isSelected = currentScreen == AppScreen.SIMULATOR,
            onClick = { onNavigate(AppScreen.SIMULATOR) }
        )

        BottomTabItem(
            icon = Icons.Default.Settings,
            label = "Machine Specs",
            isSelected = currentScreen == AppScreen.SETTINGS,
            onClick = { onNavigate(AppScreen.SETTINGS) }
        )

        BottomTabItem(
            icon = Icons.Default.Assessment,
            label = "Results",
            isSelected = currentScreen == AppScreen.RESULTS,
            onClick = { onNavigate(AppScreen.RESULTS) }
        )

        BottomTabItem(
            icon = Icons.Default.AccountCircle,
            label = "Profile",
            isSelected = currentScreen == AppScreen.PROFILE,
            onClick = { onNavigate(AppScreen.PROFILE) }
        )
    }
}

@Composable
fun BottomTabItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = WarningAmber
    val inactiveColor = MutedText

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) activeColor else inactiveColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label.uppercase(),
            color = if (isSelected) activeColor else inactiveColor,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
    }
}

// ============================================================================
// 4. WORKSPACE SCREENS (Calibration & Settings modules)
// ============================================================================
