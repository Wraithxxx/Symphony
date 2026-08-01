package io.github.wraithxxx.symphony.services.radio

internal class RadioFocusState {
    enum class LossAction {
        None,
        Pause,
        Duck,
    }

    enum class GainAction {
        None,
        Resume,
        RestoreVolume,
    }

    private enum class PendingRecovery {
        None,
        Resume,
        RestoreVolume,
    }

    private var pendingRecovery = PendingRecovery.None

    fun onTransientLoss(isPlaying: Boolean, ignoreLoss: Boolean): LossAction {
        if (ignoreLoss) {
            return LossAction.None
        }
        if (isPlaying) {
            pendingRecovery = PendingRecovery.Resume
            return LossAction.Pause
        }
        return LossAction.None
    }

    fun onDuckLoss(isPlaying: Boolean, ignoreLoss: Boolean): LossAction {
        if (ignoreLoss) {
            return LossAction.None
        }
        if (isPlaying) {
            pendingRecovery = PendingRecovery.RestoreVolume
            return LossAction.Duck
        }
        return LossAction.None
    }

    fun onPermanentLoss(isPlaying: Boolean, ignoreLoss: Boolean): LossAction {
        pendingRecovery = PendingRecovery.None
        return when {
            isPlaying && !ignoreLoss -> LossAction.Pause
            else -> LossAction.None
        }
    }

    fun onGain(): GainAction {
        val action = when (pendingRecovery) {
            PendingRecovery.None -> GainAction.None
            PendingRecovery.Resume -> GainAction.Resume
            PendingRecovery.RestoreVolume -> GainAction.RestoreVolume
        }
        pendingRecovery = PendingRecovery.None
        return action
    }

    fun cancelPendingRecovery() {
        pendingRecovery = PendingRecovery.None
    }
}
