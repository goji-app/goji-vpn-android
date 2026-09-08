package xyz.gojihub.vpn.ui.plans

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.InstrumentSerifFamily
import xyz.gojihub.vpn.ui.util.rememberPressScale

@Composable
fun PlansScreen(viewModel: PlansViewModel = hiltViewModel()) {
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
                Text(Loc.s.plansTitle, color = GodjiColors.TextPrimary, fontFamily = InstrumentSerifFamily, fontSize = 26.sp)
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
                        Text("⧉", color = GodjiColors.TextSecondary, fontSize = 11.sp)
                    }
                }
            }
            IconButton(onClick = viewModel::refresh) {
                if (state.refreshing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = GodjiColors.TealDeep)
                else Icon(Icons.Filled.Refresh, contentDescription = Loc.s.serversRefresh, tint = GodjiColors.TealDeep)
            }
        }

        Spacer(Modifier.height(13.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(GodjiColors.Surface, RoundedCornerShape(20.dp))
                .border(1.5.dp, GodjiColors.Ink, RoundedCornerShape(20.dp))
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
                }
                ActiveStatusPill()
            }
            val (extendInteraction, extendScale) = rememberPressScale()
            Button(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gojihub.xyz/#/plans"))) },
                interactionSource = extendInteraction,
                colors = ButtonDefaults.buttonColors(containerColor = GodjiColors.Ink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp).scale(extendScale.value)
            ) { Text(Loc.s.plansExtend, color = GodjiColors.Surface, fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
        }

        if (state.periods.size > 1) {
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(GodjiColors.Chip, RoundedCornerShape(16.dp)).padding(5.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                state.periods.forEach { p ->
                    val selected = p.months == state.selectedMonths
                    val (chipInteraction, chipScale) = rememberPressScale()
                    val chipBg by animateColorAsState(if (selected) GodjiColors.Ink else androidx.compose.ui.graphics.Color.Transparent, label = "chipBg")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(38.dp)
                            .scale(chipScale.value)
                            .clip(RoundedCornerShape(11.dp))
                            .background(chipBg)
                            .clickable(interactionSource = chipInteraction, indication = LocalIndication.current) { viewModel.selectPeriod(p.months) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(p.label, color = if (selected) GodjiColors.Surface else GodjiColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.plans.forEach { plan ->
                val planBg by animateColorAsState(if (plan.isCurrent) GodjiColors.TealTint else GodjiColors.Surface, label = "planBg")
                val planBorder by animateColorAsState(if (plan.isCurrent) GodjiColors.Teal else GodjiColors.CardBorder, label = "planBorder")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(planBg, RoundedCornerShape(18.dp))
                        .border(1.5.dp, planBorder, RoundedCornerShape(18.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(plan.name, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                        Text(plan.description, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 14.sp)
                    }
                    Text(plan.priceLabel, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        val (supportInteraction, supportScale) = rememberPressScale()
        Row(
            Modifier
                .fillMaxWidth()
                .scale(supportScale.value)
                .background(GodjiColors.TerracottaTint, RoundedCornerShape(16.dp))
                .border(1.dp, GodjiColors.TerracottaTintBorder, RoundedCornerShape(16.dp))
                .clickable(interactionSource = supportInteraction, indication = LocalIndication.current) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://gojihub.xyz/#/support-chat")))
                }
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✉️", fontSize = 17.sp)
            Text(Loc.s.plansSupportText, color = GodjiColors.TerracottaDeep, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
        }

        Spacer(Modifier.height(4.dp))
    }
}

/** Пульсирующая точка вместо статичной обводки — читается как "живой" статус, а не просто
 *  ярлык. Пилл теперь залит мягким тил-тоном вместо тонкой рамки — тот же приём, что уже
 *  используется для активного тарифа в списке ниже (TealTint/TealDeep). */
@Composable
private fun ActiveStatusPill() {
    val infiniteTransition = rememberInfiniteTransition(label = "activePulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "activePulseAlpha"
    )
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(GodjiColors.TealTint)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .background(GodjiColors.TealDeep.copy(alpha = pulseAlpha), CircleShape)
        )
        Text(Loc.s.plansActive, color = GodjiColors.TealDeep, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    }
}

/** Кольцо с закруглёнными краями штриха и градиентной заливкой вместо плоской заливки в один
 *  тон — плоский тонкий круг на бледном фоне выглядел скорее как техническая шкала, чем как
 *  акцентный элемент карточки. */
@Composable
private fun DaysRing(days: Int) {
    val pct = (days / 30f).coerceIn(0f, 1f)
    Box(Modifier.size(70.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
            drawArc(GodjiColors.TrackBg, startAngle = -90f, sweepAngle = 360f, useCenter = false, style = stroke)
            drawArc(
                brush = Brush.linearGradient(listOf(GodjiColors.TealDeep, GodjiColors.Teal)),
                startAngle = -90f,
                sweepAngle = 360f * pct,
                useCenter = false,
                style = stroke
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$days", color = GodjiColors.TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
            Text(Loc.s.plansDays, color = GodjiColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 7.5.sp)
        }
    }
}
