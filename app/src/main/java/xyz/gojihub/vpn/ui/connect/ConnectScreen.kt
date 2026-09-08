package xyz.gojihub.vpn.ui.connect

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border as composeBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
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
import xyz.gojihub.vpn.ui.util.rememberPressScale
import xyz.gojihub.vpn.ui.theme.InstrumentSerifFamily

@Composable
fun ConnectScreen(viewModel: ConnectViewModel = hiltViewModel(), onOpenPlans: () -> Unit = {}) {
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
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Image(
                    painter = painterResource(R.drawable.ic_notification),
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
                Text("Goji", color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
            NetworkPill(state.netState)
        }

        GlobeCard(state)

        // weight(1f) на заголовке — раньше при масштабировании интерфейса под широкий экран
        // (см. GodjiVpnTheme) длинный заголовок вроде "Ты в Германии" вытеснял кнопку
        // "Отключить"/"Включить": оба элемента без weight делят место по очереди слева направо,
        // и кнопке доставалось меньше места, чем нужно на одну строку — её текст переносился на
        // две строки и вылезал за пределы кнопки. Теперь кнопка первой получает своё натуральное
        // место, а заголовок гибко занимает остаток (перенос/многоточие — на его стороне, не на
        // стороне короткой и всегда однострочной кнопки).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    headline(state),
                    color = GodjiColors.TextPrimary,
                    fontFamily = InstrumentSerifFamily,
                    fontSize = 26.sp,
                    lineHeight = 27.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    // Пульс на точке статуса — раньше индикатор connected/connecting был
                    // статичным кружком, "живости" в нём не читалось.
                    val dotPulse = rememberInfiniteTransition(label = "dotPulse")
                    val dotAlpha by dotPulse.animateFloat(
                        initialValue = 1f, targetValue = if (state.connected || state.connecting) 0.35f else 1f,
                        animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
                        label = "dotAlpha"
                    )
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dotColor(state).copy(alpha = dotAlpha)))
                    // weight здесь — то же самое сжатие места кнопкой, только уровнем глубже:
                    // если строке не хватает ширины, ужимается (многоточием) описание сервера,
                    // а не таймер после него — таймер должен оставаться читаемым целиком.
                    Text(
                        subline(state),
                        color = GodjiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (state.connected) {
                        Text("·", color = GodjiColors.TextSecondary, fontSize = 11.5.sp)
                        Text(
                            state.connectedTimeLabel,
                            color = GodjiColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            val (ctaInteraction, ctaScale) = rememberPressScale()
            val ctaBgAnimated by animateColorAsState(ctaBg(state), label = "ctaBg")

            // Светящийся переливающийся ореол при включении/во включённом состоянии — по
            // мотивам присланного видео (glow-переключатель с "живым" градиентом вместо
            // мгновенной смены цвета). Во время connecting цвет ореола плавно перетекает
            // тил↔терракота (тот же приём, что и градиент на кольце дней подписки), в
            // подключённом состоянии — спокойное дыхание одним тилом; в отключённом ореола нет.
            val glowTransition = rememberInfiniteTransition(label = "ctaGlow")
            val glowShift by glowTransition.animateFloat(
                initialValue = 0f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
                label = "glowShift"
            )
            val glowPulse by glowTransition.animateFloat(
                initialValue = 0.5f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200), repeatMode = RepeatMode.Reverse),
                label = "glowPulse"
            )
            val glowColor = when {
                state.connecting -> lerp(GodjiColors.Teal, GodjiColors.Terracotta, glowShift)
                state.connected -> GodjiColors.Teal
                else -> null
            }

            Button(
                onClick = { toggle() },
                interactionSource = ctaInteraction,
                colors = ButtonDefaults.buttonColors(containerColor = ctaBgAnimated, contentColor = ctaColor(state)),
                shape = RoundedCornerShape(16.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                modifier = Modifier
                    .scale(ctaScale.value)
                    .drawBehind {
                        if (glowColor != null) {
                            val haloRadius = size.maxDimension * 1.8f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(glowColor.copy(alpha = glowPulse * 0.5f), glowColor.copy(alpha = 0f)),
                                    radius = haloRadius
                                ),
                                radius = haloRadius,
                                center = center
                            )
                        }
                    }
                    .height(44.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = ctaBgAnimated, spotColor = ctaBgAnimated)
            ) {
                Text(ctaLabel(state), fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard("↓ ${"%.1f".format(state.downSpeedMbps)} ${Loc.s.speedUnit}", Loc.s.statDownload, GodjiColors.Teal, Modifier.weight(1f))
            StatCard("↑ ${"%.1f".format(state.upSpeedMbps)} ${Loc.s.speedUnit}", Loc.s.statUpload, GodjiColors.Terracotta, Modifier.weight(1f))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(GodjiColors.Surface, RoundedCornerShape(16.dp))
                .border(GodjiColors.CardBorder, RoundedCornerShape(16.dp))
                .padding(11.dp, 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(GodjiColors.Chip),
                contentAlignment = Alignment.Center
            ) { Text(state.currentFlag, fontSize = 18.sp) }
            Column(Modifier.weight(1f)) {
                Text(state.currentNodeName.ifBlank { Loc.s.defaultNodeName }, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val meta = state.currentGeo?.displayCityCountry(Loc.lang).orEmpty()
                if (meta.isNotBlank()) {
                    Text(meta, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
                }
            }
        }

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

        AutoSwitchCard(state)

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

private fun ctaLabel(s: ConnectUiState) = if (s.connected || s.connecting) Loc.s.ctaDisconnect else Loc.s.ctaConnect
private fun ctaBg(s: ConnectUiState) = if (s.connected || s.connecting) GodjiColors.Danger else GodjiColors.Teal
private fun ctaColor(s: ConnectUiState) = GodjiColors.Surface

@Composable
private fun GlobeCard(state: ConnectUiState) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(GodjiColors.Surface)
            .border(GodjiColors.CardBorderStrong, RoundedCornerShape(24.dp))
    ) {
        GojiGlobe(status = state.globeStatus, node = state.globeNode, nodes = state.globeAllNodes, label = state.currentGeo?.displayCityCountry(Loc.lang).orEmpty(), modifier = Modifier.fillMaxSize())

        if (state.connected) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    state.greetingHi,
                    color = GodjiColors.TextPrimary,
                    fontFamily = if (state.greetingUsesSerif) InstrumentSerifFamily else FontFamily.Default,
                    fontSize = 28.sp
                )
                Text(state.greetingSub, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 8.5.sp)
            }
        }
    }
}

@Composable
private fun NetworkPill(net: NetState) {
    val (label, color, bg) = when (net) {
        NetState.WIFI -> Triple(Loc.s.netWifi, GodjiColors.TealDeep, GodjiColors.TealTint)
        NetState.CELLULAR -> Triple(Loc.s.netCellular, GodjiColors.TextSecondary, GodjiColors.Chip)
        NetState.JAMMED -> Triple(Loc.s.netJammed, GodjiColors.JamText, GodjiColors.JamBg)
    }
    Row(
        Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(9.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
private fun StatCard(value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(GodjiColors.Surface, RoundedCornerShape(16.dp))
            .border(GodjiColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(13.dp)
    ) {
        Text(label, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    }
}

@Composable
private fun BannerCard(text: String, kind: BannerKind, onDismiss: () -> Unit) {
    val bg = when (kind) { BannerKind.WARNING -> GodjiColors.JamBg; BannerKind.SUCCESS -> GodjiColors.TealTint; BannerKind.INFO -> GodjiColors.Chip }
    val border = when (kind) { BannerKind.WARNING -> GodjiColors.JamBorder; BannerKind.SUCCESS -> GodjiColors.TealTintBorder; BannerKind.INFO -> GodjiColors.CardBorderStrong }
    val color = when (kind) { BannerKind.WARNING -> GodjiColors.JamText; BannerKind.SUCCESS -> GodjiColors.TealDeep; BannerKind.INFO -> GodjiColors.TextPrimary }
    val icon = when (kind) { BannerKind.WARNING -> "⚠️"; BannerKind.SUCCESS -> "✅"; BannerKind.INFO -> "ℹ️" }
    Row(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(16.dp)).border(border, RoundedCornerShape(16.dp)).padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Text(icon, fontSize = 15.sp)
        Text(text, color = color, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.weight(1f), lineHeight = 15.sp)
        Text("×", color = color, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.clickable { onDismiss() })
    }
}

@Composable
private fun AutoSwitchCard(state: ConnectUiState) {
    Column(
        Modifier.fillMaxWidth().background(GodjiColors.Surface, RoundedCornerShape(16.dp)).border(GodjiColors.CardBorder, RoundedCornerShape(16.dp)).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Text(Loc.s.autoSwitchTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            Text(
                Loc.s.autoSwitchDesc,
                color = GodjiColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp
            )
        }
        Text(
            watchLine(state), color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 10.sp
        )
    }
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
            .clip(RoundedCornerShape(16.dp))
            .background(GodjiColors.Chip, RoundedCornerShape(16.dp))
            .border(GodjiColors.CardBorderStrong, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Loc.s.trafficLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
            Text(
                if (unlimited) Loc.s.trafficUnlimited("%.1f".format(state.usedGb)) else Loc.s.trafficLimited("%.1f".format(state.usedGb), state.quotaGb.toInt()),
                color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp
            )
        }
        if (!unlimited) {
            AnimatedTrafficBar(pct = pct)
        }
        Text(Loc.s.trafficExpiry(state.expiryLabel, state.daysLeft), color = GodjiColors.TextSecondary, fontSize = 10.sp)
    }
}

/** Плавно анимирует изменение доли использованного трафика (вместо мгновенного скачка) и
 *  добавляет бегущий блик по заполненной части — чтобы шкала не выглядела статичной картинкой,
 *  даже когда сам процент какое-то время не меняется. */
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
                    .background(GodjiColors.Teal)
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

private fun Modifier.border(color: Color, shape: androidx.compose.foundation.shape.RoundedCornerShape) =
    this.composeBorder(1.dp, color, shape)
