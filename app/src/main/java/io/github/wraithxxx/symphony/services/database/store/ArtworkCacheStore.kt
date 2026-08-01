package io.github.wraithxxx.symphony.services.database.store

import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.database.adapters.FileTreeDatabaseAdapter
import java.nio.file.Paths

class ArtworkCacheStore(val symphony: Symphony) {
    private val adapter = FileTreeDatabaseAdapter(
        Paths
            .get(symphony.applicationContext.dataDir.absolutePath, "covers")
            .toFile()
    )

    fun get(key: String) = adapter.get(key)
    fun all() = adapter.list()
    fun clear() = adapter.clear()
}
