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
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.border as composeBorder
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
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
import xyz.gojihub.vpn.ui.theme.AutoBadge
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.util.rememberPressScale
import xyz.gojihub.vpn.ui.theme.SpaceGroteskFamily
import xyz.gojihub.vpn.ui.theme.JetBrainsMonoFamily
import xyz.gojihub.vpn.ui.theme.godjiCard
import xyz.gojihub.vpn.ui.theme.godjiGlassPill

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

        // Хero-блок v5: круглая стеклянная кнопка 80dp по центру, заголовок и статус-строка —
        // под ней (раньше кнопка стояла компактной пилюлей справа от заголовка в одной строке).
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            val (ctaInteraction, ctaScale) = rememberPressScale()
            Box(Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                if (state.connected) {
                    // Два расходящихся кольца-пульса со сдвигом по фазе в половину периода —
                    // как gg-ring в макете (два <span> с animation-delay 1.2s на цикл 2.4s).
                    val ringTransition = rememberInfiniteTransition(label = "ctaRing")
                    repeat(2) { i ->
                        val ringT by ringTransition.animateFloat(
                            initialValue = 0f, targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2400, easing = LinearOutSlowInEasing),
                                initialStartOffset = StartOffset(i * 1200)
                            ),
                            label = "ringT$i"
                        )
                        Box(
                            Modifier
                                .matchParentSize()
                                .scale(1f + ringT * 0.55f)
                                .composeBorder(2.dp, GodjiColors.Teal.copy(alpha = 0.5f * (1f - ringT)), CircleShape)
                        )
                    }
                }
                if (state.connecting) {
                    // Вращающийся двухцветный полу-обод — как gg-spin с border-top/border-right
                    // разных цветов в макете.
                    val spin = rememberInfiniteTransition(label = "ctaSpin")
                    val angle by spin.animateFloat(
                        0f, 360f,
                        infiniteRepeatable(tween(1000, easing = LinearEasing)),
                        label = "ctaSpinAngle"
                    )
                    Canvas(Modifier.matchParentSize().padding(3.dp).rotate(angle)) {
                        val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(GodjiColors.Teal, startAngle = -90f, sweepAngle = 150f, useCenter = false, style = stroke)
                        drawArc(GodjiColors.Terracotta, startAngle = 60f, sweepAngle = 150f, useCenter = false, style = stroke)
                    }
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .scale(ctaScale.value)
                        .godjiGlassPill(shape = CircleShape)
                        .clickable(interactionSource = ctaInteraction, indication = null) { toggle() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PowerSettingsNew,
                        contentDescription = ctaLabel(state),
                        tint = ctaIconColor(state),
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    headline(state),
                    color = GodjiColors.TextPrimary,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 24.sp,
                    lineHeight = 25.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
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
                    Text(
                        subline(state),
                        color = GodjiColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
        }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            StatCard("↓ ${"%.1f".format(state.downSpeedMbps)} ${Loc.s.speedUnit}", Loc.s.statDownload, GodjiColors.Teal, state.downHistory, Modifier.weight(1f))
            StatCard("↑ ${"%.1f".format(state.upSpeedMbps)} ${Loc.s.speedUnit}", Loc.s.statUpload, GodjiColors.Terracotta, state.upHistory, Modifier.weight(1f))
        }

        Row(
            Modifier
                .fillMaxWidth()
                .godjiCard()
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
private fun ctaIconColor(s: ConnectUiState) = when {
    s.connected -> GodjiColors.Teal
    s.connecting -> GodjiColors.Warning
    else -> GodjiColors.TextPrimary
}

@Composable
private fun GlobeCard(state: ConnectUiState) {
    // v5 "hero": глобус без карточки-обрамления (ни рамки, ни тени) — крупнее (430dp) и шире
    // экрана (виден выход за боковые края, requiredWidth(maxWidth + 120.dp)), сам широкий
    // прямоугольник GLSurfaceView заливается цветом "океана" (GlobeTheme.ocean, см.
    // GojiGlobeRenderer), который теперь совпадает по тону с фоном приложения — стык
    // невидим без отдельной подложки-карточки, как в исходном макете v5.
    BoxWithConstraints(Modifier.fillMaxWidth().height(430.dp)) {
        // Плашка "страна · город" на самом глобусе больше не показываем (label="") — то же
        // название уже есть в карточке текущего узла ниже; здесь остаётся только приветствие.
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
                    .padding(top = 22.dp, end = 12.dp)
                    .widthIn(max = 190.dp)
                    .godjiGlassPill(shape = RoundedCornerShape(18.dp))
                    .padding(horizontal = 13.dp, vertical = 9.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    state.greetingHi,
                    color = GodjiColors.TextPrimary,
                    fontFamily = if (state.greetingUsesSerif) SpaceGroteskFamily else FontFamily.Default,
                    fontWeight = if (state.greetingUsesSerif) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = 20.sp,
                    textAlign = TextAlign.End
                )
                Text(state.greetingSub, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
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
private fun StatCard(value: String, label: String, accent: Color, history: List<Float>, modifier: Modifier = Modifier) {
    Column(
        modifier
            .godjiCard()
            .padding(13.dp)
    ) {
        Text(label, color = GodjiColors.TextSecondary, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.5.sp, letterSpacing = 0.6.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = GodjiColors.TextPrimary, fontFamily = JetBrainsMonoFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Sparkline(history, accent, Modifier.fillMaxWidth().height(22.dp))
    }
}

/** Мини-график последних ~30 замеров скорости (см. ConnectViewModel.SPEED_HISTORY_SIZE) —
 *  та самая "живая" телеметрия из референса редизайна вместо голого числа. Рисуется сразу
 *  заполненной область под линией (полупрозрачный accent), а не только сама линия — так
 *  читается лучше на маленькой высоте карточки. */
@Composable
private fun Sparkline(history: List<Float>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (history.size < 2) return@Canvas
        val maxV = (history.maxOrNull() ?: 0f).coerceAtLeast(0.01f)
        val stepX = size.width / (history.size - 1)
        val points = history.mapIndexed { i, v ->
            Offset(i * stepX, size.height - (v / maxV) * size.height)
        }
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            for (p in points.drop(1)) lineTo(p.x, p.y)
        }
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(fillPath, brush = Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
        drawPath(linePath, color = color, style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun BannerCard(text: String, kind: BannerKind, onDismiss: () -> Unit) {
    val bg = when (kind) { BannerKind.WARNING -> GodjiColors.JamBg; BannerKind.SUCCESS -> GodjiColors.TealTint; BannerKind.INFO -> GodjiColors.Chip }
    val border = when (kind) { BannerKind.WARNING -> GodjiColors.JamBorder; BannerKind.SUCCESS -> GodjiColors.TealTintBorder; BannerKind.INFO -> GodjiColors.CardBorderStrong }
    val color = when (kind) { BannerKind.WARNING -> GodjiColors.JamText; BannerKind.SUCCESS -> GodjiColors.TealDeep; BannerKind.INFO -> GodjiColors.TextPrimary }
    val icon = when (kind) { BannerKind.WARNING -> "⚠️"; BannerKind.SUCCESS -> "✅"; BannerKind.INFO -> "ℹ️" }
    Row(
        Modifier.fillMaxWidth().godjiCard(tint = bg, borderColor = border).padding(12.dp),
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
        Modifier.fillMaxWidth().godjiCard().padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(Loc.s.autoSwitchTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                AutoBadge()
            }
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
            .godjiCard(tint = GodjiColors.Chip, borderColor = GodjiColors.CardBorderStrong)
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
