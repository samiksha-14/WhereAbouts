package com.example.whereabouts.data.repository

import com.example.whereabouts.data.model.ContactLocation
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MapRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val database: FirebaseDatabase
) {

    /**
     * Emits the list of accepted contact UIDs for [myUid] in real time.
     * Reads from Firestore circles/{myUid}/contacts where status == "accepted".
     * Returns empty list if circle is empty (Phase 4 default — contacts added in Phase 5).
     */
    fun observeAcceptedContactUids(myUid: String): Flow<List<String>> = callbackFlow {
        val ref = firestore
            .collection("circles")
            .document(myUid)
            .collection("contacts")

        val listener = ref.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                trySend(emptyList())
                return@addSnapshotListener
            }
            val uids = snapshot.documents
                .filter { it.getString("status") == "accepted" }
                .map { it.id }
            trySend(uids)
        }

        awaitClose { listener.remove() }
    }

    /**
     * Listens to RTDB locations/{uid} in real time.
     * Returns null if the node doesn't exist or has no valid location.
     * Needs the contact's Firestore profile to populate name/email/photo.
     */
    fun observeContactLocation(
        uid: String,
        name: String,
        email: String,
        photoUrl: String
    ): Flow<ContactLocation?> = callbackFlow {
        val ref = database.getReference("locations").child(uid)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    trySend(null)
                    return
                }
                val lat        = snapshot.child("lat").getValue(Double::class.java) ?: run { trySend(null); return }
                val lng        = snapshot.child("lng").getValue(Double::class.java) ?: run { trySend(null); return }
                val accuracy   = (snapshot.child("accuracy").getValue(Double::class.java) ?: 0.0).toFloat()
                val battery    = (snapshot.child("battery").getValue(Long::class.java) ?: -1L).toInt()
                val timestamp  = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                val sessionEnd = snapshot.child("sessionEnd").getValue(Long::class.java)
                val isLive     = snapshot.child("isLive").getValue(Boolean::class.java) ?: false

                trySend(
                    ContactLocation(
                        uid           = uid,
                        name          = name,
                        email         = email,
                        photoUrl      = photoUrl,
                        lat           = lat,
                        lng           = lng,
                        accuracy      = accuracy,
                        batteryPercent = battery,
                        timestampMillis = timestamp,
                        sessionEnded  = sessionEnd != null,
                        isLive        = isLive
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                trySend(null)
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * Single fetch of a user's Firestore profile.
     * Returns triple of (name, email, photoUrl) or fallback empty strings.
     */
    suspend fun getUserProfile(uid: String): Triple<String, String, String> {
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            Triple(
                doc.getString("name")     ?: "",
                doc.getString("email")    ?: "",
                doc.getString("photoUrl") ?: ""
            )
        } catch (e: Exception) {
            Triple("", "", "")
        }
    }
}
