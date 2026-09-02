package com.tekcizgi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import androidx.annotation.RequiresApi
import com.tekcizgi.solver.Cell
import com.tekcizgi.solver.TekCizgiSolver

class TekCizgiAccessibilityService : AccessibilityService() {

    private var floatingButton: View? = null
    private lateinit var windowManager: WindowManager

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButton()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showFloatingButton() {
        val button = Button(this).apply {
            text = "Coz"
            setOnClickListener { onSolveClicked() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 24
            y = 200
        }

        windowManager.addView(button, params)
        floatingButton = button
    }

    private fun onSolveClicked() {
        val rows = 5
        val cols = 5
        val checkpoints = mapOf(
            Cell(3, 3) to 1,
            Cell(0, 0) to 2,
            Cell(1, 1) to 3,
            Cell(4, 4) to 4
        )
        val gridLeft = 130f
        val gridTop = 1815f
        val cellSize = 213f

        val solver = TekCizgiSolver(rows, cols, checkpoints)
        val path = solver.solve()

        if (path == null) {
            Log.e("TekCizgiBot", "Cozum bulunamadi")
            return
        }

        drawPathOnScreen(path, gridLeft, gridTop, cellSize)
    }

    private fun cellCenter(cell: Cell, gridLeft: Float, gridTop: Float, cellSize: Float): Pair<Float, Float> {
        val x = gridLeft + cell.col * cellSize + cellSize / 2f
        val y = gridTop + cell.row * cellSize + cellSize / 2f
        return x to y
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun drawPathOnScreen(path: List<Cell>, gridLeft: Float, gridTop: Float, cellSize: Float) {
        val gesturePath = Path()
        val (startX, startY) = cellCenter(path[0], gridLeft, gridTop, cellSize)
        gesturePath.moveTo(startX, startY)
        for (i in 1 until path.size) {
            val (x, y) = cellCenter(path[i], gridLeft, gridTop, cellSize)
            gesturePath.lineTo(x, y)
        }

        val durationMs = (path.size * 80).toLong().coerceAtLeast(500)

        val strokeDescription = GestureDescription.StrokeDescription(
            gesturePath, 0, durationMs
        )
        val gestureDescription = GestureDescription.Builder()
            .addStroke(strokeDescription)
            .build()

        dispatchGesture(gestureDescription, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d("TekCizgiBot", "Cizim tamamlandi")
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.e("TekCizgiBot", "Cizim iptal edildi")
            }
        }, null)
    }
}
