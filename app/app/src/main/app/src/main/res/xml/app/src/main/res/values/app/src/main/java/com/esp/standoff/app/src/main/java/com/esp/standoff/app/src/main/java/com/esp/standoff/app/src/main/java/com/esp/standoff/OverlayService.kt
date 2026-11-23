package com.esp.standoff

import android.accessibilityservice.AccessibilityService
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

class OverlayService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: OverlayService? = null

        fun updateDetections(dets: List<Detection>) {
            instance?.overlayView?.let {
                it.detections = dets
                it.postInvalidate()
            }
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: OverlayView

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, params)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        if (::overlayView.isInitialized) {
            try { windowManager.removeView(overlayView) } catch (_: Exception) {}
        }
        super.onDestroy()
    }

    class OverlayView(context: android.content.Context) : View(context) {
        var detections: List<Detection> = emptyList()
        private val paint = Paint().apply {
            color = Color.GREEN
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            for (d in detections) {
                canvas.drawRect(d.x1, d.y1, d.x2, d.y2, paint)
            }
        }
    }
}
