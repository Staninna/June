package com.denser.june.core.localai

/**
 * What the user did to an entry while its cleanup was running. The rewrite is made from the text
 * as it was when the cleanup started, so anything written since is not in it.
 */
sealed interface EditedMeanwhile {
    /** The entry is as it was. */
    data object No : EditedMeanwhile

    /**
     * The user kept writing after the text that is being cleaned up. Nothing has to be lost:
     * [text] goes after the rewrite ([keep]).
     *
     * @param sameParagraph the new text carried on in the last paragraph instead of starting one.
     */
    data class Added(val text: String, val sameParagraph: Boolean) : EditedMeanwhile {
        fun keep(rewrite: String): String = rewrite.trimEnd() + (if (sameParagraph) " " else "\n\n") + text
    }

    /** The text that is being cleaned up was changed itself. The rewrite cannot have those changes. */
    data object Changed : EditedMeanwhile

    companion object {
        /** @param original the entry as it was sent, [current] as it is now. */
        fun of(original: String, current: String): EditedMeanwhile {
            val before = original.trimEnd()
            val now = current.trimEnd()
            if (now == before) return No
            if (before.isEmpty() || !now.startsWith(before)) return Changed
            val rest = now.substring(before.length)
            val text = rest.trim()
            if (text.isEmpty()) return No
            return Added(text, sameParagraph = !rest.trimStart(' ', '\t').startsWith("\n"))
        }
    }
}
