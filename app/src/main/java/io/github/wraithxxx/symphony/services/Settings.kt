package io.github.wraithxxx.symphony.services

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import io.github.wraithxxx.symphony.Symphony
import io.github.wraithxxx.symphony.services.groove.repositories.AlbumArtistRepository
import io.github.wraithxxx.symphony.services.groove.repositories.AlbumRepository
import io.github.wraithxxx.symphony.services.groove.repositories.ArtistRepository
import io.github.wraithxxx.symphony.services.groove.repositories.GenreRepository
import io.github.wraithxxx.symphony.services.groove.repositories.PlaylistRepository
import io.github.wraithxxx.symphony.services.groove.repositories.SongRepository
import io.github.wraithxxx.symphony.services.radio.AudioInterruptionBehavior
import io.github.wraithxxx.symphony.services.radio.RadioQueue
import io.github.wraithxxx.symphony.ui.components.ResponsiveGridColumns
import io.github.wraithxxx.symphony.ui.theme.ThemeMode
import io.github.wraithxxx.symphony.ui.view.HomePage
import io.github.wraithxxx.symphony.ui.view.HomePageBottomBarLabelVisibility
import io.github.wraithxxx.symphony.ui.view.homeNavigationPages
import io.github.wraithxxx.symphony.ui.view.NowPlayingControlsLayout
import io.github.wraithxxx.symphony.ui.view.NowPlayingLyricsLayout
import io.github.wraithxxx.symphony.ui.view.home.ForYou
import io.github.wraithxxx.symphony.utils.ImagePreserver
import io.github.wraithxxx.symphony.utils.StringListUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.enums.EnumEntries
import kotlin.enums.enumEntries

class Settings(private val symphony: Symphony) {
    abstract class Entry<T>(val key: String) {
        private val mutableFlow by lazy {
            MutableStateFlow(getValueInternal())
        }

        val flow get() = mutableFlow.asStateFlow()
        val value get() = getValueInternal()

        fun setValue(value: T) {
            setValueInternal(value)
            mutableFlow.update { getValueInternal() }
        }

        protected abstract fun getValueInternal(): T
        protected abstract fun setValueInternal(value: T)
    }

    inner class BooleanEntry(key: String, val defaultValue: Boolean) : Entry<Boolean>(key) {
        override fun getValueInternal() = getSharedPreferences().getBoolean(key, defaultValue)

        override fun setValueInternal(value: Boolean) = getSharedPreferences().edit {
            putBoolean(key, value)
        }
    }

    inner class IntEntry(key: String, val defaultValue: Int) : Entry<Int>(key) {
        override fun getValueInternal() = getSharedPreferences().getInt(key, defaultValue)

        override fun setValueInternal(value: Int) = getSharedPreferences().edit {
            putInt(key, value)
        }
    }

    inner class FloatEntry(key: String, val defaultValue: Float) : Entry<Float>(key) {
        override fun getValueInternal() = getSharedPreferences().getFloat(key, defaultValue)

        override fun setValueInternal(value: Float) = getSharedPreferences().edit {
            putFloat(key, value)
        }
    }

    inner class NullableStringEntry(key: String) : Entry<String?>(key) {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)

        override fun setValueInternal(value: String?) = getSharedPreferences().edit {
            putString(key, value)
        }
    }

    inner class EnumEntry<T : Enum<T>>(
        key: String,
        private val values: EnumEntries<T>,
        val defaultValue: T,
    ) : Entry<T>(key) {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)
            ?.let { values.find { x -> x.name == it } }
            ?: defaultValue

        override fun setValueInternal(value: T) = getSharedPreferences().edit {
            putString(key, value.name)
        }
    }

    inner class StringSetEntry(
        key: String,
        val defaultValue: Set<String>,
    ) : Entry<Set<String>>(key) {
        override fun getValueInternal() =
            getSharedPreferences().getStringSet(key, null) ?: defaultValue

        override fun setValueInternal(value: Set<String>) = getSharedPreferences().edit {
            putStringSet(key, value)
        }
    }

    inner class EnumSetEntry<T : Enum<T>>(
        key: String,
        values: EnumEntries<T>,
        val defaultValue: Set<T>,
    ) : Entry<Set<T>>(key) {
        private val entries = values.associateBy { it.name }

        override fun getValueInternal() = getSharedPreferences().getString(key, null)
            ?.split(",")
            ?.mapNotNull { entries[it] }
            ?.toSet()
            ?: defaultValue

        override fun setValueInternal(value: Set<T>) = getSharedPreferences().edit {
            putString(key, value.joinToString(",") { it.name })
        }
    }

    val themeMode = EnumEntry("theme_mode", enumEntries<ThemeMode>(), ThemeMode.SYSTEM)
    val language = NullableStringEntry("language")
    val useMaterialYou = BooleanEntry("material_you", true)
    val useOriginalAppIcon = BooleanEntry(USE_ORIGINAL_APP_ICON_KEY, false)
    val lastUsedSongsSortBy = EnumEntry(
        "last_used_song_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.TITLE,
    )
    val lastUsedSongsSortReverse = BooleanEntry("last_used_song_sort_reverse", false)
    val lastUsedArtistsSortBy = EnumEntry(
        "last_used_artists_sort_by",
        enumEntries<ArtistRepository.SortBy>(),
        ArtistRepository.SortBy.ARTIST_NAME,
    )
    val lastUsedArtistsSortReverse = BooleanEntry("last_used_artists_sort_reverse", false)
    val lastUsedArtistsHorizontalGridColumns = IntEntry(
        "last_used_artists_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedArtistsVerticalGridColumns = IntEntry(
        "last_used_artists_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedAlbumArtistsSortBy = EnumEntry(
        "last_used_album_artists_sort_by",
        enumEntries<AlbumArtistRepository.SortBy>(),
        AlbumArtistRepository.SortBy.ARTIST_NAME,
    )
    val lastUsedAlbumArtistsSortReverse =
        BooleanEntry("last_used_album_artists_sort_reverse", false)
    val lastUsedAlbumArtistsHorizontalGridColumns = IntEntry(
        "last_used_album_artists_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedAlbumArtistsVerticalGridColumns = IntEntry(
        "last_used_album_artists_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedAlbumsSortBy = EnumEntry(
        "last_used_albums_sort_by",
        enumEntries<AlbumRepository.SortBy>(),
        AlbumRepository.SortBy.ALBUM_NAME,
    )
    val lastUsedAlbumsSortReverse = BooleanEntry("last_used_albums_sort_reverse", false)
    val lastUsedAlbumsHorizontalGridColumns = IntEntry(
        "last_used_albums_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedAlbumsVerticalGridColumns = IntEntry(
        "last_used_albums_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedGenresSortBy = EnumEntry(
        "last_used_genres_sort_by",
        enumEntries<GenreRepository.SortBy>(),
        GenreRepository.SortBy.GENRE,
    )
    val lastUsedGenresSortReverse = BooleanEntry("last_used_genres_sort_reverse", false)
    val lastUsedGenresHorizontalGridColumns = IntEntry(
        "last_used_genres_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedGenresVerticalGridColumns = IntEntry(
        "last_used_genres_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedBrowserSortBy = EnumEntry(
        "last_used_folder_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.FILENAME,
    )
    val lastUsedBrowserSortReverse = BooleanEntry("last_used_folder_sort_reverse", false)
    val lastUsedBrowserPath = NullableStringEntry("last_used_folder_path")
    val lastUsedPlaylistsSortBy = EnumEntry(
        "last_used_playlists_sort_by",
        enumEntries<PlaylistRepository.SortBy>(),
        PlaylistRepository.SortBy.CUSTOM,
    )
    val lastUsedPlaylistsSortReverse = BooleanEntry("last_used_playlists_sort_reverse", false)
    val lastUsedPlaylistsHorizontalGridColumns = IntEntry(
        "last_used_playlists_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedPlaylistsVerticalGridColumns = IntEntry(
        "last_used_playlists_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastUsedPlaylistSongsSortBy = EnumEntry(
        "last_used_playlist_songs_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.CUSTOM,
    )
    val lastUsedPlaylistSongsSortReverse =
        BooleanEntry("last_used_playlist_songs_sort_reverse", false)
    val lastUsedAlbumSongsSortBy = EnumEntry(
        "last_used_album_songs_sort_by",
        enumEntries<SongRepository.SortBy>(),
        SongRepository.SortBy.TRACK_NUMBER,
    )
    val lastUsedAlbumSongsSortReverse = BooleanEntry("last_used_album_songs_sort_reverse", false)
    val lastUsedTreePathSortBy = EnumEntry(
        "last_used_tree_path_sort_by",
        enumEntries<StringListUtils.SortBy>(),
        StringListUtils.SortBy.NAME,
    )
    val lastUsedTreePathSortReverse = BooleanEntry("last_used_tree_path_sort_reverse", false)
    val lastUsedFoldersSortBy = EnumEntry(
        "last_used_folders_sort_by",
        enumEntries<StringListUtils.SortBy>(),
        StringListUtils.SortBy.NAME,
    )
    val lastUsedFoldersSortReverse = BooleanEntry("last_used_folders_sort_reverse", false)
    val lastUsedFoldersHorizontalGridColumns = IntEntry(
        "last_used_folders_horizontal_grid_columns",
        ResponsiveGridColumns.DEFAULT_HORIZONTAL_COLUMNS,
    )
    val lastUsedFoldersVerticalGridColumns = IntEntry(
        "last_used_folders_vertical_grid_columns",
        ResponsiveGridColumns.DEFAULT_VERTICAL_COLUMNS,
    )
    val lastDisabledTreePaths = StringSetEntry("last_disabled_tree_paths", emptySet())
    val previousSongQueue = object : Entry<RadioQueue.Serialized?>("previous_song_queue") {
        override fun getValueInternal() = getSharedPreferences().getString(key, null)?.let {
            RadioQueue.Serialized.parse(it)
        }

        override fun setValueInternal(value: RadioQueue.Serialized?) =
            getSharedPreferences().edit {
                putString(key, value?.serialize())
            }
    }
    val lastLoopMode = object : Entry<RadioQueue.LoopMode>("last_loop_mode") {
        override fun getValueInternal() = getSharedPreferences()
            .getString(key, null)
            ?.let { stored -> RadioQueue.LoopMode.values.find { it.name == stored } }
            ?: RadioQueue.LoopMode.None

        override fun setValueInternal(value: RadioQueue.LoopMode) {
            getSharedPreferences().edit(commit = true) {
                putString(key, value.name)
            }
        }
    }
    val lastHomeTab = EnumEntry("home_last_page", enumEntries<HomePage>(), HomePage.Songs)
    val songsFilterPattern = NullableStringEntry("songs_filter_pattern")
    val minSongDuration = IntEntry("min_song_duration", 0)
    val checkForUpdates = BooleanEntry("check_for_updates", false)
    val fadePlayback = BooleanEntry("fade_playback", false)
    val audioInterruptionBehavior = EnumEntry(
        "audio_interruption_behavior",
        enumEntries<AudioInterruptionBehavior>(),
        AudioInterruptionBehavior.PauseAndResume,
    )
    val playOnHeadphonesConnect = BooleanEntry("play_on_headphones_connect", false)
    val pauseOnHeadphonesDisconnect = BooleanEntry("pause_on_headphones_disconnect", true)
    val primaryColor = NullableStringEntry("primary_color")
    val fadePlaybackDuration = FloatEntry("fade_playback_duration", 1f)
    val homeTabs = EnumSetEntry(
        "home_tabs",
        enumEntries<HomePage>(),
        setOf(
            HomePage.ForYou,
            HomePage.Songs,
            HomePage.Albums,
            HomePage.Artists,
            HomePage.Playlists,
        ),
    )
    val homePageBottomBarLabelVisibility = EnumEntry(
        "home_page_bottom_bar_label_visibility",
        enumEntries<HomePageBottomBarLabelVisibility>(),
        HomePageBottomBarLabelVisibility.ALWAYS_VISIBLE,
    )
    val homeNavigationTransitionButtons = BooleanEntry(
        "home_navigation_transition_buttons",
        false,
    )
    val lastHomeNavigationPage = IntEntry("home_navigation_last_page", 0)
    val forYouContents = EnumSetEntry(
        "for_you_contents",
        enumEntries<ForYou>(),
        setOf(ForYou.Albums, ForYou.Artists),
    )
    val blacklistFolders = StringSetEntry("blacklist_folders", emptySet())
    val whitelistFolders = StringSetEntry("whitelist_folders", emptySet())
    val readIntroductoryMessage = BooleanEntry("introductory_message", false)
    val nowPlayingAdditionalInfo = BooleanEntry("show_now_playing_additional_info", true)
    val nowPlayingSeekControls = BooleanEntry("enable_seek_controls", false)
    val seekBackDuration = IntEntry(SEEK_BACK_DURATION_KEY, 15)
    val seekForwardDuration = IntEntry(SEEK_FORWARD_DURATION_KEY, 30)
    val rememberTrackPositions = BooleanEntry("remember_track_positions", false)
    val minimumRememberedTrackDurationMinutes = IntEntry(
        "minimum_remembered_track_duration_minutes",
        20,
    )

    init {
        val preferences = getSharedPreferences()
        if (!preferences.contains(audioInterruptionBehavior.key)) {
            audioInterruptionBehavior.setValue(
                AudioInterruptionBehavior.fromLegacyIgnoreLoss(
                    preferences.getBoolean(LEGACY_IGNORE_AUDIO_FOCUS_LOSS_KEY, false),
                )
            )
        }
        val normalizedHomeTabs = homeNavigationPages(homeTabs.value).first().toSet()
        if (homeTabs.value.toList() != normalizedHomeTabs.toList()) {
            homeTabs.setValue(normalizedHomeTabs)
        }
        if (
            !preferences.contains(SEEK_FORWARD_DURATION_KEY) &&
            preferences.contains(SEEK_BACK_DURATION_KEY)
        ) {
            preferences.edit {
                putInt(
                    SEEK_FORWARD_DURATION_KEY,
                    preferences.getInt(SEEK_BACK_DURATION_KEY, seekForwardDuration.defaultValue),
                )
            }
        }
    }
    val miniPlayerTrackControls = BooleanEntry("mini_player_extended_controls", false)
    val miniPlayerSeekControls = BooleanEntry("mini_player_seek_controls", false)
    val fontFamily = NullableStringEntry("font_family")
    val nowPlayingControlsLayout = EnumEntry(
        "now_playing_controls_layout",
        enumEntries<NowPlayingControlsLayout>(),
        NowPlayingControlsLayout.Default,
    )
    val showUpdateToast = BooleanEntry("show_update_toast", true)
    val fontScale = FloatEntry("font_scale", 1f)
    val contentScale = FloatEntry("content_scale", 1f)
    val nowPlayingLyricsLayout = EnumEntry(
        "now_playing_lyrics_layout",
        enumEntries<NowPlayingLyricsLayout>(),
        NowPlayingLyricsLayout.ReplaceArtwork,
    )
    val artistTagSeparators = StringSetEntry("artist_tag_separators", setOf(";", "/", ",", "+"))
    val genreTagSeparators = StringSetEntry("genre_tag_separators", setOf(";", "/", ",", "+"))
    val miniPlayerTextMarquee = BooleanEntry("mini_player_text_marquee", true)
    val mediaFolders = object : Entry<Set<Uri>>("media_folders") {
        override fun getValueInternal() = getSharedPreferences().getStringSet(key, null)
            ?.map { Uri.parse(it) }
            ?.toSet()
            ?: emptySet()

        override fun setValueInternal(value: Set<Uri>) =
            getSharedPreferences().edit {
                putStringSet(key, value.map { it.toString() }.toSet())
            }
    }
    val artworkQuality = EnumEntry(
        "artwork_quality",
        enumEntries<ImagePreserver.Quality>(),
        ImagePreserver.Quality.Medium,
    )
    val useMetaphony = BooleanEntry("use_metaphony", true)
    val gaplessPlayback = BooleanEntry("gapless_playback", true)
    val caseSensitiveSorting = BooleanEntry("case_sensitive_sorting", false)
    val lyricsKeepScreenAwake = BooleanEntry("lyrics_keep_screen_awake", true)

    private fun getSharedPreferences() = symphony.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    companion object {
        internal const val PREFERENCES_NAME = "settings"
        internal const val USE_ORIGINAL_APP_ICON_KEY = "use_original_app_icon"
        internal const val SEEK_BACK_DURATION_KEY = "seek_back_duration"
        internal const val SEEK_FORWARD_DURATION_KEY = "seek_forward_duration"
        internal const val LEGACY_IGNORE_AUDIO_FOCUS_LOSS_KEY = "ignore_audio_focus_loss"
    }
}
