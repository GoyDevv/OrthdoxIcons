package app.goimium.browser

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.PointerIcon
import android.webkit.WebView

class GoimiumWebView(context: Context) : WebView(context) {

    var pointerLockActive: Boolean = false
        private set

    private var lastPhysicalGestureMs: Long = 0L
    private var lastClientX = 0.0
    private var lastClientY = 0.0
    private var buttonsMask = 0

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_ARROW))
        }
    }

    fun hasRecentUserGesture(): Boolean {
        return SystemClock.uptimeMillis() - lastPhysicalGestureMs <= USER_GESTURE_WINDOW_MS
    }

    fun enablePointerLock(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (!hasRecentUserGesture()) return false

        pointerLockActive = true
        requestFocus()
        requestPointerCapture()
        setPointerIcon(PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL))
        return true
    }

    fun disablePointerLock(notifyPage: Boolean = false) {
        val wasLocked = pointerLockActive
        pointerLockActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            releasePointerCapture()
            setPointerIcon(null)
        }
        buttonsMask = 0
        if (notifyPage && wasLocked) {
            evaluateJavascript(
                "window.__goimiumPointerLockLost && window.__goimiumPointerLockLost();",
                null
            )
        }
    }

    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? {
        if (pointerLockActive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
        }
        return super.onResolvePointerIcon(event, pointerIndex)
    }

    override fun onCapturedPointerEvent(event: MotionEvent): Boolean {
        if (!pointerLockActive) return super.onCapturedPointerEvent(event)
        if (isMouseSource(event)) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_BUTTON_PRESS -> {
                    lastPhysicalGestureMs = SystemClock.uptimeMillis()
                    dispatchButtons(event, true)
                    dispatchWheel(event)
                    return true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_BUTTON_RELEASE -> {
                    dispatchButtons(event, false)
                    return true
                }
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_HOVER_MOVE -> {
                    dispatchRelativeMove(event)
                    dispatchWheel(event)
                    return true
                }
                MotionEvent.ACTION_SCROLL -> {
                    dispatchWheel(event)
                    return true
                }
            }
        }
        return super.onCapturedPointerEvent(event)
    }

    override fun onPointerCaptureChange(hasCapture: Boolean) {
        super.onPointerCaptureChange(hasCapture)
        if (!hasCapture && pointerLockActive) {
            disablePointerLock(notifyPage = true)
        }
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        val isMouse = isMouseSource(event)

        if (isMouse) {
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_MOVE -> {
                    if (pointerLockActive) {
                        dispatchRelativeMove(event)
                        dispatchWheel(event)
                        return true
                    }
                    updateLastAbsolutePosition(event)
                }
                MotionEvent.ACTION_BUTTON_PRESS -> lastPhysicalGestureMs = SystemClock.uptimeMillis()
                MotionEvent.ACTION_SCROLL -> {
                    if (pointerLockActive) {
                        dispatchWheel(event)
                        return true
                    }
                }
            }
        }

        return super.onGenericMotionEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMouseSource(event) && (event.actionMasked == MotionEvent.ACTION_DOWN ||
                event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)) {
            lastPhysicalGestureMs = SystemClock.uptimeMillis()
            updateLastAbsolutePosition(event)
        }
        return super.onTouchEvent(event)
    }

    private fun dispatchRelativeMove(event: MotionEvent) {
        val dx = relativeAxis(event, MotionEvent.AXIS_RELATIVE_X)
        val dy = relativeAxis(event, MotionEvent.AXIS_RELATIVE_Y)
        if (dx == 0.0 && dy == 0.0) return
        dispatchJsPointerEvent("move", dx, dy, 0, buttonsMask)
    }

    private fun dispatchButtons(event: MotionEvent, down: Boolean) {
        val action = if (down) "down" else "up"
        val rawButton = if (event.actionButton != 0) event.actionButton else MotionEvent.BUTTON_PRIMARY
        val button = when (rawButton) {
            MotionEvent.BUTTON_PRIMARY -> 0
            MotionEvent.BUTTON_SECONDARY -> 2
            MotionEvent.BUTTON_TERTIARY -> 1
            MotionEvent.BUTTON_BACK -> 3
            MotionEvent.BUTTON_FORWARD -> 4
            else -> 0
        }
        if (action == "down") {
            buttonsMask = buttonsMask or (1 shl button)
        } else {
            buttonsMask = buttonsMask and (1 shl button).inv()
        }
        dispatchJsPointerEvent(action, 0.0, 0.0, button, buttonsMask)
    }

    private fun dispatchWheel(event: MotionEvent) {
        val v = event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble()
        val h = event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble()
        if (v == 0.0 && h == 0.0) return
        evaluateJavascript(
            "window.__goimiumPointerLockWheel && window.__goimiumPointerLockWheel(${h} * 40, ${-v} * 40);",
            null
        )
    }

    private fun dispatchJsPointerEvent(
        kind: String,
        dx: Double,
        dy: Double,
        button: Int,
        buttons: Int,
    ) {
        evaluateJavascript(
            "window.__goimiumPointerLockEvent && window.__goimiumPointerLockEvent(" +
                "${quote(kind)},$dx,$dy,$button,$buttons,${lastClientX},${lastClientY});",
            null
        )
    }

    private fun updateLastAbsolutePosition(event: MotionEvent) {
        lastClientX = event.x.toDouble()
        lastClientY = event.y.toDouble()
        if (event.actionMasked == MotionEvent.ACTION_DOWN ||
            event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
        ) {
            lastPhysicalGestureMs = SystemClock.uptimeMillis()
        }
    }

    private fun relativeAxis(event: MotionEvent, axis: Int): Double {
        var total = 0.0
        for (i in 0 until event.historySize) {
            total += event.getHistoricalAxisValue(axis, i).toDouble()
        }
        total += event.getAxisValue(axis).toDouble()
        return total
    }

    private fun quote(s: String): String = "'" + s.replace("'", "\'") + "'"

    private fun isMouseSource(event: MotionEvent): Boolean {
        return (event.source and InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                (event.source and InputDevice.SOURCE_MOUSE_RELATIVE) == InputDevice.SOURCE_MOUSE_RELATIVE)
    }

    companion object {
        private const val USER_GESTURE_WINDOW_MS = 1500L
    }
}
