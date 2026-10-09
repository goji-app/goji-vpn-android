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
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.StartOffset
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
import xyz.gojihub.vpn.ui.theme.CookieShape
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

    // Экран целиком помещается без прокрутки: все блоки берут свою высоту, а глобус — то, что
    // осталось (HomeFitColumn). verticalScroll — только страховка для очень низких экранов и
    // крупного шрифта, когда даже минимальный глобус не влезает.
    BoxWithConstraints(Modifier.fillMaxSize().background(GodjiColors.Background)) {
    HomeFitColumn(
        available = maxHeight - HOME_PAD_TOP - HOME_PAD_BOTTOM,
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = HOME_PAD_TOP, end = 16.dp, bottom = HOME_PAD_BOTTOM)
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

        GlobeCard(state, Modifier.layoutId(GLOBE_ID))

        // Кнопка-«печенье» 64dp, под ней заголовок 22sp и строка статуса.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ConnectButton(state, onClick = { toggle() })
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    headline(state),
                    color = GodjiColors.TextPrimary,
                    fontFamily = SpaceGroteskFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    letterSpacing = (-0.4).sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor(state)))
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
        }

        state.error?.let {
            Text(it, color = GodjiColors.Danger, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }

        StatsCard(state)

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
}

private const val GLOBE_ID = "globe"
private val HOME_PAD_TOP = 14.dp
private val HOME_PAD_BOTTOM = 8.dp
private val HOME_GAP = 8.dp
// Слот глобуса в раскладке (сам холст ещё заходит на 44dp под шапку и на 42dp под кнопку).
// 344 — как было в эталоне; меньше 150 сфера становится мелкой — тогда уже прокрутка.
private val GLOBE_SLOT_MIN = 150.dp
private val GLOBE_SLOT_MAX = 344.dp

/** Колонка главного экрана: блоки по порядку с промежутком [HOME_GAP] (пустые — без
 *  промежутка, например скрытый баннер), глобус (layoutId [GLOBE_ID]) получает остаток
 *  высоты [available] в пределах [GLOBE_SLOT_MIN]..[GLOBE_SLOT_MAX]. */
@Composable
private fun HomeFitColumn(available: Dp, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val width = constraints.maxWidth
        val gap = HOME_GAP.roundToPx()
        val loose = Constraints(maxWidth = width)
        val globeIdx = measurables.indexOfFirst { it.layoutId == GLOBE_ID }
        val fixed = measurables.mapIndexed { i, m -> if (i == globeIdx) null else m.measure(loose) }
        val shown = fixed.count { it == null || it.height > 0 }
        val gaps = gap * (shown - 1).coerceAtLeast(0)
        val fixedSum = fixed.sumOf { it?.height ?: 0 }
        val globeH = (available.roundToPx() - fixedSum - gaps)
            .coerceIn(GLOBE_SLOT_MIN.roundToPx(), GLOBE_SLOT_MAX.roundToPx())
        val globe = if (globeIdx >= 0) measurables[globeIdx].measure(Constraints.fixed(width, globeH)) else null
        val total = fixedSum + gaps + (globe?.height ?: 0)
        layout(width, total) {
            var y = 0
            fixed.forEachIndexed { i, f ->
                val p = if (i == globeIdx) globe!! else f!!
                if (i != globeIdx && p.height == 0) return@forEachIndexed
                p.place((width - p.width) / 2, y)
                y += p.height + gap
            }
        }
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
    else -> GodjiColors.Outline
}

@Composable
private fun GlobeCard(state: ConnectUiState, modifier: Modifier = Modifier) {
    // Эталон: height:430px; margin:-24px -60px -20px. Высоту слота задаёт HomeFitColumn (до
    // 344dp — тогда холст 430dp, как в эталоне); холст заходит на 44dp под шапку и на 42dp под
    // кнопку, по ширине — +60dp с каждой стороны. Прозрачный холст: вокруг сферы общий фон.
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val slot = constraints.maxHeight
                val top = 44.dp.roundToPx()
                val bottom = 42.dp.roundToPx()
                val full = slot + top + bottom
                val placeable = measurable.measure(constraints.copy(minHeight = full, maxHeight = full))
                layout(placeable.width, slot) { placeable.place(0, -top) }
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
                    .padding(top = 50.dp, end = 14.dp)
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

/** Кнопка подключения в стиле Material 3 Expressive — «печенье» (8 мягких волн) 64dp.
 *  Выключено — primaryContainer; подключение — печенье вращается (как LoadingIndicator M3E);
 *  включено — заливка primary, волны сглаживаются почти в круг. Нажатие «вдавливает» волны —
 *  форма морфится пружиной, а не переключается рывком. Без колец и теней: ничего не
 *  перерисовывается на каждом кадре, кроме поворота во время подключения. */
@Composable
private fun ConnectButton(state: ConnectUiState, onClick: () -> Unit) {
    val (pressInteraction, pressScale) = rememberPressScale()
    val pressed by pressInteraction.collectIsPressedAsState()
    val depth by animateFloatAsState(
        when {
            pressed -> 0.09f
            state.connected -> 0.025f
            else -> 0.055f
        },
        spring(dampingRatio = 0.45f, stiffness = 380f), label = "cookieDepth"
    )
    val fill by animateColorAsState(if (state.connected) GodjiColors.Teal else GodjiColors.PrimaryContainer, label = "cookieFill")
    val iconTint by animateColorAsState(if (state.connected) GodjiColors.Surface else GodjiColors.OnPrimaryContainer, label = "cookieIcon")
    val spin: State<Float> = if (state.connecting) {
        rememberInfiniteTransition(label = "cookieSpin").animateFloat(
            0f, 360f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "spin"
        )
    } else remember { mutableFloatStateOf(0f) }
    val shape = CookieShape(8, depth)
    Box(
        Modifier
            .size(64.dp)
            .graphicsLayer {
                rotationZ = spin.value
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .background(fill, shape)
            .clip(shape)
            .clickable(interactionSource = pressInteraction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.PowerSettingsNew,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(26.dp).graphicsLayer { rotationZ = -spin.value }
        )
    }
}

@Composable
private fun NetworkPill(net: NetState) {
    val (label, color) = when (net) {
        NetState.WIFI -> Loc.s.netWifi to GodjiColors.TealDeep
        NetState.CELLULAR -> Loc.s.netCellular to GodjiColors.TextSecondary
        NetState.JAMMED -> Loc.s.netJammed to GodjiColors.JamText
    }
    // netMeta из эталона: Wi-Fi/мобильная — на обычном стекле, глушение — на тёплой подложке.
    Row(
        Modifier
            .height(34.dp)
            .godjiGlassPill(tint = if (net == NetState.JAMMED) GodjiColors.JamBg else null)
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Эталон: padding 14px 4px, две колонки gap 4, разделитель — border-right первой колонки. */
@Composable
private fun StatsCard(state: ConnectUiState) {
    Row(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .padding(horizontal = 4.dp, vertical = 9.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatColumn(Loc.s.statDownload, state.downSpeedMbps, GodjiColors.Teal, down = true, Modifier.weight(1f))
        Box(Modifier.width(1.dp).fillMaxHeight().background(GodjiColors.Hair))
        StatColumn(Loc.s.statUpload, state.upSpeedMbps, GodjiColors.Terracotta, down = false, Modifier.weight(1f))
    }
}

@Composable
private fun StatColumn(label: String, speedMbps: Double, accent: Color, down: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(
                if (down) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = null, tint = accent, modifier = Modifier.size(12.dp)
            )
            Text(label, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(speedMbps), color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 20.sp, letterSpacing = (-0.4).sp, modifier = Modifier.alignByBaseline())
            Text(" ${Loc.s.speedUnit}", color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.alignByBaseline())
        }
    }
}

/** Эталон: padding 13/14, радиус 22, подложка bannerBg, круг 28dp цвета accent/warm с белым
 *  значком, текст 12sp, крестик — круг 24dp на подложке hair. */
@Composable
private fun BannerCard(text: String, kind: BannerKind, onDismiss: () -> Unit) {
    val ok = kind == BannerKind.SUCCESS
    val bg = if (ok) GodjiColors.TealTint else GodjiColors.TerracottaTint
    val iconBg = if (ok) GodjiColors.Teal else GodjiColors.Terracotta
    val icon = when (kind) { BannerKind.SUCCESS -> Icons.Filled.Check; BannerKind.WARNING -> Icons.Filled.PriorityHigh; BannerKind.INFO -> Icons.Filled.Info }
    Row(
        Modifier.fillMaxWidth().godjiGlassPill(RoundedCornerShape(20.dp), tint = bg).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        }
        Text(text, color = GodjiColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(GodjiColors.Hair).clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Text("×", color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
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
        HorizontalDivider(thickness = 2.dp, color = GodjiColors.SurfaceBase)
        Column(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 11.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = GodjiColors.TealDeep, modifier = Modifier.size(15.dp))
                Text(Loc.s.autoSwitchTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                xyz.gojihub.vpn.ui.theme.AutoBadge()
            }
            // Строка "сеть: Wi-Fi/мобильная" убрана ради экрана без прокрутки — сеть и так
            // показана плашкой в шапке.
            Text(Loc.s.autoSwitchDesc, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Loc.s.trafficLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.alignByBaseline())
            Text(
                if (unlimited) Loc.s.trafficUnlimited("%.1f".format(state.usedGb)) else Loc.s.trafficLimited("%.1f".format(state.usedGb), state.quotaGb.toInt()),
                color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.alignByBaseline()
            )
        }
        if (!unlimited) {
            AnimatedTrafficBar(pct = pct)
        }
        Text(Loc.s.trafficExpiry(state.expiryLabel, state.daysLeft), color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
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
    // gg-shimmer: блик шириной 40% заливки, translateX −100% → 300% за 1.8с
    val shimmerT by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
        label = "shimmerT"
    )
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(50))
            .background(GodjiColors.TrackBg)
    ) {
        val fillWidth = maxWidth * animatedPct
        val shimmerWidth = fillWidth * 0.4f
        if (fillWidth > 0.dp) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(fillWidth)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(0f to GodjiColors.AccentGradTop, 0.55f to GodjiColors.AccentGradMid, 1f to GodjiColors.AccentGradBottom))
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(shimmerWidth)
                        .offset(x = shimmerWidth * shimmerT)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent)
                            )
                        )
                )
            }
        }
    }
}
