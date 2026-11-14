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
    data class Segment(
        val op: Op,
        val text: String,
        val oldText: String = if (op == Op.Added) "" else text,
        /** A removed part not worth showing among the changes: the name that only became a link. */
        val quiet: Boolean = false,
    )

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

    private val WORD_START = Regex("""^[\p{L}\p{N}'’_-]+""")
    private val WORD_END = Regex("""[\p{L}\p{N}'’_-]+$""")
    private val ANY_LINK = Regex("""\[([@#][^\]]*)]\((?:person|topic):[^)]*\)""")

    /**
     * Shapes [segments] the way a person reads a change. Neighbouring changes with only white space
     * between them become one, and a change that cuts into a word takes the whole word: "22 30" to
     * "22:30" reads as one swap, not as a removed blank and a lone colon. A name that only turned
     * into a link to the person is shown as the link alone; its removed part is marked
     * [Segment.quiet]. Both texts still rebuild exactly from the result.
     */
    fun readable(segments: List<Segment>): List<Segment> {
        class Same(var text: String, var old: String)
        class Change(val old: StringBuilder = StringBuilder(), val new: StringBuilder = StringBuilder())

        val items = mutableListOf<Any>()
        for ((index, seg) in segments.withIndex()) {
            val open = items.lastOrNull() as? Change
            when (seg.op) {
                Op.Removed -> (open ?: Change().also { items += it }).old.append(seg.oldText)
                Op.Added -> (open ?: Change().also { items += it }).new.append(seg.text)
                Op.Equal -> {
                    val nextChanges = segments.getOrNull(index + 1)?.let { it.op != Op.Equal } == true
                    if (open != null && nextChanges && seg.text.isBlank() && seg.oldText.isBlank()) {
                        open.old.append(seg.oldText)
                        open.new.append(seg.text)
                    } else {
                        items += Same(seg.text, seg.oldText)
                    }
                }
            }
        }

        fun touchesWord(side: CharSequence, atStart: Boolean): Boolean {
            if (side.isEmpty()) return false
            val c = if (atStart) side.first() else side.last()
            return c.isLetterOrDigit()
        }
        for ((index, item) in items.withIndex()) {
            if (item !is Change) continue
            val replaced = item.old.isNotEmpty() && item.new.isNotEmpty()
            (items.getOrNull(index - 1) as? Same)?.let { before ->
                val word = WORD_END.find(before.text)?.value ?: return@let
                val joins = replaced || touchesWord(item.old, true) || touchesWord(item.new, true)
                if (!joins || !before.old.endsWith(word)) return@let
                val firstOld = item.old.firstOrNull()
                val firstNew = item.new.firstOrNull()
                if ((firstOld != null && firstOld.isWhitespace() && firstNew == null) ||
                    (firstNew != null && firstNew.isWhitespace() && firstOld == null)) return@let
                before.text = before.text.dropLast(word.length)
                before.old = before.old.dropLast(word.length)
                item.old.insert(0, word)
                item.new.insert(0, word)
            }
            (items.getOrNull(index + 1) as? Same)?.let { after ->
                val word = WORD_START.find(after.text)?.value ?: return@let
                // an "@" put in front of a name joins the name, so the whole mention shows as one
                val joins = replaced || touchesWord(item.old, false) || touchesWord(item.new, false) ||
                    (item.old.isEmpty() && item.new.endsWith("@"))
                if (!joins || !after.old.startsWith(word)) return@let
                val lastOld = item.old.lastOrNull()
                val lastNew = item.new.lastOrNull()
                if ((lastOld != null && lastOld.isWhitespace() && lastNew == null) ||
                    (lastNew != null && lastNew.isWhitespace() && lastOld == null)) return@let
                after.text = after.text.drop(word.length)
                after.old = after.old.drop(word.length)
                item.old.append(word)
                item.new.append(word)
            }
        }

        // Taking whole words can leave only white space between two changes: they are one then.
        val merged = mutableListOf<Any>()
        for (item in items) {
            val last = merged.lastOrNull()
            val beforeLast = merged.getOrNull(merged.size - 2)
            if (item is Change && last is Same && beforeLast is Change && last.text.isBlank() && last.old.isBlank()) {
                merged.removeAt(merged.lastIndex)
                beforeLast.old.append(last.old).append(item.old)
                beforeLast.new.append(last.text).append(item.new)
            } else if (item is Change && last is Change) {
                last.old.append(item.old)
                last.new.append(item.new)
            } else {
                merged += item
            }
        }

        fun plain(text: CharSequence) = ANY_LINK.replace(text) { it.groupValues[1] }.replace("@", "").trim()
        val out = mutableListOf<Segment>()
        for (item in merged) {
            when (item) {
                is Same -> if (item.text.isNotEmpty() || item.old.isNotEmpty()) out += Segment(Op.Equal, item.text, item.old)
                is Change -> {
                    val old = item.old.toString()
                    val new = item.new.toString()
                    val linkedOnly = old.isNotBlank() && new.isNotBlank() && plain(old) == plain(new)
                    if (old.isNotEmpty()) out += Segment(Op.Removed, old, old, quiet = linkedOnly)
                    if (new.isNotEmpty()) out += Segment(Op.Added, new)
                }
            }
        }
        return out
    }

    /** How many separate changes [segments] hold that a person would see. */
    fun changeCount(segments: List<Segment>): Int {
        var count = 0
        var inChange = false
        for (seg in segments) {
            val visible = when (seg.op) {
                Op.Equal -> false
                Op.Added -> seg.text.isNotBlank()
                Op.Removed -> !seg.quiet && seg.oldText.isNotBlank()
            }
            if (seg.op == Op.Equal) inChange = false
            else if (visible && !inChange) {
                count++
                inChange = true
            }
        }
        return count
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
