package com.phonecontroller

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.phonecontroller.models.CommandResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class WebSocketManager(
    private val serverUrl: String,
    private val onStatusChange: (ConnectionStatus) -> Unit,
    private val onLog: (String) -> Unit
) {

    enum class ConnectionStatus {
        DISCONNECTED, CONNECTING, CONNECTED
    }

    companion object {
        private const val TAG = "WebSocketManager"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var commandParser: CommandParser? = null
    private val handler = Handler(Looper.getMainLooper())

    fun connect() {
        disconnect()
        onStatusChange(ConnectionStatus.CONNECTING)
        onLog("Connecting to $serverUrl ...")
        commandParser = CommandParser

        val request = Request.Builder()
            .url(serverUrl)
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Connected to $serverUrl")
                onStatusChange(ConnectionStatus.CONNECTED)
                onLog("Connected")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "Received: $text")
                onLog("Received: $text")
                handleMessage(text) { response ->
                    val responseJson = json.encodeToString(response)
                    Log.i(TAG, "Sending: $responseJson")
                    onLog("Sent: $responseJson")
                    webSocket.send(responseJson)
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closing: $code $reason")
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Closed: $code $reason")
                onStatusChange(ConnectionStatus.DISCONNECTED)
                onLog("Disconnected: $reason")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure", t)
                onStatusChange(ConnectionStatus.DISCONNECTED)
                onLog("Connection failed: ${t.message}")
            }
        })
    }

    fun disconnect() {
        webSocket?.close(1000, "Client closing")
        webSocket = null
        onStatusChange(ConnectionStatus.DISCONNECTED)
        onLog("Disconnected")
    }

    private fun handleMessage(text: String, sendResponse: (CommandResponse) -> Unit) {
        val parseResult = commandParser?.parse(text)
        if (parseResult == null) {
            sendResponse(CommandResponse(id = "unknown", ok = false, error = "no_parser"))
            return
        }

        parseResult.onFailure { error ->
            sendResponse(
                CommandResponse(id = extractId(text), ok = false, error = error.message ?: "parse_error")
            )
            return
        }

        val cmd = parseResult.getOrThrow()

        if (cmd.type == "wait") {
            handler.postDelayed({
                sendResponse(CommandResponse(id = cmd.id, ok = true, message = "waited ${cmd.ms}ms"))
            }, cmd.ms!!.toLong())
            return
        }

        val accessibilityService = AccessibilityController.service

        if (accessibilityService == null) {
            sendResponse(
                CommandResponse(id = cmd.id, ok = false, error = "accessibility_service_not_enabled")
            )
            return
        }

        when (cmd.type) {
            "tap" -> {
                accessibilityService.executeTap(cmd.x!!, cmd.y!!) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "swipe" -> {
                val duration = cmd.duration ?: 500
                accessibilityService.executeSwipe(
                    cmd.x1!!, cmd.y1!!, cmd.x2!!, cmd.y2!!, duration
                ) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "back" -> {
                accessibilityService.executeBack { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "home" -> {
                accessibilityService.executeHome { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "get_ui_tree" -> {
                val nodes = accessibilityService.getUiTree()
                sendResponse(
                    CommandResponse(id = cmd.id, ok = true, message = "ui_tree_returned", nodes = nodes)
                )
            }
            "click_text" -> {
                accessibilityService.clickByText(cmd.text!!) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "click_description" -> {
                accessibilityService.clickByDescription(cmd.description!!) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "type" -> {
                accessibilityService.executeType(cmd.text!!) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "long_press" -> {
                val duration = cmd.duration ?: 800
                accessibilityService.executeLongPress(cmd.x!!, cmd.y!!, duration) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "keyevent" -> {
                accessibilityService.executeKeyEvent(cmd.key!!) { success, msg ->
                    sendResponse(CommandResponse(id = cmd.id, ok = success, message = msg))
                }
            }
            "get_state" -> {
                val state = accessibilityService.getCurrentState()
                sendResponse(CommandResponse(id = cmd.id, ok = true, message = "state_returned", state = state))
            }
            else -> {
                sendResponse(
                    CommandResponse(id = cmd.id, ok = false, error = "unknown_command_type")
                )
            }
        }
    }

    private fun extractId(raw: String): String {
        return try {
            val obj = json.decodeFromString<Map<String, String>>(raw)
            obj["id"] ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}
