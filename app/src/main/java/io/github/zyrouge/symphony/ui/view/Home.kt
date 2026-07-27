package io.github.zyrouge.symphony.ui.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SupervisorAccount
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.services.groove.Groove
import io.github.zyrouge.symphony.ui.components.IntroductoryDialog
import io.github.zyrouge.symphony.ui.components.NowPlayingBottomBar
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.helpers.ScaleTransition
import io.github.zyrouge.symphony.ui.helpers.SlideTransition
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.home.AlbumArtistsView
import io.github.zyrouge.symphony.ui.view.home.AlbumsView
import io.github.zyrouge.symphony.ui.view.home.ArtistsView
import io.github.zyrouge.symphony.ui.view.home.BrowserView
import io.github.zyrouge.symphony.ui.view.home.FoldersView
import io.github.zyrouge.symphony.ui.view.home.ForYouView
import io.github.zyrouge.symphony.ui.view.home.GenresView
import io.github.zyrouge.symphony.ui.view.home.PlaylistsView
import io.github.zyrouge.symphony.ui.view.home.SongsView
import io.github.zyrouge.symphony.ui.view.home.TreeView
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

enum class HomePage(
    val kind: Groove.Kind? = null,
    val label: (context: ViewContext) -> String,
    val selectedIcon: @Composable () -> ImageVector,
    val unselectedIcon: @Composable () -> ImageVector,
) {
    ForYou(
        label = { it.symphony.t.ForYou },
        selectedIcon = { Icons.Filled.Face },
        unselectedIcon = { Icons.Outlined.Face }
    ),
    Songs(
        kind = Groove.Kind.SONG,
        label = { it.symphony.t.Songs },
        selectedIcon = { Icons.Filled.MusicNote },
        unselectedIcon = { Icons.Outlined.MusicNote }
    ),
    Artists(
        kind = Groove.Kind.ARTIST,
        label = { it.symphony.t.Artists },
        selectedIcon = { Icons.Filled.Group },
        unselectedIcon = { Icons.Outlined.Group }
    ),
    Albums(
        kind = Groove.Kind.ALBUM,
        label = { it.symphony.t.Albums },
        selectedIcon = { Icons.Filled.Album },
        unselectedIcon = { Icons.Outlined.Album }
    ),
    AlbumArtists(
        kind = Groove.Kind.ALBUM_ARTIST,
        label = { it.symphony.t.AlbumArtists },
        selectedIcon = { Icons.Filled.SupervisorAccount },
        unselectedIcon = { Icons.Outlined.SupervisorAccount }
    ),
    Genres(
        kind = Groove.Kind.GENRE,
        label = { it.symphony.t.Genres },
        selectedIcon = { Icons.Filled.Tune },
        unselectedIcon = { Icons.Outlined.Tune }
    ),
    Playlists(
        kind = Groove.Kind.PLAYLIST,
        label = { it.symphony.t.Playlists },
        selectedIcon = { Icons.AutoMirrored.Filled.QueueMusic },
        unselectedIcon = { Icons.AutoMirrored.Outlined.QueueMusic }
    ),
    Browser(
        label = { it.symphony.t.Browser },
        selectedIcon = { Icons.Filled.Folder },
        unselectedIcon = { Icons.Outlined.Folder }
    ),
    Folders(
        label = { it.symphony.t.Folders },
        selectedIcon = { Icons.Filled.FolderOpen },
        unselectedIcon = { Icons.Outlined.FolderOpen }
    ),
    Tree(
        label = { it.symphony.t.Tree },
        selectedIcon = { Icons.Filled.AccountTree },
        unselectedIcon = { Icons.Outlined.AccountTree }
    );
}

enum class HomePageBottomBarLabelVisibility {
    ALWAYS_VISIBLE,
    VISIBLE_WHEN_ACTIVE,
    INVISIBLE,
}

private val defaultHomeNavigationPage = listOf(
    HomePage.ForYou,
    HomePage.Songs,
    HomePage.Albums,
    HomePage.Artists,
    HomePage.Playlists,
)

private const val NAVIGATION_PAGE_1_DESCRIPTION = "Navigation page 1"
private const val NAVIGATION_PAGE_2_DESCRIPTION = "Navigation page 2"

fun homeNavigationPages(configuredFirstPage: Set<HomePage>): List<List<HomePage>> {
    val firstPage = buildList {
        configuredFirstPage.forEach {
            if (it !in this) add(it)
        }
        defaultHomeNavigationPage.forEach {
            if (size < 5 && it !in this) add(it)
        }
        HomePage.entries.forEach {
            if (size < 5 && it !in this) add(it)
        }
    }.take(5)
    val secondPage = HomePage.entries.filterNot(firstPage::contains)
    return listOf(firstPage, secondPage)
}

@Serializable
object HomeViewRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeView(context: ViewContext) {
    val coroutineScope = rememberCoroutineScope()
    val readIntroductoryMessage by context.symphony.settings.readIntroductoryMessage.flow.collectAsState()
    val tabs by context.symphony.settings.homeTabs.flow.collectAsState()
    val labelVisibility by context.symphony.settings.homePageBottomBarLabelVisibility.flow.collectAsState()
    val showTransitionButtons by context.symphony.settings.homeNavigationTransitionButtons.flow.collectAsState()
    val storedNavigationPage by context.symphony.settings.lastHomeNavigationPage.flow.collectAsState()
    val currentTab by context.symphony.settings.lastHomeTab.flow.collectAsState()
    val navigationPages = remember(tabs) { homeNavigationPages(tabs) }
    var visibleNavigationPage by remember {
        mutableIntStateOf(storedNavigationPage.coerceIn(0, navigationPages.lastIndex))
    }
    var navigationDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var navigationBarWidthPx by remember { mutableIntStateOf(1) }
    var isNavigationDragging by remember { mutableStateOf(false) }
    var isNavigationSettling by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(storedNavigationPage) {
        if (!isNavigationDragging) {
            visibleNavigationPage = storedNavigationPage.coerceIn(
                0,
                navigationPages.lastIndex,
            )
        }
    }

    LaunchedEffect(currentTab, navigationPages) {
        val destinationPage = navigationPages.indexOfFirst { currentTab in it }
        if (
            destinationPage >= 0 &&
            destinationPage != visibleNavigationPage &&
            !isNavigationDragging
        ) {
            visibleNavigationPage = destinationPage
            navigationDragOffsetPx = 0f
            context.symphony.settings.lastHomeNavigationPage.setValue(destinationPage)
        }
    }

    fun settleNavigationPage(targetPage: Int) {
        if (isNavigationSettling) return
        val boundedTarget = targetPage.coerceIn(0, navigationPages.lastIndex)
        val sourcePage = visibleNavigationPage
        val targetOffset = (sourcePage - boundedTarget) * navigationBarWidthPx.toFloat()
        coroutineScope.launch {
            isNavigationDragging = false
            isNavigationSettling = true
            try {
                animate(
                    initialValue = navigationDragOffsetPx,
                    targetValue = targetOffset,
                    animationSpec = tween(durationMillis = 220),
                ) { value, _ ->
                    navigationDragOffsetPx = value
                }
                visibleNavigationPage = boundedTarget
                context.symphony.settings.lastHomeNavigationPage.setValue(boundedTarget)
            } finally {
                navigationDragOffsetPx = 0f
                isNavigationSettling = false
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    Row(modifier = Modifier.width(96.dp)) {
                        IconButton(
                            content = {
                                Icon(Icons.Filled.Search, null)
                            },
                            onClick = {
                                context.navController.navigate(
                                    SearchViewRoute(currentTab.kind?.name)
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(48.dp))
                    }
                },
                title = {
                    Crossfade(
                        label = "home-title",
                        targetState = currentTab.label(context),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            TopAppBarMinimalTitle { Text(it) }
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            context.symphony.groove.fetch(
                                Groove.FetchOptions(force = true),
                            )
                        },
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            context.symphony.t.Rescan,
                            modifier = Modifier.offset(x = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = {
                            context.navController.navigate(SettingsViewRoute())
                        },
                    ) {
                        Icon(
                            Icons.Filled.Settings,
                            context.symphony.t.Settings,
                            modifier = Modifier.offset(x = (-4).dp),
                        )
                    }
                }
            )
        },
        content = { contentPadding ->
            AnimatedContent(
                label = "home-content",
                targetState = currentTab,
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize(),
                transitionSpec = {
                    SlideTransition.slideUp.enterTransition()
                        .togetherWith(ScaleTransition.scaleDown.exitTransition())
                },
            ) { page ->
                when (page) {
                    HomePage.ForYou -> ForYouView(context)
                    HomePage.Songs -> SongsView(context)
                    HomePage.Albums -> AlbumsView(context)
                    HomePage.Artists -> ArtistsView(context)
                    HomePage.AlbumArtists -> AlbumArtistsView(context)
                    HomePage.Genres -> GenresView(context)
                    HomePage.Browser -> BrowserView(context)
                    HomePage.Folders -> FoldersView(context)
                    HomePage.Playlists -> PlaylistsView(context)
                    HomePage.Tree -> TreeView(context)
                }
            }
        },
        bottomBar = {
            Column {
                NowPlayingBottomBar(context, false)
                val navigationBarHeight = when (labelVisibility) {
                    HomePageBottomBarLabelVisibility.INVISIBLE -> 56.dp
                    else -> 64.dp
                }
                NavigationBar(
                    modifier = Modifier.height(navigationBarHeight),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clipToBounds()
                            .onSizeChanged {
                                navigationBarWidthPx = it.width.coerceAtLeast(1)
                            }
                            .pointerInput(
                                visibleNavigationPage,
                                navigationBarWidthPx,
                                isNavigationSettling,
                            ) {
                                if (!isNavigationSettling) {
                                    detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isNavigationDragging = true
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                        navigationDragOffsetPx = when (visibleNavigationPage) {
                                            0 -> -6.dp.toPx()
                                            else -> 6.dp.toPx()
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        val width = navigationBarWidthPx.toFloat()
                                        val minOffset = when (visibleNavigationPage) {
                                            0 -> -width
                                            else -> -width * 0.08f
                                        }
                                        val maxOffset = when (visibleNavigationPage) {
                                            0 -> width * 0.08f
                                            else -> width
                                        }
                                        navigationDragOffsetPx =
                                            (navigationDragOffsetPx + dragAmount.x)
                                                .coerceIn(minOffset, maxOffset)
                                    },
                                    onDragEnd = {
                                        val threshold = navigationBarWidthPx * 0.18f
                                        val targetPage = when {
                                            visibleNavigationPage == 0 &&
                                                navigationDragOffsetPx <= -threshold -> 1
                                            visibleNavigationPage == 1 &&
                                                navigationDragOffsetPx >= threshold -> 0
                                            else -> visibleNavigationPage
                                        }
                                        settleNavigationPage(targetPage)
                                    },
                                    onDragCancel = {
                                        settleNavigationPage(visibleNavigationPage)
                                    },
                                    )
                                }
                            },
                    ) {
                        val arrowPadding = when {
                            showTransitionButtons -> 40.dp
                            else -> 0.dp
                        }
                        navigationPages.forEachIndexed { pageIndex, page ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .offset {
                                        IntOffset(
                                            x = (
                                                (pageIndex - visibleNavigationPage) *
                                                    navigationBarWidthPx +
                                                    navigationDragOffsetPx
                                                ).roundToInt(),
                                            y = 0,
                                        )
                                    }
                                    .padding(horizontal = arrowPadding)
                                    .then(
                                        when (pageIndex) {
                                            visibleNavigationPage -> Modifier
                                            else -> Modifier.clearAndSetSemantics { }
                                        },
                                    ),
                            ) {
                                    page.forEach { destination ->
                                        val isSelected = currentTab == destination
                                        val label = destination.label(context)
                                        val iconSize by animateDpAsState(
                                            targetValue = if (isSelected) 28.dp else 24.dp,
                                            animationSpec = tween(durationMillis = 160),
                                            label = "home-bottom-bar-icon-size",
                                        )
                                        val iconColor by animateColorAsState(
                                            targetValue = when {
                                                isSelected -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            animationSpec = tween(durationMillis = 160),
                                            label = "home-bottom-bar-icon-color",
                                        )
                                        val showLabel = when (labelVisibility) {
                                            HomePageBottomBarLabelVisibility.ALWAYS_VISIBLE -> true
                                            HomePageBottomBarLabelVisibility.VISIBLE_WHEN_ACTIVE ->
                                                isSelected
                                            HomePageBottomBarLabelVisibility.INVISIBLE -> false
                                        }
                                        val reserveLabelSpace =
                                            labelVisibility !=
                                                HomePageBottomBarLabelVisibility.INVISIBLE

                                        NavigationBarItem(
                                            modifier = Modifier.weight(1f),
                                            selected = isSelected,
                                            icon = {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                ) {
                                                    Box(
                                                        modifier = Modifier.size(32.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Crossfade(
                                                            label = "home-bottom-bar-icon",
                                                            targetState = isSelected,
                                                            animationSpec = tween(
                                                                durationMillis = 160,
                                                            ),
                                                        ) {
                                                            Icon(
                                                                when {
                                                                    it -> destination.selectedIcon()
                                                                    else ->
                                                                        destination.unselectedIcon()
                                                                },
                                                                label,
                                                                modifier = Modifier.size(iconSize),
                                                                tint = iconColor,
                                                            )
                                                        }
                                                    }
                                                    if (reserveLabelSpace) {
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Box(
                                                            modifier = Modifier.height(16.dp),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            if (showLabel) {
                                                                Text(
                                                                    label,
                                                                    style = MaterialTheme.typography
                                                                        .labelSmall.copy(
                                                                            color = iconColor,
                                                                            fontWeight = when {
                                                                                isSelected ->
                                                                                    FontWeight
                                                                                        .SemiBold
                                                                                else ->
                                                                                    FontWeight.Normal
                                                                            },
                                                                        ),
                                                                    textAlign = TextAlign.Center,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    softWrap = false,
                                                                    maxLines = 1,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            label = null,
                                            alwaysShowLabel = false,
                                            colors = NavigationBarItemDefaults.colors(
                                                indicatorColor = Color.Transparent,
                                                selectedTextColor =
                                                    MaterialTheme.colorScheme.primary,
                                            ),
                                            onClick = {
                                                if (!isSelected) {
                                                    context.symphony.settings.lastHomeTab
                                                        .setValue(destination)
                                                }
                                            },
                                        )
                                    }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            navigationPages.indices.forEach { page ->
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .background(
                                            color = when (page) {
                                                visibleNavigationPage ->
                                                    MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    .copy(alpha = 0.45f)
                                            },
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }

                        if (showTransitionButtons) {
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .size(48.dp),
                                enabled = visibleNavigationPage > 0 &&
                                    !isNavigationDragging &&
                                    !isNavigationSettling,
                                onClick = {
                                    settleNavigationPage(visibleNavigationPage - 1)
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    NAVIGATION_PAGE_1_DESCRIPTION,
                                )
                            }
                            IconButton(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .size(48.dp),
                                enabled = visibleNavigationPage <
                                    navigationPages.lastIndex &&
                                    !isNavigationDragging &&
                                    !isNavigationSettling,
                                onClick = {
                                    settleNavigationPage(visibleNavigationPage + 1)
                                },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    NAVIGATION_PAGE_2_DESCRIPTION,
                                )
                            }
                        }
                    }
                }
            }
        }
    )

    if (!readIntroductoryMessage) {
        IntroductoryDialog(
            context,
            onDismissRequest = {
                context.symphony.settings.readIntroductoryMessage.setValue(true)
            },
        )
    }
}
