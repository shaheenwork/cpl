package com.shnapps.couple.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The eight tags a couple is asked about, over the twenty-seven themes the content is authored on.
 *
 * The assertions that matter are about **coverage and overlap**. A theme owned by nobody is a card
 * a limits profile can never refuse; a theme owned by two tags is a profile that answers questions
 * on the person's behalf. Both are silent failures — the app would look right and the limit would
 * not hold — so they are asserted rather than reviewed.
 */
class LimitTagTest {

    @Test
    fun `no theme belongs to two tags`() {
        val seen = mutableMapOf<String, LimitTag>()
        LimitTag.entries.forEach { tag ->
            tag.themes.forEach { theme ->
                val already = seen.put(theme, tag)
                assertThat(already).isNull()
            }
        }
    }

    @Test
    fun `every tag owns something`() {
        LimitTag.entries.forEach { tag ->
            assertThat(tag.themes).isNotEmpty()
            assertThat(tag.label).isNotEmpty()
            assertThat(tag.asks).isNotEmpty()
        }
    }

    @Test
    fun `a tag can be found from any theme it owns, and only from those`() {
        LimitTag.entries.forEach { tag ->
            tag.themes.forEach { theme -> assertThat(LimitTag.owning(theme)).isEqualTo(tag) }
        }
        LimitTag.GOVERNED_BY_TIER.forEach { theme -> assertThat(LimitTag.owning(theme)).isNull() }
    }

    @Test
    fun `nothing is owned and governed by the tier at once`() {
        assertThat(LimitTag.OWNED.intersect(LimitTag.GOVERNED_BY_TIER)).isEmpty()
    }

    @Test
    fun `the strictest answer under a tag is the one the tag reads as`() {
        assertThat(LimitAnswer.of(emptyList())).isNull()
        assertThat(LimitAnswer.of(listOf(BoundaryLevel.ALWAYS_OK))).isEqualTo(LimitAnswer.YES)
        assertThat(LimitAnswer.of(listOf(BoundaryLevel.ALWAYS_OK, BoundaryLevel.CURIOUS)))
            .isEqualTo(LimitAnswer.MAYBE)
        assertThat(LimitAnswer.of(listOf(BoundaryLevel.ALWAYS_OK, BoundaryLevel.NEVER)))
            .isEqualTo(LimitAnswer.NO)
        // "Not tonight" is a no for as long as it is set: the profile has no fourth answer to
        // show it as, and reading it as a maybe would put the card back in the deck.
        assertThat(LimitAnswer.of(listOf(BoundaryLevel.NOT_TONIGHT))).isEqualTo(LimitAnswer.NO)
        assertThat(LimitAnswer.of(listOf(BoundaryLevel.ASK_FIRST))).isEqualTo(LimitAnswer.MAYBE)
    }

    @Test
    fun `an answer is written to a level the engine already understands`() {
        assertThat(LimitAnswer.YES.asBoundary()).isEqualTo(BoundaryLevel.ALWAYS_OK)
        assertThat(LimitAnswer.MAYBE.asBoundary()).isEqualTo(BoundaryLevel.CURIOUS)
        // The one that has to be exact: a no is the hard exclusion the chokepoint acts on (§3.2).
        assertThat(LimitAnswer.NO.asBoundary()).isEqualTo(BoundaryLevel.NEVER)
        assertThat(LimitAnswer.NO.asBoundary().isHardExclusion).isTrue()
    }
}
