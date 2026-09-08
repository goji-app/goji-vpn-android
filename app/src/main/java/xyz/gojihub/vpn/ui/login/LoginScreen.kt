package xyz.gojihub.vpn.ui.login

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import xyz.gojihub.vpn.ui.util.rememberPressScale
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import xyz.gojihub.vpn.R
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.ui.globe.GojiGlobe
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.InstrumentSerifFamily

@Composable
fun LoginScreen(
    onCodeSent: (String) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    fun openInCustomTabs(url: String) {
        CustomTabsIntent.Builder().build().launchUrl(context, android.net.Uri.parse(url))
    }

    Box(Modifier.fillMaxSize().background(GodjiColors.Background)) {
        // Глобус во весь экран фоном — как в макете, без карточки-обрамления.
        GojiGlobe(status = "off", node = null, nodes = emptyList(), modifier = Modifier.fillMaxSize())

        // Плавный переход к цвету фона внизу, где сидят кнопки входа — тот же приём,
        // что и линейный градиент в макете поверх canvas.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to GodjiColors.Background.copy(alpha = 0f),
                        0.42f to GodjiColors.Background.copy(alpha = 0f),
                        0.62f to GodjiColors.Background.copy(alpha = 0.55f),
                        0.78f to GodjiColors.Background
                    )
                )
        )

        Row(
            Modifier.align(Alignment.TopStart).statusBarsPadding().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_notification),
                contentDescription = null,
                modifier = Modifier.size(40.dp)
            )
            Text("Goji", color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }

        // verticalScroll — на невысоких экранах (особенно в режиме email с показанной
        // ошибкой) весь этот блок не помещается по высоте; так как колонка прижата к низу
        // экрана, без скролла верхняя часть (заголовок, баннер "Впервые здесь?") просто
        // уезжала за пределы экрана вверх без возможности прокрутки к ней.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                Loc.s.loginGreeting,
                color = GodjiColors.TextPrimary,
                fontFamily = InstrumentSerifFamily,
                fontSize = 30.sp,
                lineHeight = 32.sp
            )

            // Реальная выдача рабочего пробного периода (с капчей/проверкой) происходит через
            // бота — вход в приложении (Google/Telegram-OAuth/email) сам по себе эту проверку
            // не проходит. Поэтому для новых пользователей сначала отправляем в бота: там же
            // создаётся аккаунт с рабочим триалом, а в приложение потом входят уже в него любым
            // способом ниже.
            val (botBannerInteraction, botBannerScale) = rememberPressScale()
            Row(
                Modifier
                    .fillMaxWidth()
                    .scale(botBannerScale.value)
                    .clip(RoundedCornerShape(14.dp))
                    .background(GodjiColors.Chip)
                    .border(1.dp, GodjiColors.CardBorderStrong, RoundedCornerShape(14.dp))
                    .clickable(interactionSource = botBannerInteraction, indication = LocalIndication.current) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/Shadow_Duck_bot")))
                    }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("✈️", fontSize = 16.sp)
                Column(Modifier.weight(1f)) {
                    Text(Loc.s.loginFirstTimeTitle, color = GodjiColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                    Text(
                        Loc.s.loginFirstTimeDesc,
                        color = GodjiColors.TextSecondary, fontSize = 10.sp, lineHeight = 13.sp
                    )
                }
                Text("›", color = GodjiColors.TextSecondary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!state.emailMode) {
                    val (googleInteraction, googleScale) = rememberPressScale()
                    Button(
                        onClick = { viewModel.startOAuth("google", onUrlReady = ::openInCustomTabs) },
                        enabled = !state.loading,
                        interactionSource = googleInteraction,
                        colors = ButtonDefaults.buttonColors(containerColor = GodjiColors.Ink),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp).scale(googleScale.value)
                    ) { Text(Loc.s.loginGoogle, color = GodjiColors.Surface, fontWeight = FontWeight.Bold, fontSize = 15.sp) }

                    // Временно отключено: вход через Telegram сейчас падает на этапе обмена
                    // кода на токен на стороне бэкенда (invalid_client) — разработчик бота
                    // пока не может сказать, когда это починится, поэтому не даём пользователям
                    // упираться в тупик, а честно показываем "скоро" вместо рабочей кнопки.
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            border = androidx.compose.foundation.BorderStroke(1.5.dp, GodjiColors.ButtonBorder),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                disabledContentColor = GodjiColors.TextSecondary
                            ),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text(Loc.s.loginTelegram, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                        Text(
                            Loc.s.loginSoon,
                            color = GodjiColors.Surface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 14.dp)
                                .clip(RoundedCornerShape(50))
                                .background(GodjiColors.Terracotta)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    val (yandexInteraction, yandexScale) = rememberPressScale()
                    OutlinedButton(
                        onClick = { viewModel.startOAuth("yandex", onUrlReady = ::openInCustomTabs) },
                        enabled = !state.loading,
                        interactionSource = yandexInteraction,
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, GodjiColors.ButtonBorder),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp).scale(yandexScale.value)
                    ) { Text(Loc.s.loginYandex, color = GodjiColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }

                    TextButton(onClick = { viewModel.toggleEmailMode(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(Loc.s.loginEmailMode, color = GodjiColors.TealDeep, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = state.email,
                        onValueChange = viewModel::onEmailChange,
                        label = { Text(Loc.s.loginEmailLabel) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val (otpInteraction, otpScale) = rememberPressScale()
                    Button(
                        onClick = { viewModel.sendOtp(onSent = onCodeSent) },
                        enabled = !state.loading,
                        interactionSource = otpInteraction,
                        colors = ButtonDefaults.buttonColors(containerColor = GodjiColors.Teal),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().height(54.dp).scale(otpScale.value)
                    ) { Text(if (state.loading) Loc.s.loginSendingOtp else Loc.s.loginGetCode, color = GodjiColors.Surface, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
                    TextButton(onClick = { viewModel.toggleEmailMode(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text(Loc.s.loginOtherMethods, color = GodjiColors.TextSecondary, fontSize = 12.sp)
                    }
                }

                state.error?.let {
                    Text(it, color = GodjiColors.Danger, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }

                Text(
                    Loc.s.loginNoAccount,
                    color = GodjiColors.TextSecondary,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                // Те же два документа и та же формулировка, что и на gojihub.xyz — раньше
                // приложение отправляло согласие на бэкенд молча, не показывая пользователю
                // ни текста, ни самих документов.
                val consentText = remember(Loc.lang) {
                    buildAnnotatedString {
                        append(Loc.s.loginConsentPrefix)
                        withLink(LinkAnnotation.Url("https://telegra.ph/Polzovatelskoe-soglashenie-servisa-ShadowDuck-10-10")) {
                            withStyle(SpanStyle(color = GodjiColors.TealDeep, fontWeight = FontWeight.SemiBold)) {
                                append(Loc.s.loginConsentTerms)
                            }
                        }
                        append(Loc.s.loginConsentAnd)
                        withLink(LinkAnnotation.Url("https://telegra.ph/Politika-konfidencialnosti-servisa-ShadowDuck-10-10")) {
                            withStyle(SpanStyle(color = GodjiColors.TealDeep, fontWeight = FontWeight.SemiBold)) {
                                append(Loc.s.loginConsentPrivacy)
                            }
                        }
                        append(Loc.s.loginConsentSuffix)
                    }
                }
                Text(
                    consentText,
                    color = GodjiColors.TextSecondary,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
