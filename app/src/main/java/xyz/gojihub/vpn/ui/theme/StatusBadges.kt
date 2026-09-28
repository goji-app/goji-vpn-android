package xyz.gojihub.vpn.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Статус-бейджи v5 "Стекло" — замена плоских пиллов на "живые" индикаторы: бегущее по рамке
 * кольцо-комета (АКТИВНА/АВТО) и переливающийся голографический блеск (Текущий/Новая).
 * Лейблы по умолчанию — как в макете; там, где уже есть локализованная строка (Loc.s.plansActive),
 * зовущий код передаёт её явно, а не полагается на дефолт.
 */
private val HOLO = listOf(Color(0xFF1FC2B2), Color(0xFF7C8CFF), Color(0xFFE58FD0), Color(0xFFF4C27A), Color(0xFF1FC2B2))

/** Кольцо-комета, бегущее по рамке пилюли (как в v5 «АКТИВНА» / «АВТО»). */
private fun Modifier.orbitRing(angle: Float, color: Color) = this.drawBehind {
    val r = size.maxDimension
    rotate(angle) {
        drawCircle(
            Brush.sweepGradient(listOf(Color.Transparent, Color.Transparent, color, Color.White, color, Color.Transparent), center),
            radius = r, center = center
        )
    }
}

@Composable
private fun rememberSpin(ms: Int): Float {
    val t = rememberInfiniteTransition(label = "spin")
    val a by t.animateFloat(0f, 360f, infiniteRepeatable(tween(ms, easing = LinearEasing)), label = "a")
    return a
}

@Composable
fun ActiveBadge(label: String = "АКТИВНА") {
    val angle = rememberSpin(2800)
    val t = rememberInfiniteTransition(label = "ping")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearOutSlowInEasing)), label = "p")
    Box(Modifier.height(28.dp).clip(RoundedCornerShape(50)).orbitRing(angle, GodjiColors.Teal).padding(1.5.dp)) {
        Row(
            Modifier.fillMaxHeight().clip(RoundedCornerShape(50)).background(GodjiColors.Surface.copy(alpha = 0.9f)).padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.matchParentSize().scale(1f + p * 1.8f).background(GodjiColors.Teal.copy(alpha = 0.75f * (1 - p)), CircleShape))
                Box(Modifier.matchParentSize().background(GodjiColors.Teal, CircleShape))
            }
            Text(label, color = GodjiColors.TealDeep, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        }
    }
}

@Composable
fun AutoBadge(label: String = "АВТО") {
    val angle = rememberSpin(4000)
    Box(Modifier.height(24.dp).clip(RoundedCornerShape(50)).orbitRing(angle, GodjiColors.Teal).padding(1.5.dp)) {
        Row(
            Modifier.fillMaxHeight().clip(RoundedCornerShape(50)).background(GodjiColors.Surface.copy(alpha = 0.9f)).padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("⟳", color = GodjiColors.TealDeep, fontSize = 12.sp, modifier = Modifier.rotate(rememberSpin(3000)))
            Text(label, color = GodjiColors.TealDeep, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
        }
    }
}

/** Переливающийся «голографический» бейдж с блеском (v5 «Текущий» / «Новая»). */
@Composable
fun HoloBadge(label: String, icon: String = "★", reverse: Boolean = false) {
    val t = rememberInfiniteTransition(label = "holo")
    val shift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing)), label = "s")
    val sweep by t.animateFloat(-0.4f, 1.6f, infiniteRepeatable(tween(3000, easing = FastOutSlowInEasing)), label = "w")
    val colors = if (reverse) HOLO.reversed() else HOLO
    Row(
        Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(50))
            .drawWithContent {
                val w = size.width * 3
                drawRect(Brush.linearGradient(colors, start = Offset(-shift * w, 0f), end = Offset(-shift * w + w, 0f), tileMode = androidx.compose.ui.graphics.TileMode.Repeated))
                drawContent()
                val x = size.width * sweep
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.7f), Color.Transparent), x - 20f, x + 20f))
            }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(icon, color = Color.White, fontSize = 9.sp)
        Text(label, color = Color.White, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable fun CurrentBadge() = HoloBadge("Текущий", "★")
@Composable fun NewBadge() = HoloBadge("НОВАЯ", "✦", reverse = true)
