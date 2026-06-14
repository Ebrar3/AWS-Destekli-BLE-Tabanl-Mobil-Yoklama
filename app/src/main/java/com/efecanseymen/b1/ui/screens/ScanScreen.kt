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
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
    var isScanning    by remember { mutableStateOf(false) }
    var isConnected   by remember { mutableStateOf(false) }
    var reportSuccess by remember { mutableStateOf<Boolean?>(null) }
    var connectedCid  by remember { mutableStateOf<String?>(null) }

    // BLE servisi broadcast'ini dinle
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == BleScannerService.ACTION_PRESENCE_REPORTED) {
                    val cid     = intent.getStringExtra(BleScannerService.EXTRA_CHECKIN_ID)
                    val success = intent.getBooleanExtra(BleScannerService.EXTRA_STATUS, false)
                    connectedCid  = cid
                    reportSuccess = success
                    if (success) isConnected = true   // Başarılıysa bağlandı moduna geç
                }
            }
        }
        val filter = IntentFilter(BleScannerService.ACTION_PRESENCE_REPORTED)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    // İzin listesi
    val permissions = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS
        )
    else if (Build.VERSION.SDK_INT >= 31)
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
            isScanning = true
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

    // Bağlandı pulse (yeşil)
    val successPulse by infiniteTransition.animateFloat(
        1f, 1.08f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "sp")

    // İkon ölçeği (buton)
    val btnScale by animateFloatAsState(
        targetValue = if (isScanning && !isConnected) 1.1f else 1f,
        animationSpec = tween(500), label = "btn"
    )

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // ─── TARAMA HALKALARI ───
        if (isScanning && !isConnected) {
            listOf(
                ring1Scale to ring1Alpha,
                ring2Scale to ring2Alpha,
                ring3Scale to ring3Alpha
            ).forEach { (scale, alpha) ->
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(scale)
                        .border(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape)
                )
            }
        }

        // ─── BAĞLANDI YEŞİL HALE ───
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(successPulse)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.08f), CircleShape)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Spacer(Modifier.height(32.dp))

            // ─── BAŞLIK (animasyonlu geçiş) ───
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
                        "connected" -> "Yoklamaya Katıldın! ✓"
                        "scanning"  -> "Taranıyor..."
                        else        -> "Ders Yayını Ara"
                    },
                    fontWeight = FontWeight.ExtraBold,
                    fontSize   = 22.sp,
                    textAlign  = TextAlign.Center,
                    color = when (state) {
                        "connected" -> Color(0xFF4CAF50)
                        else        -> MaterialTheme.colorScheme.onBackground
                    }
                )
            }

            Spacer(Modifier.height(48.dp))

            // ─── ANA BUTON / İKON ───
            if (isConnected) {
                // Bağlandı → büyük yeşil tik
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(successPulse)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.15f), RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector    = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier       = Modifier.size(100.dp),
                        tint           = Color(0xFF4CAF50)
                    )
                }
            } else {
                // Taranıyor / Başlat → BLE butonu
                Button(
                    onClick = {
                        if (isScanning) {
                            context.stopService(Intent(context, BleScannerService::class.java))
                            isScanning = false
                        } else {
                            val allGranted = permissions.all {
                                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                            }
                            if (allGranted) {
                                startBleService(context, viewModel)
                                isScanning = true
                            } else {
                                permLauncher.launch(permissions)
                            }
                        }
                    },
                    modifier  = Modifier.size(180.dp).scale(btnScale),
                    shape     = RoundedCornerShape(40.dp),
                    colors    = ButtonDefaults.buttonColors(
                        containerColor = if (isScanning)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Icon(
                        imageVector    = if (isScanning) Icons.Filled.Bluetooth
                                         else Icons.Filled.BluetoothDisabled,
                        contentDescription = null,
                        modifier       = Modifier.size(100.dp),
                        tint           = if (isScanning) MaterialTheme.colorScheme.onPrimary
                                         else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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