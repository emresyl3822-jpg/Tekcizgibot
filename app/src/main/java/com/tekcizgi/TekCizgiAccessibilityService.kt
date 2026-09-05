package com.tekcizgi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import kotlin.random.Random

class TekCizgiAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var buttonView: Button? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var isRunning = false
    private var currentStroke: GestureDescription.StrokeDescription? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showButton() {
        val button = Button(this).apply {
            text = "Ac"
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                        isDragging = true
                    }
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try { windowManager.updateViewLayout(v, params) } catch (e: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        toggleRunning()
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager.addView(button, params)
            buttonView = button
            buttonParams = params
        } catch (e: Exception) {
            Log.e("AntiAfk", "Buton eklenemedi: ${e.message}")
        }
    }

    private fun toggleRunning() {
        if (isRunning) {
            stopLoop()
        } else {
            startLoop()
        }
    }

    private fun startLoop() {
        isRunning = true
        buttonView?.text = "Kapat"
        currentStroke = null
        runCycle(goingDown = true)
    }

    private fun stopLoop() {
        isRunning = false
        buttonView?.text = "Ac"
        val prev = currentStroke
        if (prev != null) {
            val releasePath = Path()
            val cx = resources.displayMetrics.widthPixels / 2f
            val cy = resources.displayMetrics.heightPixels / 2f
            releasePath.moveTo(cx, cy)
            releasePath.lineTo(cx, cy)
            val releaseStroke = prev.continueStroke(releasePath, 0, 50, false)
            val gesture = GestureDescription.Builder().addStroke(releaseStroke).build()
            dispatchGesture(gesture, null, null)
        }
        currentStroke = null
    }

    private fun runCycle(goingDown: Boolean) {
        if (!isRunning) return

        val cx = resources.displayMetrics.widthPixels / 2f
        val cy = resources.displayMetrics.heightPixels / 2f
        val offset = 300f
        val topY = cy - offset / 2f
        val bottomY = cy + offset / 2f

        val fromY = if (goingDown) topY else bottomY
        val toY = if (goingDown) bottomY else topY

        val movePath = Path()
        movePath.moveTo(cx, fromY)
        movePath.lineTo(cx, toY)
        val moveDuration = 700L

        val moveStroke = if (currentStroke == null) {
            GestureDescription.StrokeDescription(movePath, 0, moveDuration, true)
        } else {
            currentStroke!!.continueStroke(movePath, 0, moveDuration, true)
        }

        val moveGesture = GestureDescription.Builder().addStroke(moveStroke).build()
        val dispatched = dispatchGesture(moveGesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                if (!isRunning) return
                currentStroke = moveStroke

                val waitMs = Random.nextLong(3000, 5001)
                val waitPath = Path()
                waitPath.moveTo(cx, toY)
                waitPath.lineTo(cx, toY)
                val waitStroke = moveStroke.continueStroke(waitPath, 0, waitMs, true)
                val waitGesture = GestureDescription.Builder().addStroke(waitStroke).build()

                val waitDispatched = dispatchGesture(waitGesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        currentStroke = waitStroke
                        if (isRunning) {
                            runCycle(goingDown = !goingDown)
                        }
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.e("AntiAfk", "Bekleme iptal edildi")
                        if (isRunning) mainHandler.postDelayed({ runCycle(!goingDown) }, 500)
                    }
                }, null)

                if (!waitDispatched) {
                    Log.e("AntiAfk", "Bekleme dispatch basarisiz")
                    if (isRunning) mainHandler.postDelayed({ runCycle(!goingDown) }, 500)
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e("AntiAfk", "Hareket iptal edildi")
                currentStroke = null
                if (isRunning) mainHandler.postDelayed({ runCycle(goingDown) }, 500)
            }
        }, null)

        if (!dispatched) {
            Log.e("AntiAfk", "Hareket dispatch basarisiz")
            currentStroke = null
            if (isRunning) mainHandler.postDelayed({ runCycle(goingDown) }, 500)
        }
    }
}
