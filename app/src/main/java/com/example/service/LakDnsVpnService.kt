package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow

class LakDnsVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val ACTION_CONNECT = "com.example.service.CONNECT"
        const val ACTION_DISCONNECT = "com.example.service.DISCONNECT"
        const val EXTRA_DNS_IP = "com.example.service.EXTRA_DNS_IP"
        const val EXTRA_DNS_LABEL = "com.example.service.EXTRA_DNS_LABEL"

        const val CHANNEL_ID = "lakdns_channel"
        const val NOTIFICATION_ID = 485121

        val isRunning = MutableStateFlow(false)
        val activeIp = MutableStateFlow<String?>(null)
        val activeLabel = MutableStateFlow<String?>(null)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_DISCONNECT) {
            stopVpn()
            return START_NOT_STICKY
        }

        val dnsIp = intent?.getStringExtra(EXTRA_DNS_IP) ?: "1.1.1.1"
        val dnsLabel = intent?.getStringExtra(EXTRA_DNS_LABEL) ?: "Cloudflare"

        startVpn(dnsIp, dnsLabel)
        return START_STICKY
    }

    private fun startVpn(dnsIp: String, dnsLabel: String) {
        try {
            stopVpn() // close existing before starting new

            val builder = Builder()
                .setSession("LakDNS Connection")
                .addAddress("10.0.0.5", 32) // Use a virtual IP address
                .addDnsServer(dnsIp)

            // Specifically configure the interface to prevent standard traffic routing issues
            vpnInterface = builder.establish()

            activeIp.value = dnsIp
            activeLabel.value = dnsLabel
            isRunning.value = true

            // Trigger foreground notification
            startForeground(NOTIFICATION_ID, createNotification(dnsIp, dnsLabel))
            Log.d("LakDnsVpnService", "VPN established with DNS: $dnsIp")
        } catch (e: Exception) {
            Log.e("LakDnsVpnService", "Failed to start VPN: ", e)
            stopSelf()
        }
    }

    private fun stopVpn() {
        try {
            vpnInterface?.close()
            vpnInterface = null
        } catch (e: Exception) {
            Log.e("LakDnsVpnService", "Error closing interface: ", e)
        }
        activeIp.value = null
        activeLabel.value = null
        isRunning.value = false
        stopForeground(true)
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    private fun createNotification(ip: String, label: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val disconnectIntent = Intent(this, LakDnsVpnService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("لاکت دی‌ان‌اس متصل است | LakDNS Enabled")
            .setContentText("آی‌پی فعال: $label ($ip)")
            .setSmallIcon(android.R.drawable.ic_lock_lock) // System lock/key icon
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "قطع اتصال / Disconnect",
                disconnectPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "LakDNS Active Connection Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active DNS proxy connection details"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }
}
