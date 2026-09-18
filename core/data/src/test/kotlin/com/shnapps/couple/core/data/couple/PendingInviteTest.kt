package com.shnapps.couple.core.data.couple

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PendingInviteTest {

    @Test
    fun `holds a well-formed code until consumed once`() {
        val invite = PendingInvite()
        invite.offer("048213")
        assertThat(invite.consume()).isEqualTo("048213")
        assertThat(invite.consume()).isNull()
    }

    @Test
    fun `ignores anything that is not six digits`() {
        // Links come from outside the app; a malformed or hostile one is simply dropped.
        val invite = PendingInvite()
        for (bad in listOf(null, "", "12345", "1234567", "12a456", "123456;DROP", "../../x")) {
            invite.offer(bad)
            assertThat(invite.pending.value).isNull()
        }
    }
}
