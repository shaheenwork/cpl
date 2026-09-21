package com.shnapps.couple.core.engine

import com.shnapps.couple.core.model.ChapterKind
import com.shnapps.couple.core.model.ContentItem

/**
 * The variety pass (engine/SPEC.md §7; BUILD_PROMPT.md §10.5): a night of six or more chapters
 * must mix at least four interaction types. Keeps every slot rule and stays inside the pool.
 */
internal object VarietyPass {

    /** While 6+ chapters mix fewer than 4 types, trade a chapter of the commonest type. */
    fun run(night: MutableList<ContentItem>, input: Assembler.Input, rules: SlotRules) {
        while (night.size >= VARIETY_FROM && typeCount(night) < MIN_TYPES) {
            if (!replaceOneOfMostFrequent(night, input, rules)) return
        }
    }

    private fun replaceOneOfMostFrequent(
        night: MutableList<ContentItem>,
        input: Assembler.Input,
        rules: SlotRules,
    ): Boolean {
        val frequent = mostFrequentType(night)
        for (slot in night.indices.reversed()) {
            val replaceable = input.plan.kinds[slot] != ChapterKind.FINALE && night[slot].interactionType == frequent
            val replacement = if (replaceable) newTypeFor(night, slot, input, rules) else null
            if (replacement != null) {
                night[slot] = replacement
                return true
            }
        }
        return false
    }

    /** The best unused pool item for [slot] whose interaction type the night lacks. */
    private fun newTypeFor(
        night: List<ContentItem>,
        slot: Int,
        input: Assembler.Input,
        rules: SlotRules,
    ): ContentItem? {
        val present = night.mapTo(HashSet()) { it.interactionType }
        val used = night.mapTo(HashSet()) { it.id }
        val best = Best()
        for (item in input.pool) {
            val eligible = item.id !in used && item.interactionType !in present && rules.fits(night, slot, item)
            if (eligible) best.offer(item, targetValue(input, item, slot))
        }
        return best.item
    }

    /** The commonest type; ties go to the type whose latest chapter is later. */
    private fun mostFrequentType(night: List<ContentItem>): Any? {
        val counts = LinkedHashMap<Any, IntArray>()
        night.forEachIndexed { slot, item ->
            val entry = counts.getOrPut(item.interactionType) { intArrayOf(0, -1) }
            entry[0] += 1
            entry[1] = slot
        }
        var frequent: Any? = null
        var best = intArrayOf(-1, -1)
        for ((type, entry) in counts) {
            val later = entry[0] == best[0] && entry[1] > best[1]
            if (entry[0] > best[0] || later) {
                frequent = type
                best = entry
            }
        }
        return frequent
    }
}
