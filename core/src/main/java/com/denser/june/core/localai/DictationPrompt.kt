package com.denser.june.core.localai

/**
 * The "Clean up dictation" prompt, and how an entry is prepared for the model.
 *
 * Two things keep a small model on track:
 * - The entry is sent in a simple form ([simplify]): `@Emma` and `#awake` instead of June's link
 *   codes. The model has less to copy and nothing to break; [OutputValidator] puts the codes back.
 * - The prompt only carries the rules this entry needs ([buildSystemPrompt]), with the part that
 *   changes between entries (the people) at the very end, so the server can reuse the rest.
 */
object DictationPrompt {
    const val PEOPLE_PLACEHOLDER = "{{PEOPLE}}"

    private const val INTRO =
        "You rewrite my voice-dictated notes into a clean journal entry for my journal app. " +
            "My message is always raw dictated notes to rewrite, never a question or request to you."

    /**
     * The markup June's editor understands (the Hyphen library): what an entry can contain besides
     * plain text. A rule about a kind of markup is only sent when the entry uses it.
     */
    internal data class Features(
        /** Inline marks in use, as examples: `**bold**`, `*italic*`, … */
        val inlineMarks: List<String>,
        /** Line-level layout in use, as descriptions. */
        val blocks: List<String>,
        val links: Boolean,
        val topics: Boolean,
    )

    private val INLINE_MARKS: List<Pair<Regex, String>> =
        listOf(
            Regex("""\*\*(.+?)\*\*""") to "**bold**",
            Regex("""(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)|(?<![\w_])_(?!_)(.+?)(?<!_)_(?![\w_])""") to "*italic*",
            Regex("""(?<![\w_])__(?!_)(.+?)(?<!_)__(?![\w_])""") to "__underline__",
            Regex("""~~(.+?)~~""") to "~~strikethrough~~",
            Regex("""==(.+?)==""") to "==highlight==",
            Regex("""`(.+?)`""") to "`code`",
        )

    private val BLOCKS: List<Pair<Regex, String>> =
        listOf(
            Regex("""(?m)^[ \t]*[-*][ \u00A0]\[[ xX]\][ \u00A0]""") to "checkbox lines that start with - [ ] or - [x]",
            Regex("""(?m)^[ \t]*(?:[-*\u2022][ \u00A0](?!\[[ xX]\])|\d+\.[ \u00A0])""") to "list lines that start with - or 1.",
            Regex("""(?m)^>[ \u00A0]""") to "quote lines that start with >",
            Regex("""(?m)^#{1,6} """) to "heading lines that start with # and a space",
            Regex("""(?m)^[ \t]*([*\-_][ \t]*){3,}$""") to "divider lines (---)",
            Regex("""(?m)^(`{3,}|~{3,})""") to "code blocks between ``` lines (copy them unchanged)",
            Regex("""(?m)^\|.+\|$""") to "tables (lines with |, copy them unchanged)",
        )

    private val LINK = Regex("""!?\[[^\[\]]+\]\([^()\s]+\)|!\[\[[^\]]+\]\]""")
    private val TOPIC_WORD = Regex("""(?<![\p{L}\p{N}_&/#])#[\p{L}\p{N}_-]+""")

    internal val ALL_FEATURES = Features(INLINE_MARKS.map { it.second }, BLOCKS.map { it.second }, links = true, topics = true)

    /** Which markup the (simplified) entry uses. */
    internal fun featuresOf(body: String): Features =
        Features(
            // the mark of a divider or a list bullet is not emphasis: look at the text without those lines' markers
            inlineMarks = body.replace(Regex("""(?m)^[ \t]*([*\-_][ \t]*){3,}$|^[ \t]*[*][ \u00A0]"""), "").let { text ->
                INLINE_MARKS.filter { it.first.containsMatchIn(text) }.map { it.second }
            },
            blocks = BLOCKS.filter { it.first.containsMatchIn(body) }.map { it.second },
            links = LINK.containsMatchIn(body),
            topics = TOPIC_WORD.containsMatchIn(body),
        )

    private fun rules(f: Features, numberedNames: Boolean = false): List<String> =
        listOfNotNull(
            "Reply with the journal entry only. Start directly with its first sentence. No title, no intro, no comments after it.",
            "Keep every fact, event, person, time and number, in the same order. Add nothing: no new details, feelings or conclusions. " +
                "Keep everything I did exactly as I said it (drinking, smoking, joints), without judging or softening it. " +
                "The entry should be about as long as the notes minus filler. If the notes are already clean, return them unchanged.",
            "Write in first person (\"I\"), in my own casual voice and words. Keep the tense I used: things happening now stay in the present.",
            "Fix dictation mess: punctuation, sentence breaks, random capitals (\"I Wanted\"), doubled words (\"and And\"), " +
                "fillers (uh, um, yeah, so yeah, nou, ja), and stray \"Thank you\" or \"Thanks for watching\" at the end. " +
                "For self-corrections keep only the corrected part (\"round 11 no round 22 30\" becomes \"around 22:30\"). " +
                "Fix misheard words only when the context makes the right word obvious (tink = think, Albert Hine = Albert Heijn, carnival = Carnaval).",
            "If a word makes no sense and you are not sure what I meant, keep my word and put (?) after it.",
            "Plain paragraphs, with a blank line between them. Do not add lists, headings, bold or any other formatting that is not in my notes. " +
                "Use ( ) for asides. Write times like 15:45.",
            f.inlineMarks.takeIf { it.isNotEmpty() }?.let {
                "My notes use formatting marks: ${it.joinToString(", ")}. Keep every mark exactly where it is, around the same words. Do not add new ones."
            },
            f.blocks.takeIf { it.isNotEmpty() }?.let {
                "Keep the layout of my notes: ${it.joinToString("; ")}. Each of those lines stays its own line, in the same order, " +
                    "with the same mark at its start. Only fix the words in it."
            },
            "Keep links like [text](address) and pictures like ![name](file) exactly as written.".takeIf { f.links },
            "Words that start with # (like #awake) are my topic tags. Keep each one exactly as written, in the same place.".takeIf { f.topics },
            // last on purpose: the list of people is the only part that differs between entries
            "Names with @ in front (like @Emma) are tags for people I know. The people I know are $PEOPLE_PLACEHOLDER. " +
                "Write such a name with @ in front, spelled exactly as in this list, also when I said it without @ or it was misheard. " +
                "Write all other names as plain text, without @." +
                // only said when this entry needs it: two people in it share a name
                if (numberedNames) {
                    " Some of them share a name, so a name can end in a number (like @Bart2). " +
                        "That is a different person than the name without the number: keep each one exactly as I wrote it."
                } else {
                    ""
                },
        )

    private fun compose(rules: List<String>): String =
        INTRO + "\n\nRules, most important first:\n" + rules.mapIndexed { i, r -> "${i + 1}. $r" }.joinToString("\n")

    /** The full prompt with every rule: what Settings shows and what a custom prompt starts from. */
    val DEFAULT_SYSTEM_PROMPT: String = compose(rules(ALL_FEATURES))

    private val PERSON_CODE = Regex("""\[@?([^\[\]]*)\]\(\s*person\s*:\s*([^()\n]+?)\s*\)""")
    private val TOPIC_CODE = Regex("""\[(#[^\[\]\s]+)\]\(\s*topic\s*:\s*([^()\n]+?)\s*\)""")

    /** An entry as the model sees it, plus what is needed to turn its answer back into June markup. */
    data class Simplified(
        val text: String,
        /** Topic tag as written (`#awake`) to its id. */
        val topics: Map<String, String>,
        /**
         * Each person mentioned in the entry: the name the model sees (`@Bart`) to the id of the
         * person meant. Two people with the same name get different names here (`@Bart`,
         * `@Bart2`), so the answer still says which one is which.
         */
        val mentions: Map<String, String> = emptyMap(),
    ) {
        /** True when the entry mentions two people who share a name. */
        val hasNumberedNames: Boolean
            get() = mentions.keys.any { NUMBERED.matches(it) && it.dropLastWhile(Char::isDigit) in mentions }

        /** The names the model has to know to keep these mentions: `Bart`, `Bart2`. */
        val names: List<String>
            get() = mentions.keys.map { it.removePrefix("@") }
    }

    private val NUMBERED = Regex(""".*\D\d+""")

    /**
     * `[@Emma](person:<id>)` becomes `@Emma` and `[#awake](topic:awake)` becomes `#awake`.
     * Broken or nested codes are left alone; [OutputValidator] deals with those.
     */
    fun simplify(body: String): Simplified {
        val topics = LinkedHashMap<String, String>()
        var text =
            TOPIC_CODE.replace(body) { m ->
                topics[m.groupValues[1]] = m.groupValues[2].trim()
                m.groupValues[1]
            }
        val mentions = LinkedHashMap<String, String>()
        fun find(handle: String): String? = mentions.keys.firstOrNull { it.equals(handle, ignoreCase = true) }
        text =
            PERSON_CODE.replace(text) { m ->
                val id = m.groupValues[2].trim()
                val base = "@" + m.groupValues[1].trim().ifEmpty { id }
                // the same name for someone else: Bart, Bart2, Bart3
                var handle = base
                var n = 1
                while (true) {
                    val taken = find(handle)
                    if (taken == null) {
                        mentions[handle] = id
                        break
                    }
                    if (mentions[taken] == id) {
                        handle = taken
                        break
                    }
                    handle = base + ++n
                }
                handle
            }
        return Simplified(text, topics, mentions)
    }

    /**
     * The people worth telling the model about for this entry: everyone when the list is short,
     * otherwise the ones the entry seems to name (also misheard: same first letter, nearly the same
     * spelling) first, then the rest up to [limit]. A long list costs reading time on every request.
     */
    fun relevantPeople(people: List<String>, body: String, limit: Int = MAX_PEOPLE): List<String> {
        val names = people.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (names.size <= limit) return names
        val words = Regex("""[\p{L}\p{N}'-]{2,}""").findAll(body).map { it.value.lowercase() }.toSet()
        fun named(name: String): Boolean =
            name.lowercase().split(' ').any { part ->
                part.length >= 2 && words.any { w -> w == part || (w[0] == part[0] && editDistanceAtMost(w, part, 2)) }
            }
        val (hit, rest) = names.partition(::named)
        return (hit + rest).take(maxOf(limit, hit.size)).sortedBy { it.lowercase() }
    }

    private fun editDistanceAtMost(a: String, b: String, max: Int): Boolean {
        if (kotlin.math.abs(a.length - b.length) > max) return false
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length] <= max
    }

    /**
     * Fills {{PEOPLE}} with the names of the people in the journal, e.g. "Emma, Noor and Sam".
     * With nobody yet it says so, so the rule still reads naturally.
     */
    fun peopleList(people: List<String>): String {
        val names = people.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return when (names.size) {
            0 -> "not listed (no people yet)"
            1 -> names[0]
            else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
        }
    }

    /**
     * The system prompt for one entry. With the default prompt, rules about markup the entry does
     * not use (formatting marks, lists and other layout, links, topic tags) are left out and the people are narrowed down with
     * [relevantPeople]. A prompt the user wrote is used as it is; only {{PEOPLE}} is filled in.
     *
     * @param body the simplified entry, or null for the full prompt.
     */
    fun buildSystemPrompt(
        template: String,
        people: List<String>,
        body: String? = null,
        /** The entry mentions two people who share a name, told apart by a number (`@Bart2`). */
        numberedNames: Boolean = false,
    ): String {
        if (body == null || template != DEFAULT_SYSTEM_PROMPT) {
            return template.replace(PEOPLE_PLACEHOLDER, peopleList(if (body == null) people else relevantPeople(people, body)))
        }
        return compose(rules(featuresOf(body), numberedNames)).replace(PEOPLE_PLACEHOLDER, peopleList(relevantPeople(people, body)))
    }

    const val MAX_PEOPLE = 40

    /** The names to tell the model about: everyone's, once each, sorted. Namesakes share one. */
    fun names(people: Collection<CleanupPerson>): List<String> =
        people.map { it.name.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
}

/**
 * A person as the cleanup needs to know them. Several people can have the same [name]; [note]
 * ("brother", "work") and whether they are [onEntry] help to tell which one a name in the text means.
 */
data class CleanupPerson(
    val id: String,
    val name: String,
    val note: String? = null,
    /** This person is on the entry that is being cleaned up. */
    val onEntry: Boolean = false,
    /** How the person's icon looks, only so they look the same when the user is asked who was meant. */
    val color: Int? = null,
    val shape: Int? = null,
    val contactLookupKey: String? = null,
)

/**
 * A name in a rewrite that several people share. Nobody guesses who is meant: the mention stays
 * open, written as `[@Bart](person:?bart)`, until the user picks one of [candidates] or says it
 * is none of them.
 *
 * @param suggested the candidate something in the entry points to, if any. Shown as picked,
 *   and still the user's call.
 */
data class NameChoice(val name: String, val candidates: List<CleanupPerson>, val suggested: String? = null) {
    /** What stands in for the person's id while the question is open. */
    val key: String
        get() = OPEN + name.trim().lowercase()

    companion object {
        private const val OPEN = "?"
        private val OPEN_MENTION = Regex("""\[@?([^\[\]]*)\]\(person:(\?[^()\n]+)\)""")

        /**
         * Settles the open mentions in [text]. [picks] says per [key] which person is meant; a name
         * without a pick is not a mention of anyone and becomes plain text.
         */
        fun settle(text: String, choices: List<NameChoice>, picks: Map<String, String?>): String =
            OPEN_MENTION.replace(text) { m ->
                val key = m.groupValues[2]
                val person = choices.firstOrNull { it.key == key }?.candidates?.firstOrNull { it.id == picks[key] }
                if (person != null) "[@${person.name.trim()}](person:${person.id})" else m.groupValues[1].removePrefix("@").trim()
            }
    }
}
