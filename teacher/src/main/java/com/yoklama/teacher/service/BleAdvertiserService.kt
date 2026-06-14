package com.yoklama.teacher.service

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * ForegroundService: Öğretmen telefonu BLE yayını yapar.
 *
 * BLE 4.x advertisement paketi 31 byte ile sınırlı olduğundan,
 * session_id ve checkin_id string olarak SIĞMAZ (~73 byte).
 *
 * Çözüm: Binary UUID encoding + split packets
 * - AdvertiseData  → Service UUID (filtre için) + session_id (16 byte binary, ServiceData)
 * - ScanResponse   → checkin_id (16 byte binary, ManufacturerData)
 *
 * Toplam: AdvertiseData ~27 byte ✓ | ScanResponse ~20 byte ✓
 */
class BleAdvertiserService : Service() {

    private var advertiser: BluetoothLeAdvertiser? = null
    private var currentCallback: AdvertiseCallback? = null

    var sessionId: String = ""
    var checkinId: String = ""

    companion object {
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_CHECKIN_ID = "checkin_id"
        const val ACTION_UPDATE_CHECKIN = "com.yoklama.teacher.UPDATE_CHECKIN"
        const val CHANNEL_ID = "BLE_YOKLAMA_CHANNEL"
        const val NOTIF_ID = 1001

        /** Ana UUID — scanner filtresi ve session_id taşıyıcı */
        val SERVICE_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEF5-0000-1000-8000-00805F9B34FB")

        /** İkinci UUID — scan response'ta checkin_id taşıyıcı */
        val CHECKIN_UUID: ParcelUuid =
            ParcelUuid.fromString("0000FEF6-0000-1000-8000-00805F9B34FB")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_CHECKIN -> {
                checkinId = intent.getStringExtra(EXTRA_CHECKIN_ID) ?: checkinId
                Log.d("BLE", "Checkin güncellendi: $checkinId — yayın yeniden başlatılıyor")
                restartAdvertising()
            }
            else -> {
                sessionId = intent?.getStringExtra(EXTRA_SESSION_ID) ?: ""
                checkinId = intent?.getStringExtra(EXTRA_CHECKIN_ID) ?: ""
                Log.d("BLE", "=== BleAdvertiserService BAŞLATILIYOR === session=$sessionId | checkin=$checkinId")
                startForeground(NOTIF_ID, buildNotification())
                Log.d("BLE", "startForeground tamamlandı")
                startAdvertising()
            }
        }
        return START_STICKY
    }

    // ────────────────────────────── Encoding ──────────────────────────────

    /** Kısa string ID → UTF-8 byte array (ör: "SC3AEB0B0" → 9 byte) */
    private fun stringToBytes(value: String): ByteArray = value.toByteArray(Charsets.UTF_8)

    // ────────────────────────────── Advertising ──────────────────────────────

    private fun startAdvertising() {
        try {
            val btManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            val adapter = btManager.adapter
            if (adapter == null) {
                Log.e("BLE", "BluetoothAdapter NULL! BLE desteklenmiyor.")
                return
            }
            if (!adapter.isEnabled) {
                Log.e("BLE", "Bluetooth KAPALI! Lütfen Bluetooth'u açın.")
                return
            }
            advertiser = adapter.bluetoothLeAdvertiser
            if (advertiser == null) {
                Log.e("BLE", "BluetoothLeAdvertiser NULL! Cihaz BLE yayınını desteklemiyor.")
                return
            }

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(false)
                .build()

            val advertiseData = buildAdvertiseData()
            val scanResponse = buildScanResponse()

            currentCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d("BLE", "✓ BLE yayın BAŞLADI — session=$sessionId | checkin=$checkinId")
                }

                override fun onStartFailure(errorCode: Int) {
                    val reason = when (errorCode) {
                        ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                        ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                        ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                        ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                        ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                        else -> "UNKNOWN"
                    }
                    Log.e("BLE", "✗ BLE yayın BAŞARISIZ! errorCode=$errorCode ($reason)")
                }
            }

            Log.d("BLE", "startAdvertising çağrılıyor (advertiseData + scanResponse)...")
            advertiser?.startAdvertising(settings, advertiseData, scanResponse, currentCallback)

        } catch (e: SecurityException) {
            Log.e("BLE", "BLE yayın izin hatası: ${e.message}", e)
        } catch (e: Exception) {
            Log.e("BLE", "startAdvertising HATA: ${e.message}", e)
        }
    }

    private fun restartAdvertising() {
        try {
            currentCallback?.let { advertiser?.stopAdvertising(it) }
        } catch (e: SecurityException) {
            Log.e("BLE", "stopAdvertising izin hatası: ${e.message}")
        }
        startAdvertising()
    }

    /**
     * Ana yayın paketi (max 31 byte):
     *   - Flags: 3 byte
     *   - 16-bit Service UUID list: 4 byte  (filtre eşleşmesi için)
     *   - Service Data (16-bit UUID + session_id string): 4 + ~10 = ~14 byte
     *   - Toplam: ~21 byte ✓
     */
    private fun buildAdvertiseData(): AdvertiseData {
        val builder = AdvertiseData.Builder()
            .addServiceUuid(SERVICE_UUID)
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)

        if (sessionId.isNotBlank()) {
            val sessionBytes = stringToBytes(sessionId)
            builder.addServiceData(SERVICE_UUID, sessionBytes)
            Log.d("BLE", "AdvertiseData: serviceUuid + sessionId '${sessionId}' (${sessionBytes.size} byte)")
        } else {
            Log.w("BLE", "Session ID boş — sadece UUID yayınlanıyor")
        }
        return builder.build()
    }

    /**
     * Scan Response paketi (max 31 byte):
     *   - Service Data (CHECKIN_UUID 16-bit + checkin_id string): 4 + ~10 = ~14 byte
     *   - Toplam: ~14 byte ✓
     */
    private fun buildScanResponse(): AdvertiseData {
        val builder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)

        if (checkinId.isNotBlank()) {
            val checkinBytes = stringToBytes(checkinId)
            builder.addServiceData(CHECKIN_UUID, checkinBytes)
            Log.d("BLE", "ScanResponse: checkinId '${checkinId}' (${checkinBytes.size} byte)")
        } else {
            Log.w("BLE", "Checkin ID boş — scan response veri içermiyor")
        }
        return builder.build()
    }

    // ────────────────────────────── Notification ──────────────────────────────

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            CHANNEL_ID, "Yoklama BLE Yayını", NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Yoklama Aktif")
            .setContentText("Ders devam ediyor — Bluetooth yayını açık")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }

    // ────────────────────────────── Lifecycle ──────────────────────────────

    override fun onDestroy() {
        try {
            currentCallback?.let { advertiser?.stopAdvertising(it) }
            Log.d("BLE", "BLE yayın durduruldu (onDestroy)")
        } catch (e: SecurityException) {
            Log.e("BLE", "stopAdvertising izin hatası: ${e.message}")
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
