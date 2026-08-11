package com.g1.sketchbook.data.model

import com.google.firebase.database.IgnoreExtraProperties

/**
 * One completed brush stroke. Points are stored flat as [x0, y0, x1, y1, ...] in
 * normalized 0..1 coordinates so the drawing scales to any screen size and stays compact.
 * All fields have defaults + this is a plain class -> Firebase Realtime Database can
 * deserialize it via reflection.
 */
@IgnoreExtraProperties
data class Stroke(
    var uid: String = "",
    var color: Long = 0xFF000000L,
    var width: Float = 6f,
    var erase: Boolean = false,
    var points: List<Float> = emptyList(),
)

@IgnoreExtraProperties
data class Member(
    var name: String = "",
    var photoUrl: String = "",
    var joinedAt: Long = 0L,
)

@IgnoreExtraProperties
data class RoomMeta(
    var name: String = "",
    var createdBy: String = "",
    var createdAt: Long = 0L,
)

@IgnoreExtraProperties
data class ArchiveEntry(
    var date: String = "",
    var url: String = "",
    var savedBy: String = "",
    var savedByName: String = "",
    var savedAt: Long = 0L,
)
