package io.github.wraithxxx.symphony.ui.helpers

import io.github.wraithxxx.symphony.utils.SimpleFileSystem
import io.github.wraithxxx.symphony.utils.SimplePath

fun SimpleFileSystem.Folder.navigateToFolder(path: SimplePath): SimpleFileSystem.Folder? {
    var folder: SimpleFileSystem.Folder? = this
    path.parts.forEach { x ->
        folder = folder?.let {
            val child = it.children[x]
            child as? SimpleFileSystem.Folder
        }
    }
    return folder
}
