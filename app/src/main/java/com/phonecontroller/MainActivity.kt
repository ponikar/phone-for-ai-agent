package com.phonecontroller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.phonecontroller.WebSocketService.Companion.BROADCAST_LOG
import com.phonecontroller.WebSocketService.Companion.BROADCAST_STATUS
import com.phonecontroller.WebSocketService.Companion.EXTRA_LOG
import com.phonecontroller.WebSocketService.Companion.EXTRA_STATUS
import com.phonecontroller.WebSocketManager.ConnectionStatus

class MainActivity : AppCompatActivity() {

    private lateinit var accessibilityStatusText: TextView
    private lateinit var connectionStatusText: TextView
    private lateinit var logText: TextView
    private lateinit var serverUrlInput: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private lateinit var openSettingsButton: MaterialButton

    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        accessibilityStatusText = findViewById(R.id.accessibilityStatusText)
        connectionStatusText = findViewById(R.id.connectionStatusText)
        logText = findViewById(R.id.logText)
        serverUrlInput = findViewById(R.id.serverUrlInput)
        connectButton = findViewById(R.id.connectButton)
        openSettingsButton = findViewById(R.id.openAccessibilitySettingsButton)

        openSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        connectButton.setOnClickListener {
            toggleConnection()
        }

        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(statusReceiver, IntentFilter(BROADCAST_STATUS))
            registerReceiver(logReceiver, IntentFilter(BROADCAST_LOG))
        }
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(statusReceiver)
            unregisterReceiver(logReceiver)
        }
        super.onDestroy()
    }

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra(EXTRA_STATUS) ?: return
            runOnUiThread {
                when (ConnectionStatus.valueOf(status)) {
                    ConnectionStatus.CONNECTED -> {
                        connectionStatusText.text = getString(R.string.connected)
                        connectionStatusText.setTextColor(0xFF4CAF50.toInt())
                        connectButton.text = getString(R.string.disconnect)
                        isConnected = true
                    }
                    ConnectionStatus.CONNECTING -> {
                        connectionStatusText.text = getString(R.string.connecting)
                        connectionStatusText.setTextColor(0xFFFFA500.toInt())
                    }
                    ConnectionStatus.DISCONNECTED -> {
                        connectionStatusText.text = getString(R.string.disconnected)
                        connectionStatusText.setTextColor(0xFFFF0000.toInt())
                        connectButton.text = getString(R.string.connect)
                        isConnected = false
                    }
                }
            }
        }
    }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val msg = intent.getStringExtra(EXTRA_LOG) ?: return
            runOnUiThread { appendLog(msg) }
        }
    }

    private fun updateAccessibilityStatus() {
        val isEnabled = isAccessibilityServiceEnabled(this, PhoneAccessibilityService::class.java)
        accessibilityStatusText.text = getString(if (isEnabled) R.string.enabled else R.string.disabled)
        accessibilityStatusText.setTextColor(
            if (isEnabled) 0xFF4CAF50.toInt() else 0xFFFF0000.toInt()
        )
    }

    private fun toggleConnection() {
        if (isConnected) {
            startService(Intent(this, WebSocketService::class.java).apply {
                action = WebSocketService.ACTION_DISCONNECT
            })
            return
        }

        val url = serverUrlInput.text?.toString()?.trim()
        if (url.isNullOrBlank()) {
            appendLog("Please enter a server URL")
            return
        }

        startService(Intent(this, WebSocketService::class.java).apply {
            action = WebSocketService.ACTION_CONNECT
            putExtra(WebSocketService.EXTRA_URL, url)
        })
    }

    private fun appendLog(msg: String) {
        logText.append("$msg\n")
        val scrollParent = logText.parent as? ScrollView
        scrollParent?.fullScroll(ScrollView.FOCUS_DOWN)
    }

    companion object {
        fun isAccessibilityServiceEnabled(context: Context, service: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
            val pref = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            return pref.split(':').any { it.equals(context.packageName + "/" + service.name, ignoreCase = true) }
        }
    }
}
