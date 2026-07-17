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
fun SettingsScreen(
    state: WeldVisionState,
    viewModel: WeldVisionUiViewModel
) {
    var selectedSubTab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (selectedSubTab == 0) "WELD VISION CONTROLLERS" else "MULTI-DEVICE CLOUD INFRASTRUCTURE",
                    color = WarningAmber,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (selectedSubTab == 0) "Machine Calibration & Tolerance Limits" else "Secure Database Synchronization Engine",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Sub-tabs switch
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { selectedSubTab = 0 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSubTab == 0) WarningAmber else ContainerGrey,
                        contentColor = if (selectedSubTab == 0) DeepSpaceBlue else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp).testTag("settings_tab_machine")
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Machine Params", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { selectedSubTab = 1 },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedSubTab == 1) WarningAmber else ContainerGrey,
                        contentColor = if (selectedSubTab == 1) DeepSpaceBlue else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(32.dp).testTag("settings_tab_cloud_sync")
                ) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Cloud Database Sync", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (selectedSubTab == 0) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Process & Material Selection Col
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ContainerGrey, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Text("Process Mode Selection", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            WeldProcess.values().forEach { process ->
                                val isSelected = state.currentProcess == process
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) WarningAmber.copy(alpha = 0.1f) else Color.Transparent)
                                        .border(1.dp, if (isSelected) WarningAmber else BorderGrey, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.updateProcess(process) }
                                        .padding(10.dp)
                                        .padding(bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).background(if (isSelected) WarningAmber else MutedText, CircleShape))
                                    Text(process.label, color = if (isSelected) Color.White else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        
                        item {
                            Text("Material Selection (Thermal Sim)", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            MaterialType.values().forEach { mat ->
                                val isSelected = state.currentMaterial == mat
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) AccentCyan.copy(alpha = 0.1f) else Color.Transparent)
                                        .border(1.dp, if (isSelected) AccentCyan else BorderGrey, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.updateMaterial(mat) }
                                        .padding(10.dp)
                                        .padding(bottom = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(mat.label, color = if (isSelected) Color.White else MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    if (isSelected) {
                                        Text("Melt: ${mat.meltPoint}", color = AccentCyan, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                        
                        item {
                            Text("Joint, Env & Clamping", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f).border(1.dp, AccentCyan, RoundedCornerShape(8.dp)).clickable { 
                                    val nextEnv = EnvironmentFactor.values()[(state.currentEnvironment.ordinal + 1) % EnvironmentFactor.values().size]
                                    viewModel.updateEnvironment(nextEnv)
                                }.padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(state.currentEnvironment.label, color = AccentCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                                Box(modifier = Modifier.weight(1f).border(1.dp, WarningAmber, RoundedCornerShape(8.dp)).clickable { 
                                    val nextJoint = JointConfig.values()[(state.currentJoint.ordinal + 1) % JointConfig.values().size]
                                    viewModel.updateJoint(nextJoint)
                                }.padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(state.currentJoint.label, color = WarningAmber, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                                Box(modifier = Modifier.weight(1f).border(1.dp, AlertEmerald, RoundedCornerShape(8.dp)).clickable { 
                                    val nextClamp = ClampingRestraint.values()[(state.clampingRestraint.ordinal + 1) % ClampingRestraint.values().size]
                                    viewModel.updateClampingRestraint(nextClamp)
                                }.padding(8.dp), contentAlignment = Alignment.Center) {
                                    Text(state.clampingRestraint.label, color = AlertEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                }

                // Target Parameters Col
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ContainerGrey, RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Graded Tolerance Target Anchors", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Speed Goal", color = MutedText, fontSize = 10.sp)
                            Text("${state.targetSpeed} mm/s", color = WarningAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = state.targetSpeed,
                            onValueChange = { viewModel.updateTargetSpeed(it) },
                            valueRange = 1.5f..8.0f,
                            colors = SliderDefaults.colors(thumbColor = WarningAmber, activeTrackColor = WarningAmber)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Simulate Current Speed", color = MutedText, fontSize = 10.sp)
                            Text("${String.format("%.1f", state.travelSpeed)} mm/s", color = AccentCyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = state.travelSpeed,
                            onValueChange = { viewModel.updateTravelSpeed(it) },
                            valueRange = 1.0f..10.0f,
                            colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Arc Gap goal", color = MutedText, fontSize = 10.sp)
                            Text("${state.targetGap} mm", color = WarningAmber, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = state.targetGap,
                            onValueChange = { viewModel.updateTargetGap(it) },
                            valueRange = 1.0f..6.0f,
                            colors = SliderDefaults.colors(thumbColor = WarningAmber, activeTrackColor = WarningAmber)
                        )
                    }
                }

                // Simulated Physical Dials
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(ContainerGrey, RoundedCornerShape(16.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Simulated Machine Parameters", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    // Dial 0: Material & Thickness
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ContainerGrey, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Material & Thickness", color = MutedText, fontSize = 8.sp)
                            Text("${state.currentMaterial.label} (${state.materialThickness}mm)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { viewModel.updateMaterialThickness((state.materialThickness - 0.5f).coerceAtLeast(1.0f)) }, modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            IconButton(onClick = { viewModel.updateMaterialThickness((state.materialThickness + 0.5f).coerceAtMost(25.0f)) }, modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Dial 1: Voltage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ContainerGrey, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Voltage", color = MutedText, fontSize = 8.sp)
                            Text("${state.voltage}V", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { viewModel.adjustVoltage(-0.2f) }, modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            IconButton(onClick = { viewModel.adjustVoltage(0.2f) }, modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Dial 2: Wire Speed / Amperage
                    val isGmaw = state.currentProcess == WeldProcess.GMAW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ContainerGrey, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(if (isGmaw) "Wire Speed" else "Amperage", color = MutedText, fontSize = 8.sp)
                            Text(
                                text = if (isGmaw) "${state.wireFeedSpeed} IPM" else "${state.amperage} A",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { if (isGmaw) viewModel.adjustFeedSpeed(-5) else viewModel.adjustAmperage(-5) },
                                modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            IconButton(
                                onClick = { if (isGmaw) viewModel.adjustFeedSpeed(5) else viewModel.adjustAmperage(5) },
                                modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Dial 3: Gas Flow Rate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ContainerGrey, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Gas Flow Rate", color = MutedText, fontSize = 8.sp)
                            Text(
                                text = "${String.format("%.1f", state.gasFlowRate)} CFH",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { viewModel.adjustGasFlowRate(-0.5f) },
                                modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                            IconButton(
                                onClick = { viewModel.adjustGasFlowRate(0.5f) },
                                modifier = Modifier.size(24.dp).background(BorderGrey, RoundedCornerShape(4.dp))
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        } else {
            // New Cloud Sync row
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Panel: Configuration (Weight 1.2f)
                Column(
                    modifier = Modifier
                        .weight(1.2f)
                        .fillMaxHeight()
                        .background(ContainerGrey, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("CONFIGURATION & SECURITY CREDENTIALS", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    
                    // Device metadata row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DeepSpaceBlue, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Devices, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        Column {
                            Text("LOCAL DEVICE IDENTIFIER", color = MutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                            Text(state.deviceId, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Server URL Input
                    var tempUrl by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("REMOTE DATABASE GATEWAY URL", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("Moshi / Retrofit", color = AccentCyan, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = tempUrl,
                            onValueChange = { 
                                tempUrl = it
                                viewModel.updateSyncSettings(it, state.authToken, state.isAutoSyncEnabled)
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = BorderGrey,
                                focusedContainerColor = DeepSpaceBlue,
                                unfocusedContainerColor = DeepSpaceBlue
                            ),
                            placeholder = { Text("https://api.example.com/", color = MutedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sync_server_url")
                        )
                    }

                    // Auth Token / Secret Input
                    var tempToken by remember(state.authToken) { mutableStateOf(state.authToken) }
                    Column {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("REMOTE ACCESS KEY (BEARER TOKEN)", color = MutedText, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("AES Secure", color = AlertRed, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = tempToken,
                            onValueChange = { 
                                tempToken = it
                                viewModel.updateSyncSettings(state.serverUrl, it, state.isAutoSyncEnabled)
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp, color = Color.White, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WarningAmber,
                                unfocusedBorderColor = BorderGrey,
                                focusedContainerColor = DeepSpaceBlue,
                                unfocusedContainerColor = DeepSpaceBlue
                            ),
                            placeholder = { Text("Enter your secure token...", color = MutedText, fontSize = 11.sp, fontFamily = FontFamily.Monospace) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("sync_auth_token")
                        )
                    }

                    // Background Auto-Sync Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("BACKGROUND AUTOMATIC SYNC", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("Automatically backup to cloud on run completion", color = MutedText, fontSize = 8.sp)
                        }
                        Switch(
                            checked = state.isAutoSyncEnabled,
                            onCheckedChange = { viewModel.updateSyncSettings(state.serverUrl, state.authToken, it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = WarningAmber,
                                checkedTrackColor = WarningAmber.copy(alpha = 0.5f),
                                uncheckedThumbColor = BorderGrey,
                                uncheckedTrackColor = DeepSpaceBlue
                            ),
                            modifier = Modifier.testTag("sync_auto_toggle")
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    // Sync action button
                    val isSyncing = state.syncStatus.contains("Syncing")
                    Button(
                        onClick = { viewModel.performCloudSync() },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = WarningAmber, disabledContainerColor = WarningAmber.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(40.dp).testTag("sync_now_button")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = DeepSpaceBlue, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("SYNCING CLOUD CORE...", color = DeepSpaceBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        } else {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = DeepSpaceBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("SYNCHRONIZE DATABASE", color = DeepSpaceBlue, fontSize = 11.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Status and timestamp indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("STATUS: ${state.syncStatus.uppercase()}", 
                            color = when {
                                state.syncStatus.contains("Success") -> Color(0xFF4DEEEA)
                                state.syncStatus.contains("Error") -> AlertRed
                                state.syncStatus.contains("Syncing") -> WarningAmber
                                else -> MutedText
                            }, 
                            fontSize = 8.sp, 
                            fontWeight = FontWeight.Bold, 
                            fontFamily = FontFamily.Monospace
                        )
                        Text("LAST SYNC: ${state.lastSyncTime}", color = MutedText, fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    }
                }

                // Right Panel: Audit Logs & Diagnostics console
                Column(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                        .background(ContainerGrey, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("SECURE TRANSMISSION JOURNAL", color = MutedText, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        TextButton(onClick = { viewModel.clearSyncDatabaseLogs() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Logs", tint = AlertRed, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("CLEAR JOURNAL", color = AlertRed, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                    }

                    // Logs viewport
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(DeepSpaceBlue, RoundedCornerShape(12.dp))
                            .border(1.dp, BorderGrey, RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        if (state.syncLogs.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Default.CloudQueue, contentDescription = null, tint = MutedText, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No Transmission Logs Available", color = MutedText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Perform synchronization to start data replication.", color = MutedText, fontSize = 8.sp)
                            }
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(state.syncLogs) { log ->
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, if (log.status == "SUCCESS") Color(0xFF4DEEEA).copy(alpha = 0.2f) else if (log.status == "FAILED") AlertRed.copy(alpha = 0.2f) else BorderGrey, RoundedCornerShape(8.dp))
                                            .background(if (log.status == "SUCCESS") Color(0xFF4DEEEA).copy(alpha = 0.03f) else if (log.status == "FAILED") AlertRed.copy(alpha = 0.03f) else Color.Transparent)
                                            .padding(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(
                                                            when (log.status) {
                                                                "SUCCESS" -> Color(0xFF4DEEEA)
                                                                "FAILED" -> AlertRed
                                                                else -> WarningAmber
                                                            },
                                                            CircleShape
                                                        )
                                                )
                                                Text(
                                                    text = "${log.action} - ${log.status}",
                                                    color = when (log.status) {
                                                        "SUCCESS" -> Color(0xFF4DEEEA)
                                                        "FAILED" -> AlertRed
                                                        else -> WarningAmber
                                                    },
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            Text(log.timestamp, color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(log.details, color = Color.White, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                        if (log.durationMs > 0L) {
                                            Spacer(Modifier.height(4.dp))
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text("Duration: ${log.durationMs}ms", color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                                Text("Payload: ${log.bytesTransferred} Bytes", color = MutedText, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// 5. THE RUN SIMULATOR SCREEN (Live camera overlay HUD + Canvas tracking)
// ============================================================================
