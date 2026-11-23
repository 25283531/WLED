package ca.cgagnier.wlednativeandroid.ui.settingsScreen

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ca.cgagnier.wlednativeandroid.R
import ca.cgagnier.wlednativeandroid.repository.ThemeSettings
import ca.cgagnier.wlednativeandroid.util.AppLanguage
import ca.cgagnier.wlednativeandroid.util.AppLanguageManager
import org.koin.androidx.compose.koinViewModel

@Composable
fun Settings(navigateUp: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val currentLanguage = remember(context) { AppLanguageManager.current(context) }
    val isFollowingSystem = remember(context) { AppLanguageManager.isFollowingSystem(context) }
    val actions = remember(viewModel, activity) {
        SettingsActions(
            setShowHiddenDevices = { viewModel.setShowHiddenDevices(it) },
            setAutoDiscover = { viewModel.setAutoDiscover(it) },
            setShowOfflineDevicesLast = { viewModel.setShowOfflineDevicesLast(it) },
            setTheme = { viewModel.setTheme(it) },
            setLanguage = { lang -> activity?.let { AppLanguageManager.set(it, lang) } },
            followSystem = { activity?.let { AppLanguageManager.followSystem(it) } },
        )
    }
    SettingsContent(
        settingsState = settingsState.copy(
            language = currentLanguage,
            isFollowingSystem = isFollowingSystem,
        ),
        actions = actions,
        navigateUp = navigateUp,
    )
}

/** 设置项回调，保持 [SettingsContent] 无状态，便于预览与截图测试。 */
@Immutable
data class SettingsActions(
    val setShowHiddenDevices: (Boolean) -> Unit = {},
    val setAutoDiscover: (Boolean) -> Unit = {},
    val setShowOfflineDevicesLast: (Boolean) -> Unit = {},
    val setTheme: (ThemeSettings) -> Unit = {},
    val setLanguage: (AppLanguage) -> Unit = {},
    val followSystem: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    settingsState: SettingsState,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier,
    actions: SettingsActions = SettingsActions(),
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { DeviceSettingsAppBar(navigateUp = navigateUp, scrollBehavior = scrollBehavior) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OneOrTwoColumnLayout(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .widthIn(0.dp, 1100.dp),
                firstColumn = {
                    GeneralOptions(
                        currentLanguage = settingsState.language,
                        isFollowingSystem = settingsState.isFollowingSystem,
                        currentTheme = settingsState.theme,
                        setLanguage = actions.setLanguage,
                        followSystem = actions.followSystem,
                        setTheme = actions.setTheme,
                    )
                },
                secondColumn = {
                    ListingOptions(
                        showHiddenDevices = settingsState.showHiddenDevices,
                        isAutoDiscoveryEnabled = settingsState.isAutoDiscoveryEnabled,
                        showOfflineDevicesLast = settingsState.showOfflineLast,
                        setShowHiddenDevices = actions.setShowHiddenDevices,
                        setAutoDiscover = actions.setAutoDiscover,
                        setShowOfflineDevicesLast = actions.setShowOfflineDevicesLast,
                    )
                    AboutOptions()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSettingsAppBar(
    modifier: Modifier = Modifier,
    navigateUp: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
) {
    CenterAlignedTopAppBar(
        modifier = modifier,
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(onClick = navigateUp) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.description_back_button),
                )
            }
        },
        scrollBehavior = scrollBehavior,
    )
}

/** 分组卡片：标题 + 圆角卡片容器。 */
@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        ElevatedCard(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, description: String? = null) {
    ListItem(
        modifier = Modifier.clickable(role = Role.Switch) { onCheckedChange(!checked) },
        headlineContent = { Text(label) },
        supportingContent = description?.let { { Text(it) } },
        trailingContent = { Switch(checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
fun RadioRow(label: String, checked: Boolean, onSelected: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = checked, role = Role.RadioButton, onClick = onSelected),
        leadingContent = { RadioButton(selected = checked, onClick = null) },
        headlineContent = { Text(label) },
        trailingContent = if (checked) {
            { Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
    )
}

@Composable
fun GeneralOptions(
    currentLanguage: AppLanguage,
    isFollowingSystem: Boolean,
    currentTheme: ThemeSettings,
    setLanguage: (AppLanguage) -> Unit,
    followSystem: () -> Unit,
    setTheme: (ThemeSettings) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_general)) {
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.outline_translate_24), contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.language)) },
            supportingContent = { Text(stringResource(R.string.settings_language_current, currentLanguage.nativeName)) },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
        SwitchRow(
            label = stringResource(R.string.settings_language_follow_system),
            checked = isFollowingSystem,
            onCheckedChange = { follow ->
                if (follow) followSystem() else setLanguage(currentLanguage)
            },
            description = stringResource(R.string.settings_language_follow_system_description),
        )
        if (!isFollowingSystem) {
            AppLanguage.entries.forEach { lang ->
                RadioRow(label = lang.nativeName, checked = lang == currentLanguage) {
                    if (lang != currentLanguage) setLanguage(lang)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        ThemeOptions(currentTheme = currentTheme, setTheme = setTheme)
    }
}

@Composable
fun ListingOptions(
    showHiddenDevices: Boolean,
    isAutoDiscoveryEnabled: Boolean,
    showOfflineDevicesLast: Boolean,
    setShowHiddenDevices: (Boolean) -> Unit,
    setAutoDiscover: (Boolean) -> Unit,
    setShowOfflineDevicesLast: (Boolean) -> Unit,
) {
    SettingsSection(stringResource(R.string.settings_device_list)) {
        SwitchRow(
            label = stringResource(R.string.automatically_discover_new_devices),
            checked = isAutoDiscoveryEnabled,
            onCheckedChange = setAutoDiscover,
            description = stringResource(R.string.automatically_discover_new_devices_description),
        )
        SwitchRow(
            label = stringResource(R.string.show_hidden_devices),
            checked = showHiddenDevices,
            onCheckedChange = setShowHiddenDevices,
        )
        SwitchRow(
            label = stringResource(R.string.show_offline_devices_last),
            checked = showOfflineDevicesLast,
            onCheckedChange = setShowOfflineDevicesLast,
        )
    }
}

/** 主题：分段按钮（浅色 / 深色 / 跟随系统）。 */
@Composable
fun ThemeOptions(currentTheme: ThemeSettings, setTheme: (ThemeSettings) -> Unit = {}) {
    val themes = listOf(
        ThemeSettings.Light to stringResource(R.string.light),
        ThemeSettings.Dark to stringResource(R.string.dark),
        ThemeSettings.Auto to stringResource(R.string.system_default),
    )
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(stringResource(R.string.theme), style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            themes.forEachIndexed { index, (theme, label) ->
                SegmentedButton(
                    selected = currentTheme == theme,
                    onClick = { setTheme(theme) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
fun AboutOptions() {
    val uriHandler = LocalUriHandler.current
    SettingsSection(stringResource(R.string.settings_about)) {
        ListItem(
            modifier = Modifier.clickable { uriHandler.openUri("https://kno.wled.ge/") },
            leadingContent = { Icon(Icons.Outlined.Info, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.wled_documentation)) },
            supportingContent = { Text("kno.wled.ge") },
            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}

@Composable
fun OneOrTwoColumnLayout(
    modifier: Modifier = Modifier,
    firstColumn: @Composable () -> Unit,
    secondColumn: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        if (maxWidth < 800.dp) {
            Column {
                firstColumn()
                secondColumn()
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) { firstColumn() }
                Column(modifier = Modifier.weight(1f)) { secondColumn() }
            }
        }
    }
}
