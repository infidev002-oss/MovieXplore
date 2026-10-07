package com.movies.moviexplore.watchlist

import android.content.Context
import com.google.firebase.database.ValueEventListener
import com.movies.moviexplore.auth.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object WatchlistRepository {
    private lateinit var dao: WatchlistDao
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: ValueEventListener? = null
    private var listenUid: String? = null

    /** Call once from Application.onCreate(). Resumes sync if a user is already signed in. */
    fun init(context: Context) {
        dao = AppDatabase.get(context).watchlistDao()
        AuthManager.currentUser()?.let { startListening(it.uid) }
    }

    fun observeAll(): Flow<List<WatchlistItem>> = dao.observeAll()
    fun observeContains(uniqueId: String): Flow<Boolean> = dao.observeExists(uniqueId)

    /** Adds the item if missing, removes it if present. */
    fun toggle(item: WatchlistItem) {
        scope.launch {
            if (dao.exists(item.uniqueId)) remove(item.uniqueId) else add(item)
        }
    }

    fun add(item: WatchlistItem) {
        val stamped = item.copy(timestamp = System.currentTimeMillis())
        scope.launch {
            dao.upsert(stamped)
            AuthManager.currentUser()?.let { WatchlistRemote.add(it.uid, stamped) }
        }
    }

    fun remove(uniqueId: String) {
        scope.launch {
            dao.delete(uniqueId)
            AuthManager.currentUser()?.let { WatchlistRemote.remove(it.uid, uniqueId) }
        }
    }

    /** Merge local items into the account, then mirror the account into the local table. */
    fun onSignedIn(uid: String) {
        scope.launch {
            val remote = suspendCancellableCoroutine { cont ->
                WatchlistRemote.getAll(uid) { cont.resume(it) }
            }
            val remoteIds = remote.map { it.uniqueId }.toSet()
            dao.getAll().filter { it.uniqueId !in remoteIds }.forEach { WatchlistRemote.add(uid, it) }
            startListening(uid)
        }
    }

    fun onSignedOut() {
        stopListening()
        scope.launch { dao.clear() }
    }

    @Synchronized
    private fun startListening(uid: String) {
        if (listenUid == uid && listener != null) return
        stopListening()
        listenUid = uid
        listener = WatchlistRemote.listen(uid) { items ->
            scope.launch { dao.replaceAll(items) }
        }
    }

    @Synchronized
    private fun stopListening() {
        val uid = listenUid
        val l = listener
        if (uid != null && l != null) WatchlistRemote.stopListening(uid, l)
        listener = null
        listenUid = null
    }
}
