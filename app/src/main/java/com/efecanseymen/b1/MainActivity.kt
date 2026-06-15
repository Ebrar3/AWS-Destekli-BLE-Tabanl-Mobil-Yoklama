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
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.bluetooth.BluetoothAdapter
import com.efecanseymen.b1.ui.screens.Navigation
import com.efecanseymen.b1.ui.theme.B1Theme
import com.efecanseymen.b1.viewmodel.HomeViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private var nfcAdapter: NfcAdapter? = null

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                    viewModel.scanConnected.value = false
                    viewModel.scanReportSuccess.value = null
                    viewModel.scanServerMessage.value = null
                    viewModel.stopScan()
                    context?.stopService(Intent(context, com.efecanseymen.b1.service.BleScannerService::class.java))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        viewModel.nfcAvailable.value = (nfcAdapter != null)
        viewModel.nfcEnabled.value   = (nfcAdapter?.isEnabled == true)

        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        registerReceiver(bluetoothStateReceiver, filter)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.isNfcReadingEnabled.collect { enabled ->
                    if (enabled) enableNfcDispatch() else disableNfcDispatch()
                }
            }
        }

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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothStateReceiver)
    }

    override fun onResume() {
        super.onResume()
        viewModel.nfcEnabled.value = (nfcAdapter?.isEnabled == true)
    }

    override fun onPause() {
        super.onPause()
        disableNfcDispatch()
    }

    private fun enableNfcDispatch() {
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_MUTABLE
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)
    }

    private fun disableNfcDispatch() {
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
