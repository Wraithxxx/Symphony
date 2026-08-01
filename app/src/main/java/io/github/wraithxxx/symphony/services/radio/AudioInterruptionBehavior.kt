package io.github.wraithxxx.symphony.services.radio

enum class AudioInterruptionBehavior {
    PauseAndResume,
    KeepPlaying;

    companion object {
        fun fromLegacyIgnoreLoss(ignoreLoss: Boolean) = when {
            ignoreLoss -> KeepPlaying
            else -> PauseAndResume
        }
    }
}
