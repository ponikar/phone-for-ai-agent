package com.phonecontroller

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.phonecontroller.models.UiNode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

class PhoneAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "PhoneAccessibilityService"
        private const val MAX_NODES = 300
        private const val TAP_DURATION_MS = 50L
        private const val SWIPE_DEFAULT_DURATION_MS = 500
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val nodeCounter = AtomicInteger(0)

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected")
        AccessibilityController.register(this)
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        AccessibilityController.unregister()
        Log.i(TAG, "AccessibilityService destroyed")
    }

    fun executeTap(x: Int, y: Int, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            try {
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
                    .build()
                val result = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        callback(true, "tap executed at ($x, $y)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        callback(false, "tap cancelled at ($x, $y)")
                    }
                }, null)
                if (!result) {
                    callback(false, "tap dispatch failed at ($x, $y)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "tap error", e)
                callback(false, "tap error: ${e.message}")
            }
        }
    }

    fun executeSwipe(
        x1: Int, y1: Int, x2: Int, y2: Int,
        durationMs: Int = SWIPE_DEFAULT_DURATION_MS,
        callback: (Boolean, String) -> Unit
    ) {
        mainHandler.post {
            try {
                val path = Path().apply {
                    moveTo(x1.toFloat(), y1.toFloat())
                    lineTo(x2.toFloat(), y2.toFloat())
                }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.toLong()))
                    .build()
                val result = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        callback(true, "swipe executed ($x1,$y1)->($x2,$y2)")
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        callback(false, "swipe cancelled ($x1,$y1)->($x2,$y2)")
                    }
                }, null)
                if (!result) {
                    callback(false, "swipe dispatch failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "swipe error", e)
                callback(false, "swipe error: ${e.message}")
            }
        }
    }

    fun executeBack(callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            val result = performGlobalAction(GLOBAL_ACTION_BACK)
            callback(result, if (result) "back executed" else "back failed")
        }
    }

    fun executeHome(callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            val result = performGlobalAction(GLOBAL_ACTION_HOME)
            callback(result, if (result) "home executed" else "home failed")
        }
    }

    fun getUiTree(): List<UiNode> {
        return try {
            nodeCounter.set(0)
            val root = rootInActiveWindow ?: return emptyList()
            val result = mutableListOf<UiNode>()
            collectNodes(root, result)
            result
        } catch (e: Exception) {
            Log.e(TAG, "getUiTree error", e)
            emptyList()
        }
    }

    private fun collectNodes(node: AccessibilityNodeInfo, result: MutableList<UiNode>) {
        if (nodeCounter.getAndIncrement() >= MAX_NODES) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        result.add(
            UiNode(
                text = node.text?.toString()?.takeIf { it.isNotBlank() },
                description = node.contentDescription?.toString()?.takeIf { it.isNotBlank() },
                viewId = node.viewIdResourceName?.takeIf { it.isNotBlank() },
                className = node.className?.toString()?.takeIf { it.isNotBlank() },
                packageName = node.packageName?.toString()?.takeIf { it.isNotBlank() },
                clickable = node.isClickable,
                enabled = node.isEnabled,
                focused = node.isFocused,
                bounds = listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)
            )
        )

        for (i in 0 until node.childCount) {
            if (nodeCounter.get() >= MAX_NODES) break
            val child = node.getChild(i) ?: continue
            collectNodes(child, result)
            child.recycle()
        }
    }

    fun clickByText(text: String, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            try {
                val root = rootInActiveWindow
                if (root == null) {
                    callback(false, "no active window")
                    return@post
                }
                val target = findClickableNode(root, text, matchBy = { it.text?.toString() })
                if (target != null) {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    callback(true, "clicked text: $text")
                } else {
                    callback(false, "no clickable node found with text: $text")
                }
            } catch (e: Exception) {
                Log.e(TAG, "clickByText error", e)
                callback(false, "clickByText error: ${e.message}")
            }
        }
    }

    fun clickByDescription(description: String, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            try {
                val root = rootInActiveWindow
                if (root == null) {
                    callback(false, "no active window")
                    return@post
                }
                val target = findClickableNode(root, description, matchBy = { it.contentDescription?.toString() })
                if (target != null) {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    callback(true, "clicked description: $description")
                } else {
                    callback(false, "no clickable node found with description: $description")
                }
            } catch (e: Exception) {
                Log.e(TAG, "clickByDescription error", e)
                callback(false, "clickByDescription error: ${e.message}")
            }
        }
    }

    fun executeType(text: String, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            try {
                val root = rootInActiveWindow
                if (root == null) {
                    callback(false, "no active window")
                    return@post
                }
                val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focused == null) {
                    callback(false, "no focused input field found")
                    return@post
                }
                val args = Bundle()
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                val result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (result) {
                    callback(true, "typed text: $text")
                } else {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("type", text))
                    val pasted = focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    if (pasted) {
                        callback(true, "typed text (paste fallback): $text")
                    } else {
                        callback(false, "type failed: SET_TEXT and PASTE both failed")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "executeType error", e)
                callback(false, "type error: ${e.message}")
            }
        }
    }

    fun executeLongPress(x: Int, y: Int, durationMs: Int = 800, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            try {
                val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
                val gesture = GestureDescription.Builder()
                    .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.toLong()))
                    .build()
                val result = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        callback(true, "long_press executed at ($x, $y) for ${durationMs}ms")
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        callback(false, "long_press cancelled at ($x, $y)")
                    }
                }, null)
                if (!result) {
                    callback(false, "long_press dispatch failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "long_press error", e)
                callback(false, "long_press error: ${e.message}")
            }
        }
    }

    fun executeKeyEvent(key: Int, callback: (Boolean, String) -> Unit) {
        mainHandler.post {
            try {
                val globalAction = when (key) {
                    1 -> GLOBAL_ACTION_BACK
                    2 -> GLOBAL_ACTION_HOME
                    3 -> GLOBAL_ACTION_RECENTS
                    4 -> GLOBAL_ACTION_NOTIFICATIONS
                    6 -> GLOBAL_ACTION_QUICK_SETTINGS
                    7 -> GLOBAL_ACTION_POWER_DIALOG
                    9 -> GLOBAL_ACTION_LOCK_SCREEN
                    10 -> GLOBAL_ACTION_TAKE_SCREENSHOT
                    else -> -1
                }
                if (globalAction < 0) {
                    callback(false, "keyevent $key not supported. Keys: 1=BACK, 2=HOME, 3=RECENTS, 4=NOTIFICATIONS, 6=QUICK_SETTINGS, 7=POWER, 9=LOCK, 10=SCREENSHOT")
                    return@post
                }
                val result = performGlobalAction(globalAction)
                val label = when (key) {
                    1 -> "BACK"; 2 -> "HOME"; 3 -> "RECENTS"; 4 -> "NOTIFICATIONS"
                    6 -> "QUICK_SETTINGS"; 7 -> "POWER_DIALOG"; 9 -> "LOCK_SCREEN"; 10 -> "SCREENSHOT"
                    else -> "$key"
                }
                if (result) {
                    callback(true, "keyevent $label executed")
                } else {
                    callback(false, "keyevent $label failed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "keyevent error", e)
                callback(false, "keyevent error: ${e.message}")
            }
        }
    }

    fun getCurrentState(): Map<String, String> {
        val state = mutableMapOf<String, String>()
        try {
            val root = rootInActiveWindow
            if (root != null) {
                state["package"] = root.packageName?.toString() ?: "unknown"
                state["window"] = root.className?.toString() ?: "unknown"
            }
        } catch (_: Exception) {
            state["package"] = "unknown"
        }
        return state
    }

    private fun findClickableNode(
        node: AccessibilityNodeInfo,
        query: String,
        matchBy: (AccessibilityNodeInfo) -> String?
    ): AccessibilityNodeInfo? {
        val nodeText = matchBy(node)
        if (nodeText != null && (nodeText.equals(query, ignoreCase = true) || nodeText.contains(query, ignoreCase = true))) {
            if (node.isClickable) return node
            val parent = findClickableParent(node)
            if (parent != null) return parent
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findClickableNode(child, query, matchBy)
            if (result != null) {
                child.recycle()
                return result
            }
            child.recycle()
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return parent
            val grandParent = parent.parent
            if (grandParent != null) parent.recycle()
            parent = grandParent
        }
        return null
    }
}
