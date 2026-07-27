package io.github.zyrouge.symphony.services.groove

internal class GrooveRefreshGate(private val intervalMs: Long) {
    private var lastSuccessfulRefreshAt: Long? = null

    fun shouldRefresh(now: Long, force: Boolean): Boolean {
        if (force) {
            return true
        }
        val lastSuccess = lastSuccessfulRefreshAt ?: return true
        return now - lastSuccess >= intervalMs
    }

    fun onSuccess(now: Long) {
        lastSuccessfulRefreshAt = now
    }
}
