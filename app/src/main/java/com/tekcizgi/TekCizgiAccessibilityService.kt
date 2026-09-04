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
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class DetectedNumber(val value: Int, val centerX: Float, val centerY: Float, val width: Float, val height: Float)

class TekCizgiAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    @Volatile private var isSolving = false
    private var overlayView: View? = null
    private var buttonContainer: View? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingButtons()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showFloatingButtons() {
        buttonContainer?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
        }

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
        buttonContainer = container
    }

    private data class TextNode(val text: String, val bounds: Rect)

    private fun collectTextNodes(node: AccessibilityNodeInfo, out: MutableList<TextNode>) {
        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            out.add(TextNode(text, bounds))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTextNodes(it, out) }
        }
    }

    private fun onScanClicked() {
        val root = rootInActiveWindow
        if (root == null) {
            showTextOverlay("HATA: rootInActiveWindow bos.")
            return
        }
        val nodes = mutableListOf<TextNode>()
        collectTextNodes(root, nodes)
        val sb = StringBuilder()
        for (n in nodes) {
            sb.append("text='${n.text}' bounds=(${n.bounds.left},${n.bounds.top})-(${n.bounds.right},${n.bounds.bottom})\n")
        }
        showTextOverlay(if (sb.isEmpty()) "Hicbir metin bulunamadi." else sb.toString())
    }

    private fun showMessage(text: String, durationMs: Long = 2500) {
        val tv = TextView(this).apply {
            this.text = text
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#DD000000"))
            setPadding(28, 20, 28, 20)
            textSize = 13f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM
            y = 180
        }
        try {
            windowManager.addView(tv, params)
            mainHandler.postDelayed({
                try { windowManager.removeView(tv) } catch (e: Exception) {}
            }, durationMs)
        } catch (e: Exception) {
            Log.e("TekCizgiBot", "showMessage basarisiz: ${e.message}")
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
        val scrollView = ScrollView(this).apply { addView(tv) }
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
        ).apply { gravity = Gravity.CENTER }
        windowManager.addView(rootLayout, params)
        overlayView = rootLayout
        mainHandler.postDelayed({ closeOverlay() }, 20000)
    }

    private fun onSolveClicked() {
        if (isSolving) {
            showMessage("Zaten calisiyor, bekle...")
            return
        }
        isSolving = true

        Thread {
            try {
                val root = rootInActiveWindow
                if (root == null) {
                    mainHandler.post {
                        showMessage("Ekran okunamadi")
                        isSolving = false
                    }
                    return@Thread
                }
                val nodes = mutableListOf<TextNode>()
                collectTextNodes(root, nodes)

                val headerNode = nodes.firstOrNull { it.text.contains("kare") }
                val instructionNode = nodes.firstOrNull { it.text.startsWith("Parmağını") }

                if (headerNode == null || instructionNode == null) {
                    mainHandler.post {
                        showMessage("Baslik/talimat metni bulunamadi")
                        isSolving = false
                    }
                    return@Thread
                }

                val kareRegex = Regex("""/(\d+)\s*kare""")
                val totalCellsMatch = kareRegex.find(headerNode.text)
                if (totalCellsMatch == null) {
                    mainHandler.post {
                        showMessage("Hucre sayisi okunamadi")
                        isSolving = false
                    }
                    return@Thread
                }
                val totalCells = totalCellsMatch.groupValues[1].toInt()
                val gridSize = sqrt(totalCells.toDouble()).roundToInt()

                val screenWidth = resources.displayMetrics.widthPixels.toFloat()
                val gridTop = headerNode.bounds.bottom.toFloat()
                val gridBottomLimit = instructionNode.bounds.top.toFloat()
                val gridHeightPx = gridBottomLimit - gridTop
                if (gridHeightPx < 50f) {
                    mainHandler.post {
                        showMessage("Izgara alani hesaplanamadi")
                        isSolving = false
                    }
                    return@Thread
                }
                val cellSize = gridHeightPx / gridSize
                val gridWidthPx = cellSize * gridSize
                val gridLeft = (screenWidth - gridWidthPx) / 2f

                val digitRegex = Regex("""^\d{1,2}$""")
                val candidates = nodes.filter {
                    digitRegex.matches(it.text) &&
                    it.bounds.centerY() in gridTop.toInt()..gridBottomLimit.toInt()
                }.map {
                    DetectedNumber(
                        value = it.text.toInt(),
                        centerX = it.bounds.centerX().toFloat(),
                        centerY = it.bounds.centerY().toFloat(),
                        width = it.bounds.width().toFloat(),
                        height = it.bounds.height().toFloat()
                    )
                }

                if (candidates.isEmpty()) {
                    mainHandler.post {
                        showMessage("Kontrol noktalari bulunamadi")
                        isSolving = false
                    }
                    return@Thread
                }

                val checkpoints = mutableMapOf<Cell, Int>()
                for (c in candidates) {
                    val col = ((c.centerX - gridLeft) / cellSize).toInt().coerceIn(0, gridSize - 1)
                    val row = ((c.centerY - gridTop) / cellSize).toInt().coerceIn(0, gridSize - 1)
                    checkpoints[Cell(row, col)] = c.value
                }

                if (!checkpoints.values.contains(1)) {
                    mainHandler.post {
                        showMessage("1 numarali nokta bulunamadi")
                        isSolving = false
                    }
                    return@Thread
                }

                val solver = TekCizgiSolver(gridSize, gridSize, checkpoints, timeoutMs = 4000L)
                val path = solver.solve()

                if (path == null) {
                    val msg = if (solver.timedOut) "Zaman asimi - cozum bulunamadi" else "Cozum bulunamadi (cp=${checkpoints.size})"
                    mainHandler.post {
                        showMessage(msg)
                        isSolving = false
                    }
                    return@Thread
                }

                mainHandler.post {
                    showMessage("Cozuldu! ${path.size} adim ciziliyor...")
                    drawPathOnScreen(path, gridLeft, gridTop, cellSize) {
                        isSolving = false
                        showFloatingButtons()
                    }
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showMessage("Hata: ${e.message}")
                    isSolving = false
                }
            }
        }.start()
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun drawPathOnScreen(
        path: List<Cell>,
        gridLeft: Float,
        gridTop: Float,
        cellSize: Float,
        onFinished: () -> Unit
    ) {
        fun cellToScreen(cell: Cell): Pair<Float, Float> {
            val x = gridLeft + cell.col * cellSize + cellSize / 2f
            val y = gridTop + cell.row * cellSize + cellSize / 2f
            return x to y
        }

        var finished = false
        fun finishOnce() {
            if (!finished) {
                finished = true
                onFinished()
            }
        }

        val chunkSize = 5
        var watchdog: Runnable? = null

        fun dispatchChunk(startIndex: Int, prevStroke: GestureDescription.StrokeDescription?) {
            if (startIndex >= path.size - 1) {
                watchdog?.let { mainHandler.removeCallbacks(it) }
                finishOnce()
                return
            }
            val endIndex = minOf(startIndex + chunkSize, path.size - 1)
            val segmentPath = Path()
            val (sx, sy) = cellToScreen(path[startIndex])
            segmentPath.moveTo(sx, sy)
            for (i in (startIndex + 1)..endIndex) {
                val (x, y) = cellToScreen(path[i])
                segmentPath.lineTo(x, y)
            }
            val segDuration = ((endIndex - startIndex) * 90L).coerceAtLeast(100)
            val willContinue = endIndex < path.size - 1

            val stroke = if (prevStroke == null) {
                GestureDescription.StrokeDescription(segmentPath, 0, segDuration, willContinue)
            } else {
                prevStroke.continueStroke(segmentPath, 0, segDuration, willContinue)
            }
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            watchdog = Runnable {
                Log.e("TekCizgiBot", "Parca $startIndex-$endIndex takildi, iptal ediliyor")
                showMessage("Cizim takildi, durduruldu")
                finishOnce()
            }
            mainHandler.postDelayed(watchdog!!, segDuration + 2000)

            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    watchdog?.let { mainHandler.removeCallbacks(it) }
                    mainHandler.post { dispatchChunk(endIndex, stroke) }
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    watchdog?.let { mainHandler.removeCallbacks(it) }
                    Log.e("TekCizgiBot", "Parca $startIndex-$endIndex iptal edildi")
                    mainHandler.post {
                        showMessage("Cizim iptal edildi ($startIndex/${path.size})")
                        finishOnce()
                    }
                }
            }, null)

            if (!dispatched) {
                watchdog?.let { mainHandler.removeCallbacks(it) }
                Log.e("TekCizgiBot", "dispatchGesture basarisiz (parca $startIndex)")
                showMessage("Cizim baslatilamadi, tekrar dene")
                finishOnce()
            }
        }

        dispatchChunk(0, null)
    }
}
