package xyz.gojihub.vpn.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.gojihub.vpn.i18n.AppLanguage
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.settings.PingMethod
import xyz.gojihub.vpn.ui.theme.FontSizePreset
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.InstrumentSerifFamily
import xyz.gojihub.vpn.ui.util.LogViewerDialog
import xyz.gojihub.vpn.ui.util.RichContent
import xyz.gojihub.vpn.ui.util.rememberPressScale
import xyz.gojihub.vpn.util.LogCategory

@Composable
fun SettingsScreen(
    onLoggedOut: () -> Unit,
    onOpenPingSettings: () -> Unit,
    onOpenLogLevel: () -> Unit,
    onOpenAppTunneling: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var logDialog by remember { mutableStateOf<Pair<LogCategory, String>?>(null) }
    logDialog?.let { (category, title) ->
        LogViewerDialog(category = category, title = title, onDismiss = { logDialog = null })
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(GodjiColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp, 18.dp, 18.dp, 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(Loc.s.settingsTitle, color = GodjiColors.TextPrimary, fontFamily = InstrumentSerifFamily, fontSize = 26.sp)

        SettingsSection(Loc.s.settingsConnection) {
            SettingsToggleRow(
                title = Loc.s.settingsAutoConnectWifi,
                subtitle = Loc.s.settingsAutoConnectWifiDesc,
                checked = state.autoConnectOnWifi,
                onCheckedChange = viewModel::setAutoConnectOnWifi
            )
            Spacer(Modifier.height(10.dp))
            SettingsToggleRow(
                title = Loc.s.settingsKillSwitch,
                subtitle = Loc.s.settingsKillSwitchDesc,
                checked = state.killSwitch,
                onCheckedChange = viewModel::setKillSwitch
            )
        }

        SettingsSection(Loc.s.settingsNotifications) {
            SettingsToggleRow(
                title = Loc.s.settingsPinNotif,
                subtitle = Loc.s.settingsPinNotifDesc,
                checked = state.pinNotification,
                onCheckedChange = viewModel::setPinNotification
            )
        }

        SettingsSection(Loc.s.settingsAppearance) {
            SettingsToggleRow(
                title = Loc.s.settingsDarkTheme,
                subtitle = null,
                checked = state.darkTheme,
                onCheckedChange = viewModel::setDarkTheme
            )
            Spacer(Modifier.height(14.dp))
            Text(Loc.s.settingsLanguage, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                AppLanguage.entries.forEach { lang ->
                    LanguageChip(lang, selected = state.language == lang, onSelect = { viewModel.setLanguage(lang) })
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(Loc.s.settingsFontSize, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                FontSizePreset.entries.forEach { preset ->
                    FontSizeChip(preset, selected = state.fontSize == preset, onSelect = { viewModel.setFontSize(preset) })
                }
            }
        }

        SettingsSection(Loc.s.settingsServerCheck) {
            SettingsLinkRow(title = Loc.s.settingsPingLink, subtitle = Loc.s.settingsPingLinkDesc, onClick = onOpenPingSettings)
        }

        SettingsSection(Loc.s.settingsAppTunneling) {
            SettingsLinkRow(title = Loc.s.settingsAppTunneling, subtitle = Loc.s.settingsAppTunnelingDesc, onClick = onOpenAppTunneling)
        }

        SettingsSection(Loc.s.settingsAlwaysOnTitle) {
            Text(Loc.s.settingsAlwaysOnDesc, color = GodjiColors.TextSecondary, fontSize = 11.5.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(10.dp))
            SettingsLinkRow(
                title = Loc.s.settingsAlwaysOnOpen,
                subtitle = null,
                onClick = {
                    // Программно включить Always-on VPN/"Блокировать соединения без VPN" нельзя —
                    // это намеренное ограничение Android (иначе любое приложение само тихо
                    // заблокировало бы весь трафик устройства без ведома пользователя). Можно
                    // только открыть системный экран, где пользователь включает это сам.
                    runCatching { context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) }
                }
            )
        }

        SettingsSection(Loc.s.settingsAbout) {
            AboutRow(Loc.s.settingsAppVersion, state.appVersion)
            AboutRow(Loc.s.settingsXrayVersion, state.xrayVersion)
            AboutRow(Loc.s.settingsHwid, state.hwid)
            AboutRow(Loc.s.settingsDeviceInfo, state.deviceInfo)
        }

        SettingsSection(Loc.s.settingsUpdatesTitle) {
            UpdateSectionContent(state, viewModel)
        }

        SettingsSection(Loc.s.settingsLogs) {
            SettingsLinkRow(title = Loc.s.settingsLogLevel, subtitle = Loc.s.settingsLogLevelDesc, onClick = onOpenLogLevel)
            Spacer(Modifier.height(10.dp))
            LogRow(Loc.s.logMain) { logDialog = LogCategory.MAIN to Loc.s.logMain }
            LogRow(Loc.s.logCore) { logDialog = LogCategory.CORE to Loc.s.logCore }
            LogRow(Loc.s.logSubscription) { logDialog = LogCategory.SUBSCRIPTION to Loc.s.logSubscription }
            LogRow(Loc.s.logService) { logDialog = LogCategory.SERVICE to Loc.s.logService }
            LogRow(Loc.s.logPush) { logDialog = LogCategory.PUSH to Loc.s.logPush }
        }

        SettingsSection(Loc.s.settingsAccount) {
            val (outInteraction, outScale) = rememberPressScale()
            TextButton(
                onClick = { viewModel.logout(); onLoggedOut() },
                interactionSource = outInteraction,
                modifier = Modifier.fillMaxWidth().scale(outScale.value)
            ) {
                Text(Loc.s.settingsLogout, color = GodjiColors.Danger, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(GodjiColors.Surface, RoundedCornerShape(18.dp))
            .border(1.5.dp, GodjiColors.CardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GodjiColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            subtitle?.let { Text(it, color = GodjiColors.TextSecondary, fontSize = 10.5.sp, lineHeight = 14.sp) }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GodjiColors.Surface,
                checkedTrackColor = GodjiColors.Teal,
                uncheckedThumbColor = GodjiColors.Surface,
                uncheckedTrackColor = GodjiColors.ButtonBorder
            )
        )
    }
}

@Composable
private fun SettingsLinkRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = GodjiColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            subtitle?.let { Text(it, color = GodjiColors.TextSecondary, fontSize = 10.5.sp, lineHeight = 14.sp) }
        }
        Text("→", color = GodjiColors.TextSecondary, fontSize = 16.sp)
    }
}

/** GitHub Releases (см. AppUpdateChecker) — либо ещё не проверяли, либо идёт проверка,
 *  либо версия последняя, либо найдено обновление (changelog — тело релиза, тот же
 *  Markdown/HTML, что и в новостях подписки, рендерится тем же RichContent), либо идёт
 *  загрузка. Саму установку по завершении загрузки запускает системный broadcast-приёмник
 *  (см. UpdateDownloadReceiver) — не завязано на то, открыт ли ещё этот экран. */
@Composable
private fun UpdateSectionContent(state: SettingsUiState, viewModel: SettingsViewModel) {
    when {
        state.updateDownloading -> {
            Text(Loc.s.updateDownloading, color = GodjiColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.updateDownloadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = GodjiColors.TealDeep,
                trackColor = GodjiColors.Chip
            )
            Spacer(Modifier.height(4.dp))
            Text("${state.updateDownloadProgress}%", color = GodjiColors.TextSecondary, fontSize = 10.5.sp)
        }
        state.updateAvailable != null -> {
            Text(
                Loc.s.updateAvailableText(state.updateAvailable.version),
                color = GodjiColors.TealDeep,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            if (state.updateAvailable.changelog.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichContent(raw = state.updateAvailable.changelog, collapsedBlocks = 5, readMoreLabel = Loc.s.plansNewsReadMore)
            }
            Spacer(Modifier.height(12.dp))
            val (interaction, scale) = rememberPressScale()
            Box(
                Modifier
                    .fillMaxWidth()
                    .scale(scale.value)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GodjiColors.Ink)
                    .clickable(interactionSource = interaction, indication = androidx.compose.foundation.LocalIndication.current, onClick = viewModel::downloadUpdate)
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(Loc.s.updateDownloadInstall, color = GodjiColors.Surface, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
        state.updateChecking -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                Text(Loc.s.updateChecking, color = GodjiColors.TextSecondary, fontSize = 12.5.sp)
            }
        }
        state.updateChecked -> {
            Text(Loc.s.updateUpToDate, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 12.5.sp)
            Spacer(Modifier.height(10.dp))
            SettingsLinkRow(title = Loc.s.settingsCheckUpdates, subtitle = null, onClick = viewModel::checkForUpdate)
        }
        else -> {
            SettingsLinkRow(title = Loc.s.settingsCheckUpdates, subtitle = null, onClick = viewModel::checkForUpdate)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    val clipboard = LocalClipboardManager.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { clipboard.setText(AnnotatedString(value)) }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = GodjiColors.TextSecondary, fontSize = 12.5.sp)
        Text(
            value,
            color = GodjiColors.TextPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 12.5.sp,
            modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LogRow(title: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        Text("→", color = GodjiColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun LanguageChip(lang: AppLanguage, selected: Boolean, onSelect: () -> Unit) {
    val bg = if (selected) GodjiColors.Ink else GodjiColors.Chip
    val fg = if (selected) GodjiColors.Surface else GodjiColors.TextPrimary
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(lang.displayName, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun FontSizeChip(preset: FontSizePreset, selected: Boolean, onSelect: () -> Unit) {
    val bg = if (selected) GodjiColors.Ink else GodjiColors.Chip
    val fg = if (selected) GodjiColors.Surface else GodjiColors.TextPrimary
    val label = when (preset) {
        FontSizePreset.SMALL -> Loc.s.fontSizeSmall
        FontSizePreset.NORMAL -> Loc.s.fontSizeNormal
        FontSizePreset.LARGE -> Loc.s.fontSizeLarge
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
fun PingMethodRow(label: String, method: PingMethod, selected: PingMethod, onSelect: (PingMethod) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onSelect(method) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(
            selected = selected == method,
            onClick = { onSelect(method) },
            colors = RadioButtonDefaults.colors(selectedColor = GodjiColors.TealDeep, unselectedColor = GodjiColors.ButtonBorder)
        )
        Text(label, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}
