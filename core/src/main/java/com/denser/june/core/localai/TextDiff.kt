package com.denser.june.core.localai

/**
 * Word-level diff used to highlight what the rewrite changed. Mention codes such as
 * `[@Emma](person:Emma)` are kept as single tokens so they are never split.
 */
object TextDiff {
    enum class Op { Equal, Added, Removed }

    data class Segment(val op: Op, val text: String)

    private val TOKEN = Regex("""\[@[^\]]*]\(person:[^)]*\)|\s+|[\p{L}\p{N}'’_-]+|[^\s\p{L}\p{N}]""")

    fun tokenize(text: String): List<String> = TOKEN.findAll(text).map { it.value }.toList()

    /**
     * Returns the segments of [old] -> [new]. Falls back to "all removed / all added" when the
     * texts are too long for an LCS table (keeps the UI responsive on huge entries).
     */
    fun diff(old: String, new: String, maxCells: Long = 6_000_000L): List<Segment> {
        val a = tokenize(old)
        val b = tokenize(new)
        if (a.isEmpty() && b.isEmpty()) return emptyList()
        if (a.size.toLong() * b.size > maxCells) {
            return listOfNotNull(
                old.takeIf { it.isNotEmpty() }?.let { Segment(Op.Removed, it) },
                new.takeIf { it.isNotEmpty() }?.let { Segment(Op.Added, it) },
            )
        }
        // LCS lengths, suffix-based so the walk below goes forward
        val n = a.size
        val m = b.size
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (same(a[i], b[j])) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val out = mutableListOf<Segment>()
        fun push(op: Op, t: String) {
            val last = out.lastOrNull()
            if (last != null && last.op == op) out[out.size - 1] = Segment(op, last.text + t) else out += Segment(op, t)
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                same(a[i], b[j]) -> {
                    push(Op.Equal, b[j]); i++; j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> push(Op.Removed, a[i++])
                else -> push(Op.Added, b[j++])
            }
        }
        while (i < n) push(Op.Removed, a[i++])
        while (j < m) push(Op.Added, b[j++])
        return out
    }

    // whitespace differences (e.g. a new paragraph break) are not worth highlighting
    private fun same(x: String, y: String) = x == y || (x.isBlank() && y.isBlank())
}
