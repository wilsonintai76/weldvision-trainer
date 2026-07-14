package com.example.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.camera.camera2.interop.Camera2Interop
import android.hardware.camera2.CaptureRequest
import android.util.Range
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.Videocam
import com.example.tracking.WeldVisionJNI
import com.example.tracking.WexelGrid
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraPreviewView(
    modifier: Modifier = Modifier,
    state: WeldVisionState,
    onArcTracked: (Float, Float, Boolean) -> Unit = { _, _, _ -> },
    onTagPose: (FloatArray?) -> Unit = {},
    showArOverlay: Boolean = true
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isPowerSaveEnabled = state.isPowerSaveEnabled

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCameraPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreviewContent(
                state = state,
                onArcTracked = onArcTracked,
                onTagPose = onTagPose,
                isPowerSaveEnabled = isPowerSaveEnabled,
                lifecycleOwner = lifecycleOwner,
                context = context,
                showArOverlay = showArOverlay,
                onPermissionRequest = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        } else {
            SimulatedWeldFeed(
                modifier = Modifier.fillMaxSize(),
                isPowerSaveEnabled = isPowerSaveEnabled,
                showAuthorizeButton = true,
                onAuthorizeClick = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.camera2.interop.ExperimentalCamera2Interop::class)
@Composable
fun CameraPreviewContent(
    state: WeldVisionState,
    onArcTracked: (Float, Float, Boolean) -> Unit,
    onTagPose: (FloatArray?) -> Unit,
    isPowerSaveEnabled: Boolean,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    context: Context,
    showArOverlay: Boolean = true,
    onPermissionRequest: () -> Unit
) {
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var hasPhysicalCamera by remember { mutableStateOf<Boolean?>(null) } // null = checking, false = fallback, true = preview

    // Retrieve the camera provider exactly once and safely check for physical hardware
    LaunchedEffect(context) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProvider = suspendCoroutine { continuation ->
            cameraProviderFuture.addListener({
                try {
                    val provider = cameraProviderFuture.get()
                    val hasAnyCamera = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ||
                                       provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                    hasPhysicalCamera = hasAnyCamera
                    continuation.resume(provider)
                } catch (e: Exception) {
                    e.printStackTrace()
                    hasPhysicalCamera = false
                    continuation.resume(null)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(cameraProvider) {
        onDispose {
            try {
                cameraProvider?.unbindAll()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Rebind camera reactively when provider or power save mode changes
    LaunchedEffect(cameraProvider, lifecycleOwner, isPowerSaveEnabled, hasPhysicalCamera) {
        val provider = cameraProvider ?: return@LaunchedEffect
        if (hasPhysicalCamera != true) return@LaunchedEffect
        
        try {
            provider.unbindAll()

            val previewBuilder = Preview.Builder()
            
            if (isPowerSaveEnabled) {
                // Restrict target frame rate to low FPS via Camera2Interop for physical energy conservation
                try {
                    val extender = Camera2Interop.Extender(previewBuilder)
                    extender.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                        Range(10, 15)
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val preview = previewBuilder.build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            // Real-time Computer Vision / ImageAnalysis to detect bright physical welding arc
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                try {
                    // ── AprilTag detection: pass YUV planes to native tracker ──
                    val planes = imageProxy.planes
                    if (planes.size >= 3) {
                        try {
                            val yBuf = planes[0].buffer
                            val uBuf = planes[1].buffer
                            val vBuf = planes[2].buffer
                            val w = imageProxy.width
                            val h = imageProxy.height
                            val yStride = planes[0].rowStride
                            val uvStride = planes[1].rowStride
                            val uvPxStride = planes[1].pixelStride

                            WeldVisionJNI.nativeDetectTags(
                                yBuf, uBuf, vBuf, w, h, yStride, uvStride, uvPxStride,
                                800f, 800f, w / 2f, h / 2f, 0.05f
                            )
                            val tagPose = WeldVisionJNI.getLatestTranslation()
                            onTagPose(tagPose)
                        } catch (t: Throwable) {
                            android.util.Log.e("WeldVisionJNI", "Tag detection failed", t)
                        }
                    }

                    // ── Brightness-based arc tracking ──
                    val buffer = imageProxy.planes[0].buffer
                    val width = imageProxy.width
                    val height = imageProxy.height

                    var maxBrightness = 0
                    var maxIdx = -1

                    // Fast pixel sampling for edge/glow tracking
                    val step = 8
                    for (y in 0 until height step step) {
                        for (x in 0 until width step step) {
                            val idx = y * width + x
                            if (idx < buffer.remaining()) {
                                val pixelVal = buffer.get(idx).toInt() and 0xFF
                                if (pixelVal > maxBrightness) {
                                    maxBrightness = pixelVal
                                    maxIdx = idx
                                }
                            }
                        }
                    }

                    if (maxBrightness > 220 && maxIdx != -1) {
                        // Normalize physical coordinate ratio
                        val rawX = (maxIdx % width).toFloat() / width
                        val rawY = (maxIdx / width).toFloat() / height
                        
                        // Pass detected coordinates back up (Main thread dispatch)
                        onArcTracked(rawX, rawY, true)
                    } else {
                        onArcTracked(-1f, -1f, false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                null
            }

            if (cameraSelector != null) {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            hasPhysicalCamera = false
        }
    }

    // For absolute low-power look, we can simulate frame rate dropping on the UI layer using a timed opacity overlay
    var frameTick by remember { mutableStateOf(false) }
    if (isPowerSaveEnabled) {
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(100) // 10 FPS refresh visual effect
                frameTick = !frameTick
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (hasPhysicalCamera) {
            true -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { previewView },
                        modifier = Modifier
                            .fillMaxSize()
                            // Apply a slight opacity wobble under power-save mode to mimic 10 FPS visual sampling
                            .alpha(if (isPowerSaveEnabled && frameTick) 0.92f else 1.0f)
                    )

                    // Real-time AR Joint seam tracker & weld guidance overlay
                    if (showArOverlay) {
                        ARWeldWorkspaceOverlay(
                        modifier = Modifier.fillMaxSize(),
                        state = state,
                        isPowerSaveEnabled = isPowerSaveEnabled
                    )
                    }

                    // When power save is active, overlay a subtle dark/dimming scrim to save OLED power emissions
                    if (isPowerSaveEnabled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.35f))
                        )

                        // Power Save Badge
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BatterySaver,
                                    contentDescription = "Battery Saver Active",
                                    tint = Color(0xFF38E54D),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "FPS THROTTLED (10 FPS)",
                                    color = Color(0xFF38E54D),
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
            false -> {
                SimulatedWeldFeed(
                    modifier = Modifier.fillMaxSize(),
                    isPowerSaveEnabled = isPowerSaveEnabled,
                    showAuthorizeButton = false
                )
            }
            null -> {
                // Checking / Loading State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F1012)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFF4DEEEA))
                }
            }
        }
    }
}

// ── Sub-components ──

data class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val alpha: Float, val size: Float, val color: Color)

@Composable
fun SimulatedWeldFeed(modifier: Modifier, isPowerSaveEnabled: Boolean, showAuthorizeButton: Boolean, onAuthorizeClick: () -> Unit = {}) {
    val infiniteTransition = rememberInfiniteTransition(label = "WeldSim")
    val arcXProgress by infiniteTransition.animateFloat(0.15f, 0.85f, infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse), label = "ArcX")
    val arcGlow by infiniteTransition.animateFloat(24f, 36f, infiniteRepeatable(tween(400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "Glow")

    var sparks by remember { mutableStateOf(emptyList<Spark>()) }
    val random = remember { java.util.Random() }

    LaunchedEffect(isPowerSaveEnabled) {
        val delayMs = if (isPowerSaveEnabled) 32L else 16L
        while (true) {
            kotlinx.coroutines.delay(delayMs)
            sparks = sparks.map { it.copy(x = it.x + it.vx, y = it.y + it.vy, vy = it.vy + 0.15f, alpha = (it.alpha - 0.03f).coerceAtLeast(0f)) }.filter { it.alpha > 0f }
            if (random.nextFloat() < 0.45f) {
                val newSparks = (1..(random.nextInt(4) + 1)).map {
                    val a = random.nextFloat() * 2 * Math.PI; val s = random.nextFloat() * 6f + 2f
                    Spark(0f, 0f, (Math.cos(a) * s).toFloat(), (Math.sin(a) * s - 1.5f).toFloat(), 1f, random.nextFloat() * 3f + 1.5f, Color(0xFFFFE17D))
                }
                sparks = sparks + newSparks
            }
        }
    }

    Box(modifier = modifier.background(Color(0xFF0F1012)).fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height; val cy = h * 0.55f; val ax = w * arcXProgress
            drawRect(Color(0xFF2C2D31), androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Size(w, cy - 10f))
            drawRect(Color(0xFF1E1F23), androidx.compose.ui.geometry.Offset(0f, cy + 10f), androidx.compose.ui.geometry.Size(w, h - cy - 10f))
            drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFFFEA7A).copy(alpha = 0.85f), Color(0xFFFF9800).copy(alpha = 0.45f), Color.Transparent)), center = Offset(ax, cy), radius = arcGlow * density)
            drawCircle(Color.White, radius = 8f * density, center = Offset(ax, cy))
            sparks.forEach { drawCircle(it.color.copy(alpha = it.alpha), it.size * density, Offset(ax + it.x * density, cy + it.y * density)) }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Videocam, null, tint = Color(0xFF4DEEEA), modifier = Modifier.size(36.dp))
            if (showAuthorizeButton) {
                Text("AUTHORIZE CAMERA STREAM", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Button(onClick = onAuthorizeClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DEEEA)), shape = RoundedCornerShape(6.dp)) {
                    Text("GRANT PERMISSION", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            } else {
                Text("VIRTUAL FEED: EMULATOR MODE", color = Color(0xFF4DEEEA), fontSize = 10.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                Text("High-fidelity simulated sensor tracking.", color = Color.White.copy(alpha = 0.7f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun ARWeldWorkspaceOverlay(modifier: Modifier, state: WeldVisionState, isPowerSaveEnabled: Boolean) {
    val pulseAlpha by rememberInfiniteTransition(label = "pulse").animateFloat(0.3f, 0.7f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")

    Column(modifier = modifier) {
        // ── Always-visible target guide (joint seam + calibrated/gyro crosshair) ──
        Canvas(modifier = Modifier.fillMaxSize().weight(1f)) {
            val w = size.width; val h = size.height; val cy = h * 0.55f

            // ── Crosshair position (CNC WCS concept) ──
            // Machine Zero (MZ) = first gyro reading → "power-on home"
            // WCS Zero = tip world position at calibration → "touch-off"
            val tx: Float
            val ty: Float

            if (state.isCalibrated && state.tagVisible) {
                // WCS mode: delta from calibrated touch-off point
                val tipWorldX = state.tagTxMm + state.calibrationOffsetX
                val tipWorldZ = state.tagTzMm + state.calibrationOffsetZ
                val deltaX = (tipWorldX - state.calibrationRefX).coerceIn(-50f, 50f)
                val deltaZ = (tipWorldZ - state.calibrationRefZ).coerceIn(-30f, 30f)
                tx = (w / 2f + deltaX * 3f).coerceIn(w * 0.05f, w * 0.95f)
                ty = (h * 0.55f + deltaZ * 3f).coerceIn(h * 0.05f, h * 0.95f)
            } else if (state.hasMachineZero) {
                // Machine Zero mode: delta from initial gyro position
                val workDelta = state.gyroWorkAngle - state.machineZeroWorkAngle
                val travelDelta = state.gyroTravelAngle - state.machineZeroTravelAngle
                // MZ is the center reference — positive delta = moved from initial
                val workFrac = ((workDelta + 20f) / 40f).coerceIn(0f, 1f)
                val travelFrac = ((travelDelta + 10f) / 20f).coerceIn(0f, 1f)
                tx = w * (0.1f + 0.8f * travelFrac)
                ty = h * (0.1f + 0.8f * workFrac)
            } else {
                // No gyro yet: centered
                tx = w / 2f; ty = h * 0.55f
            }

            var finalTx = tx
            var finalTy = ty

            // Weaving mode: add sinusoidal oscillation to horizontal position
            if (state.weldMode == WeldMode.WEAVING) {
                val now = System.currentTimeMillis() / 1000f
                val weaveOffset = kotlin.math.sin(now * 2.5f) * w * 0.18f
                finalTx = (tx + weaveOffset).coerceIn(w * 0.05f, w * 0.95f)
            }

            // Joint seam line
            drawLine(Color(0xFF00FFF0).copy(alpha = 0.35f), Offset(0f, cy), Offset(w, cy), 3.dp.toPx())
            drawLine(Color(0xFF005577).copy(alpha = 0.5f), Offset(0f, cy - 2.dp.toPx()), Offset(w, cy - 2.dp.toPx()), 1.dp.toPx())

            // Target guide ring
            val ringColor = if (state.isCalibrated && state.tagVisible) Color(0xFF00FF88) else Color(0xFF00FFF0)
            drawCircle(ringColor.copy(alpha = pulseAlpha), 28.dp.toPx(), Offset(finalTx, finalTy),
                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f))))
            drawCircle(ringColor.copy(alpha = pulseAlpha), 6.dp.toPx(), Offset(finalTx, finalTy))

            // Direction dots along seam
            val arrowSpacing = 80.dp.toPx()
            var ax = 20.dp.toPx()
            while (ax < w - 20.dp.toPx()) {
                drawCircle(Color(0xFF00FFF0).copy(alpha = 0.15f), 2.dp.toPx(), Offset(ax, cy))
                ax += arrowSpacing
            }
        }

        // ── HUD panels — hidden in minimal mode ──
        if (!state.isMinimalHud) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(8.dp)) {
                    Text("AR HUD: SEAM LOCK", color = Color(0xFF00FFF0), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("JOINT: ${state.currentJoint.label}", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text("CAMERA: 1080p @ ${if (isPowerSaveEnabled) "10" else "60"} FPS", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
                Column(Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(8.dp)).padding(8.dp), horizontalAlignment = Alignment.End) {
                    Text("THERMAL", color = Color(0xFFFF3B30), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Text("PLATE: ${state.currentMaterial.label}", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                    Text("MELT: ${state.currentMaterial.meltPoint}", color = Color.White.copy(alpha = 0.8f), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun TorchAngleGuideCard(modifier: Modifier, workAngle: Int, travelAngle: Int) {
    val isAligned = workAngle in 85..95 && travelAngle in 5..15
    Row(modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp)).border(1.dp, if (isAligned) Color(0xFF38E54D).copy(alpha = 0.4f) else Color(0xFF00FFF0).copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(Modifier.size(54.dp).background(Color(0xFF1E1E1E).copy(alpha = 0.5f), RoundedCornerShape(27.dp))) {
            val cx = size.width / 2f; val cy = size.height / 2f
            drawCircle(Color(0xFF38E54D).copy(alpha = 0.3f), radius = 5f * 1.8f, center = Offset(cx, cy), style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))))
            drawCircle(if (isAligned) Color(0xFF38E54D) else Color(0xFFFF5252), radius = 4.dp.toPx(), center = Offset(cx + (workAngle - 90) * 1.8f, cy - (travelAngle - 10) * 1.8f))
        }
        Column { Text("WORK: ${workAngle}°", color = if (isAligned) Color(0xFF38E54D) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold); Text("TRAVEL: ${travelAngle}°", color = if (isAligned) Color(0xFF38E54D) else MutedText, fontSize = 10.sp) }
    }
}

@Composable
fun TravelSpeedGuideCard(modifier: Modifier, targetSpeed: Float, currentSpeed: Float) {
    val diff = currentSpeed - targetSpeed
    val isOk = kotlin.math.abs(diff) < 1.0f
    Column(modifier.background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(12.dp)).border(1.dp, if (isOk) Color(0xFF38E54D).copy(alpha = 0.4f) else BorderGrey, RoundedCornerShape(12.dp)).padding(12.dp)) {
        Text("TRAVEL SPEED", color = Color(0xFF00FFF0), fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text("${String.format("%.1f", currentSpeed)} mm/s", color = if (isOk) Color(0xFF38E54D) else Color(0xFFFF5252), fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text("Target: ${String.format("%.1f", targetSpeed)} mm/s", color = MutedText, fontSize = 9.sp)
    }
}

// ── Real-time Weld Bead Overlay ────────────────────────────────────────────

@Composable
fun BeadOverlay(modifier: Modifier, grid: WexelGrid) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (w <= 0 || h <= 0) return@Canvas

        val gw = grid.width; val gh = grid.height
        val cellW = w / gw; val cellH = h / gh
        val cellSz = minOf(cellW, cellH)
        val offsetX = (w - gw * cellSz) / 2f
        val offsetY = (h - gh * cellSz) / 2f

        // Draw bead cells that have displacement
        for (x in 0 until gw) {
            for (z in 0 until gh) {
                val d = grid.heightAt(x, z)
                val heat = grid.getWexel(x, z)?.heat ?: 0f
                if (d < 0.002f && heat < 0.05f) continue

                val px = offsetX + x * cellSz
                val py = offsetY + z * cellSz

                // Color: heat (red→orange→yellow) blended with solid (gray metallic)
                val alpha = (d * 0.8f + heat * 0.4f).coerceIn(0f, 1f)
                val r = (0.6f + heat * 0.4f).coerceIn(0f, 1f)
                val g = (0.45f - heat * 0.3f + d * 0.2f).coerceIn(0f, 1f)
                val b = (0.3f - heat * 0.2f).coerceIn(0f, 1f)

                drawRect(
                    color = Color(r, g, b, alpha),
                    topLeft = Offset(px, py),
                    size = androidx.compose.ui.geometry.Size(cellSz + 1f, cellSz + 1f)
                )
            }
        }
    }
}
