package com.example.whereabouts.data.model

data class CircleContact(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val photoUrl: String = "",
    val status: String = "pending",   // "pending" | "accepted"
    val initiatedBy: String = "",     // uid of whoever sent the invite
    val timestampMillis: Long = 0L
) {
    /** Two-letter initials for avatar: "Samiksha Mahure" → "SM" */
    val initials: String
        get() = name
            .split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
            .ifBlank { email.take(2).uppercase() }
}
