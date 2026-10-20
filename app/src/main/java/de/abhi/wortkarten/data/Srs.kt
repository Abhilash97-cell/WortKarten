package de.abhi.wortkarten.data

import java.util.concurrent.TimeUnit

/**
 * Leitner-style spaced repetition, separately for each direction.
 * Box -> next review in days: 1->1, 2->3, 3->7, 4->14, 5->30, 6->60, 7->120, 8->240.
 * "Gewusst" moves one box up. "Nochmal" goes back to box 1 (shown again in ~10 min).
 */
object Srs {
    private val intervalDays = intArrayOf(0, 1, 3, 7, 14, 30, 60, 120, 240)

    /** From box 4 (next review >= 14 days away) a direction counts as learned. */
    const val LEARNED_BOX = 4
    private val MAX_BOX = intervalDays.lastIndex

    fun correct(c: Card, now: Long = System.currentTimeMillis()): Card {
        val b = (c.box + 1).coerceAtMost(MAX_BOX)
        return Card(b, now + TimeUnit.DAYS.toMillis(intervalDays[b].toLong()) - TimeUnit.HOURS.toMillis(2))
    }

    fun wrong(now: Long = System.currentTimeMillis()) = Card(1, now + TimeUnit.MINUTES.toMillis(10))
    fun fresh(now: Long = System.currentTimeMillis()) = Card(0, now)
    fun learned(now: Long = System.currentTimeMillis()) = Card(LEARNED_BOX, now + TimeUnit.DAYS.toMillis(14))
}
