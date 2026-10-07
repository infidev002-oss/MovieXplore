package your.app.watchlist

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Same class for Room (local table) and Firebase (Users/{uid}/watchlist/{uniqueId}). */
@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey var uniqueId: String = "",
    var id: String = "",
    var title: String = "",
    var type: String = "",        // "movie" or "tv"
    var posterPath: String = "",
    var timestamp: Long = 0L
) {
    companion object {
        fun key(type: String, id: String) = "${type}_$id"
    }
}
