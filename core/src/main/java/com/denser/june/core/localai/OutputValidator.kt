package com.denser.june.core.localai

/**
 * Makes model output safe to put into a June entry.
 *
 * June's editor (Hyphen) parses `[display](person:id)` mentions with
 * `\[(.+?)\]\((person|topic):(.+?)\)` *after* bold/italic/strikethrough/highlight, so:
 *  - a stray `[` earlier in the text makes the lazy `.+?` swallow everything up to the next mention;
 *  - emphasis around a mention (`**[@Emma](person:Emma)**`) is matched first and breaks the mention.
 * Both are removed here, together with invisible characters, preamble/commentary, and mentions of
 * people that are not in June's People tags. Topic tags (`[#awake](topic:awake)`) are kept, and the
 * simple forms the model is given (`@Emma`, `#awake`) are turned back into codes.
 */
object OutputValidator {

    data class Result(val text: String, val changes: List<String>)

    private val INVISIBLE = Regex("[​‎‏⁠﻿]")
    private val NBSP = Regex("[   ]")

    // Innermost mention: no brackets inside the display text. The id may not contain parentheses.
    private val INNER_MENTION = Regex("""\[([^\[\]]*)\]\(\s*person\s*:\s*([^()\n]+?)\s*\)""")
    private val TOPIC_CODE = Regex("""\[(#[^\[\]\s]+)\]\(\s*topic\s*:\s*([^()\n]+?)\s*\)""")
    // [text](address) and ![name](file), but not a mention or topic code
    private val LINK = Regex("""!?\[[^\[\]\uE000\uE001]+\]\((?!\s*(?:person|topic)\s*:)[^()\s]+\)""")
    private val KEPT = Regex("\uE002(\\d+)\uE003")
    private val WIKI_IMAGE = Regex("""!\[\[[^\[\]]+\]\]""")
    private val CHECKBOX = Regex("""(?m)^[ \t]*[-*][ \u00A0]\[[ xX]\](?=[ \u00A0])""")
    private val BARE_MENTION = Regex("""(?<![\w@\[])@([\p{L}\p{N}_'-]+)""")
    private val WORD = Regex("""[\p{L}\p{N}]+""")
    /** How far around a name its context is looked for, in characters. */
    private const val CONTEXT = 80
    private val PLACEHOLDER = Regex("(\\d+)")

    private val PREAMBLE =
        Regex(
            """^(?:sure|okay|ok|certainly|of course|absolutely|here(?:'s| is| are)|below is|this is|i(?:'ve| have) (?:cleaned|rewritten|rewrote)|cleaned[- ]up|rewritten|journal entry|entry)\b.{0,160}$""",
            RegexOption.IGNORE_CASE,
        )
    private val TRAILING =
        Regex(
            """^\(?(?:note|notes|i (?:kept|have|removed|fixed|changed|left|added|corrected|made)|i've|let me know|hope|this (?:version|entry|rewrite)|changes|edits|explanation)\b""",
            RegexOption.IGNORE_CASE,
        )
    private val HEADING = Regex("""^#{1,6}\s+.+$""")
    private val BOLD_LINE = Regex("""^\*\*[^*]{1,80}\*\*:?$""")

    /**
     * @param linkPlainNames also turn plain, capitalised, whole-word occurrences of known people
     *   into mention codes (prompt rule 6). Small models often write "Emma" instead of the code.
     */
    fun validate(
        raw: String,
        knownPeople: Collection<CleanupPerson>,
        linkPlainNames: Boolean = true,
        topics: Map<String, String> = emptyMap(),
        /** The notes as they were sent. What looks like commentary but is in the notes is kept. */
        original: String? = null,
        /** Who the names in the notes stood for ([DictationPrompt.Simplified.mentions]). */
        mentions: Map<String, String> = emptyMap(),
    ): Result {
        val changes = mutableListOf<String>()
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')

        // leftover reasoning, in case a server does not strip it
        val noThink = text.replace(Regex("""<think>[\s\S]*?</think>"""), "").replace(Regex("""^[\s\S]*?</think>"""), "")
        if (noThink != text) changes += "removed reasoning"
        text = noThink

        val cleanedInvisible = text.replace(INVISIBLE, "").replace(NBSP, " ")
        if (cleanedInvisible != text) changes += "removed invisible characters"
        text = cleanedInvisible

        val originalLines = original.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        if (original?.trimStart()?.startsWith("```") != true) text = stripCodeFence(text)
        text = stripPreambleAndCommentary(text, changes, originalLines)
        text = stripWrappingQuotes(text)

        // --- mentions ---------------------------------------------------------------------------
        val people = knownPeople.filter { it.name.isNotBlank() }
        val byId = people.associateBy { it.id }
        val names = DictationPrompt.names(people)
        val saidAs = mentions.entries.associate { it.key.removePrefix("@").trim().lowercase() to it.value }
        val idsInNotes = mentions.values.toSet()
        val guessed = LinkedHashSet<String>()
        fun code(p: CleanupPerson) = "[@${p.name.trim()}](person:${p.id})"

        /**
         * Who [name] means. A name from the notes is the person it stood for there. A name only one
         * person has is that person. For a name several people share, in this order: the one whose
         * context ("brother", "work") is said near the name, the one on this entry, the one already
         * mentioned in the notes, and failing all that the most recent one, which is reported.
         */
        fun who(name: String, context: String): CleanupPerson? {
            var n = name.trim().removePrefix("@").trim()
            if (n.isEmpty()) return null
            saidAs[n.lowercase()]?.let { id ->
                byId[id]?.let { return it }
                // an older mention carries a name where the id should be: look that name up
                n = id
            }
            val candidates = people.filter { it.name.trim().equals(n, ignoreCase = true) }
            if (candidates.size <= 1) return candidates.firstOrNull()
            val nearby = WORD.findAll(context.lowercase()).map { it.value }.toSet()
            candidates.filter { c -> WORD.findAll(c.note.orEmpty().lowercase()).any { it.value.length >= 3 && it.value in nearby } }
                .singleOrNull()?.let { return it }
            val onEntry = candidates.filter { it.onEntry }
            onEntry.singleOrNull()?.let { return it }
            val inNotes = candidates.filter { it.id in idsInNotes }
            inNotes.singleOrNull()?.let { return it }
            guessed += candidates.first().name.trim()
            return onEntry.firstOrNull() ?: inNotes.firstOrNull() ?: candidates.first()
        }
        fun around(range: IntRange): String =
            text.substring((range.first - CONTEXT).coerceAtLeast(0), (range.last + 1 + CONTEXT).coerceAtMost(text.length))

        // Replace mentions innermost-first with placeholders so broken nested codes such as
        // [[@Emm](person:Emma)a](person:Emma) collapse into one mention.
        data class Mention(val display: String, val id: String, val context: String = "")
        val found = mutableListOf<Mention>()
        var guard = 0
        while (guard++ < 20) {
            val next =
                INNER_MENTION.replace(text) { m ->
                    val display = PLACEHOLDER.replace(m.groupValues[1]) { p -> found[p.groupValues[1].toInt()].display }
                    found += Mention(display, m.groupValues[2].trim(), around(m.range))
                    "${found.size - 1}"
                }
            if (next == text) break
            text = next
        }
        // collapse a placeholder directly nested in another one's display (already merged above)
        val rendered = mutableMapOf<Int, String>()
        var unknown = 0
        found.forEachIndexed { i, m ->
            val person = byId[m.id] ?: who(m.id, m.context) ?: who(m.display, m.context)
            rendered[i] =
                if (person != null) {
                    code(person)
                } else {
                    unknown++
                    m.display.removePrefix("@").trim().ifEmpty { m.id }
                }
        }
        if (unknown > 0) changes += "converted $unknown unknown mention(s) to plain text"

        // Links, pictures and checkboxes are valid markup with square brackets. They are set aside
        // under their own marker, so the emphasis rule for found below leaves them alone.
        val kept = mutableListOf<String>()
        for (re in listOf(WIKI_IMAGE, LINK, CHECKBOX)) {
            text =
                re.replace(text) { m ->
                    kept += m.value
                    "\uE002${kept.size - 1}\uE003"
                }
        }

        // topic codes the model wrote out in full are kept as they are
        text =
            TOPIC_CODE.replace(text) { m ->
                found += Mention(m.groupValues[1], m.groupValues[2].trim())
                rendered[found.size - 1] = "[${m.groupValues[1]}](topic:${m.groupValues[2].trim()})"
                "\uE000${found.size - 1}\uE001"
            }
        // "#awake" as it was sent to the model (DictationPrompt.simplify) becomes the code again
        for ((display, id) in topics.entries.sortedByDescending { it.key.length }) {
            val re = Regex("""(?<![\p{L}\p{N}_&/\uE000])""" + Regex.escape(display) + """(?![\p{L}\p{N}_-])""")
            text =
                re.replace(text) {
                    found += Mention(display, id)
                    rendered[found.size - 1] = "[$display](topic:$id)"
                    "\uE000${found.size - 1}\uE001"
                }
        }

        // "@First Last": names of more than one word, which the pattern below cannot see
        for (name in names.filter { it.contains(' ') }.sortedByDescending { it.length }) {
            val re = Regex("""(?<![\w@\[])@""" + Regex.escape(name) + """(?![\p{L}\p{N}_])""", RegexOption.IGNORE_CASE)
            text =
                re.replace(text) { m ->
                    val person = who(name, around(m.range)) ?: return@replace m.value
                    found += Mention("@$name", person.id)
                    rendered[found.size - 1] = code(person)
                    "\uE000${found.size - 1}\uE001"
                }
        }

        // bare "@Name": Hyphen would turn it into a mention on its own
        text =
            BARE_MENTION.replace(text) { m ->
                val person = who(m.groupValues[1], around(m.range))
                if (person != null) {
                    found += Mention("@${person.name}", person.id)
                    rendered[found.size - 1] = code(person)
                    "${found.size - 1}"
                } else {
                    changes += "removed @ from ${m.groupValues[1]}"
                    m.groupValues[1]
                }
            }

        // plain known names -> codes (whole word, same capitalisation as the People tag)
        if (linkPlainNames && people.isNotEmpty()) {
            var linked = 0
            for (name in names.sortedByDescending { it.length }) {
                val re = Regex("""(?<![\p{L}\p{N}_@\uE000-\uE001])""" + Regex.escape(name) + """(?![\p{L}\p{N}_\uE001])""")
                text =
                    re.replace(text) { m ->
                        val person = who(name, around(m.range)) ?: return@replace m.value
                        linked++
                        found += Mention("@$name", person.id)
                        rendered[found.size - 1] = code(person)
                        "\uE000${found.size - 1}\uE001"
                    }
            }
            if (linked > 0) changes += "linked $linked name(s) to People"
        }

        if (guessed.isNotEmpty()) changes += "could not tell which ${guessed.joinToString(", ")} you meant: check the name"

        // emphasis wrapping a mention breaks Hyphen's parser: drop those markers
        val beforeEmphasis = text
        text = unwrapEmphasisAroundMentions(text)
        if (text != beforeEmphasis) changes += "removed emphasis around found"

        // any remaining square bracket would derail the mention regex
        val noBrackets = text.replace("[", "").replace("]", "")
        if (noBrackets != text) changes += "removed square brackets"
        text = noBrackets

        // restore unknown found first as plain text, then known ones as clean codes
        text = PLACEHOLDER.replace(text) { p -> rendered[p.groupValues[1].toInt()] ?: "" }
        text = KEPT.replace(text) { p -> kept[p.groupValues[1].toInt()] }

        // --- whitespace ------------------------------------------------------------------------
        text =
            text.lines().joinToString("\n") { it.trimEnd() }
                .replace(Regex("\n{3,}"), "\n\n")
                // not at the start of a line: indentation belongs to nested lists
                .replace(Regex("(?<=\\S)[ \t]{2,}"), " ")
                .trim()
        return Result(text, changes.distinct())
    }

    private fun stripCodeFence(text: String): String {
        val t = text.trim()
        val fence = Regex("""^```[\w-]*\n([\s\S]*?)\n```$""")
        return fence.find(t)?.groupValues?.get(1) ?: t
    }

    private fun stripWrappingQuotes(text: String): String {
        val t = text.trim()
        val pairs = listOf('"' to '"', '“' to '”', '\'' to '\'')
        for ((open, close) in pairs) {
            if (t.length > 2 && t.first() == open && t.last() == close && t.count { it == open || it == close } == 2) {
                return t.substring(1, t.length - 1).trim()
            }
        }
        return t
    }

    private fun stripPreambleAndCommentary(text: String, changes: MutableList<String>, originalLines: Set<String>): String {
        var paragraphs =
            text.trim('\n', '\r').split(Regex("\n\\s*\n")).map { it.trim('\n', '\r').trimEnd() }.filter { it.isNotBlank() }.toMutableList()
        if (paragraphs.isEmpty()) return ""

        // Leading lines: "Here is the cleaned up entry:", a "# Title", a lone "**Journal Entry**".
        while (paragraphs.size > 1 || (paragraphs.size == 1 && paragraphs[0].lines().size > 1)) {
            val firstPara = paragraphs[0]
            val firstLine = firstPara.lines().first().trim()
            // a heading, a bold line or a line with a colon that is in my notes is not an intro
            if (firstLine in originalLines) break
            val isIntro =
                (PREAMBLE.matches(firstLine) && (firstLine.endsWith(":") || firstLine.length < 80)) ||
                    HEADING.matches(firstLine) ||
                    BOLD_LINE.matches(firstLine) ||
                    (firstLine.endsWith(":") && firstLine.length < 100 && !firstLine.contains("."))
            if (!isIntro) break
            changes += "removed intro"
            val rest = firstPara.lines().drop(1).joinToString("\n").trim()
            if (rest.isEmpty()) paragraphs.removeAt(0) else paragraphs[0] = rest
            if (paragraphs.isEmpty()) break
        }

        // Trailing commentary after a separator, or a last paragraph that talks about the edit.
        val sep = paragraphs.indexOfLast { it.trim().matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) }
        if (sep >= 0 && paragraphs[sep].trim() !in originalLines) {
            changes += "removed comments after the entry"
            paragraphs = paragraphs.subList(0, sep).toMutableList()
        }
        while (
            paragraphs.size > 1 && TRAILING.containsMatchIn(paragraphs.last().trim()) &&
            paragraphs.last().lines().first().trim() !in originalLines
        ) {
            changes += "removed comments after the entry"
            paragraphs.removeAt(paragraphs.size - 1)
        }
        return paragraphs.joinToString("\n\n")
    }

    /** Removes **, __, ~~, ==, * and _ pairs whose span contains a mention placeholder. */
    private fun unwrapEmphasisAroundMentions(input: String): String {
        var text = input
        val markers = listOf("**", "__", "~~", "==", "*", "_")
        var changed = true
        var guard = 0
        while (changed && guard++ < 10) {
            changed = false
            for (m in markers) {
                val q = Regex.escape(m)
                // the span may not cross a paragraph break
                val re = Regex("""$q((?:(?!$q)[^\n])*?\d+(?:(?!$q)[^\n])*?)$q""")
                val next = re.replace(text) { it.groupValues[1] }
                if (next != text) {
                    text = next
                    changed = true
                }
            }
        }
        return text
    }
}
