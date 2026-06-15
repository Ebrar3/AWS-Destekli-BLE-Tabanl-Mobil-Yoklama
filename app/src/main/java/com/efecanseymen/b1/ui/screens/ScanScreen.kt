package com.efecanseymen.b1.ui.screens

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.efecanseymen.b1.service.BleScannerService
import com.efecanseymen.b1.viewmodel.HomeViewModel

/** Öğrencinin BLE tarama ekranı. 3 mod: IDLE → SCANNING → CONNECTED */
@Composable
fun ScanScreen(viewModel: HomeViewModel, modifier: Modifier) {
    val context = LocalContext.current

    // --- Durum ---
    val isScanning    by viewModel.isScanning.collectAsState()
    val isConnected   by viewModel.scanConnected.collectAsState()
    val reportSuccess by viewModel.scanReportSuccess.collectAsState()
    var connectedCid  by remember { mutableStateOf<String?>(null) }
    val serverMessage by viewModel.scanServerMessage.collectAsState()

    // BLE servisi broadcast'ini dinle
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BleScannerService.ACTION_PRESENCE_REPORTED) {
                    val cid     = intent.getStringExtra(BleScannerService.EXTRA_CHECKIN_ID)
                    val sid     = intent.getStringExtra(BleScannerService.EXTRA_SESSION_ID)
                    val msg     = intent.getStringExtra(BleScannerService.EXTRA_MESSAGE)
                    val success = intent.getBooleanExtra(BleScannerService.EXTRA_STATUS, false)
                    viewModel.scanConnectedCid.value = cid
                    viewModel.scanReportSuccess.value = success
                    if (success) {
                        viewModel.scanConnected.value = true
                        
                        // API'den mesaj geldiyse onu, yoksa sessionId içinden ders kodunu bulmaya çalışalım
                        if (!msg.isNullOrBlank()) {
                            viewModel.scanServerMessage.value = msg
                        } else if (!sid.isNullOrBlank()) {
                            val courses = viewModel.courses.value ?: emptyList()
                            val matchedCourse = courses.find { sid.contains(it.course_code, ignoreCase = true) }
                            viewModel.scanServerMessage.value = matchedCourse?.course_name?.let { "$it Yoklamasına Katıldınız" }
                                ?: "Yoklamaya Katıldın! ✓"
                        } else {
                            viewModel.scanServerMessage.value = "Yoklamaya Katıldın! ✓"
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(BleScannerService.ACTION_PRESENCE_REPORTED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // İzin listesi
    val permissions = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    else
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.all { it }) {
            startBleService(context, viewModel)
            viewModel.startScan()
        }
    }

    // --- Animasyonlar ---
    val infiniteTransition = rememberInfiniteTransition(label = "scan_rings")

    // Tarama halkaları
    val ring1Scale by infiniteTransition.animateFloat(
        0.6f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "r1s")
    val ring1Alpha by infiniteTransition.animateFloat(
        0.8f, 0f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "r1a")
    val ring2Scale by infiniteTransition.animateFloat(
        0.6f, 2f, infiniteRepeatable(tween(1200, 400, LinearEasing), RepeatMode.Restart), label = "r2s")
    val ring2Alpha by infiniteTransition.animateFloat(
        0.8f, 0f, infiniteRepeatable(tween(1200, 400, LinearEasing), RepeatMode.Restart), label = "r2a")
    val ring3Scale by infiniteTransition.animateFloat(
        0.6f, 2f, infiniteRepeatable(tween(1200, 800, LinearEasing), RepeatMode.Restart), label = "r3s")
    val ring3Alpha by infiniteTransition.animateFloat(
        0.8f, 0f, infiniteRepeatable(tween(1200, 800, LinearEasing), RepeatMode.Restart), label = "r3a")

    // Yeşil Atom Bombası (Arka Plan Patlaması)
    val explosionScale by animateFloatAsState(
        targetValue = if (isConnected) 30f else 0f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "explosion"
    )

    // Buton Şekil Animasyonu (Çok yuvarlaktan basık karemsi şekle)
    val buttonCornerRadius by animateDpAsState(
        targetValue = if (isConnected) 40.dp else 90.dp,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "cornerRadius"
    )

    // Buton Büyüme Animasyonu (Bağlanınca biraz büyür)
    val buttonScale by animateFloatAsState(
        targetValue = if (isConnected) 1.15f else 1f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "buttonScale"
    )

    // Buton Rengi
    val buttonColor by animateColorAsState(
        targetValue = when {
            isConnected -> Color(0xFF4CAF50).copy(alpha = 0.15f)
            isScanning -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(400), label = "buttonColor"
    )

    // İkon Rengi
    val iconColor by animateColorAsState(
        targetValue = when {
            isConnected -> Color(0xFF4CAF50)
            isScanning -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        },
        animationSpec = tween(400), label = "iconColor"
    )

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // ─── YEŞİL ATOM BOMBASI (ARKA PLAN) ───
        Box(
            modifier = Modifier
                .size(100.dp)
                .graphicsLayer {
                    scaleX = explosionScale
                    scaleY = explosionScale
                }
                .background(Color(0xFF0D2B0D), CircleShape)
        )
        
        // ─── TARAMA HALKALARI (graphicsLayer ile optimize) ───
        if (isScanning && !isConnected) {
            Box(
                modifier = Modifier.size(200.dp).graphicsLayer {
                    scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha
                }.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                modifier = Modifier.size(200.dp).graphicsLayer {
                    scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha
                }.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
            Box(
                modifier = Modifier.size(200.dp).graphicsLayer {
                    scaleX = ring3Scale; scaleY = ring3Scale; alpha = ring3Alpha
                }.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
            )
        }


        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp)
        ) {
            // ─── BAŞLIK ───
            AnimatedContent(
                targetState = when {
                    isConnected  -> "connected"
                    isScanning   -> "scanning"
                    else         -> "idle"
                },
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "title"
            ) { state ->
                Text(
                    text = when (state) {
                        "connected" -> serverMessage ?: "Yoklamaya Katıldın! ✓"
                        "scanning"  -> "Taranıyor..."
                        else        -> "Ders Yayını Ara"
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 22.sp,
                    textAlign  = TextAlign.Center,
                    color = when (state) {
                        "connected" -> Color(0xFF4CAF50)
                        else        -> MaterialTheme.colorScheme.onBackground
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(48.dp))

            // ─── ANA BUTON / İKON ───
            Box(
                modifier = Modifier
                    .offset(y = -22.dp)
                    .graphicsLayer {
                        scaleX = buttonScale
                        scaleY = buttonScale
                    }
                    .size(180.dp)
                    .clip(RoundedCornerShape(buttonCornerRadius))
                    .background(buttonColor)
                    .clickable(enabled = !isConnected) {
                        if (isScanning) {
                            context.stopService(Intent(context, BleScannerService::class.java))
                            viewModel.stopScan()
                        } else {
                            val allGranted = permissions.all {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            if (allGranted) {
                                startBleService(context, viewModel)
                                viewModel.startScan()
                            } else {
                                permLauncher.launch(permissions)
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = when {
                        isConnected -> "success"
                        isScanning -> "scanning"
                        else -> "idle"
                    },
                    transitionSpec = {
                        scaleIn(tween(400)) togetherWith scaleOut(tween(400))
                    }, label = "icon"
                ) { state ->
                    Icon(
                        imageVector = when (state) {
                            "success" -> Icons.Filled.CheckCircle
                            "scanning" -> Icons.Filled.Bluetooth
                            else -> Icons.Filled.BluetoothDisabled
                        },
                        contentDescription = null,
                        modifier = Modifier.size(100.dp),
                        tint = iconColor
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ─── BAĞLANDI DETAY KARTI ───
            AnimatedVisibility(visible = isConnected, enter = fadeIn(), exit = fadeOut()) {
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B5E20)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Yoklama sistemi sizi algıladı.",
                            color      = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize   = 15.sp,
                            textAlign  = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tarama arka planda devam ediyor",
                            color    = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // ─── HATA KARTI ───
            AnimatedVisibility(
                visible = reportSuccess == false && connectedCid != null,
                enter = fadeIn(), exit = fadeOut()
            ) {
                Card(
                    shape  = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4E0D12)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Sunucuya bağlanılamadı, tekrar denenecek.",
                            color = Color.White, fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Alt açıklama
        Text(
            text = when {
                isConnected -> "Öğretmen yakınlığınız doğrulandı ✓"
                isScanning  -> "Öğretmen yakındaysa otomatik kaydolacaksın"
                else        -> "Yoklamaya katılmak için başlat"
            },
            style    = MaterialTheme.typography.bodyMedium,
            color    = if (isConnected) Color(0xFF4CAF50).copy(alpha = 0.8f)
                       else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center,
            modifier  = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp, start = 32.dp, end = 32.dp)
        )
    }
}

private fun startBleService(context: Context, viewModel: HomeViewModel) {
    val studentId = viewModel.currentUserId ?: return
    val intent    = Intent(context, BleScannerService::class.java).apply {
        putExtra(BleScannerService.EXTRA_STUDENT_ID, studentId)
    }
    context.startForegroundService(intent)
}