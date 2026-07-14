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
fun OnboardingOverlay(
    state: WeldVisionState,
    viewModel: WeldVisionUiViewModel
) {
    if (!state.showOnboardingOverlay) return

    var currentStep by remember { mutableStateOf(0) }

    val steps = listOf(
        Triple(
            "1. Align the Camera",
            "Point your device's back camera directly at the printed AprilTag tracking grid. Align the grid boundaries inside the active viewfinder.",
            Icons.Default.GpsFixed
        ),
        Triple(
            "2. Keep Optimal Distance",
            "Maintain a steady distance of 15 to 30 cm (approx. 6–12 inches) between the torch tip and the welding plate for precise depth estimation.",
            Icons.Default.Gesture
        ),
        Triple(
            "3. Prevent Obstructions",
            "Avoid blocking or shadowing the printed AprilTag with your hands or the welding nozzle during active practice runs.",
            Icons.Default.Warning
        ),
        Triple(
            "4. Start Practice Session",
            "Hold your torch flat at a 10°–15° travel angle. Ready? Once positioned properly, begin dragging your physical torch to start!",
            Icons.Default.Bolt
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ContainerGrey),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f)),
            modifier = Modifier
                .width(420.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Page / Step indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TRACKING CALIBRATION GUIDE",
                        color = MutedText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Step ${currentStep + 1} of ${steps.size}",
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Horizontal Step progress dots
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    steps.forEachIndexed { idx, _ ->
                        Box(
                            modifier = Modifier
                                .size(width = if (idx == currentStep) 16.dp else 6.dp, height = 6.dp)
                                .clip(CircleShape)
                                .background(if (idx == currentStep) AccentCyan else BorderGrey)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Step Icon
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(AccentCyan.copy(alpha = 0.1f), CircleShape)
                        .border(1.dp, AccentCyan.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = steps[currentStep].third,
                        contentDescription = "Onboarding Icon",
                        tint = AccentCyan,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Step content
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = steps[currentStep].first,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = steps[currentStep].second,
                        color = MutedText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }

                // Custom Help / Pro-Tip Callout Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .border(1.dp, BorderGrey.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning, // Standard warning/info fallback
                            contentDescription = null,
                            tint = WarningAmber,
                            modifier = Modifier.size(14.dp)
                        )
                        Column {
                            Text(
                                text = "PRO-TIP",
                                color = WarningAmber,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = when(currentStep) {
                                    0 -> "Keep tag printed flat with no folds. Bright indoor overhead light prevents specular tag glare."
                                    1 -> "A wider distance helps the camera fit multiple tags, boosting 3D angle calculation robustness."
                                    2 -> "Wear thin gloves or slide torch on a level guide to maintain visible lines of sight."
                                    else -> "Starting a run automatically hides these prompts so you can focus completely on technique!"
                                },
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 9.sp,
                                lineHeight = 12.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Navigation Button Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { viewModel.setOnboardingOverlayVisible(false) }
                    ) {
                        Text(
                            text = "SKIP GUIDE",
                            color = MutedText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Button(
                        onClick = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                viewModel.setOnboardingOverlayVisible(false)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (currentStep == steps.size - 1) "START PRACTICE" else "NEXT STEP",
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
