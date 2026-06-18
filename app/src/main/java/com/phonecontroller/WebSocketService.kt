package com.phonecontroller

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.phonecontroller.WebSocketManager.ConnectionStatus

class WebSocketService : Service() {

    companion object {
        const val TAG = "WebSocketService"
        const val CHANNEL_ID = "phonecontroller_ws"
        const val NOTIFICATION_ID = 1
        const val ACTION_CONNECT = "com.phonecontroller.CONNECT"
        const val ACTION_DISCONNECT = "com.phonecontroller.DISCONNECT"
        const val EXTRA_URL = "server_url"
        const val BROADCAST_STATUS = "com.phonecontroller.STATUS"
        const val BROADCAST_LOG = "com.phonecontroller.LOG"
        const val EXTRA_STATUS = "connection_status"
        const val EXTRA_LOG = "log_message"
    }

    private var webSocketManager: WebSocketManager? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_STICKY
                Log.i(TAG, "Connecting to $url")
                connect(url)
            }
            ACTION_DISCONNECT -> {
                Log.i(TAG, "Disconnecting")
                disconnect()
            }
        }
        return START_STICKY
    }

    override fun onBind(p0: Intent?): IBinder? = null

    override fun onDestroy() {
        webSocketManager?.disconnect()
        webSocketManager = null
        Log.i(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun connect(url: String) {
        disconnect()

        val notification = buildNotification("Connecting...")
        startForeground(NOTIFICATION_ID, notification)

        webSocketManager = WebSocketManager(
            serverUrl = url,
            onStatusChange = { status ->
                val displayText = when (status) {
                    ConnectionStatus.DISCONNECTED -> "Disconnected"
                    ConnectionStatus.CONNECTING -> "Connecting..."
                    ConnectionStatus.CONNECTED -> "Connected"
                }
                updateNotification(displayText)

                val notification2 = buildNotification(displayText)
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.notify(NOTIFICATION_ID, notification2)

                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_STATUS).putExtra(EXTRA_STATUS, status.name))
            },
            onLog = { msg ->
                LocalBroadcastManager.getInstance(this).sendBroadcast(Intent(BROADCAST_LOG).putExtra(EXTRA_LOG, msg))
            }
        )

        webSocketManager!!.connect()
    }

    private fun disconnect() {
        webSocketManager?.disconnect()
        webSocketManager = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WebSocket Connection",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows PhoneController WebSocket connection status"
            setShowBadge(false)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PhoneController")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val notification = buildNotification(text)
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }
}
