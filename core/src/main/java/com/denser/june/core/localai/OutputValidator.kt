package com.denser.june.core.localai

/**
 * Makes model output safe to put into a June entry.
 *
 * June's editor (Hyphen) parses `[display](person:id)` mentions with
 * `\[(.+?)\]\((person|topic):(.+?)\)` *after* bold/italic/strikethrough/highlight, so:
 *  - a stray `[` earlier in the text makes the lazy `.+?` swallow everything up to the next mention;
 *  - emphasis around a mention (`**[@Emma](person:Emma)**`) is matched first and breaks the mention.
 * Both are removed here, together with invisible characters, preamble/commentary, and mentions of
 * people that are not in June's People tags.
 */
object OutputValidator {

    data class Result(val text: String, val changes: List<String>)

    private val INVISIBLE = Regex("[​‎‏⁠﻿]")
    private val NBSP = Regex("[   ]")

    // Innermost mention: no brackets inside the display text. The id may not contain parentheses.
    private val INNER_MENTION = Regex("""\[([^\[\]]*)\]\(\s*person\s*:\s*([^()\n]+?)\s*\)""")
    private val BARE_MENTION = Regex("""(?<![\w@\[])@([\p{L}\p{N}_'-]+)""")
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
    fun validate(raw: String, knownPeople: Collection<String>, linkPlainNames: Boolean = true): Result {
        val changes = mutableListOf<String>()
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')

        // leftover reasoning, in case a server does not strip it
        val noThink = text.replace(Regex("""<think>[\s\S]*?</think>"""), "").replace(Regex("""^[\s\S]*?</think>"""), "")
        if (noThink != text) changes += "removed reasoning"
        text = noThink

        val cleanedInvisible = text.replace(INVISIBLE, "").replace(NBSP, " ")
        if (cleanedInvisible != text) changes += "removed invisible characters"
        text = cleanedInvisible

        text = stripCodeFence(text)
        text = stripPreambleAndCommentary(text, changes)
        text = stripWrappingQuotes(text)

        // --- mentions ---------------------------------------------------------------------------
        val people = knownPeople.map { it.trim() }.filter { it.isNotEmpty() }
        fun canonical(name: String): String? {
            val n = name.trim().removePrefix("@").trim()
            return people.firstOrNull { it.equals(n, ignoreCase = true) }
        }

        // Replace mentions innermost-first with placeholders so broken nested codes such as
        // [[@Emm](person:Emma)a](person:Emma) collapse into one mention.
        data class Mention(val display: String, val id: String)
        val mentions = mutableListOf<Mention>()
        var guard = 0
        while (guard++ < 20) {
            val next =
                INNER_MENTION.replace(text) { m ->
                    val display = PLACEHOLDER.replace(m.groupValues[1]) { p -> mentions[p.groupValues[1].toInt()].display }
                    mentions += Mention(display, m.groupValues[2].trim())
                    "${mentions.size - 1}"
                }
            if (next == text) break
            text = next
        }
        // collapse a placeholder directly nested in another one's display (already merged above)
        val rendered = mutableMapOf<Int, String>()
        var unknown = 0
        mentions.forEachIndexed { i, m ->
            val name = canonical(m.id) ?: canonical(m.display)
            rendered[i] =
                if (name != null) {
                    "[@$name](person:$name)"
                } else {
                    unknown++
                    m.display.removePrefix("@").trim().ifEmpty { m.id }
                }
        }
        if (unknown > 0) changes += "converted $unknown unknown mention(s) to plain text"

        // bare "@Name": Hyphen would turn it into a mention on its own
        text =
            BARE_MENTION.replace(text) { m ->
                val name = canonical(m.groupValues[1])
                if (name != null) {
                    mentions += Mention("@$name", name)
                    rendered[mentions.size - 1] = "[@$name](person:$name)"
                    "${mentions.size - 1}"
                } else {
                    changes += "removed @ from ${m.groupValues[1]}"
                    m.groupValues[1]
                }
            }

        // plain known names -> codes (whole word, same capitalisation as the People tag)
        if (linkPlainNames && people.isNotEmpty()) {
            var linked = 0
            for (name in people.sortedByDescending { it.length }) {
                val re = Regex("""(?<![\p{L}\p{N}_@\uE000-\uE001])""" + Regex.escape(name) + """(?![\p{L}\p{N}_\uE001])""")
                text =
                    re.replace(text) {
                        linked++
                        mentions += Mention("@$name", name)
                        rendered[mentions.size - 1] = "[@$name](person:$name)"
                        "\uE000${mentions.size - 1}\uE001"
                    }
            }
            if (linked > 0) changes += "linked $linked name(s) to People"
        }

        // emphasis wrapping a mention breaks Hyphen's parser: drop those markers
        val beforeEmphasis = text
        text = unwrapEmphasisAroundMentions(text)
        if (text != beforeEmphasis) changes += "removed emphasis around mentions"

        // any remaining square bracket would derail the mention regex
        val noBrackets = text.replace("[", "").replace("]", "")
        if (noBrackets != text) changes += "removed square brackets"
        text = noBrackets

        // restore unknown mentions first as plain text, then known ones as clean codes
        text = PLACEHOLDER.replace(text) { p -> rendered[p.groupValues[1].toInt()] ?: "" }

        // --- whitespace ------------------------------------------------------------------------
        text =
            text.lines().joinToString("\n") { it.trimEnd() }
                .replace(Regex("\n{3,}"), "\n\n")
                .replace(Regex("[ \t]{2,}"), " ")
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

    private fun stripPreambleAndCommentary(text: String, changes: MutableList<String>): String {
        var paragraphs = text.trim().split(Regex("\n\\s*\n")).map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
        if (paragraphs.isEmpty()) return ""

        // Leading lines: "Here is the cleaned up entry:", a "# Title", a lone "**Journal Entry**".
        while (paragraphs.size > 1 || (paragraphs.size == 1 && paragraphs[0].lines().size > 1)) {
            val firstPara = paragraphs[0]
            val firstLine = firstPara.lines().first().trim()
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
        val sep = paragraphs.indexOfLast { it.matches(Regex("^(-{3,}|\\*{3,}|_{3,})$")) }
        if (sep >= 0) {
            changes += "removed comments after the entry"
            paragraphs = paragraphs.subList(0, sep).toMutableList()
        }
        while (paragraphs.size > 1 && TRAILING.containsMatchIn(paragraphs.last())) {
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
