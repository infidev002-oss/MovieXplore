package your.app.watchlist

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistItem>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE uniqueId = :uniqueId)")
    fun observeExists(uniqueId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE uniqueId = :uniqueId)")
    suspend fun exists(uniqueId: String): Boolean

    @Upsert
    suspend fun upsert(item: WatchlistItem)

    @Upsert
    suspend fun upsertAll(items: List<WatchlistItem>)

    @Query("DELETE FROM watchlist WHERE uniqueId = :uniqueId")
    suspend fun delete(uniqueId: String)

    @Query("DELETE FROM watchlist")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<WatchlistItem>) {
        clear()
        upsertAll(items)
    }
}

@Database(entities = [WatchlistItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "app.db")
                .build().also { instance = it }
        }
    }
}
