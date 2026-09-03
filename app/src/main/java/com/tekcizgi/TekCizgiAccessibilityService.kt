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

    // --- ORTAK: agac tarama ---

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

    // --- TARAMA (diagnostic, elle kontrol icin) ---

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

    // --- GERCEK COZME: ekrani oku, hesapla, ciz ---

    private fun onSolveClicked() {
        val root = rootInActiveWindow
        if (root == null) {
            Toast.makeText(this, "Ekran okunamadi", Toast.LENGTH_SHORT).show()
            return
        }
        val nodes = mutableListOf<TextNode>()
        collectTextNodes(root, nodes)

        val headerNode = nodes.firstOrNull { it.text.contains("kare") }
        val instructionNode = nodes.firstOrNull { it.text.startsWith("Parmağını") }

        if (headerNode == null || instructionNode == null) {
            Toast.makeText(this, "Baslik/talimat metni bulunamadi", Toast.LENGTH_LONG).show()
            return
        }

        val kareRegex = Regex("""/(\d+)\s*kare""")
        val totalCellsMatch = kareRegex.find(headerNode.text)
        if (totalCellsMatch == null) {
            Toast.makeText(this, "Hucre sayisi okunamadi", Toast.LENGTH_LONG).show()
            return
        }
        val totalCells = totalCellsMatch.groupValues[1].toInt()
        val gridSize = sqrt(totalCells.toDouble()).roundToInt()

        val topLimit = headerNode.bounds.bottom
        val bottomLimit = instructionNode.bounds.top

        val digitRegex = Regex("""^\d{1,2}$""")
        val candidates = nodes.filter {
            digitRegex.matches(it.text) &&
            it.bounds.centerY() in topLimit..bottomLimit
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
            Toast.makeText(this, "Kontrol noktalari bulunamadi", Toast.LENGTH_LONG).show()
            return
        }

        val reference = candidates.firstOrNull { it.value == 1 }
        if (reference == null) {
            Toast.makeText(this, "1 numarali nokta bulunamadi", Toast.LENGTH_LONG).show()
            return
        }

        val cellSize = candidates.map { (it.width + it.height) / 2f }.average().toFloat()

        if (cellSize < 10f) {
            Toast.makeText(this, "Hucre boyutu hesaplanamadi", Toast.LENGTH_LONG).show()
            return
        }

        data class RelPos(val value: Int, val relRow: Int, val relCol: Int, val screenX: Float, val screenY: Float)
        val relList = candidates.map {
            val relCol = ((it.centerX - reference.centerX) / cellSize).roundToInt()
            val relRow = ((it.centerY - reference.centerY) / cellSize).roundToInt()
            RelPos(it.value, relRow, relCol, it.centerX, it.centerY)
        }

        val minRow = relList.minOf { it.relRow }
        val minCol = relList.minOf { it.relCol }

        val checkpoints = mutableMapOf<Cell, Int>()
        for (r in relList) {
            val cell = Cell(r.relRow - minRow, r.relCol - minCol)
            checkpoints[cell] = r.value
        }

        val maxRow = checkpoints.keys.maxOf { it.row }
        val maxCol = checkpoints.keys.maxOf { it.col }
        val rows = maxOf(gridSize, maxRow + 1)
        val cols = maxOf(gridSize, maxCol + 1)

        val solver = TekCizgiSolver(rows, cols, checkpoints)
        val path = solver.solve()

        if (path == null) {
            Toast.makeText(this, "Cozum bulunamadi (rows=$rows cols=$cols cp=${checkpoints.size})", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "Cozuldu! ${path.size} adim ciziliyor...", Toast.LENGTH_SHORT).show()

        val refRelRow = relList.first { it.value == 1 }.relRow - minRow
        val refRelCol = relList.first { it.value == 1 }.relCol - minCol

        drawPathOnScreen(path, reference.centerX, reference.centerY, refRelRow, refRelCol, cellSize)
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun drawPathOnScreen(
        path: List<Cell>,
        refScreenX: Float,
        refScreenY: Float,
        refRow: Int,
        refCol: Int,
        cellSize: Float
    ) {
        fun cellToScreen(cell: Cell): Pair<Float, Float> {
            val x = refScreenX + (cell.col - refCol) * cellSize
            val y = refScreenY + (cell.row - refRow) * cellSize
            return x to y
        }

        val gesturePath = Path()
        val (startX, startY) = cellToScreen(path[0])
        gesturePath.moveTo(startX, startY)
        for (i in 1 until path.size) {
            val (x, y) = cellToScreen(path[i])
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
