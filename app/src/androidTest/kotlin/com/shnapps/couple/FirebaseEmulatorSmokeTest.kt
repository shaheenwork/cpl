package com.shnapps.couple

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import javax.inject.Inject

/**
 * Phase 1 exit criterion: the app really does read and write against the Firebase
 * Emulator Suite (BUILD_PROMPT.md §22).
 *
 * Also the first of the security tests from §7.3 — it asserts that one user cannot read
 * another user's document, which is the foundation every later privacy guarantee is
 * built on.
 *
 * Requires `firebase emulators:start` to be running on the host.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorSmokeTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var auth: FirebaseAuth

    @Inject
    lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun signs_in_and_round_trips_a_document_through_the_emulator() = runBlocking {
        val user = auth.signInAnonymously().await().user
        assertThat(user).isNotNull()
        val uid = requireNotNull(user).uid

        val nickname = "afterhours-${UUID.randomUUID()}"
        firestore.collection("users").document(uid)
            .set(mapOf("displayName" to nickname, "createdAt" to System.currentTimeMillis()))
            .await()

        val readBack = firestore.collection("users").document(uid).get().await()

        assertThat(readBack.exists()).isTrue()
        assertThat(readBack.getString("displayName")).isEqualTo(nickname)
    }

    @Test
    fun one_user_cannot_read_another_users_document() = runBlocking {
        auth.signInAnonymously().await()

        val someoneElse = "not-${UUID.randomUUID()}"

        val error = runCatching {
            firestore.collection("users").document(someoneElse).get().await()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(FirebaseFirestoreException::class.java)
        assertThat((error as FirebaseFirestoreException).code)
            .isEqualTo(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    }
}
