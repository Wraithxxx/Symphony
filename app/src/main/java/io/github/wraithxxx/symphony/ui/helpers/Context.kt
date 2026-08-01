package io.github.wraithxxx.symphony.ui.helpers

import androidx.navigation.NavHostController
import io.github.wraithxxx.symphony.MainActivity
import io.github.wraithxxx.symphony.Symphony

data class ViewContext(
    val symphony: Symphony,
    val activity: MainActivity,
    val navController: NavHostController,
) {
    companion object {
        fun <T> parameterizedFn(fn: (ViewContext) -> T) = fn
    }
}
