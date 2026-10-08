package com.shnapps.couple.feature.chat

import com.shnapps.couple.core.model.MessageMedia
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

/**
 * The bytes, without any.
 *
 * It records what was asked for and what was merely offered, because that distinction is the
 * feature: a once-only photo must reach [looked] only when somebody taps it, and never through
 * [wanted] — fetching one is what deletes it (D-083).
 */
internal class FakeChatMedia : ChatMedia {

    override val shown = MutableStateFlow<Map<String, Shown>>(emptyMap())

    /** Ids actually fetched, in order. A once-only photo appears here only after a tap. */
    val looked = mutableListOf<String>()

    /** Ids offered to [wanted], whether or not they were then fetched. */
    val offered = mutableListOf<String>()

    /** Files remembered from this phone's own uploads. */
    val kept = mutableMapOf<String, File>()

    /** What a fetch turns into. The default is a voice note's bytes, which needs no decoder. */
    var result: (MessageMedia) -> Shown = { Shown.Sound(File("fetched_${it.id}")) }

    override fun keep(id: String, file: File) {
        kept[id] = file
    }

    override suspend fun wanted(record: MessageMedia) {
        offered += record.id
        // Once per id, as the real one does: the conversation re-emits constantly.
        if (shown.value.containsKey(record.id)) return
        if (!record.burnAfterView) {
            look(record)
            return
        }
        if (record.gone) shown.value = shown.value + (record.id to Shown.Gone)
    }

    override suspend fun look(record: MessageMedia) {
        looked += record.id
        shown.value = shown.value + (record.id to result(record))
    }

    /** Previews asked for, in order: a send must show something before the upload finishes. */
    val previewed = mutableListOf<String>()

    override suspend fun preview(key: String, file: File) {
        previewed += key
        shown.value = shown.value + (key to Shown.Loading)
    }

    override fun forget(key: String) {
        shown.value = shown.value - key
    }

    override fun clear() {
        looked.clear()
        offered.clear()
        kept.clear()
        shown.value = emptyMap()
    }
}
