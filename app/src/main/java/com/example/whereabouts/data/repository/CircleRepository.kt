package com.example.whereabouts.data.repository

import com.example.whereabouts.data.model.CircleContact
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CircleRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // ── User lookup ───────────────────────────────────────────────────────────

    /**
     * Search the users collection for an account with [email].
     * Returns a partially filled [CircleContact] (uid + profile) or null if not found.
     */
    suspend fun findUserByEmail(email: String): CircleContact? {
        return try {
            val query = firestore.collection("users")
                .whereEqualTo("email", email.trim().lowercase())
                .limit(1)
                .get()
                .await()
            val doc = query.documents.firstOrNull() ?: return null
            CircleContact(
                uid      = doc.id,
                name     = doc.getString("name")     ?: "",
                email    = doc.getString("email")    ?: "",
                photoUrl = doc.getString("photoUrl") ?: ""
            )
        } catch (_: Exception) {
            null
        }
    }

    // ── Invite flow ───────────────────────────────────────────────────────────

    /**
     * Write mirrored documents in both
     *   circles/{me.uid}/contacts/{target.uid}
     *   circles/{target.uid}/contacts/{me.uid}
     * both with status="pending" and initiatedBy=me.uid.
     */
    suspend fun sendInvite(me: CircleContact, target: CircleContact): Result<Unit> {
        return try {
            val batch = firestore.batch()
            val ts    = System.currentTimeMillis()

            // My side: I sent this invite
            batch.set(
                circleRef(me.uid, target.uid),
                contactMap(target, initiatedBy = me.uid, timestamp = ts)
            )
            // Their side: they received this invite
            batch.set(
                circleRef(target.uid, me.uid),
                contactMap(me, initiatedBy = me.uid, timestamp = ts)
            )

            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accept an incoming invite from [fromUid].
     * Flips status to "accepted" in both circle documents.
     */
    suspend fun acceptInvite(myUid: String, fromUid: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            batch.update(circleRef(myUid,   fromUid), "status", "accepted")
            batch.update(circleRef(fromUid, myUid),   "status", "accepted")
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reject an incoming invite OR cancel a sent invite OR remove an accepted contact.
     * Deletes the document from both sides.
     */
    suspend fun removeContact(myUid: String, contactUid: String): Result<Unit> {
        return try {
            val batch = firestore.batch()
            batch.delete(circleRef(myUid,      contactUid))
            batch.delete(circleRef(contactUid, myUid))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Real-time stream ──────────────────────────────────────────────────────

    /** Emits the live list of all entries in circles/{myUid}/contacts. */
    fun observeCircle(myUid: String): Flow<List<CircleContact>> = callbackFlow {
        val ref = firestore
            .collection("circles")
            .document(myUid)
            .collection("contacts")

        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) { trySend(emptyList()); return@addSnapshotListener }
            val list = snapshot.documents.mapNotNull { doc ->
                CircleContact(
                    uid             = doc.getString("uid")          ?: doc.id,
                    name            = doc.getString("name")         ?: "",
                    email           = doc.getString("email")        ?: "",
                    photoUrl        = doc.getString("photoUrl")     ?: "",
                    status          = doc.getString("status")       ?: "pending",
                    initiatedBy     = doc.getString("initiatedBy")  ?: "",
                    timestampMillis = doc.getLong("timestampMillis") ?: 0L
                )
            }
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    // ── FCM token ─────────────────────────────────────────────────────────────

    /** Persist the device FCM token so Cloud Functions can push to this device. */
    suspend fun saveFcmToken(uid: String, token: String) {
        try {
            firestore.collection("users").document(uid)
                .update("fcmToken", token)
                .await()
        } catch (_: Exception) { /* best-effort */ }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun circleRef(ownerUid: String, contactUid: String) =
        firestore.collection("circles")
            .document(ownerUid)
            .collection("contacts")
            .document(contactUid)

    private fun contactMap(
        contact: CircleContact,
        initiatedBy: String,
        timestamp: Long
    ) = mapOf(
        "uid"             to contact.uid,
        "name"            to contact.name,
        "email"           to contact.email,
        "photoUrl"        to contact.photoUrl,
        "status"          to "pending",
        "initiatedBy"     to initiatedBy,
        "timestampMillis" to timestamp
    )
}
