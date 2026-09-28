package xyz.gojihub.vpn.ui.plans

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import xyz.gojihub.vpn.BuildConfig
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.ui.theme.ActiveBadge
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.HoloBadge
import xyz.gojihub.vpn.ui.theme.SpaceGroteskFamily
import xyz.gojihub.vpn.ui.theme.godjiCard
import xyz.gojihub.vpn.ui.theme.godjiGlassPill
import xyz.gojihub.vpn.ui.util.RichContent
import xyz.gojihub.vpn.ui.util.rememberPressScale

@Composable
fun PlansScreen(onOpenSupport: () -> Unit, viewModel: PlansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // verticalScroll — на невысоких экранах без него нижние элементы (в первую очередь
    // "Выйти из аккаунта") обрезались краем экрана. LazyColumn заменён на обычные Row внутри
    // общего скролла — список тарифов короткий, виртуализация не нужна, а вложенный
    // вертикально скроллящийся контейнер внутри другого вертикально скроллящегося вызвал бы
    // краш Compose.
    Column(
        Modifier
            .fillMaxSize()
            .background(GodjiColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(18.dp, 18.dp, 18.dp, 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(Loc.s.plansTitle, color = GodjiColors.TextPrimary, fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Text(Loc.s.plansSubtitle, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
                state.customerId?.let { id ->
                    Row(
                        Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { clipboard.setText(AnnotatedString(id)) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text("${Loc.s.plansIdPrefix}$id", color = GodjiColors.TextSecondary, fontWeight = FontWeight.SemiBold, fontSize = 10.5.sp)
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = GodjiColors.TextSecondary, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Box(Modifier.size(40.dp).godjiGlassPill().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = viewModel::refresh), contentAlignment = Alignment.Center) {
                if (state.refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                else Icon(Icons.Filled.Refresh, contentDescription = Loc.s.serversRefresh, tint = GodjiColors.TealDeep, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(13.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .godjiCard(RoundedCornerShape(28.dp), tint = GodjiColors.TealTint)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                DaysRing(state.daysLeft)
                Column(Modifier.weight(1f)) {
                    Text(
                        buildAnnotatedString {
                            append(Loc.s.plansYourPlanPrefix)
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = GodjiColors.TextPrimary)) {
                                append(state.planName)
                            }
                        },
                        color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 13.sp
                    )
                    Text(Loc.s.plansUntil(state.expiryLabel), color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 10.5.sp)
                    if (state.personalDiscountPercent > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = GodjiColors.TealDeep, modifier = Modifier.size(12.dp))
                            Text(
                                Loc.s.plansPersonalDiscount(state.personalDiscountPercent),
                                color = GodjiColors.TealDeep,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp
                            )
                        }
                    }
                }
                ActiveBadge(label = Loc.s.plansActive)
            }
            // Google Play запрещает продажу цифровой подписки в приложении в обход Google Play
            // Billing — во флейворе play кнопка оплаты не собирается вовсе (см. ENABLE_EXTERNAL_CHECKOUT
            // в app/build.gradle.kts), а не просто прячется поверх экрана, чтобы не оставлять
            // недоступный, но всё ещё присутствующий в APK путь на внешний чекаут.
            if (BuildConfig.ENABLE_EXTERNAL_CHECKOUT) {
                val (extendInteraction, extendScale) = rememberPressScale()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .scale(extendScale.value)
                        .clip(RoundedCornerShape(50))
                        .background(Brush.verticalGradient(listOf(GodjiColors.AccentGradTop, GodjiColors.AccentGradMid, GodjiColors.AccentGradBottom)))
                        .clickable(interactionSource = extendInteraction, indication = LocalIndication.current) {
                            // Открываем сразу /checkout с уже известным тарифом и периодом (Custom Tabs,
                            // не внешний браузер отдельным приложением — тот же приём, что для нативного
                            // OAuth-логина, androidx.browser уже в зависимостях) — раньше кнопка вела на
                            // общий /#/plans, откуда пользователь заново выбирал тариф на сайте, хотя
                            // "Продлить" уже подразумевает именно текущий тариф на уже выбранный здесь
                            // период. Сама оплата всё равно происходит на странице платёжного шлюза
                            // (ЮKassa/Т-Банк/Robokassa/…) — приложение не участвует в передаче данных
                            // карты. Без определённого текущего тарифа (например ещё не подгрузился
                            // список) — прежнее поведение, общий /#/plans.
                            val currentPlan = state.plans.firstOrNull { it.isCurrent }
                            val checkoutUrl = if (currentPlan != null) {
                                "https://gojihub.xyz/#/checkout?plan=${currentPlan.id}&defaultPeriod=${state.selectedMonths}&defaultPeriodUnit=${currentPlan.periodUnit}"
                            } else {
                                "https://gojihub.xyz/#/plans"
                            }
                            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(checkoutUrl))
                        },
                    contentAlignment = Alignment.Center
                ) { Text(Loc.s.plansExtend, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
            }
        }

        if (state.periods.size > 1) {
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth().godjiGlassPill().padding(4.dp)) {
                val slotWidth = maxWidth / state.periods.size
                val selectedIndex = state.periods.indexOfFirst { it.months == state.selectedMonths }.coerceAtLeast(0)
                val thumbX by animateDpAsState(slotWidth * selectedIndex, spring(dampingRatio = 0.62f, stiffness = 380f), label = "periodThumb")
                Box(
                    Modifier
                        .offset(x = thumbX)
                        .width(slotWidth)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(GodjiColors.Thumb)
                )
                Row(Modifier.fillMaxWidth()) {
                    state.periods.forEach { p ->
                        val selected = p.months == state.selectedMonths
                        Box(
                            Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { viewModel.selectPeriod(p.months) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                p.label, color = if (selected) GodjiColors.TextPrimary else GodjiColors.TextSecondary,
                                fontWeight = FontWeight.Bold, fontSize = 12.sp,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().godjiCard()) {
            state.plans.forEachIndexed { index, plan ->
                if (index > 0) HorizontalDivider(thickness = 1.dp, color = GodjiColors.Hair)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (plan.isCurrent) GodjiColors.SelBg else Color.Transparent)
                        .padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(plan.name, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                            if (plan.isCurrent) HoloBadge(Loc.s.plansCurrentLabel, "★")
                        }
                        // Длинные описания тарифов раньше разворачивали карточку на пол-экрана —
                        // сжимаем до 2 строк и прячем остальное за "читать полностью", сам тоггл
                        // показываем только если текст реально не поместился (hasVisualOverflow),
                        // а не всегда — короткие описания тогда не получали бы лишнюю ссылку в никуда.
                        var expanded by remember(plan.id) { mutableStateOf(false) }
                        var overflowing by remember(plan.id) { mutableStateOf(false) }
                        Text(
                            plan.description,
                            color = GodjiColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            maxLines = if (expanded) Int.MAX_VALUE else 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow }
                        )
                        if (overflowing || expanded) {
                            Text(
                                if (expanded) Loc.s.plansDescriptionCollapse else Loc.s.plansDescriptionExpand,
                                color = GodjiColors.TealDeep,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.5.sp,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { expanded = !expanded }
                            )
                        }
                    }
                    Text(plan.priceLabel, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }

        if (state.subscriptionId != null) {
            DevicesSection(
                devices = state.devices,
                deviceLimit = state.deviceLimit,
                deleteSupportOnly = state.devicesDeleteSupportOnly,
                onRename = viewModel::renameDevice,
                onDelete = viewModel::deleteDevice,
                onOpenSupport = onOpenSupport
            )
        }

        if (state.referral != null || state.partner != null) {
            ProgramSection(state.referral, state.partner, clipboard, context)
        }

        if (state.news.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            SectionLabel(Loc.s.plansNewsTitle)
            Spacer(Modifier.height(8.dp))
            if (!state.newsExpanded) {
                // Свёрнутый вид — только самые свежие NEWS_PREVIEW_COUNT, остальное скрыто
                // за "Показать все", а не просто обрезано по высоте: старые новости не должны
                // отвлекать от тарифов на этой вкладке, если пользователь сам их не запросил.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.news.take(NEWS_PREVIEW_COUNT).forEach { NewsCard(it) }
                }
                if (state.news.size > NEWS_PREVIEW_COUNT) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Loc.s.plansNewsShowAll,
                        color = GodjiColors.TealDeep,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        modifier = Modifier.clickable { viewModel.toggleNewsExpanded() }
                    )
                }
            } else {
                val pages = state.news.chunked(NEWS_PAGE_SIZE)
                val page = state.newsPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    pages.getOrNull(page)?.forEach { NewsCard(it) }
                }
                if (pages.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = if (page > 0) GodjiColors.TealDeep else GodjiColors.CardBorder,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = page > 0) { viewModel.setNewsPage(page - 1) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        Text("${page + 1} / ${pages.size}", color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (page < pages.size - 1) GodjiColors.TealDeep else GodjiColors.CardBorder,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = page < pages.size - 1) { viewModel.setNewsPage(page + 1) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    Loc.s.plansNewsCollapse,
                    color = GodjiColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { viewModel.toggleNewsExpanded() }
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        val (supportInteraction, supportScale) = rememberPressScale()
        Row(
            Modifier
                .fillMaxWidth()
                .scale(supportScale.value)
                .godjiCard(tint = GodjiColors.TerracottaTint, borderColor = GodjiColors.TerracottaTintBorder)
                .clickable(interactionSource = supportInteraction, indication = LocalIndication.current, onClick = onOpenSupport)
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(GodjiColors.Terracotta), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(Loc.s.plansSupportText, color = GodjiColors.TerracottaDeep, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** Подпись секции ("НОВОСТИ", "ПРИГЛАСИ ДРУЗЕЙ" в эталоне) — капс мелким текстом с трекингом,
 *  как в "Настройках" (SettingsSection), а не крупный заголовок. */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = GodjiColors.TextSecondary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.66.sp,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp)
    )
}

/** Карточка одной новости/рассылки (gojihub.xyz/api/broadcasts) — content рендерится через
 *  RichContent (Rich Markdown + Telegram HTML-теги, см. ui/util/RichContent.kt), сворачивается
 *  до 6 блоков с одноразовым "Читать полностью" (раскрывается и остаётся раскрытым, как и на
 *  самой странице сайта). Кнопки-ссылки рассылки (Buttons, отдельное поле DTO — не часть
 *  Rich Markdown-разметки) показываются под контентом, если есть. */
@Composable
private fun NewsCard(item: NewsUi) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RichContent(
            raw = item.rawContent,
            collapsedBlocks = 6,
            readMoreLabel = Loc.s.plansNewsReadMore
        )
        if (item.buttons.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item.buttons.forEach { btn ->
                    val (btnInteraction, btnScale) = rememberPressScale()
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .scale(btnScale.value)
                            .clip(RoundedCornerShape(50))
                            .background(GodjiColors.TealTint)
                            .clickable(interactionSource = btnInteraction, indication = LocalIndication.current) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(btn.url)))
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(btn.text, color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }
        Text(item.dateLabel, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 9.5.sp)
    }
}

/** Рефералы и партнёрка раньше были двумя отдельными секциями подряд — визуально дублировали
 *  друг друга (у обеих ссылка/сводка/список) и вместе растягивали экран подписки на пол-ленты.
 *  Объединили в одно меню "Программа" с переключателем вкладок, когда доступны обе программы
 *  сразу; если доступна только одна — показываем её карточку без лишнего переключателя. */
@Composable
private fun ProgramSection(
    referral: ReferralUi?,
    partner: PartnerUi?,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    // 0 = рефералы, 1 = партнёрка — по умолчанию открываем ту, что вообще доступна.
    var tab by remember(referral != null, partner != null) { mutableIntStateOf(if (referral != null) 0 else 1) }

    Spacer(Modifier.height(16.dp))
    SectionLabel(Loc.s.plansProgramTitle)
    Spacer(Modifier.height(8.dp))

    if (referral != null && partner != null) {
        Row(
            Modifier.fillMaxWidth().godjiGlassPill().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            ProgramTab(Loc.s.plansProgramTabReferral, selected = tab == 0, modifier = Modifier.weight(1f)) { tab = 0 }
            ProgramTab(Loc.s.plansProgramTabPartner, selected = tab == 1, modifier = Modifier.weight(1f)) { tab = 1 }
        }
        Spacer(Modifier.height(8.dp))
    }

    when {
        referral != null && tab == 0 -> ReferralCard(referral, clipboard, context)
        partner != null -> PartnerCard(partner, context)
    }
}

@Composable
private fun ProgramTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) GodjiColors.Thumb else Color.Transparent, label = "programTabBg")
    Box(
        modifier
            .height(38.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label, color = if (selected) GodjiColors.TextPrimary else GodjiColors.TextSecondary,
            fontWeight = FontWeight.Bold, fontSize = 11.5.sp,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ReferralCard(referral: ReferralUi, clipboard: androidx.compose.ui.platform.ClipboardManager, context: Context) {
    Column(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(GodjiColors.Chip)
                .clickable { clipboard.setText(AnnotatedString(referral.link)) }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(referral.link, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ContentCopy, contentDescription = null, tint = GodjiColors.TealDeep, modifier = Modifier.size(15.dp))
            // Открывает системный share-sheet — иконка "поделиться" точнее отражает действие,
            // чем условная стрелка "открыть во внешнем".
            Icon(
                Icons.Filled.Share,
                contentDescription = null,
                tint = GodjiColors.TealDeep,
                modifier = Modifier.size(16.dp).clickable {
                    // Системный share-sheet — раньше ссылку можно было только скопировать в
                    // буфер, что лишний шаг перед отправкой в Telegram/WhatsApp/куда угодно.
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, referral.link)
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            ReferralStat(Loc.s.plansReferralInvited, "${referral.totalReferrals}")
            ReferralStat(Loc.s.plansReferralActive, "${referral.activeReferrals}", GodjiColors.TealDeep)
            ReferralStat(Loc.s.plansReferralBonusDays, "${referral.totalBonusDays}", GodjiColors.TerracottaDeep)
        }
        if (referral.entries.isNotEmpty()) {
            HorizontalDivider(color = GodjiColors.Hair)
            Text(Loc.s.plansReferralListTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                referral.entries.forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(e.displayName, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (e.bonusDays > 0) {
                                Text(Loc.s.plansReferralBonusSuffix(e.bonusDays), color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                            }
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (e.isActive) GodjiColors.TealTint else GodjiColors.Chip)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    if (e.isActive) Loc.s.plansReferralActiveBadge else Loc.s.plansReferralInactiveBadge,
                                    color = if (e.isActive) GodjiColors.TealDeep else GodjiColors.TextSecondary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }
        } else {
            Text(Loc.s.plansReferralEmpty, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ReferralStat(label: String, value: String, valueColor: Color = GodjiColors.TextPrimary) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(label, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 9.sp)
    }
}

/** Только статус/сводка — подача заявки и запрос вывода средств делаются на сайте (та же
 *  логика, что и "Продлить" для тарифов: не переизобретаем денежные формы нативно). */
@Composable
private fun PartnerCard(partner: PartnerUi, context: Context) {
    Column(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            partner.isPartner && !partner.isActive ->
                Text(Loc.s.plansPartnerDeactivated, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
            partner.isPartner -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ReferralStat(Loc.s.plansPartnerCommission, "${partner.commissionRate}%")
                    ReferralStat(Loc.s.plansPartnerClients, "${partner.clientCount}")
                    ReferralStat(Loc.s.plansPartnerEarned, "${partner.totalEarned.toInt()} ₽", GodjiColors.TerracottaDeep)
                }
                HorizontalDivider(color = GodjiColors.Hair)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(Loc.s.plansPartnerBalance, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                    Text("${partner.availableBalance.toInt()} ₽", color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                if (partner.pendingBalance > 0) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(Loc.s.plansPartnerPendingBalance, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
                        Text("${partner.pendingBalance.toInt()} ₽", color = GodjiColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
                PartnerActionButton(Loc.s.plansPartnerOpenDashboard, context)
            }
            partner.applicationStatus == "pending" -> {
                Text(Loc.s.plansPartnerPending, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
            }
            partner.applicationStatus == "rejected" -> {
                Text(Loc.s.plansPartnerRejected, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                PartnerActionButton(Loc.s.plansPartnerApply, context)
            }
            else -> {
                Text(Loc.s.plansPartnerNotPartnerText, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.5.sp)
                PartnerActionButton(Loc.s.plansPartnerApply, context)
            }
        }
    }
}

@Composable
private fun PartnerActionButton(label: String, context: Context) {
    val (interaction, scale) = rememberPressScale()
    Box(
        Modifier
            .fillMaxWidth()
            .scale(scale.value)
            .clip(RoundedCornerShape(50))
            .background(Brush.verticalGradient(listOf(GodjiColors.AccentGradTop, GodjiColors.AccentGradMid, GodjiColors.AccentGradBottom)))
            .clickable(interactionSource = interaction, indication = LocalIndication.current) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gojihub.xyz/#/partner-dashboard")))
            }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

/** Сводка + ссылка + список приглашённых (gojihub.xyz/api/dashboard/referrals). Имена/
 *  юзернеймы/email приглашённых уже замаскированы во ViewModel (см. displayNameFor) — это
 *  чужие персональные данные, не наши. */
@Composable
private fun DevicesSection(
    devices: List<DeviceUi>,
    deviceLimit: Int,
    deleteSupportOnly: Boolean,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onOpenSupport: () -> Unit
) {
    var renameTarget by remember { mutableStateOf<DeviceUi?>(null) }
    var deleteTarget by remember { mutableStateOf<DeviceUi?>(null) }

    Spacer(Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Loc.s.plansDevicesTitle, color = GodjiColors.TextPrimary, fontFamily = SpaceGroteskFamily, fontWeight = FontWeight.Medium, fontSize = 19.sp)
        if (deviceLimit > 0) {
            Text(
                Loc.s.plansDevicesCountLabel(devices.size, deviceLimit),
                color = GodjiColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Column(
        Modifier
            .fillMaxWidth()
            .godjiCard()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (devices.isEmpty()) {
            Text(Loc.s.plansDevicesEmpty, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        } else {
            devices.forEachIndexed { index, device ->
                if (index > 0) HorizontalDivider(color = GodjiColors.Hair)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(device.name, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        val subtitle = listOfNotNull(
                            device.platform,
                            device.connectedVia?.let(Loc.s.plansDevicesConnectedVia),
                            device.createdAtLabel
                        ).joinToString(" · ")
                        if (subtitle.isNotEmpty()) {
                            Text(subtitle, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 10.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    if (device.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = GodjiColors.TealDeep,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { renameTarget = device }
                                    .padding(2.dp)
                            )
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                tint = GodjiColors.Danger,
                                modifier = Modifier
                                    .size(17.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { deleteTarget = device }
                                    .padding(2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { device ->
        var name by remember(device.hwid) { mutableStateOf(device.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(Loc.s.plansDevicesRenameTitle) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(Loc.s.plansDevicesRenameLabel) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { onRename(device.hwid, name); renameTarget = null }) { Text(Loc.s.plansDevicesSave) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text(Loc.s.plansDevicesCancel) }
            }
        )
    }

    deleteTarget?.let { device ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(Loc.s.plansDevicesDeleteTitle) },
            text = { Text(if (deleteSupportOnly) Loc.s.plansDevicesDeleteSupportOnly else Loc.s.plansDevicesDeleteConfirm) },
            confirmButton = {
                if (deleteSupportOnly) {
                    TextButton(onClick = {
                        onOpenSupport()
                        deleteTarget = null
                    }) { Text(Loc.s.plansDevicesContactSupport) }
                } else {
                    TextButton(onClick = { onDelete(device.hwid); deleteTarget = null }) {
                        Text(Loc.s.plansDevicesDelete, color = GodjiColors.Danger)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(Loc.s.plansDevicesCancel) }
            }
        )
    }
}

/** Кольцо-таймер дней подписки — 68dp, кольцо Teal по TrackBg, "ядро" RingCore 54dp. */
@Composable
private fun DaysRing(days: Int) {
    val pct = (days / 30f).coerceIn(0f, 1f)
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            drawArc(GodjiColors.TrackBg, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(GodjiColors.Teal, startAngle = -90f, sweepAngle = 360f * pct, useCenter = false, style = stroke)
        }
        Box(Modifier.size(54.dp).clip(CircleShape).background(GodjiColors.RingCore), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$days", color = GodjiColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text(Loc.s.plansDays, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 7.5.sp)
            }
        }
    }
}
