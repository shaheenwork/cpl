package com.shnapps.couple.core.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Salted PBKDF2 hashing for the app-lock PIN (BUILD_PROMPT.md §3.4, §57).
 *
 * **Threat model, stated plainly.** A 4-to-6 digit PIN has at most a million candidates,
 * so anyone who can read the stored hash can brute-force it offline no matter how the
 * hashing is tuned. The iteration count raises the cost of that attack; it does not
 * prevent it.
 *
 * That is acceptable because app lock is a privacy affordance, not a security boundary:
 * it exists to stop a partner, a flatmate or a passer-by opening an already-unlocked
 * phone. It is not a defence against a determined attacker with the device, and nothing
 * in the product should be designed as if it were. The real protections are Firestore
 * rules and server-side authority (§7.1).
 *
 * The PIN itself is never stored, logged, or sent anywhere.
 */
@Singleton
class PinHasher @Inject constructor() {

    fun newSalt(): String {
        val salt = ByteArray(SALT_BYTES)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    fun hash(pin: String, salt: String): String {
        val saltBytes = Base64.getDecoder().decode(salt)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(ALGORITHM)
            Base64.getEncoder().encodeToString(factory.generateSecret(spec).encoded)
        } finally {
            // Clears the copy PBEKeySpec holds; the caller still owns the original String.
            spec.clearPassword()
        }
    }

    /**
     * Constant-time comparison, so verification time cannot be used to learn how much of
     * a guess was correct.
     */
    fun verify(pin: String, salt: String, expectedHash: String): Boolean {
        val actual = hash(pin, salt).toByteArray()
        val expected = expectedHash.toByteArray()
        if (actual.size != expected.size) return false
        var diff = 0
        for (i in actual.indices) {
            diff = diff or (actual[i].toInt() xor expected[i].toInt())
        }
        return diff == 0
    }

    private companion object {
        const val ALGORITHM = "PBKDF2WithHmacSHA256"
        const val ITERATIONS = 120_000
        const val KEY_LENGTH_BITS = 256
        const val SALT_BYTES = 16
    }
}

/** PIN length bounds, enforced by the UI and by [PinHasher] callers alike. */
object PinRules {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 8

    fun isValid(pin: String): Boolean =
        pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it.isDigit() }
}
