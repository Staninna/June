package com.denser.june.core.localai

/**
 * Word-level diff used to highlight what the rewrite changed. Mention codes such as
 * `[@Emma](person:Emma)` are kept as single tokens so they are never split.
 */
object TextDiff {
    enum class Op { Equal, Added, Removed }

    /**
     * @param text what the segment shows: the new text, or the old text of a removed part.
     * @param oldText the same stretch as it is in the old text. It only differs from [text] in
     *   white space, which is not treated as a change.
     */
    data class Segment(val op: Op, val text: String, val oldText: String = if (op == Op.Added) "" else text)

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
        fun push(op: Op, t: String, old: String = if (op == Op.Added) "" else t) {
            val last = out.lastOrNull()
            if (last != null && last.op == op) out[out.size - 1] = Segment(op, last.text + t, last.oldText + old)
            else out += Segment(op, t, old)
        }
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                same(a[i], b[j]) -> {
                    push(Op.Equal, b[j], a[i]); i++; j++
                }
                lcs[i + 1][j] >= lcs[i][j + 1] -> push(Op.Removed, a[i++])
                else -> push(Op.Added, b[j++])
            }
        }
        while (i < n) push(Op.Removed, a[i++])
        while (j < m) push(Op.Added, b[j++])
        return out
    }

    private val MENTION = Regex("""\[@?([^\]]*)]\(person:[^)]*\)""")

    // White space differences (e.g. a new paragraph break) are not worth highlighting. Neither
    // is a mention that reads the same and only points to the person in another way: older
    // mentions carry a name where newer ones carry the person's id.
    private fun same(x: String, y: String): Boolean {
        if (x == y || (x.isBlank() && y.isBlank())) return true
        val a = MENTION.matchEntire(x) ?: return false
        val b = MENTION.matchEntire(y) ?: return false
        return a.groupValues[1].trim().equals(b.groupValues[1].trim(), ignoreCase = true)
    }
}
