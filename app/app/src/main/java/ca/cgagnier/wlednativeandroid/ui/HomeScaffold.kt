package ca.cgagnier.wlednativeandroid.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.ui.create.CreateScreen
import ca.cgagnier.wlednativeandroid.ui.device.DeviceScreen
import ca.cgagnier.wlednativeandroid.ui.isle.Glyph
import ca.cgagnier.wlednativeandroid.ui.isle.GlyphIcon
import ca.cgagnier.wlednativeandroid.ui.isle.IsleColors
import ca.cgagnier.wlednativeandroid.ui.isle.SelectedDeviceStore
import ca.cgagnier.wlednativeandroid.ui.library.LibraryScreen
import ca.cgagnier.wlednativeandroid.ui.settings.SettingsScreen
import org.koin.compose.koinInject

/** 底部导航页签。顺序即设计稿：设备 / 灯效库 / 创作 / 设置。 */
enum class HomeTab(@StringRes val labelRes: Int, val glyph: Glyph) {
    DEVICE(R.string.isle_tab_device, Glyph.Devices),
    LIBRARY(R.string.isle_tab_library, Glyph.Library),
    CREATE(R.string.isle_tab_create, Glyph.Create),
    SETTINGS(R.string.isle_tab_settings, Glyph.Settings),
}

/**
 * App 主框架：四个页签 + 自绘底部导航。
 *
 * 用自绘导航栏而不是 `NavigationBar`：设计稿要的是"文字压在图标下、选中项只有颜色变化、
 * 整条栏与背景融为一体"，Material3 的默认胶囊指示器在这里反而抢眼。
 */
@Composable
fun HomeScaffold(
    initialDeviceMacAddress: NavigationEvent? = null,
    openSettings: () -> Unit = {},
    openChangelogs: () -> Unit = {},
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(HomeTab.DEVICE.ordinal) }
    val selectedTab = HomeTab.entries[selectedTabIndex]
    val tabStateHolder = rememberSaveableStateHolder()
    val selectedDeviceStore = koinInject<SelectedDeviceStore>()

    // 深链带过来的设备直接成为当前设备，其它三个页签也跟着切过去
    LaunchedEffect(initialDeviceMacAddress) {
        initialDeviceMacAddress?.let { selectedDeviceStore.select(it.macAddress) }
    }

    // 光屿是固定的深色视觉，Material 组件（弹层、涟漪、默认文字色）也必须跟着暗下来，
    // 否则系统处于浅色主题时会在深色页面里漏出白色的底和灰边。
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = IsleColors.Cyan,
            secondary = IsleColors.Violet,
            background = IsleColors.Background,
            surface = IsleColors.Surface,
            surfaceVariant = IsleColors.SurfaceHigh,
            onPrimary = Color(0xFF08202A),
            onBackground = IsleColors.TextPrimary,
            onSurface = IsleColors.TextPrimary,
            onSurfaceVariant = IsleColors.TextSecondary,
            outline = IsleColors.StrokeStrong,
        ),
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                IsleBottomBar(
                    selected = selectedTab,
                    onSelect = { selectedTabIndex = it.ordinal },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                tabStateHolder.SaveableStateProvider(selectedTab.name) {
                    when (selectedTab) {
                        HomeTab.DEVICE -> DeviceScreen()
                        HomeTab.LIBRARY -> LibraryScreen()
                        HomeTab.CREATE -> CreateScreen()
                        HomeTab.SETTINGS -> SettingsScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun IsleBottomBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xF00A0B10))
            .drawBehind {
                // 顶部一道极细高光，把导航栏和内容区分开
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color(0x1FFFFFFF), Color.Transparent),
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(size.width, 0f),
                    strokeWidth = 1f,
                )
            }
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            HomeTab.entries.forEach { tab ->
                BottomBarItem(
                    tab = tab,
                    selected = tab == selected,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(tab: HomeTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tint by animateColorAsState(
        targetValue = if (selected) IsleColors.Cyan else IsleColors.TextMuted,
        label = "tabTint",
    )
    val glow by animateFloatAsState(if (selected) 1f else 0f, label = "tabGlow")

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (glow > 0.01f) {
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 26.dp)
                        .background(
                            Brush.radialGradient(
                                listOf(IsleColors.Cyan.copy(alpha = 0.20f * glow), Color.Transparent),
                            ),
                            RoundedCornerShape(13.dp),
                        ),
                )
            }
            GlyphIcon(glyph = tab.glyph, size = 21.dp, tint = tint)
        }
        Text(
            text = stringResource(tab.labelRes),
            color = tint,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
