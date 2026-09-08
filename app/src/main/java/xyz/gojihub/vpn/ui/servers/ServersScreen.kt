package xyz.gojihub.vpn.ui.servers

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.InstrumentSerifFamily
import xyz.gojihub.vpn.ui.util.rememberPressScale

@Composable
fun ServersScreen(viewModel: ServersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val refreshingSubscription by viewModel.refreshingSubscription.collectAsState()
    val refreshMessage by viewModel.refreshMessage.collectAsState()

    Column(Modifier.fillMaxSize().background(GodjiColors.Background).padding(18.dp, 18.dp, 18.dp, 10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column {
                Text(Loc.s.serversTitle, color = GodjiColors.TextPrimary, fontFamily = InstrumentSerifFamily, fontSize = 26.sp)
                Text(Loc.s.serversCount(state.servers.size), color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
            }
            // Компактные иконки без рамок-квадратов — как в тулбаре Happ, но в нашей палитре.
            // Подпись под каждой — сами по себе значки "⟳" и "⚡" без пояснения было легко
            // спутать (обновление подписки vs. проверка пинга всех серверов).
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.refreshSubscription() }) {
                        if (refreshingSubscription) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GodjiColors.Terracotta)
                        else Icon(Icons.Filled.Refresh, contentDescription = Loc.s.serversRefresh, tint = GodjiColors.Terracotta)
                    }
                    Text(Loc.s.serversRefresh, color = GodjiColors.Terracotta, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { viewModel.pingAll() }) {
                        if (state.checkingAll) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                        else Text("⚡", fontSize = 19.sp)
                    }
                    Text(Loc.s.serversPing, color = GodjiColors.TealDeep, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp)
                }
            }
        }

        Spacer(Modifier.height(13.dp))

        AnimatedContent(
            targetState = refreshMessage,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "refreshBanner"
        ) { msg ->
            if (msg != null) {
                Column {
                    RefreshBanner(msg, onDismiss = viewModel::dismissRefreshMessage)
                    Spacer(Modifier.height(13.dp))
                }
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.servers, key = { it.id }) { node ->
                val selected = node.id == state.selectedId
                val (rowInteraction, rowScale) = rememberPressScale()
                val rowBg by animateColorAsState(if (selected) GodjiColors.TealTint else GodjiColors.Surface, label = "rowBg")
                val rowBorder by animateColorAsState(if (selected) GodjiColors.Teal else GodjiColors.CardBorder, label = "rowBorder")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(rowScale.value)
                        .background(rowBg, RoundedCornerShape(12.dp))
                        .border(1.5.dp, rowBorder, RoundedCornerShape(12.dp))
                        .clickable(interactionSource = rowInteraction, indication = LocalIndication.current) { viewModel.select(node.id) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(Modifier.size(23.dp).background(GodjiColors.Chip, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text(node.flag, fontSize = 12.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(node.name, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                    TextButton(
                        onClick = { viewModel.pingOne(node.id) },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            when {
                                state.checkingId == node.id -> "…"
                                node.pingMs == -2 -> Loc.s.serversCheck
                                node.pingMs == -1 -> Loc.s.serversUnavailable
                                else -> Loc.s.serversPingMs(node.pingMs)
                            },
                            color = pingColor(node.pingMs), fontWeight = FontWeight.Bold, fontSize = 10.sp
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
            .background(bg, RoundedCornerShape(16.dp))
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(if (message.isError) "⚠️" else "✅", fontSize = 15.sp)
        Text(if (message.isError) Loc.s.refreshFail else Loc.s.refreshOk, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f), lineHeight = 15.sp)
        Text("×", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { onDismiss() })
    }
}

private fun pingColor(pingMs: Int) = GodjiColors.TextPrimary
