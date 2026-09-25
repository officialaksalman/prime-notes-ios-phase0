package com.primenotes.bd.domain.model

/**
 * A note together with its tags.
 *
 * [Note] deliberately does not carry its tags: the notes list would then need a
 * query per row. This type exists only where both are genuinely required.
 */
data class NoteWithTags(
    val note: Note,
    val tags: List<Tag>
)
