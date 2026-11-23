package ca.cgagnier.wlednativeandroid.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.control.ControlScreen
import ca.cgagnier.wlednativeandroid.ui.effects.EffectsScreen
import ca.cgagnier.wlednativeandroid.ui.favorites.FavoritesScreen
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetail
import ca.cgagnier.wlednativeandroid.ui.homeScreen.DeviceListDetailActions

/** 底部导航页签。 */
enum class HomeTab(@StringRes val labelRes: Int, val icon: ImageVector) {
    DEVICES(R.string.nav_devices, Icons.Filled.Home),
    EFFECTS(R.string.nav_effects, Icons.Filled.Star),
    CONTROL(R.string.nav_control, Icons.Filled.PlayArrow),
    FAVORITES(R.string.nav_favorites, Icons.Filled.Favorite),
}

/**
 * App 主框架：底部四个页签 + 各自的页面内容。
 *
 * 外层 [Scaffold] 故意把 `contentWindowInsets` 设为 0，让内层页面（设备列表自带 TopAppBar、
 * 其余页面用 `statusBarsPadding`）各自处理系统栏，避免状态栏内边距被叠加两次。
 */
@Composable
fun HomeScaffold(
    initialDeviceMacAddress: NavigationEvent? = null,
    openSettings: () -> Unit = {},
    openChangelogs: () -> Unit = {},
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(HomeTab.DEVICES.ordinal) }
    val selectedTab = HomeTab.entries[selectedTabIndex]
    // 每个页签各自保存界面状态：切回来时不会跳回列表首页
    val tabStateHolder = rememberSaveableStateHolder()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                HomeTab.entries.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding()),
        ) {
            tabStateHolder.SaveableStateProvider(selectedTab.name) {
                when (selectedTab) {
                    HomeTab.DEVICES -> DeviceListDetail(
                        initialDeviceMacAddress = initialDeviceMacAddress,
                        actions = DeviceListDetailActions(
                            openSettings = openSettings,
                            openChangelogs = openChangelogs,
                        ),
                    )

                    HomeTab.EFFECTS -> EffectsScreen()

                    HomeTab.CONTROL -> ControlScreen()

                    HomeTab.FAVORITES -> FavoritesScreen()
                }
            }
        }
    }
}
