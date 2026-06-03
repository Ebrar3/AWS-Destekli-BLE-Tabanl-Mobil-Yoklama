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
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.efecanseymen.b1.viewmodel.HomeViewModel

@Composable
fun ClassScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val context      = LocalContext.current
    val nfcData      by viewModel.nfcTagData.observeAsState()
    val nfcAvailable by viewModel.nfcAvailable.observeAsState(false)  // donanım var mı?
    val nfcEnabled   by viewModel.nfcEnabled.observeAsState(false)    // açık mı?

    // Durum: "no_hw" | "disabled" | "waiting" | "scanned"
    val nfcState = when {
        nfcData != null    -> "scanned"
        !nfcAvailable      -> "no_hw"
        !nfcEnabled        -> "disabled"
        else               -> "waiting"
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {

            // ─── BAŞLIK ───
            Text(
                text = "Hangi Derslik?",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (nfcState) {
                    "no_hw"    -> "Bu cihazda NFC donanımı yok"
                    "disabled" -> "NFC kapalı — ayarlardan açın"
                    "waiting"  -> "NFC kartını cihaza yaklaştır"
                    else       -> "Derslik bilgisi okundu ✓"
                },
                fontSize = 13.sp,
                color = when (nfcState) {
                    "disabled" -> Color(0xFFFFB74D)
                    "scanned"  -> Color(0xFF4CAF50)
                    else       -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))

            // ─── İKON + HALKALAR ───
            Box(contentAlignment = Alignment.Center) {
                // Yayılan halkalar (sadece NFC bekliyorken)
                if (nfcState == "waiting") {
                    listOf(ring1Scale to ring1Alpha, ring2Scale to ring2Alpha)
                        .forEach { (scale, alpha) ->
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .scale(scale)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                                        CircleShape
                                    )
                            )
                        }
                }

                // Merkez daire
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(successScale)
                        .background(
                            color = when (nfcState) {
                                "scanned"  -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                                "disabled" -> Color(0xFFFFB74D).copy(alpha = 0.12f)
                                "no_hw"    -> MaterialTheme.colorScheme.surfaceVariant
                                else       -> MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (nfcState) {
                            "scanned"  -> Icons.Filled.CheckCircle
                            "disabled" -> Icons.Filled.SettingsBluetooth
                            else       -> Icons.Filled.Nfc
                        },
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = when (nfcState) {
                            "scanned"  -> Color(0xFF4CAF50)
                            "disabled" -> Color(0xFFFFB74D)
                            "no_hw"    -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            else       -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }

            Spacer(Modifier.height(36.dp))

            // ─── NFC KAPALI → AYARLAR BUTONU ───
            AnimatedVisibility(visible = nfcState == "disabled") {
                Button(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFB74D).copy(alpha = 0.85f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "NFC Ayarlarını Aç",
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
            }

            // ─── KART OKUNDU → SONUÇ ───
            AnimatedVisibility(
                visible = nfcState == "scanned",
                enter   = fadeIn() + slideInVertically { it / 2 },
                exit    = fadeOut()
            ) {
                nfcData?.let { data ->
                    Column {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(16.dp),
                            colors   = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "📍 Derslik Bilgisi",
                                    fontSize = 12.sp,
                                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text      = data,
                                    fontSize  = 26.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color     = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        OutlinedButton(
                            onClick  = { viewModel.clearNfcTag() },
                            shape    = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Temizle")
                        }
                    }
                }
            }

            // ─── BEKLEME YAZISI ───
            AnimatedVisibility(visible = nfcState == "waiting", enter = fadeIn(), exit = fadeOut()) {
                Text(
                    "Kart bekleniyor...",
                    fontSize  = 14.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}