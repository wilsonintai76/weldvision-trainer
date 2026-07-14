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
fun WeldVisionLandscapeApp(
    viewModel: WeldVisionUiViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    val context = LocalContext.current
    DisposableEffect(context, state.isBatterySimulated) {
        if (!state.isBatterySimulated) {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(null, filter)
            if (stickyIntent != null) {
                val level = stickyIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = stickyIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    val pct = (level.toFloat() / scale.toFloat()) * 100f
                    viewModel.updateBatteryLevel(pct)
                }
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                    if (level != -1 && scale != -1) {
                        val pct = (level.toFloat() / scale.toFloat()) * 100f
                        viewModel.updateBatteryLevel(pct)
                    }
                }
            }
            context.registerReceiver(receiver, filter)
            onDispose {
                context.unregisterReceiver(receiver)
            }
        } else {
            onDispose {}
        }
    }

    DisposableEffect(context, state.isProximitySimulated) {
        if (!state.isProximitySimulated) {
            val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
            
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val distance = event.values.getOrNull(0) ?: 0f
                    val maxRange = proximitySensor?.maximumRange ?: 5f
                    val isFar = distance >= maxRange || distance >= 5.0f
                    viewModel.setSimulationPaused(isFar)
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }
            
            if (sensorManager != null && proximitySensor != null) {
                sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
            }
            
            onDispose {
                sensorManager?.unregisterListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    // Complementary Filter Sensor Fusion — blends gyro (fast) + accelerometer (drift-free)
    //   fused = α × (prev + gyro×dt) + (1-α) × accel_angle
    // where α = τ / (τ + dt), τ = 0.5s time constant
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Complementary filter state
        var fusedPitch = 0f   // degrees — maps to work angle (90° + pitch)
        var fusedRoll = 0f    // degrees — maps to travel angle (10° + roll)
        var lastGyroTime = 0L
        var angularSpeed = 0f

        // Accelerometer gravity vector (latest reading)
        var accelX = 0f; var accelY = 0f; var accelZ = 9.81f

        val TIME_CONSTANT = 0.5f // τ — higher = more gyro trust, lower = more accel trust

        val sensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        // Low-pass filter raw accel for noise reduction
                        val alpha = 0.15f
                        accelX = alpha * event.values[0] + (1f - alpha) * accelX
                        accelY = alpha * event.values[1] + (1f - alpha) * accelY
                        accelZ = alpha * event.values[2] + (1f - alpha) * accelZ
                    }
                    Sensor.TYPE_GYROSCOPE -> {
                        val now = System.nanoTime()
                        val dt = if (lastGyroTime != 0L) {
                            ((now - lastGyroTime) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                        } else 0.01f

                        // Angular velocity in rad/s → deg/s
                        val gx = Math.toDegrees(event.values[0].toDouble()).toFloat()
                        val gy = Math.toDegrees(event.values[1].toDouble()).toFloat()
                        val gz = Math.toDegrees(event.values[2].toDouble()).toFloat()

                        // Gyro-only estimate: integrate angular velocity
                        val gyroPitch = fusedPitch + gx * dt
                        val gyroRoll  = fusedRoll  + gy * dt

                        // Accelerometer estimate: derive pitch/roll from gravity vector
                        val accelPitch = Math.toDegrees(
                            Math.atan2(-accelX.toDouble(), Math.sqrt((accelY * accelY + accelZ * accelZ).toDouble()))
                        ).toFloat()
                        val accelRoll = Math.toDegrees(
                            Math.atan2(accelY.toDouble(), accelZ.toDouble())
                        ).toFloat()

                        // Complementary filter blend
                        val alphaFusion = TIME_CONSTANT / (TIME_CONSTANT + dt)
                        fusedPitch = alphaFusion * gyroPitch + (1f - alphaFusion) * accelPitch
                        fusedRoll  = alphaFusion * gyroRoll  + (1f - alphaFusion) * accelRoll

                        // Angular speed magnitude (deg/s)
                        angularSpeed = Math.sqrt((gx * gx + gy * gy + gz * gz).toDouble()).toFloat()

                        // Map to welding angles
                        val workAngle = (90f + fusedPitch).coerceIn(60f, 120f)
                        val travelAngle = (10f + fusedRoll).coerceIn(-10f, 30f)
                        viewModel.updateGyroData(workAngle, travelAngle, angularSpeed)

                        lastGyroTime = now
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        if (sensorManager != null && gyroSensor != null) {
            sensorManager.registerListener(sensorListener, gyroSensor, SensorManager.SENSOR_DELAY_GAME)
        }
        if (sensorManager != null && accelSensor != null) {
            sensorManager.registerListener(sensorListener, accelSensor, SensorManager.SENSOR_DELAY_GAME)
        }

        onDispose {
            sensorManager?.unregisterListener(sensorListener)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DeepSpaceBlue
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                
                // 1. Android Top Bezel Status Bar
                LandscapeTopStatusBar(state = state, onPowerSaveToggle = { viewModel.togglePowerSaveMode() })

                // 2. Center Dynamic Screen Content (Controlled via navigation state)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (state.currentScreen) {
                        AppScreen.SIMULATOR -> SimulatorScreen(state, viewModel)
                        AppScreen.CALIBRATE -> CalibrationScreen(state, viewModel)
                        AppScreen.SETTINGS -> SettingsScreen(state, viewModel)
                        AppScreen.RESULTS -> ResultsScreen(state, viewModel)
                        AppScreen.PROFILE -> ProfileScreen(state, viewModel)
                    }
                }

                // 3. Android Navigation Bar (Bottom persistent menu)
                AndroidBottomNavBar(
                    currentScreen = state.currentScreen,
                    isPowerSaveEnabled = state.isPowerSaveEnabled,
                    onNavigate = { viewModel.navigateTo(it) }
                )
            }

        // Dynamic Achievement Unlocked Floating Toast overlay
        AnimatedVisibility(
            visible = state.unlockedAchievementTitle != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp)
        ) {
            state.unlockedAchievementTitle?.let { title ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = ContainerGrey),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.5.dp, WarningAmber),
                    modifier = Modifier
                        .width(360.dp)
                        .shadow(12.dp, RoundedCornerShape(8.dp))
                        .clickable { viewModel.dismissAchievementToast() }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(WarningAmber.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Achievement Icon",
                                tint = WarningAmber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ACHIEVEMENT UNLOCKED!",
                                color = WarningAmber,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            state.unlockedAchievementDesc?.let { desc ->
                                    Text(
                                        text = desc,
                                        color = MutedText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                            }
                        }
                        IconButton(
                            onClick = { viewModel.dismissAchievementToast() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Toast",
                                tint = MutedText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                
                // Auto-dismiss after 6 seconds
                LaunchedEffect(title) {
                    kotlinx.coroutines.delay(6000)
                    viewModel.dismissAchievementToast()
                }
            }
        }
    }
}
}

// ============================================================================
// 3. NAVIGATION & BAR OVERLAYS (Top & Bottom persistent components)
// ============================================================================
