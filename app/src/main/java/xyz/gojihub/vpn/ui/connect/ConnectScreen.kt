package xyz.gojihub.vpn.ui.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.gojihub.vpn.R
import xyz.gojihub.vpn.i18n.AppLanguage
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.network.NetState
import xyz.gojihub.vpn.ui.globe.GojiGlobe
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.SpaceGroteskFamily
import xyz.gojihub.vpn.ui.theme.godjiCard
import xyz.gojihub.vpn.ui.theme.godjiGlassPill
import xyz.gojihub.vpn.ui.theme.godjiGlassStrong
import xyz.gojihub.vpn.ui.util.rememberPressScale

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = hiltViewModel(), onOpenPlans: () -> Unit = {}, onOpenServers: () -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.startTunnel(context)
    }

    // На Android 13+ мало объявить POST_NOTIFICATIONS в манифесте — без явного рантайм-запроса
    // канал молча остаётся importance=NONE, и уведомление foreground-сервиса (с реальным
    // сервером и скоростью) никогда не показывается, хотя сам сервис исправно работает.
    // Не блокируем подключение отказом — просто не покажется уведомление, это не критично.
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* игнорируем результат — уведомление необязательно для работы VPN */ }

    fun toggle() {
        if (state.connected || state.connecting) {
            viewModel.stopTunnel(context)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) vpnPermissionLauncher.launch(prepareIntent) else viewModel.startTunnel(context)
        }
    }

    // verticalScroll — без него на невысоких/мелких экранах нижние карточки (в первую
    // очередь "Трафик") просто обрезались краем экрана без какой-либо прокрутки: контент
    // не помещался по высоте, а Column сам по себе не скроллится. На больших экранах ничего
    // не меняет — скроллить нечего, если всё и так помещается.
    Column(
        Modifier
            .fillMaxSize()
            .background(GodjiColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp, 14.dp, 16.dp, 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // z-index:2 в эталоне — шапка лежит поверх верхнего края глобуса (он заходит под неё на 24dp).
        Row(Modifier.fillMaxWidth().zIndex(2f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .godjiGlassPill()
                    .padding(start = 4.dp, top = 4.dp, end = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(GodjiColors.Ink), contentAlignment = Alignment.Center) {
                    Image(painterResource(R.drawable.ic_notification), contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Text("Goji", color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            NetworkPill(state.netState)
        }

        GlobeCard(state)

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ConnectButton(state, onClick = { toggle() })
            Spacer(Modifier.height(14.dp))
            Text(
                headline(state),
                color = GodjiColors.TextPrimary,
                fontFamily = SpaceGroteskFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                letterSpacing = (-0.65).sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                // Пульс на точке статуса — раньше индикатор connected/connecting был
                // статичным кружком, "живости" в нём не читалось.
                val dotPulse = rememberInfiniteTransition(label = "dotPulse")
                val dotAlpha by dotPulse.animateFloat(
                    initialValue = 1f, targetValue = if (state.connected || state.connecting) 0.35f else 1f,
                    animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
                    label = "dotAlpha"
                )
                Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor(state).copy(alpha = dotAlpha)))
                Text(
                    subline(state),
                    color = GodjiColors.TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (state.connected) {
                    Text("· ${state.connectedTimeLabel}", color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        StatsCard(state)

        state.error?.let {
            Text(it, color = GodjiColors.Danger, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        AnimatedContent(
            targetState = state.banner to state.bannerKind,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
            label = "banner"
        ) { (banner, kind) ->
            if (banner != null) BannerCard(banner, kind, onDismiss = viewModel::dismissBanner)
        }

        NodeAndAutoSwitchCard(state, onOpenServers = onOpenServers)

        TrafficCard(state, onClick = onOpenPlans)
    }
}

private fun countryPhrase(geo: xyz.gojihub.vpn.geo.CountryGeo?) = when (Loc.lang) {
    AppLanguage.RU -> geo?.ruPrep ?: Loc.s.connectFallbackPlace
    else -> geo?.displayName(Loc.lang) ?: Loc.s.connectFallbackPlace
}

private fun headline(s: ConnectUiState) = when {
    s.connected -> Loc.s.connectHeadlineProtected(countryPhrase(s.currentGeo))
    s.connecting -> Loc.s.connectHeadlineConnecting
    else -> Loc.s.connectHeadlineOff
}

private fun subline(s: ConnectUiState): String {
    val nodeName = s.currentNodeName.ifBlank { Loc.s.defaultNodeName }
    return when {
        s.connected -> Loc.s.connectSublineConnected(nodeName)
        s.connecting -> Loc.s.connectSublineConnecting(nodeName)
        else -> Loc.s.connectSublineOff
    }
}

private fun dotColor(s: ConnectUiState) = when {
    s.connected -> GodjiColors.Teal
    s.connecting -> GodjiColors.Warning
    else -> GodjiColors.ButtonBorder
}

@Composable
private fun GlobeCard(state: ConnectUiState) {
    // Эталон: height:430px; margin:-24px -60px -20px — блок 430dp, но в раскладке занимает
    // 430−24−20 = 386dp (заходит на 24dp под шапку и на 20dp под кнопку), по ширине — +60dp с
    // каждой стороны. Прозрачный холст: вокруг сферы виден общий фон.
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val full = 430.dp.roundToPx()
                val top = 24.dp.roundToPx()
                val bottom = 20.dp.roundToPx()
                val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                layout(placeable.width, full - top - bottom) { placeable.place(0, -top) }
            }
    ) {
        GojiGlobe(
            status = state.globeStatus,
            node = state.globeNode,
            label = "",
            modifier = Modifier
                .align(Alignment.Center)
                .requiredWidth(maxWidth + 120.dp)
                .fillMaxHeight()
        )

        if (state.connected) {
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 30.dp, end = 14.dp)
                    .widthIn(max = 190.dp)
                    .godjiGlassPill(RoundedCornerShape(18.dp))
                    .padding(horizontal = 9.dp, vertical = 13.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(state.greetingHi, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text(state.greetingSub, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
            }
        }
    }
}

/** Круглая кнопка подключения (80dp) по центру под глобусом — вместо прежней текстовой
 *  кнопки рядом с заголовком. Выключено — стеклянная (godjiGlassStrong), включено — залита
 *  акцентным градиентом с двумя расходящимися кольцами, подключение — вращающаяся
 *  двухцветная дуга-спиннер вокруг кнопки. */
@Composable
private fun ConnectButton(state: ConnectUiState, onClick: () -> Unit) {
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        if (state.connected) {
            repeat(2) { i ->
                val ringTransition = rememberInfiniteTransition(label = "ring$i")
                val progress by ringTransition.animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(2400, delayMillis = i * 1200, easing = LinearEasing)
                    ),
                    label = "ringProgress$i"
                )
                Box(
                    Modifier
                        .size(80.dp)
                        .scale(1f + progress * 0.55f)
                        .border(2.dp, GodjiColors.Teal.copy(alpha = 0.5f * (1f - progress)), CircleShape)
                )
            }
        }

        if (state.connecting) {
            val spinTransition = rememberInfiniteTransition(label = "ctaSpin")
            val spinAngle by spinTransition.animateFloat(
                initialValue = 0f, targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                label = "ctaSpinAngle"
            )
            Canvas(Modifier.size(90.dp).rotate(spinAngle)) {
                val stroke = 2.5.dp.toPx()
                val inset = 5.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(listOf(GodjiColors.Teal, GodjiColors.Terracotta, GodjiColors.Teal)),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                    topLeft = Offset(-inset, -inset),
                    size = Size(size.width + inset * 2, size.height + inset * 2)
                )
            }
        }

        val (pressInteraction, pressScale) = rememberPressScale()
        Box(
            Modifier
                .size(80.dp)
                .scale(pressScale.value)
                .then(
                    if (state.connected)
                        Modifier.background(
                            Brush.verticalGradient(listOf(GodjiColors.AccentGradTop, GodjiColors.AccentGradMid, GodjiColors.AccentGradBottom)),
                            CircleShape
                        )
                    else Modifier.godjiGlassStrong(CircleShape)
                )
                .clip(CircleShape)
                .clickable(interactionSource = pressInteraction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = if (state.connected) Color.White else GodjiColors.TextPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun NetworkPill(net: NetState) {
    val (label, color) = when (net) {
        NetState.WIFI -> Loc.s.netWifi to GodjiColors.TealDeep
        NetState.CELLULAR -> Loc.s.netCellular to GodjiColors.TextSecondary
        NetState.JAMMED -> Loc.s.netJammed to GodjiColors.JamText
    }
    Row(
        Modifier
            .height(34.dp)
            .godjiGlassPill()
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun StatsCard(state: ConnectUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(Loc.s.statDownload, state.downSpeedMbps, GodjiColors.Teal, down = true, Modifier.weight(1f))
        Box(Modifier.width(1.dp).height(40.dp).background(GodjiColors.Hair))
        StatColumn(Loc.s.statUpload, state.upSpeedMbps, GodjiColors.Terracotta, down = false, Modifier.weight(1f))
    }
}

@Composable
private fun StatColumn(label: String, speedMbps: Double, accent: Color, down: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                if (down) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = null, tint = accent, modifier = Modifier.size(12.dp)
            )
            Text(label, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
        }
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(speedMbps), color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(" ${Loc.s.speedUnit}", color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BannerCard(text: String, kind: BannerKind, onDismiss: () -> Unit) {
    val bg = when (kind) { BannerKind.WARNING -> GodjiColors.JamBg; BannerKind.SUCCESS -> GodjiColors.TealTint; BannerKind.INFO -> GodjiColors.Chip }
    val border = when (kind) { BannerKind.WARNING -> GodjiColors.JamBorder; BannerKind.SUCCESS -> GodjiColors.TealTintBorder; BannerKind.INFO -> GodjiColors.CardBorderStrong }
    val color = when (kind) { BannerKind.WARNING -> GodjiColors.JamText; BannerKind.SUCCESS -> GodjiColors.TealDeep; BannerKind.INFO -> GodjiColors.TextPrimary }
    val icon = when (kind) { BannerKind.WARNING -> Icons.Filled.Warning; BannerKind.SUCCESS -> Icons.Filled.CheckCircle; BannerKind.INFO -> Icons.Filled.Info }
    Row(
        Modifier.fillMaxWidth().godjiCard(tint = bg, borderColor = border).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f), lineHeight = 15.sp)
        Icon(Icons.Filled.Close, contentDescription = null, tint = color, modifier = Modifier.size(16.dp).clickable { onDismiss() })
    }
}

/** Узел + "Держит тебя в сети" — одна карточка. Строка узла — кнопка на всю ширину карточки,
 *  открывает "Серверы" для быстрого выбора другого узла (goServers в эталоне). */
@Composable
private fun NodeAndAutoSwitchCard(state: ConnectUiState, onOpenServers: () -> Unit) {
    val cardShape = RoundedCornerShape(26.dp)
    Column(Modifier.fillMaxWidth().godjiCard(cardShape).clip(cardShape)) {
        val nodeInteraction = remember { MutableInteractionSource() }
        val pressed by nodeInteraction.collectIsPressedAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (pressed) Color(0x147F7F7F) else Color.Transparent)
                .clickable(interactionSource = nodeInteraction, indication = null, onClick = onOpenServers)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(GodjiColors.Chip)
                    .border(1.dp, GodjiColors.CardBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(state.currentFlag, fontSize = 19.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.currentNodeName.ifBlank { Loc.s.defaultNodeName }, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                val meta = state.currentGeo?.displayCityCountry(Loc.lang).orEmpty()
                if (meta.isNotBlank()) {
                    Text(meta, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = GodjiColors.TextSecondary, modifier = Modifier.size(16.dp))
        }
        HorizontalDivider(thickness = 1.dp, color = GodjiColors.Hair)
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 13.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = GodjiColors.TealDeep, modifier = Modifier.size(15.dp))
                Text(Loc.s.autoSwitchTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                xyz.gojihub.vpn.ui.theme.AutoBadge()
            }
            Text(Loc.s.autoSwitchDesc, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
            HorizontalDivider(Modifier.padding(top = 6.dp), thickness = 1.dp, color = GodjiColors.Hair)
            Row(
                Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(netColor(state.netState)))
                Text(watchLine(state), color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            }
        }
    }
}

private fun netColor(net: NetState) = when (net) {
    NetState.WIFI -> GodjiColors.TealDeep
    NetState.CELLULAR -> GodjiColors.TextSecondary
    NetState.JAMMED -> GodjiColors.JamText
}

private fun watchLine(s: ConnectUiState) = when {
    s.netState == NetState.JAMMED -> Loc.s.autoSwitchJammed
    s.netState == NetState.WIFI -> Loc.s.autoSwitchWifi
    else -> Loc.s.autoSwitchCellular
}

@Composable
private fun TrafficCard(state: ConnectUiState, onClick: () -> Unit) {
    val unlimited = state.isUnlimited || state.quotaGb <= 0.0
    val pct = if (unlimited) 0f else (state.usedGb / state.quotaGb).coerceIn(0.0, 1.0).toFloat()
    Column(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Loc.s.trafficLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(
                if (unlimited) Loc.s.trafficUnlimited("%.1f".format(state.usedGb)) else Loc.s.trafficLimited("%.1f".format(state.usedGb), state.quotaGb.toInt()),
                color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp
            )
        }
        if (!unlimited) {
            AnimatedTrafficBar(pct = pct)
        }
        Text(Loc.s.trafficExpiry(state.expiryLabel, state.daysLeft), color = GodjiColors.TextSecondary, fontSize = 11.sp)
    }
}

/** Плавно анимирует изменение доли использованного трафика (вместо мгновенного скачка) и
 *  добавляет бегущий блик по заполненной части — акцентный градиент вместо плоского тона. */
@Composable
private fun AnimatedTrafficBar(pct: Float, modifier: Modifier = Modifier) {
    val animatedPct by animateFloatAsState(
        targetValue = pct.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "trafficPct"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "trafficShimmer")
    val shimmerT by infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "shimmerT"
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(GodjiColors.TrackBg)
    ) {
        val fillWidth = maxWidth * animatedPct
        val shimmerWidth = maxWidth * 0.3f
        if (fillWidth > 0.dp) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(fillWidth)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Brush.horizontalGradient(listOf(GodjiColors.AccentGradTop, GodjiColors.AccentGradMid, GodjiColors.AccentGradBottom)))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(shimmerWidth)
                        .offset(x = fillWidth * shimmerT)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}
