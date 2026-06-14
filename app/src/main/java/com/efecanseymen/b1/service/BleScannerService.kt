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
import com.efecanseymen.b1.data.model.ReportPresenceRequest
import com.efecanseymen.b1.data.network.RetrofitInstance
import kotlinx.coroutines.*

/**
 * ForegroundService: Arka planda BLE tarar.
 * Öğretmenin UUID'sini (SERVICE_UUID) görünce payload'dan
 * session_id ve checkin_id okur → /report-presence çağırır.
 *
 * Payload formatı (binary):
 *   - AdvertiseData  → ServiceData(SERVICE_UUID) = session_id (16 byte binary UUID)
 *   - ScanResponse   → ManufacturerData(MANUFACTURER_ID) = checkin_id (16 byte binary UUID)
 */
class BleScannerService : Service() {

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val reportedCheckins = mutableSetOf<String>()



    private var studentId: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        studentId = intent?.getStringExtra(EXTRA_STUDENT_ID) ?: ""
        Log.d("BLE", "=== BleScannerService onStartCommand === studentId=$studentId")

        // ÖNCE startForeground çağırılmalı
        startForeground(NOTIF_ID, buildNotification("BLE Taranıyor..."))
        Log.d("BLE", "startForeground tamamlandı")

        // POST_NOTIFICATIONS izni olmasa bile servis çalışmaya devam etmeli!
        // Bildirim gözükmeyebilir ama BLE tarama çalışır.
        startScanning()
        return START_STICKY
    }

    // ────────────────────────────── Decoding ──────────────────────────────

    /** UTF-8 byte array → string (ör: 9 byte → "SC3AEB0B0") */
    private fun bytesToString(bytes: ByteArray): String? {
        return try {
            String(bytes, Charsets.UTF_8).also {
                if (it.isBlank()) return null
            }
        } catch (e: Exception) {
            Log.e("BLE", "bytesToString dönüşüm hatası: ${e.message}")
            null
        }
    }

    // ────────────────────────────── Scanning ──────────────────────────────

    private val scanHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val scanRestartRunnable = object : Runnable {
        override fun run() {
            Log.d("BLE", "⟳ Scan restart (periyodik yenileme)")
            restartScanning()
            scanHandler.postDelayed(this, SCAN_RESTART_INTERVAL_MS)
        }
    }

    companion object {
        const val EXTRA_STUDENT_ID = "student_id"
        const val CHANNEL_ID = "BLE_SCANNER_CHANNEL"
        const val NOTIF_ID = 2001
        /** Ana UUID — scanner filtresi ve session_id taşıyıcı */
        val SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEF5-0000-1000-8000-00805F9B34FB")

        /** İkinci UUID — scan response'ta checkin_id taşıyıcı (öğretmen ile aynı) */
        val CHECKIN_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEF6-0000-1000-8000-00805F9B34FB")

        // Broadcast action — UI'a bildir
        const val ACTION_PRESENCE_REPORTED = "com.efecanseymen.b1.PRESENCE_REPORTED"
        const val EXTRA_CHECKIN_ID = "checkin_id"
        const val EXTRA_STATUS = "status"

        /** Samsung cihazlarda BLE scan sessizce durabiliyor - periyodik restart */
        const val SCAN_RESTART_INTERVAL_MS = 25_000L
    }

    private fun startScanning() {
        Log.d("BLE", "startScanning() çağrıldı")

        // Konum servisi kontrolü — Samsung cihazlarda BLE scan için ZORUNLU
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
        val isLocationEnabled = locationManager?.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) == true
                || locationManager?.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER) == true
        Log.d("BLE", "Konum servisi açık mı: $isLocationEnabled")
        if (!isLocationEnabled) {
            Log.e("BLE", "⚠ KONUM SERVİSİ KAPALI! Samsung cihazlarda BLE tarama çalışmaz. Lütfen Konum'u açın.")
            updateNotification("⚠ Konum Servisi kapalı — BLE çalışmaz!")
        }

        try {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter
            if (adapter == null) {
                Log.e("BLE", "BluetoothAdapter NULL! BLE desteklenmiyor.")
                return
            }
            if (!adapter.isEnabled) {
                Log.e("BLE", "Bluetooth KAPALI!")
                return
            }
            scanner = adapter.bluetoothLeScanner
            if (scanner == null) {
                Log.e("BLE", "BluetoothLeScanner NULL! BLE tarayıcı alınamadı.")
                return
            }
            Log.d("BLE", "Scanner alındı, filtre ayarlanıyor...")

            // Service UUID filtresi — sadece öğretmen yayınlarını eşleştirir
            val filter = ScanFilter.Builder()
                .setServiceUuid(SERVICE_UUID)
                .build()

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0) // anında raporla
                .build()

            scanCallback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult?) {
                    result ?: return
                    val scanRecord = result.scanRecord ?: return

                    Log.d("BLE", "onScanResult — cihaz: ${result.device?.address} rssi=${result.rssi}")

                    // Session ID: ServiceData'dan oku (SERVICE_UUID, UTF-8 string)
                    val sessionBytes = scanRecord.getServiceData(SERVICE_UUID)
                    // Checkin ID: ServiceData'dan oku (CHECKIN_UUID, UTF-8 string)
                    val checkinBytes = scanRecord.getServiceData(CHECKIN_UUID)

                    Log.d("BLE", "  → sessionBytes=${sessionBytes?.size ?: "null"}, checkinBytes=${checkinBytes?.size ?: "null"}")

                    if (sessionBytes == null && checkinBytes == null) {
                        Log.d("BLE", "  → Her iki ServiceData da boş, atlanıyor")
                        return
                    }

                    // Session ID'yi dönüştür
                    val sessionId = if (sessionBytes != null) {
                        bytesToString(sessionBytes)
                    } else null

                    // Checkin ID'yi dönüştür
                    val checkinId = if (checkinBytes != null) {
                        bytesToString(checkinBytes)
                    } else null

                    Log.d("BLE", "  → Parsed: session=$sessionId | checkin=$checkinId")

                    if (sessionId.isNullOrBlank() || checkinId.isNullOrBlank()) {
                        Log.w("BLE", "  → Eksik veri: session=$sessionId, checkin=$checkinId — atlanıyor")
                        return
                    }

                    if (checkinId in reportedCheckins) {
                        Log.d("BLE", "  → checkin=$checkinId zaten raporlandı, atlanıyor")
                        return
                    }
                    reportedCheckins.add(checkinId)

                    Log.d("BLE", "✓ Öğretmen tespit edildi: session=$sessionId | checkin=$checkinId")
                    updateNotification("Yoklama tespit edildi! ✓")
                    reportPresence(sessionId, checkinId)
                }

                override fun onScanFailed(errorCode: Int) {
                    val reason = when (errorCode) {
                        SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                        SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "APP_REGISTRATION_FAILED"
                        SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                        SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                        else -> "UNKNOWN"
                    }
                    Log.e("BLE", "✗ Tarama BAŞARISIZ! errorCode=$errorCode ($reason)")
                    updateNotification("Tarama hatası: $errorCode ($reason)")
                }
            }

            scanner?.startScan(listOf(filter), settings, scanCallback!!)
            Log.d("BLE", "✓ BLE tarama BAŞLADI — UUID=${SERVICE_UUID.uuid}  Filtre aktif")

            // Periyodik restart başlat (Samsung BLE scan timeout'unu önler)
            scanHandler.removeCallbacks(scanRestartRunnable)
            scanHandler.postDelayed(scanRestartRunnable, SCAN_RESTART_INTERVAL_MS)

        } catch (e: SecurityException) {
            Log.e("BLE", "Bluetooth izni yok: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("BLE", "startScanning HATA: ${e.message}", e)
        }
    }

    // ────────────────────────────── HTTP Report ──────────────────────────────

    private fun reportPresence(sessionId: String, checkinId: String) {
        scope.launch {
            try {
                Log.d("BLE", "→ reportPresence çağrılıyor: student=$studentId, checkin=$checkinId, session=$sessionId")
                val r = RetrofitInstance.api.reportPresence(
                    ReportPresenceRequest(studentId, checkinId, sessionId)
                )
                Log.d("BLE", "← HTTP ${r.code()} | isSuccessful=${r.isSuccessful}")
                val wrapper = r.body()
                Log.d("BLE", "← LambdaWrapper: statusCode=${wrapper?.statusCode}, body=${wrapper?.body?.take(200)}")

                val body = if (r.isSuccessful) wrapper?.parse(com.efecanseymen.b1.data.model.ReportPresenceBody::class.java) else null
                val success = body?.success == true
                Log.d("BLE", "← Parsed: success=$success, body=$body")

                // UI'a bildir
                val broadcastIntent = Intent(ACTION_PRESENCE_REPORTED).apply {
                    putExtra(EXTRA_CHECKIN_ID, checkinId)
                    putExtra(EXTRA_STATUS, success)
                }
                sendBroadcast(broadcastIntent)

                if (!success) reportedCheckins.remove(checkinId) // tekrar dene
            } catch (e: Exception) {
                Log.e("BLE", "Presence hatası: ${e.message}", e)
                reportedCheckins.remove(checkinId)
            }
        }
    }

    // ────────────────────────────── Notification ──────────────────────────────

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

    private fun restartScanning() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: SecurityException) {
            Log.e("BLE", "stopScan izin hatası: ${e.message}")
        }
        startScanning()
    }

    // ────────────────────────────── Lifecycle ──────────────────────────────

    override fun onDestroy() {
        scanHandler.removeCallbacks(scanRestartRunnable)
        try {
            scanCallback?.let { scanner?.stopScan(it) }
            Log.d("BLE", "BLE tarama durduruldu (onDestroy)")
        } catch (e: SecurityException) {
            Log.e("BLE", "stopScan izin hatası: ${e.message}")
        }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
