package com.yoklama.teacher.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.yoklama.teacher.data.model.PresentStudentItem
import com.yoklama.teacher.data.model.StudentResult
import com.yoklama.teacher.viewmodel.TeacherViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActiveSessionScreen(
    viewModel: TeacherViewModel,
    onSessionEnded: () -> Unit,
    onBack: () -> Unit
) {
    val context          = LocalContext.current
    val checkinTriggered by viewModel.checkinTriggered.observeAsState()
    val sessionEnded     by viewModel.sessionEnded.observeAsState()
    val checkinCount     by viewModel.checkinCount.observeAsState(0)
    val errorMessage     by viewModel.errorMessage.observeAsState()
    val presentStudents  by viewModel.presentStudents.observeAsState(emptyList())
    val courseName       = viewModel.currentCourseName ?: viewModel.currentCourseCode ?: "Ders"

    // Android 12+ BLE yayın izinleri + Android 13+ bildirim izni
    val blePerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.POST_NOTIFICATIONS
        )
    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
        arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
    else emptyArray()

    // BLE başlatma durumu — tekrar tekrar başlatmayı önler
    var bleStarted by remember { mutableStateOf(false) }

    val blePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allOk = granted.values.all { it }
        if (allOk || blePerms.isEmpty()) {
            val sid = viewModel.currentSessionId ?: return@rememberLauncherForActivityResult
            val cid = viewModel.currentCheckinId ?: return@rememberLauncherForActivityResult
            viewModel.startBle(sid, cid)
            viewModel.startAutoCheckin()
            viewModel.startPollingPresent()
            bleStarted = true
        }
    }

    // BLE'yi başlat — ekran açıldığında veya yeni checkin geldiğinde
    // NOT: HomeScreen'den navigate edildiğinde checkinTriggered zaten set edilmiş olabilir,
    // bu yüzden sadece değişimi değil, mevcut değeri de kontrol ediyoruz.
    LaunchedEffect(checkinTriggered, checkinCount) {
        if (bleStarted) return@LaunchedEffect
        val cid = checkinTriggered?.checkin_id ?: return@LaunchedEffect
        val sid = viewModel.currentSessionId   ?: return@LaunchedEffect
        if (checkinCount >= 1) {
            val allGranted = blePerms.isEmpty() || blePerms.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                viewModel.startBle(sid, cid)
                viewModel.startAutoCheckin()
                viewModel.startPollingPresent()
                bleStarted = true
            } else {
                blePermLauncher.launch(blePerms)
            }
        }
    }

    LaunchedEffect(sessionEnded) {
        if (sessionEnded != null) {
            viewModel.stopPollingPresent()
            onSessionEnded()
        }
    }

    // Geri sayım sayacı (her yeni yoklamada sıfırla)
    var remainingSecs by remember { mutableStateOf(480) }
    LaunchedEffect(checkinCount) {
        remainingSecs = 480
        while (remainingSecs > 0) {
            delay(1000)
            remainingSecs--
        }
    }

    // Pulse animasyonu
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f, targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseScale"
    )

    var showEndDialog by remember { mutableStateOf(false) }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Dersi Bitir?") },
            text = {
                Text("$checkinCount yoklama tamamlandı.\nDersi bitirip sonuçları hesaplamak istiyor musun?")
            },
            confirmButton = {
                Button(
                    onClick = { showEndDialog = false; viewModel.endSession() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679))
                ) { Text("Evet, Bitir") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Vazgeç") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(courseName, fontWeight = FontWeight.Bold)
                        Text("Oturum: ${viewModel.currentSessionId ?: "—"}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri")
                    }
                },
                actions = {
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Filled.Stop, "Bitir", tint = Color(0xFFCF6679))
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // BLE göstergesi
            item {
                Box(
                    modifier = Modifier
                        .size(130.dp)
                        .scale(pulseScale)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.BluetoothSearching, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(4.dp))
                Text("Bluetooth Yayını Aktif", fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }

            // Sayaç kartları
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Yoklama", "$checkinCount", Modifier.weight(1f))
                    StatCard(
                        "Sonraki",
                        "${remainingSecs / 60}:${(remainingSecs % 60).toString().padStart(2, '0')}",
                        Modifier.weight(1f)
                    )
                    StatCard("Mevcut", "${presentStudents.size}", Modifier.weight(1f))
                }
            }

            // Hata mesajı
            errorMessage?.let {
                item {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            // Aktif katılımcı listesi
            if (presentStudents.isNotEmpty()) {
                item {
                    Text(
                        "Katılımcılar (${presentStudents.size})",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                items(presentStudents) { student ->
                    PresentStudentCard(student)
                }
            }

            // Ders sonu sonuçlar
            sessionEnded?.results?.let { results ->
                item {
                    Text("Sonuçlar (${results.size} öğrenci)",
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                items(results) { s -> StudentResultCard(s) }
            }

            // Dersi Bitir butonu
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { showEndDialog = true },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCF6679))
                ) {
                    Icon(Icons.Filled.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Dersi Bitir", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(4.dp)) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary)
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PresentStudentCard(student: PresentStudentItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).background(Color(0xFF4CAF50), CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(student.student_name, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(student.student_id, fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${student.checkin_count} yoklama",
                fontSize = 12.sp,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StudentResultCard(student: StudentResult) {
    val color = if (student.status == "present") Color(0xFF4CAF50) else Color(0xFFCF6679)
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
        Row(modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(student.student_name, fontWeight = FontWeight.SemiBold)
                Text("${student.checkins_attended}/${student.total_checkins} yoklama",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("%${student.percentage.toInt()}",
                fontWeight = FontWeight.Bold, color = color, fontSize = 16.sp)
        }
    }
}
