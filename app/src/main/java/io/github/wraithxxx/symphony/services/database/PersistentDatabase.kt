package io.github.wraithxxx.symphony.services.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.database.store.PlaylistStore
import io.github.wraithxxx.symphony.services.groove.Playlist
import io.github.wraithxxx.symphony.utils.RoomConvertors

@Database(entities = [Playlist::class], version = 1)
@TypeConverters(RoomConvertors::class)
abstract class PersistentDatabase : RoomDatabase() {
    abstract fun playlists(): PlaylistStore

    companion object {
        fun create(symphony: Symphony) = Room
            .databaseBuilder(
                symphony.applicationContext,
                PersistentDatabase::class.java,
                "persistent"
            )
            .build()
    }
}
