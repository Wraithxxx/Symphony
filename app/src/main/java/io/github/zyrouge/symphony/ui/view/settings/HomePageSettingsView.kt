package io.github.zyrouge.symphony.ui.view.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.zyrouge.symphony.ui.components.IconButtonPlaceholder
import io.github.zyrouge.symphony.ui.components.TopAppBarMinimalTitle
import io.github.zyrouge.symphony.ui.components.settings.ConsiderContributingTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsMultiOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsOptionTile
import io.github.zyrouge.symphony.ui.components.settings.SettingsSideHeading
import io.github.zyrouge.symphony.ui.components.settings.SettingsSwitchTile
import io.github.zyrouge.symphony.ui.helpers.ViewContext
import io.github.zyrouge.symphony.ui.view.HomePage
import io.github.zyrouge.symphony.ui.view.HomePageBottomBarLabelVisibility
import io.github.zyrouge.symphony.ui.view.homeNavigationPages
import io.github.zyrouge.symphony.ui.view.home.ForYou
import kotlinx.serialization.Serializable

@Serializable
object HomePageSettingsViewRoute

private const val BOTTOM_NAVIGATION = "Bottom navigation"
private const val NAVIGATION_PAGE_1 = "Navigation page 1"
private const val NAVIGATION_PAGE_2 = "Navigation page 2"
private const val SELECT_EXACTLY_FIVE = "Select exactly 5 destinations"
private const val ENABLE_TRANSITION_BUTTONS = "Enable transition buttons"
private const val TRANSITION_BUTTONS_DESCRIPTION =
    "Show < and > at the ends of the bottom bar"
private const val NAVIGATION_GESTURE_DESCRIPTION =
    "Touch and hold the bottom bar until it gently moves, then drag left or right to switch pages."

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomePageSettingsView(context: ViewContext) {
    val scrollState = rememberScrollState()
    val homeTabs by context.symphony.settings.homeTabs.flow.collectAsState()
    val showTransitionButtons by context.symphony.settings.homeNavigationTransitionButtons.flow.collectAsState()
    val forYouContents by context.symphony.settings.forYouContents.flow.collectAsState()
    val homePageBottomBarLabelVisibility by context.symphony.settings.homePageBottomBarLabelVisibility.flow.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    TopAppBarMinimalTitle {
                        Text("${context.symphony.t.Settings} - ${context.symphony.t.Home}")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                navigationIcon = {
                    IconButton(
                        onClick = {
                            context.navController.popBackStack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    IconButtonPlaceholder()
                },
            )
        },
        content = { contentPadding ->
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                Column(modifier = Modifier.verticalScroll(scrollState)) {
                    ConsiderContributingTile(context)
                    SettingsSideHeading(context.symphony.t.Home)
                    SettingsSideHeading(BOTTOM_NAVIGATION)
                    val navigationPages = homeNavigationPages(homeTabs)
                    SettingsMultiOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Home, null)
                        },
                        title = {
                            Text(NAVIGATION_PAGE_1)
                        },
                        note = {
                            Text(SELECT_EXACTLY_FIVE)
                        },
                        value = navigationPages[0].toSet(),
                        values = HomePage.entries.associateWith { it.label(context) },
                        satisfies = { it.size == 5 },
                        onChange = { value ->
                            context.symphony.settings.homeTabs.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsMultiOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.GridView, null)
                        },
                        title = {
                            Text(NAVIGATION_PAGE_2)
                        },
                        note = {
                            Text(SELECT_EXACTLY_FIVE)
                        },
                        value = navigationPages[1].toSet(),
                        values = HomePage.entries.associateWith { it.label(context) },
                        satisfies = { it.size == 5 },
                        onChange = { value ->
                            context.symphony.settings.homeTabs.setValue(
                                HomePage.entries.filterNot(value::contains).toSet(),
                            )
                        }
                    )
                    HorizontalDivider()
                    SettingsSwitchTile(
                        icon = {
                            Icon(Icons.Filled.SwapHoriz, null)
                        },
                        title = {
                            Text(ENABLE_TRANSITION_BUTTONS)
                        },
                        subtitle = {
                            Text(TRANSITION_BUTTONS_DESCRIPTION)
                        },
                        value = showTransitionButtons,
                        onChange = {
                            context.symphony.settings.homeNavigationTransitionButtons.setValue(it)
                        },
                    )
                    Text(
                        NAVIGATION_GESTURE_DESCRIPTION,
                        modifier = Modifier.padding(
                            start = 56.dp,
                            top = 4.dp,
                            end = 24.dp,
                            bottom = 12.dp,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HorizontalDivider()
                    SettingsMultiOptionTile(
                        context,
                        icon = {
                            Icon(Icons.Filled.Recommend, null)
                        },
                        title = {
                            Text(context.symphony.t.ForYou)
                        },
                        value = forYouContents,
                        values = ForYou.entries.associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.forYouContents.setValue(value)
                        }
                    )
                    HorizontalDivider()
                    SettingsOptionTile(
                        icon = {
                            Icon(Icons.AutoMirrored.Filled.Label, null)
                        },
                        title = {
                            Text(context.symphony.t.BottomBarLabelVisibility)
                        },
                        value = homePageBottomBarLabelVisibility,
                        values = HomePageBottomBarLabelVisibility.entries
                            .associateWith { it.label(context) },
                        onChange = { value ->
                            context.symphony.settings.homePageBottomBarLabelVisibility.setValue(
                                value,
                            )
                        }
                    )
                }
            }
        }
    )
}

fun HomePageBottomBarLabelVisibility.label(context: ViewContext) = when (this) {
    HomePageBottomBarLabelVisibility.ALWAYS_VISIBLE -> context.symphony.t.AlwaysVisible
    HomePageBottomBarLabelVisibility.VISIBLE_WHEN_ACTIVE -> context.symphony.t.VisibleWhenActive
    HomePageBottomBarLabelVisibility.INVISIBLE -> context.symphony.t.Invisible
}
