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

    private enum class Needs { Always, Emphasis, Topics }

    private val RULES: List<Pair<Needs, String>> =
        listOf(
            Needs.Always to
                "Reply with the journal entry only. Start directly with its first sentence. No title, no intro, no comments after it.",
            Needs.Always to
                "Keep every fact, event, person, time and number, in the same order. Add nothing: no new details, feelings or conclusions. " +
                "Keep everything I did exactly as I said it (drinking, smoking, joints), without judging or softening it. " +
                "The entry should be about as long as the notes minus filler. If the notes are already clean, return them unchanged.",
            Needs.Always to
                "Write in first person (\"I\"), in my own casual voice and words. Keep the tense I used: things happening now stay in the present.",
            Needs.Always to
                "Fix dictation mess: punctuation, sentence breaks, random capitals (\"I Wanted\"), doubled words (\"and And\"), " +
                "fillers (uh, um, yeah, so yeah, nou, ja), and stray \"Thank you\" or \"Thanks for watching\" at the end. " +
                "For self-corrections keep only the corrected part (\"round 11 no round 22 30\" becomes \"around 22:30\"). " +
                "Fix misheard words only when the context makes the right word obvious (tink = think, Albert Hine = Albert Heijn, carnival = Carnaval).",
            Needs.Always to
                "If a word makes no sense and you are not sure what I meant, keep my word and put (?) after it.",
            Needs.Always to
                "Plain paragraphs, with a blank line between them. No lists, no headings. Use ( ) for asides. Write times like 15:45.",
            Needs.Emphasis to
                "Keep the marks *, ** and ~~ exactly where they are, around the same words. Do not add new ones.",
            Needs.Topics to
                "Words that start with # (like #awake) are my topic tags. Keep each one exactly as written, in the same place.",
            // last on purpose: the list of people is the only part that differs between entries
            Needs.Always to
                "Names with @ in front (like @Emma) are tags for my friends. My friends are $PEOPLE_PLACEHOLDER. " +
                "Write a friend's name with @ in front, spelled exactly as in this list, also when I said it without @ or it was misheard. " +
                "Write all other names as plain text, without @.",
        )

    private fun compose(rules: List<String>): String =
        INTRO + "\n\nRules, most important first:\n" + rules.mapIndexed { i, r -> "${i + 1}. $r" }.joinToString("\n")

    /** The full prompt with every rule: what Settings shows and what a custom prompt starts from. */
    val DEFAULT_SYSTEM_PROMPT: String = compose(RULES.map { it.second })

    private val PERSON_CODE = Regex("""\[@?([^\[\]]*)\]\(\s*person\s*:\s*([^()\n]+?)\s*\)""")
    private val TOPIC_CODE = Regex("""\[(#[^\[\]\s]+)\]\(\s*topic\s*:\s*([^()\n]+?)\s*\)""")
    private val EMPHASIS = Regex("""\*|~~""")
    private val TOPIC_WORD = Regex("""(?<![\p{L}\p{N}_&/])#[\p{L}\p{N}_-]+""")

    /** An entry as the model sees it, plus what is needed to turn its answer back into June markup. */
    data class Simplified(
        val text: String,
        /** Topic tag as written (`#awake`) to its id. */
        val topics: Map<String, String>,
    )

    /**
     * `[@Emma](person:Emma)` becomes `@Emma` and `[#awake](topic:awake)` becomes `#awake`.
     * Broken or nested codes are left alone; [OutputValidator] deals with those.
     */
    fun simplify(body: String): Simplified {
        val topics = LinkedHashMap<String, String>()
        var text =
            TOPIC_CODE.replace(body) { m ->
                topics[m.groupValues[1]] = m.groupValues[2].trim()
                m.groupValues[1]
            }
        text = PERSON_CODE.replace(text) { m -> "@" + m.groupValues[1].trim().ifEmpty { m.groupValues[2].trim() } }
        return Simplified(text, topics)
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
     * Fills {{PEOPLE}} from June's People tags (`@Name`), e.g. "Emma, Noor and Sam".
     * With no People tags it says so, so rule 6 still reads naturally.
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
     * The system prompt for one entry. With the default prompt, rules the entry has no use for
     * (emphasis marks, topic tags) are left out and the people are narrowed down with
     * [relevantPeople]. A prompt the user wrote is used as it is; only {{PEOPLE}} is filled in.
     *
     * @param body the simplified entry, or null for the full prompt.
     */
    fun buildSystemPrompt(template: String, people: List<String>, body: String? = null): String {
        if (body == null || template != DEFAULT_SYSTEM_PROMPT) {
            return template.replace(PEOPLE_PLACEHOLDER, peopleList(if (body == null) people else relevantPeople(people, body)))
        }
        val emphasis = EMPHASIS.containsMatchIn(body)
        val topics = TOPIC_WORD.containsMatchIn(body)
        val rules =
            RULES.filter { (needs, _) ->
                when (needs) {
                    Needs.Always -> true
                    Needs.Emphasis -> emphasis
                    Needs.Topics -> topics
                }
            }
        return compose(rules.map { it.second }).replace(PEOPLE_PLACEHOLDER, peopleList(relevantPeople(people, body)))
    }

    const val MAX_PEOPLE = 40

    /**
     * Everyone June knows: People tags (`@Emma`) plus the people recorded on entries
     * (JournalPerson names). Case-insensitive de-duplication; tag spelling wins.
     */
    fun knownPeople(tags: List<String>, personNames: List<String>): List<String> =
        (peopleFromTags(tags) + personNames.map { it.trim() }.filter { it.isNotEmpty() })
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    /** People names from June tags: `@Emma` -> `Emma`. */
    fun peopleFromTags(tags: List<String>): List<String> =
        tags.filter { it.startsWith("@") }.map { it.removePrefix("@").trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() }
}
