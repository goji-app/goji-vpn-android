package xyz.gojihub.vpn.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.settings.PingMethod
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.SpaceGroteskFamily
import xyz.gojihub.vpn.ui.util.BackButton
import xyz.gojihub.vpn.ui.util.rememberPressScale

/** Вынесено из основного экрана Настроек в отдельное подменю — способ пинга и URL теста
 *  используются редко и загромождали общий список переключателей. */
@Composable
fun PingSettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .background(GodjiColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp, 18.dp, 18.dp, 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onClick = onBack)
            Text(Loc.s.pingSettingsTitle, color = GodjiColors.TextPrimary, fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
        }

        SettingsSection(Loc.s.settingsServerCheck) {
            Text(Loc.s.pingMethodLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PingMethodRow(Loc.s.pingProxyGet, PingMethod.PROXY_GET, state.pingMethod, viewModel::setPingMethod)
                PingMethodRow(Loc.s.pingProxyHead, PingMethod.PROXY_HEAD, state.pingMethod, viewModel::setPingMethod)
                PingMethodRow(Loc.s.pingTcp, PingMethod.TCP, state.pingMethod, viewModel::setPingMethod)
                PingMethodRow(Loc.s.pingIcmp, PingMethod.ICMP, state.pingMethod, viewModel::setPingMethod)
            }
            Spacer(Modifier.height(14.dp))
            Text(Loc.s.pingUrlLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = state.pingTestUrl,
                onValueChange = viewModel::setPingTestUrl,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.5.sp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        SettingsSection(Loc.s.leakSectionTitle) {
            Text(Loc.s.leakCaption, color = GodjiColors.TextSecondary, fontSize = 10.5.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(10.dp))
            if (state.leakChecked) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Loc.s.leakPublicIpLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        if (state.leakError) "—" else listOfNotNull(state.leakPublicIp, state.leakCountry).joinToString(" · ").ifBlank { "—" },
                        color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(Loc.s.leakDnsLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text(
                        state.leakDnsServers.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: Loc.s.leakDnsEmpty,
                        color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        modifier = Modifier.weight(1f, fill = false).padding(start = 12.dp)
                    )
                }
                if (state.leakError) {
                    Spacer(Modifier.height(6.dp))
                    Text(Loc.s.leakErrorText, color = GodjiColors.Danger, fontSize = 10.5.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            val (checkInteraction, checkScale) = rememberPressScale()
            Box(
                Modifier
                    .fillMaxWidth()
                    .scale(checkScale.value)
                    .background(GodjiColors.Chip, androidx.compose.foundation.shape.RoundedCornerShape(50))
                    .clickable(interactionSource = checkInteraction, indication = androidx.compose.foundation.LocalIndication.current, enabled = !state.leakChecking) { viewModel.checkLeak() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.leakChecking) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                        Text(Loc.s.leakChecking, color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                    }
                } else {
                    Text(Loc.s.leakCheckButton, color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}
