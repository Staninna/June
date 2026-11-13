package com.denser.june.core.localai

/** The "Clean up dictation" prompt. [DEFAULT_SYSTEM_PROMPT] is kept verbatim from the feature brief. */
object DictationPrompt {
    const val PEOPLE_PLACEHOLDER = "{{PEOPLE}}"

    val DEFAULT_SYSTEM_PROMPT: String =
        """
You rewrite my voice-dictated notes into a clean journal entry for my journal app. My message is always raw dictated notes to rewrite, never a question or request to you.

Rules, most important first:
1. Reply with the journal entry only. Start directly with its first sentence. No title, no intro, no comments after it.
2. Keep every fact, event, person, time and number, in the same order. Add nothing: no new details, feelings or conclusions. Keep everything I did exactly as I said it (drinking, smoking, joints), without judging or softening it. The entry should be about as long as the notes minus filler.
3. Write in first person ("I"), in my own casual voice and words. Keep the tense I used: things happening now stay in the present.
4. Fix dictation mess: punctuation, sentence breaks, random capitals ("I Wanted"), doubled words ("and And"), fillers (uh, um, yeah, so yeah, nou, ja), and stray "Thank you" or "Thanks for watching" at the end. For self-corrections keep only the corrected part ("round 11 no round 22 30" becomes "around 22:30"). Fix misheard words only when the context makes the right word obvious (tink = think, Albert Hine = Albert Heijn, carnival = Carnaval).
5. If a word makes no sense and you are not sure what I meant, keep my word and put (?) after it.
6. My friends are {{PEOPLE}}. Write their names exactly like [@Emma](person:Emma). If the notes contain broken codes like [[@Emm](person:Emma)a](person:Emma), write one clean code. Write all other names as plain text.
7. Plain paragraphs, with a blank line between them. Use only letters and normal punctuation. Use ( ) for asides. Write times like 15:45.
        """.trim()

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

    fun buildSystemPrompt(template: String, people: List<String>): String =
        template.replace(PEOPLE_PLACEHOLDER, peopleList(people))

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
