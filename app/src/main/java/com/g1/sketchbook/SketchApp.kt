package com.g1.sketchbook

import android.app.Application
import com.g1.sketchbook.auth.GoogleAuthClient
import com.g1.sketchbook.data.ArchiveRepository
import com.g1.sketchbook.data.RoomRepository
import com.g1.sketchbook.data.SessionStore
import com.google.firebase.database.FirebaseDatabase

/** Simple manual DI container. Held by the Application so ViewModels can reach shared singletons. */
class Graph(app: Application) {
    val sessionStore = SessionStore(app)
    val roomRepository = RoomRepository()
    val archiveRepository = ArchiveRepository()
    val authClient = GoogleAuthClient(
        context = app,
        webClientId = app.getString(R.string.web_client_id),
    )
}

class SketchApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        // Keep the current day's canvas available offline / snappy on reconnect.
        FirebaseDatabase.getInstance().setPersistenceEnabled(true)
        graph = Graph(this)
    }
}
