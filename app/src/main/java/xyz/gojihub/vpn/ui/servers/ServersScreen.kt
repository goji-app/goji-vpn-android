package xyz.gojihub.vpn.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.SpaceGroteskFamily
import xyz.gojihub.vpn.ui.theme.godjiCard
import xyz.gojihub.vpn.ui.theme.godjiGlassFlat
import xyz.gojihub.vpn.ui.theme.godjiGlassPill

@Composable
fun ServersScreen(viewModel: ServersViewModel = hiltViewModel(), onServerPicked: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val refreshingSubscription by viewModel.refreshingSubscription.collectAsState()
    val refreshMessage by viewModel.refreshMessage.collectAsState()

    // Авто-пинг при каждом заходе на вкладку — composable пересоздаётся при переключении вкладок
    // (NavHost saveState/restoreState держит только ViewModel, не Compose-дерево), поэтому
    // LaunchedEffect(Unit) срабатывает ровно при каждом открытии "Серверов", а не один раз за
    // всё время жизни приложения. Раньше нужно было нажимать "⚡" вручную — выбор шёл вслепую.
    // PingRepository.pingAllInternal() сам не даст двум проверкам наложиться друг на друга.
    LaunchedEffect(Unit) { viewModel.pingAll() }

    Column(
        Modifier
            .fillMaxSize()
            .background(GodjiColors.Background)
            .padding(18.dp, 18.dp, 18.dp, 10.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text(Loc.s.serversTitle, color = GodjiColors.TextPrimary, fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text(Loc.s.serversCount(state.servers.size), color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .godjiGlassPill()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.refreshSubscription() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (refreshingSubscription) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GodjiColors.Terracotta)
                        else Icon(Icons.Filled.Refresh, contentDescription = Loc.s.serversRefresh, tint = GodjiColors.Terracotta, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(Loc.s.serversRefresh, color = GodjiColors.Terracotta, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .godjiGlassPill()
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.pingAll() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (state.checkingAll) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                        else Icon(Icons.Filled.Bolt, contentDescription = Loc.s.serversPing, tint = GodjiColors.TealDeep, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(Loc.s.serversPing, color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                }
            }
        }

        AnimatedContent(
            targetState = refreshMessage,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "refreshBanner"
        ) { msg ->
            if (msg != null) RefreshBanner(msg, onDismiss = viewModel::dismissRefreshMessage)
        }

        // Одна карточка со всеми узлами (строки разделены Hair), а не отдельная карточка на
        // каждый узел — как в эталоне.
        Column(Modifier.fillMaxWidth().godjiCard()) {
            state.servers.forEachIndexed { index, node ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = GodjiColors.Hair)
                val selected = node.id == state.selectedId
                val rowBg by animateColorAsState(if (selected) GodjiColors.SelBg else Color.Transparent, label = "rowBg")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.select(node.id); onServerPicked() }
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GodjiColors.Chip)
                            .then(if (selected) Modifier.border(1.5.dp, GodjiColors.Teal, RoundedCornerShape(12.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(node.flag, fontSize = 16.sp)
                    }
                    Text(node.name, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, modifier = Modifier.weight(1f))
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = GodjiColors.TealDeep, modifier = Modifier.size(18.dp))
                    }
                    // Избранное — в эталоне такого элемента нет (там нет данных под него), но
                    // это рабочая функция приложения, а не визуальная деталь — оставляю её,
                    // просто компактно, а не убираю по правилу PROMPT.md п.5.
                    Text(
                        if (node.isFavorite) "★" else "☆",
                        color = if (node.isFavorite) GodjiColors.Warning else GodjiColors.TextMuted,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.toggleFavorite(node.id) }
                            .padding(3.dp)
                    )
                    Box(
                        Modifier
                            .defaultMinSize(minWidth = 70.dp, minHeight = 30.dp)
                            .godjiGlassFlat(RoundedCornerShape(50))
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when {
                                state.checkingId == node.id -> "…"
                                node.pingMs == -2 -> Loc.s.serversCheck
                                node.pingMs == -1 -> Loc.s.serversUnavailable
                                else -> Loc.s.serversPingMs(node.pingMs)
                            },
                            color = if (state.checkingId == node.id) GodjiColors.TextSecondary else pingColor(node.pingMs),
                            fontWeight = FontWeight.Bold, fontSize = 11.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshBanner(message: RefreshMessage, onDismiss: () -> Unit) {
    val bg = if (message.isError) GodjiColors.JamBg else GodjiColors.TealTint
    val border = if (message.isError) GodjiColors.JamBorder else GodjiColors.TealTintBorder
    val color = if (message.isError) GodjiColors.JamText else GodjiColors.TealDeep
    Row(
        Modifier
            .fillMaxWidth()
            .godjiCard(tint = bg, borderColor = border)
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(if (message.isError) Icons.Filled.Warning else Icons.Filled.CheckCircle, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Text(if (message.isError) Loc.s.refreshFail else Loc.s.refreshOk, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f), lineHeight = 15.sp)
        Icon(Icons.Filled.Close, contentDescription = null, tint = color, modifier = Modifier.size(16.dp).clickable { onDismiss() })
    }
}

/** <40мс — TealDeep, <90мс — Warning, недоступен/иначе — Danger, не проверен — TextSecondary
 *  (см. handoff-1.0.77/README.md — раньше всегда возвращала TextPrimary независимо от пинга). */
private fun pingColor(pingMs: Int): Color = when {
    pingMs == -2 -> GodjiColors.TextSecondary
    pingMs == -1 -> GodjiColors.Danger
    pingMs < 40 -> GodjiColors.TealDeep
    pingMs < 90 -> GodjiColors.Warning
    else -> GodjiColors.Danger
}
