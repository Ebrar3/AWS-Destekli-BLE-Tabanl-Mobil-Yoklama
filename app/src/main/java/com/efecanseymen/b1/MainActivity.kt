package com.efecanseymen.b1

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.efecanseymen.b1.ui.screens.Navigation
import com.efecanseymen.b1.ui.theme.B1Theme
import com.efecanseymen.b1.viewmodel.HomeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        viewModel.nfcAvailable.value = (nfcAdapter != null)
        viewModel.nfcEnabled.value   = (nfcAdapter?.isEnabled == true)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color(0xFF121212).toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color(0xFF121212).toArgb())
        )
        setContent {
            B1Theme {
                Navigation(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Kullanıcı ayarlardan NFC'yi açmış olabilir — her resume'de güncelle
        viewModel.nfcEnabled.value = (nfcAdapter?.isEnabled == true)
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.action
        if (action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED   ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED) {
            handleNfcIntent(intent)
        }
    }

    private fun handleNfcIntent(intent: Intent) {
        // NDEF mesajı varsa text record'u oku
        val rawMessages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, NdefMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        }

        val text = if (!rawMessages.isNullOrEmpty()) {
            val msg = rawMessages[0] as NdefMessage
            msg.records.firstOrNull()?.let { record ->
                val payload = record.payload
                // NDEF Text record: ilk byte status (dil kodu uzunluğu içeriyor)
                val langLen = payload[0].toInt() and 0x3F
                String(payload, 1 + langLen, payload.size - 1 - langLen, Charsets.UTF_8)
            } ?: "İçerik okunamadı"
        } else {
            // NDEF yok → ham Tag ID'yi göster
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
            }
            "Etiket ID: ${tag?.id?.joinToString("") { "%02X".format(it) } ?: "?"}"
        }

        viewModel.onNfcTagDetected(text)
    }
}
