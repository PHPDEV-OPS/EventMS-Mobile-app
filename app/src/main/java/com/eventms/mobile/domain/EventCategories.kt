package com.eventms.mobile.domain

/**
 * The categories an organizer can choose from.
 *
 * Deliberately a fixed list rather than free text: when every organizer invents
 * their own wording ("tech", "Tech", "Technology", "IT"), attendees end up with
 * a filter row that is useless for actually narrowing anything down.
 */
object EventCategories {

    val ALL = listOf(
        "Music",
        "Tech",
        "Business",
        "Food",
        "Sports",
        "Arts",
        "Fashion",
        "Education",
        "Health",
        "Community",
        "Film",
        "Other"
    )

    /**
     * Maps whatever is already stored onto the fixed list — existing rows and
     * the web app were never restricted to it.
     */
    fun normalise(raw: String?): String {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return "Other"
        return ALL.firstOrNull { it.equals(value, ignoreCase = true) } ?: "Other"
    }

    /**
     * A stock cover for events with no image of their own, picked by category so
     * the listing does not turn into a wall of identical placeholders.
     */
    fun defaultCoverFor(category: String?): String = DEFAULT_COVERS[normalise(category)]
        ?: DEFAULT_COVERS.getValue("Other")

    /**
     * Unsplash stills, hotlinked at a size that suits a card. The web app already
     * seeds its demo events from the same source.
     */
    private val DEFAULT_COVERS = mapOf(
        "Music" to "https://images.unsplash.com/photo-1470229722913-7ea0d1e2b4b1?auto=format&fit=crop&w=1200&q=80",
        "Tech" to "https://images.unsplash.com/photo-1540575467063-178a50c2df87?auto=format&fit=crop&w=1200&q=80",
        "Business" to "https://images.unsplash.com/photo-1505373877841-8d25f7d46678?auto=format&fit=crop&w=1200&q=80",
        "Food" to "https://images.unsplash.com/photo-1511795409834-ef04bbd61622?auto=format&fit=crop&w=1200&q=80",
        "Sports" to "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?auto=format&fit=crop&w=1200&q=80",
        "Arts" to "https://images.unsplash.com/photo-1499781350541-7783f6c6a0c8?auto=format&fit=crop&w=1200&q=80",
        "Fashion" to "https://images.unsplash.com/photo-1509631179647-0177331693ae?auto=format&fit=crop&w=1200&q=80",
        "Education" to "https://images.unsplash.com/photo-1523050854058-8df90110c9f1?auto=format&fit=crop&w=1200&q=80",
        "Health" to "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b?auto=format&fit=crop&w=1200&q=80",
        "Community" to "https://images.unsplash.com/photo-1511632765486-a01980e01a18?auto=format&fit=crop&w=1200&q=80",
        "Film" to "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?auto=format&fit=crop&w=1200&q=80",
        "Other" to "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?auto=format&fit=crop&w=1200&q=80"
    )
}
