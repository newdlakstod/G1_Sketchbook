package com.g1.sketchbook.data

import android.content.Context

/** Tiny persistence for "which room am I in", so the app reopens straight into it. */
class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("g1_session", Context.MODE_PRIVATE)

    var currentRoomId: String?
        get() = prefs.getString(KEY_ROOM, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_ROOM) else putString(KEY_ROOM, value)
        }.apply()

    companion object {
        private const val KEY_ROOM = "current_room"
    }
}
