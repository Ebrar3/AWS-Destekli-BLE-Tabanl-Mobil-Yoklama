package com.efecanseymen.b1.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efecanseymen.b1.viewmodel.HomeViewModel

// NFC'den okunan ders bilgisi (UI katmanı data class)
private data class NfcCourseInfo(
    val day: String,
    val courseCode: String,
    val courseName: String,
    val time: String,
    val instructor: String
)

private data class NfcPayload(
    val classroomName: String,
    val courses: List<NfcCourseInfo>
)

// NFC payload'ını parse et: "DerslikAdı~Gün|DersKodu|DersAdı|Saat|Hoca;..." formatını çözer
private fun parseNfcPayload(data: String): NfcPayload {
    return try {
        val mainParts = data.split("~", limit = 2)
        val classroomName = if (mainParts.size == 2) mainParts[0].trim() else "Bilinmeyen Derslik"
        val courseData = if (mainParts.size == 2) mainParts[1] else data

        val courses = courseData.split(";").mapNotNull { entry ->
            val parts = entry.trim().split("|")
            if (parts.size >= 5) {
                NfcCourseInfo(
                    day        = parts[0].trim(),
                    courseCode  = parts[1].trim(),
                    courseName = parts[2].trim(),
                    time       = parts[3].trim(),
                    instructor = parts[4].trim()
                )
            } else null
        }
        NfcPayload(classroomName, courses)
    } catch (_: Exception) {
        NfcPayload("Bilinmeyen Derslik", emptyList())
    }
}

private fun getCurrentDayString(): String {
    val calendar = java.util.Calendar.getInstance()
    return when (calendar.get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "Pzt"
        java.util.Calendar.TUESDAY -> "Sal"
        java.util.Calendar.WEDNESDAY -> "Çar"
        java.util.Calendar.THURSDAY -> "Per"
        java.util.Calendar.FRIDAY -> "Cum"
        java.util.Calendar.SATURDAY -> "Cmt"
        java.util.Calendar.SUNDAY -> "Paz"
        else -> ""
    }
}

@Composable
fun ClassScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context      = LocalContext.current
    val nfcData      by viewModel.nfcTagData.observeAsState()
    val nfcAvailable by viewModel.nfcAvailable.observeAsState(false)  // donanım var mı?
    val nfcEnabled   by viewModel.nfcEnabled.observeAsState(false)    // açık mı?

    val isNfcReadingEnabled by viewModel.isNfcReadingEnabled.collectAsState()

    // Durum: "no_hw" | "disabled" | "app_disabled" | "waiting" | "scanned"
    val nfcState = when {
        nfcData != null      -> "scanned"
        !nfcAvailable        -> "no_hw"
        !nfcEnabled          -> "disabled"
        !isNfcReadingEnabled -> "app_disabled"
        else                 -> "waiting"
    }

    // NFC verisini parse et ve bugüne göre filtrele
    val parsedPayload = remember(nfcData) {
        nfcData?.let { parseNfcPayload(it) } ?: NfcPayload("Okunuyor...", emptyList())
    }

    val currentDay = remember { getCurrentDayString().lowercase() }
    val todayCourses = remember(parsedPayload) {
        parsedPayload.courses.filter { it.day.lowercase() == currentDay }
    }

    // Halka animasyonu (NFC bekleme)
    val infiniteTransition = rememberInfiniteTransition(label = "nfc_rings")
    val ring1Scale by infiniteTransition.animateFloat(
        0.7f, 2f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "r1"
    )
    val ring1Alpha by infiniteTransition.animateFloat(
        0.6f, 0f,
        infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "r1a"
    )
    val ring2Scale by infiniteTransition.animateFloat(
        0.7f, 2f,
        infiniteRepeatable(tween(1400, 500, LinearEasing), RepeatMode.Restart),
        label = "r2"
    )
    val ring2Alpha by infiniteTransition.animateFloat(
        0.6f, 0f,
        infiniteRepeatable(tween(1400, 500, LinearEasing), RepeatMode.Restart),
        label = "r2a"
    )

    val successScale by animateFloatAsState(
        targetValue  = if (nfcState == "scanned") 1.15f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "success"
    )

    // ─── KART OKUNMADI → Ortalanmış NFC Bekleme Ekranı ───
    if (nfcState != "scanned") {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            ) {
                // Başlık
                Text(
                    text = "Hangi Derslik?",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (nfcState) {
                        "no_hw"        -> "Bu cihazda NFC donanımı yok"
                        "disabled"     -> "NFC kapalı — ayarlardan açın"
                        "app_disabled" -> "NFC kapalı"
                        else           -> "NFC kartını cihaza yaklaştır"
                    },
                    fontSize = 14.sp,
                    color = when (nfcState) {
                        "disabled", "app_disabled" -> Color(0xFFFFB74D)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )



                Spacer(Modifier.height(32.dp))

                // İkon + Halkalar
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(260.dp)
                ) {
                    if (nfcState == "waiting") {
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .graphicsLayer {
                                    scaleX = ring1Scale; scaleY = ring1Scale; alpha = ring1Alpha
                                }
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(200.dp)
                                .graphicsLayer {
                                    scaleX = ring2Scale; scaleY = ring2Scale; alpha = ring2Alpha
                                }
                                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        )
                    }

                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val baseScale = if (nfcState == "waiting" || nfcState == "scanned") 1f else 0.75f
                    val buttonScale by animateFloatAsState(
                        targetValue = if (isPressed) baseScale * 1.25f else baseScale,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                        label = "nfc_button_scale"
                    )

                    Surface(
                        modifier = Modifier
                            .size(200.dp)
                            .scale(buttonScale),
                        shape = RoundedCornerShape(20),
                        interactionSource = interactionSource,
                        color = when (nfcState) {
                            "disabled", "app_disabled" -> Color(0xFFFFB74D).copy(alpha = 0.12f)
                            "no_hw"                    -> MaterialTheme.colorScheme.surfaceVariant
                            else                       -> MaterialTheme.colorScheme.primaryContainer
                        },
                        onClick = {
                            if (nfcAvailable && nfcEnabled && nfcState != "scanned") {
                                viewModel.isNfcReadingEnabled.value = !isNfcReadingEnabled
                            }
                        }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.Nfc,
                                contentDescription = "NFC Toggle",
                                modifier = Modifier.size(100.dp),
                                tint = when (nfcState) {
                                    "disabled", "app_disabled" -> Color(0xFFFFB74D)
                                    "no_hw"                    -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else                       -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(40.dp))

                // NFC Kapalı → Ayarlar butonu
                AnimatedVisibility(visible = nfcState == "disabled", enter = fadeIn(), exit = fadeOut()) {
                    Button(
                        onClick = { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFB74D).copy(alpha = 0.85f)
                        ),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("NFC Ayarlarını Aç", fontWeight = FontWeight.SemiBold, color = Color.Black)
                    }
                }

                // Bekleme yazısı
                val waitingAlpha by animateFloatAsState(
                    targetValue = if (nfcState == "waiting") 1f else 0f,
                    animationSpec = tween(300),
                    label = "waiting_alpha"
                )
                Text(
                    "Kart bekleniyor...",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = waitingAlpha }
                )
            }
        }
    }
    // ─── KART OKUNDU → Ders Programı Listesi ───
    else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Üst başlık bandı
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (nfcState == "scanned") parsedPayload.classroomName else "Hangi Derslik?",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (todayCourses.isNotEmpty()) "Bugün (${currentDay.uppercase()}): ${todayCourses.size} ders"
                               else "Bugün bu sınıfta ders yok",
                        fontSize = 12.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.clearNfcTag() },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Temizle", fontSize = 13.sp)
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Eğer bugünün dersi varsa → kutucuk kartlar
            if (todayCourses.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    items(todayCourses) { course ->
                        NfcCourseCard(course)
                    }
                }
            }
            // Parse başarısızsa → ham veriyi tek kart olarak göster
            else {
                nfcData?.let { rawData ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "📍 Okunan Veri",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = rawData,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NfcCourseCard(course: NfcCourseInfo) {
    val dayColor = when (course.day.lowercase()) {
        "pzt" -> Color(0xFF2196F3)
        "sal" -> Color(0xFF9C27B0)
        "çar" -> Color(0xFF4CAF50)
        "per" -> Color(0xFFFF9800)
        "cum" -> Color(0xFFE91E63)
        "cmt" -> Color(0xFF607D8B)
        "paz" -> Color(0xFF795548)
        else  -> MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            // Renkli çizgi (güne göre renk)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(dayColor, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // Ders adı
                Text(
                    text = course.courseName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(2.dp))

                // Ders kodu
                Text(
                    text = course.courseCode,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // Saat ve Hoca
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🕐 ${course.time}",
                        fontSize = 13.sp,
                        color = Color(0xFFCF6679),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = course.instructor,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Gün etiketi
            Box(
                modifier = Modifier
                    .align(Alignment.CenterVertically)
                    .padding(end = 14.dp)
                    .background(dayColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = course.day,
                    fontWeight = FontWeight.ExtraBold,
                    color = dayColor,
                    fontSize = 14.sp
                )
            }
        }
    }
}