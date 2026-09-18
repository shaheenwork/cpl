package com.shnapps.couple.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The PIN is the fallback that keeps a failed fingerprint from locking someone out of
 * their own app (BUILD_PROMPT.md §3.4), so its handling has to be right even though the
 * lock is a privacy affordance rather than a security boundary.
 */
class PinHasherTest {

    private val hasher = PinHasher()

    @Test
    fun `hashing is deterministic for the same pin and salt`() {
        val salt = hasher.newSalt()
        assertThat(hasher.hash("1234", salt)).isEqualTo(hasher.hash("1234", salt))
    }

    @Test
    fun `the same pin under different salts hashes differently`() {
        // Without this, two users with the same PIN would share a hash, and one leaked
        // hash would reveal every account that picked the same four digits.
        val hashA = hasher.hash("1234", hasher.newSalt())
        val hashB = hasher.hash("1234", hasher.newSalt())
        assertThat(hashA).isNotEqualTo(hashB)
    }

    @Test
    fun `salts are not reused`() {
        val salts = List(SALT_SAMPLE_SIZE) { hasher.newSalt() }
        assertThat(salts.toSet()).hasSize(SALT_SAMPLE_SIZE)
    }

    @Test
    fun `the pin never appears in its own hash`() {
        val salt = hasher.newSalt()
        val pin = "867530"
        assertThat(hasher.hash(pin, salt)).doesNotContain(pin)
    }

    @Test
    fun `verify accepts the correct pin`() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("4821", salt)
        assertThat(hasher.verify("4821", salt, hash)).isTrue()
    }

    @Test
    fun `verify rejects a wrong pin`() {
        val salt = hasher.newSalt()
        val hash = hasher.hash("4821", salt)
        assertThat(hasher.verify("4822", salt, hash)).isFalse()
    }

    @Test
    fun `verify rejects the right pin under the wrong salt`() {
        val hash = hasher.hash("4821", hasher.newSalt())
        assertThat(hasher.verify("4821", hasher.newSalt(), hash)).isFalse()
    }

    @Test
    fun `verify rejects a malformed stored hash instead of throwing`() {
        // A corrupted or truncated preference must fail closed, not crash the lock screen
        // and leave the user with no way in.
        assertThat(hasher.verify("4821", hasher.newSalt(), "not-a-real-hash")).isFalse()
    }

    @Test
    fun `pin rules accept 4 to 8 digits`() {
        assertThat(PinRules.isValid("1234")).isTrue()
        assertThat(PinRules.isValid("12345678")).isTrue()
    }

    @Test
    fun `pin rules reject short long and non-numeric pins`() {
        assertThat(PinRules.isValid("123")).isFalse()
        assertThat(PinRules.isValid("123456789")).isFalse()
        assertThat(PinRules.isValid("12a4")).isFalse()
        assertThat(PinRules.isValid("")).isFalse()
    }

    private companion object {
        const val SALT_SAMPLE_SIZE = 50
    }
}
