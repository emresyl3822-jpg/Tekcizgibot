package com.tekcizgi.solver

data class Cell(val row: Int, val col: Int)

class TekCizgiSolver(
    private val rows: Int,
    private val cols: Int,
    private val checkpoints: Map<Cell, Int>
) {
    private val totalCells = rows * cols
    private val maxCheckpoint = checkpoints.values.maxOrNull() ?: 0
    private val visited = Array(rows) { BooleanArray(cols) }
    private val path = mutableListOf<Cell>()

    private fun neighbors(cell: Cell): List<Cell> {
        val deltas = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        return deltas.mapNotNull { (dr, dc) ->
            val nr = cell.row + dr
            val nc = cell.col + dc
            if (nr in 0 until rows && nc in 0 until cols) Cell(nr, nc) else null
        }
    }

    fun solve(): List<Cell>? {
        val start = checkpoints.entries.firstOrNull { it.value == 1 }?.key
            ?: throw IllegalArgumentException("1 numarali kontrol noktasi bulunamadi")
        return if (backtrack(start, 1)) path.toList() else null
    }

    private fun backtrack(cell: Cell, nextNeeded: Int): Boolean {
        visited[cell.row][cell.col] = true
        path.add(cell)

        val cpNum = checkpoints[cell]
        var nn = nextNeeded
        if (cpNum != null) {
            if (cpNum != nextNeeded) {
                visited[cell.row][cell.col] = false
                path.removeAt(path.size - 1)
                return false
            }
            nn = nextNeeded + 1
        }

        if (path.size == totalCells) {
            val ok = nn == maxCheckpoint + 1
            if (!ok) {
                visited[cell.row][cell.col] = false
                path.removeAt(path.size - 1)
            }
            return ok
        }

        for (next in neighbors(cell)) {
            if (!visited[next.row][next.col]) {
                if (backtrack(next, nn)) return true
            }
        }

        visited[cell.row][cell.col] = false
        path.removeAt(path.size - 1)
        return false
    }
}
