package com.jozy.geminitouch

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Bitmap
import android.graphics.Path
import android.hardware.HardwareBuffer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.Executor

class ScreenAgentService : AccessibilityService() {
    companion object {
        @Volatile var active: ScreenAgentService? = null
    }

    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
    override fun onDestroy() { if (active === this) active = null; super.onDestroy() }

    fun capture(callback: (Bitmap?) -> Unit) {
        if (Build.VERSION.SDK_INT < 30) { callback(null); return }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, Executor { it.run() }, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    var bitmap: Bitmap? = null
                    try {
                        val buffer: HardwareBuffer = screenshot.hardwareBuffer
                        bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                        buffer.close()
                    } catch (_: Exception) { bitmap = null }
                    callback(bitmap)
                }
                override fun onFailure(errorCode: Int) { callback(null) }
            })
        } catch (_: Exception) { callback(null) }
    }

    fun tap(x: Float, y: Float, durationMs: Long = 60L, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(30, 1000))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { callback?.invoke(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { callback?.invoke(false) }
        }, Handler(Looper.getMainLooper()))
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, durationMs: Long = 350L, callback: ((Boolean) -> Unit)? = null) {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(80, 2000))
        dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { callback?.invoke(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { callback?.invoke(false) }
        }, Handler(Looper.getMainLooper()))
    }

    fun typeText(value: String): Boolean {
        val node = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return try {
            val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
            val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            node.recycle()
            ok
        } catch (_: Exception) { false }
    }

    fun pressBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun pressHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
}
