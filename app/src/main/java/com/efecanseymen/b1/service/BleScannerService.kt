package com.efecanseymen.b1.service

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import com.efecanseymen.b1.data.model.ReportPresenceBody
import com.efecanseymen.b1.data.model.ReportPresenceRequest
import com.efecanseymen.b1.data.network.RetrofitInstance
import kotlinx.coroutines.*

/**
 * ForegroundService: Arka planda BLE tarar.
 * Öğretmenin UUID'sini (SERVICE_UUID) görünce payload'dan
 * session_id ve checkin_id okur → /report-presence çağırır.
 */
class BleScannerService : Service() {

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val reportedCheckins = mutableSetOf<String>()

    companion object {
        const val EXTRA_STUDENT_ID = "student_id"
        const val CHANNEL_ID = "BLE_SCANNER_CHANNEL"
        const val NOTIF_ID = 2001
        val SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEF5-0000-1000-8000-00805F9B34FB")

        // Broadcast action — UI'a bildir
        const val ACTION_PRESENCE_REPORTED = "com.efecanseymen.b1.PRESENCE_REPORTED"
        const val EXTRA_CHECKIN_ID = "checkin_id"
        const val EXTRA_STATUS = "status"
    }

    private var studentId: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        studentId = intent?.getStringExtra(EXTRA_STUDENT_ID) ?: ""
        // Android 13+ bildirim izni kontrolü
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // İzin yoksa servisi durdur — ScanScreen'den izin isteniyor
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startForeground(NOTIF_ID, buildNotification("BLE Taranıyor..."))
        startScanning()
        return START_STICKY
    }


    private fun startScanning() {
        val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        scanner = btManager.adapter.bluetoothLeScanner

        val filter = ScanFilter.Builder()
            .setServiceUuid(SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                val serviceData = result.scanRecord?.getServiceData(SERVICE_UUID) ?: return
                val payload = String(serviceData, Charsets.UTF_8)
                val parts = payload.split("|")
                if (parts.size < 2) return

                val sessionId = parts[0]
                val checkinId = parts[1]

                if (checkinId.isBlank() || checkinId in reportedCheckins) return
                reportedCheckins.add(checkinId)

                Log.d("BLE", "Öğretmen tespit edildi: $sessionId | $checkinId")
                updateNotification("Yoklama tespit edildi! ✓")
                reportPresence(sessionId, checkinId)
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Tarama hatası: $errorCode")
                updateNotification("Tarama hatası: $errorCode")
            }
        }

        try {
            scanner?.startScan(listOf(filter), settings, scanCallback!!)
            Log.d("BLE", "BLE tarama başladı")
        } catch (e: SecurityException) {
            Log.e("BLE", "Bluetooth izni yok: ${e.message}")
        }
    }

    private fun reportPresence(sessionId: String, checkinId: String) {
        scope.launch {
            try {
                val r = RetrofitInstance.api.reportPresence(
                    ReportPresenceRequest(studentId, checkinId, sessionId)
                )
                // LambdaWrapper içindeki body'yi ReportPresenceBody'ye parse et
                val body = if (r.isSuccessful) r.body()?.parse(ReportPresenceBody::class.java) else null
                val success = body?.success == true
                Log.d("BLE", "Presence report: ${if (success) "başarılı" else "başarısız"}")

                // UI'a bildir
                val broadcastIntent = Intent(ACTION_PRESENCE_REPORTED).apply {
                    putExtra(EXTRA_CHECKIN_ID, checkinId)
                    putExtra(EXTRA_STATUS, success)
                }
                sendBroadcast(broadcastIntent)

                if (!success) reportedCheckins.remove(checkinId) // tekrar dene
            } catch (e: Exception) {
                Log.e("BLE", "Presence hatası: ${e.message}")
                reportedCheckins.remove(checkinId)
            }
        }
    }


    private fun buildNotification(text: String): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Yoklama Tarayıcı", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yoklama Sistemi Aktif")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onDestroy() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: SecurityException) {
            Log.e("BLE", "stopScan izin hatası: ${e.message}")
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
