package com.tekcizgi

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.tekcizgi.solver.Cell
import com.tekcizgi.solver.TekCizgiSolver

class TekCizgiAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButtons()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showFloatingButtons() {
        val solveButton = Button(this).apply {
            text = "Coz"
            setOnClickListener { onSolveClicked() }
        }
        val scanButton = Button(this).apply {
            text = "Tara"
            setOnClickListener { onScanClicked() }
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(scanButton)
            addView(solveButton)
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

        windowManager.addView(container, params)
    }

    // --- TARAMA (diagnostic) ---

    private fun onScanClicked() {
        val root = rootInActiveWindow
        if (root == null) {
            showTextOverlay("HATA: rootInActiveWindow bos.")
            return
        }
        val sb = StringBuilder()
        dumpNode(root, sb, 0)
        if (sb.isEmpty()) {
            sb.append("Hicbir metin bulunamadi.")
        }
        showTextOverlay(sb.toString())
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank() || !desc.isNullOrBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            sb.append("[${node.className?.toString()?.substringAfterLast('.')}] ")
            sb.append("text='${text ?: ""}' desc='${desc ?: ""}' ")
            sb.append("bounds=(${bounds.left},${bounds.top})-(${bounds.right},${bounds.bottom})\n")
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, sb, depth + 1) }
        }
    }

    private fun closeOverlay() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }
        overlayView = null
    }

    private fun showTextOverlay(content: String) {
        closeOverlay()

        val closeButton = Button(this).apply {
            text = "X KAPAT"
            setOnClickListener { closeOverlay() }
        }

        val tv = TextView(this).apply {
            text = content
            setTextColor(Color.WHITE)
            textSize = 11f
            setPadding(24, 24, 24, 24)
        }
        val scrollView = ScrollView(this).apply {
            addView(tv)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            addView(closeButton)
            addView(scrollView)
        }

        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.92).toInt(),
            (resources.displayMetrics.heightPixels * 0.75).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            0,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        windowManager.addView(rootLayout, params)
        overlayView = rootLayout

        // GUVENLIK: butona basilmasa bile 20 saniye sonra otomatik kapanir
        mainHandler.postDelayed({ closeOverlay() }, 20000)
    }

    // --- COZME (hala test verisiyle) ---

    private fun onSolveClicked() {
        Toast.makeText(this, "Buton calisti!", Toast.LENGTH_SHORT).show()
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
        val strokeDescription = GestureDescription.StrokeDescription(gesturePath, 0, durationMs)
        val gestureDescription = GestureDescription.Builder().addStroke(strokeDescription).build()

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
