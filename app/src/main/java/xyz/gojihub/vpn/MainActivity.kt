package xyz.gojihub.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import xyz.gojihub.vpn.auth.AuthRepository
import xyz.gojihub.vpn.i18n.Loc
import xyz.gojihub.vpn.ui.connect.ConnectScreen
import xyz.gojihub.vpn.ui.login.LoginScreen
import xyz.gojihub.vpn.ui.login.VerifyEmailScreen
import xyz.gojihub.vpn.ui.navigation.GodjiDestinations
import xyz.gojihub.vpn.ui.plans.PlansScreen
import xyz.gojihub.vpn.ui.servers.ServersScreen
import xyz.gojihub.vpn.ui.settings.AppTunnelingScreen
import xyz.gojihub.vpn.ui.settings.LogLevelScreen
import xyz.gojihub.vpn.ui.settings.PingSettingsScreen
import xyz.gojihub.vpn.ui.settings.SettingsScreen
import xyz.gojihub.vpn.ui.support.FaqScreen
import xyz.gojihub.vpn.ui.support.NewTicketScreen
import xyz.gojihub.vpn.ui.support.SupportListScreen
import xyz.gojihub.vpn.ui.support.TicketChatScreen
import xyz.gojihub.vpn.ui.theme.GlassBackdrop
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.GodjiVpnTheme
import xyz.gojihub.vpn.ui.theme.ThemeMode
import xyz.gojihub.vpn.ui.theme.godjiGlassBar
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GodjiVpnTheme {
                GodjiApp(startLoggedIn = authRepository.isLoggedIn(), authRepository = authRepository)
            }
        }
    }
}

/** Редизайн "Apple Glass" (2026-09-28): раньше это было 4 независимых NavHost-назначения с
 *  переключением только по тапу на NavigationBarItem. Теперь — одна и та же вкладка/индекс
 *  страницы что для тапа по нижней панели, что для свайпа пальцем по контенту (HorizontalPager
 *  из коробки поддерживает drag) — оба способа переключения читают/двигают один и тот же
 *  PagerState, так что рассинхронизации между ними в принципе не может быть.
 *
 *  Иконки — векторные (Filled/Outlined из material-icons-extended), не эмодзи: цветные эмодзи
 *  (🏠🌐💳⚙️) в нижней панели были самым "не-Apple" элементом при переходе к минималистичной
 *  премиум-эстетике (правка "минимализм/премиум", тот же день) — выбранная вкладка рисуется
 *  Filled-версией, невыбранная — Outlined, вместо смены цвета/эмодзи. */
private enum class GodjiTab(
    val label: () -> String,
    val iconSelected: androidx.compose.ui.graphics.vector.ImageVector,
    val iconUnselected: androidx.compose.ui.graphics.vector.ImageVector
) {
    CONNECT({ Loc.s.tabHome }, Icons.Filled.Shield, Icons.Outlined.Shield),
    SERVERS({ Loc.s.tabServers }, Icons.Filled.Public, Icons.Outlined.Public),
    PLANS({ Loc.s.tabPlans }, Icons.Filled.CreditCard, Icons.Outlined.CreditCard),
    SETTINGS({ Loc.s.tabSettings }, Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun GodjiApp(startLoggedIn: Boolean, authRepository: AuthRepository) {
    // При режиме "Системная" (см. ThemeMode, настройка Внешний вид → Тема оформления) следим
    // за системной тёмной темой живьём, пока приложение открыто — раньше (до ThemeMode.SYSTEM)
    // приложение всегда игнорировало системную тему и держалось только явного переключателя.
    // isSystemInDarkTheme() перекомпонует этот блок при смене темы Android на лету — SideEffect
    // ниже просто переносит актуальное значение в GodjiColors, если сейчас включён SYSTEM.
    val systemDark = isSystemInDarkTheme()
    val themeMode = GodjiColors.themeMode
    SideEffect {
        if (themeMode == ThemeMode.SYSTEM && systemDark != GodjiColors.isDark) {
            if (systemDark) GodjiColors.applyDark() else GodjiColors.applyLight()
        }
    }

    val view = LocalView.current
    val isDark = GodjiColors.isDark
    val barColor = GodjiColors.Background
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window
        if (window != null) {
            window.statusBarColor = barColor.toArgb()
            window.navigationBarColor = barColor.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    val navController = rememberNavController()

    // Реальный 401 от бэкенда (см. authInterceptor в NetworkModule) — единственный надёжный
    // признак протухшей сессии. Раньше это решалось только сравнением с локально посчитанным
    // expires_at при следующем холодном старте MainActivity, из-за чего приложение периодически
    // "выходило из профиля" даже посреди активной работы (Android регулярно убивает фоновые
    // процессы) ещё до того, как токен реально переставал бы приниматься сервером. Теперь
    // реагируем сразу, во время работы приложения, а не только при пересоздании Activity.
    val sessionExpired by authRepository.sessionExpired.collectAsState()
    LaunchedEffect(sessionExpired) {
        if (sessionExpired) {
            navController.navigate(GodjiDestinations.LOGIN) {
                popUpTo(0) { inclusive = true }
            }
            authRepository.consumeSessionExpired()
        }
    }

    NavHost(
        navController = navController,
        startDestination = if (startLoggedIn) GodjiDestinations.MAIN else GodjiDestinations.LOGIN
    ) {
        composable(GodjiDestinations.LOGIN) {
            LoginScreen(
                onCodeSent = { email ->
                    navController.navigate(GodjiDestinations.verifyEmail(email))
                }
            )
        }
        composable(
            route = GodjiDestinations.VERIFY_EMAIL,
            arguments = listOf(navArgument("email") { type = NavType.StringType })
        ) { backStackEntry ->
            val email = backStackEntry.arguments?.getString("email").orEmpty()
            VerifyEmailScreen(
                email = email,
                onVerified = {
                    navController.navigate(GodjiDestinations.MAIN) {
                        popUpTo(GodjiDestinations.LOGIN) { inclusive = true }
                    }
                }
            )
        }
        composable(
            GodjiDestinations.MAIN,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            MainTabsScreen(navController = navController)
        }
        composable(
            GodjiDestinations.SUPPORT_LIST,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            SupportListScreen(
                onBack = { navController.popBackStack() },
                onOpenTicket = { ticketId -> navController.navigate(GodjiDestinations.supportTicket(ticketId)) },
                onNewTicket = { navController.navigate(GodjiDestinations.SUPPORT_NEW) },
                onOpenFaq = { navController.navigate(GodjiDestinations.SUPPORT_FAQ) }
            )
        }
        composable(
            GodjiDestinations.SUPPORT_NEW,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            NewTicketScreen(
                onBack = { navController.popBackStack() },
                onCreated = { ticketId ->
                    navController.navigate(GodjiDestinations.supportTicket(ticketId)) {
                        popUpTo(GodjiDestinations.SUPPORT_LIST) { inclusive = false }
                    }
                },
                onGoToTicket = { ticketId ->
                    navController.navigate(GodjiDestinations.supportTicket(ticketId)) {
                        popUpTo(GodjiDestinations.SUPPORT_LIST) { inclusive = false }
                    }
                }
            )
        }
        composable(
            route = GodjiDestinations.SUPPORT_TICKET,
            arguments = listOf(navArgument("ticketId") { type = NavType.LongType }),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) { backStackEntry ->
            val ticketId = backStackEntry.arguments?.getLong("ticketId") ?: 0L
            TicketChatScreen(ticketId = ticketId, onBack = { navController.popBackStack() })
        }
        composable(
            GodjiDestinations.SUPPORT_FAQ,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            FaqScreen(onBack = { navController.popBackStack() })
        }
        composable(
            GodjiDestinations.PING_SETTINGS,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            PingSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            GodjiDestinations.LOG_LEVEL,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            LogLevelScreen(onBack = { navController.popBackStack() })
        }
        composable(
            GodjiDestinations.APP_TUNNELING,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None }
        ) {
            AppTunnelingScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Хост четырёх основных вкладок — сам HorizontalPager (свайп) плюс плавающая стеклянная
 *  капсула поверх него (тап). onLoggedOut/onOpenSupport и т.п. по-прежнему толкают через
 *  внешний navController — с ним тут ничего не поменялось, эти экраны просто больше не
 *  отдельные top-level назначения NavHost, а страницы одного и того же. */
@Composable
private fun MainTabsScreen(navController: NavHostController) {
    val tabs = GodjiTab.entries
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    GlassBackdrop {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            // Снизу — место под плавающую капсулу, чтобы прокручиваемый контент вкладок
            // (списки серверов, тарифы, настройки) не прятался под ней навсегда, а
            // останавливался чуть выше — тот же приём, что раньше делал Scaffold(bottomBar=…)
            // через свой padding, просто явно, раз панель теперь не в Scaffold.
            contentPadding = PaddingValues(bottom = 96.dp)
        ) { page ->
            when (tabs[page]) {
                GodjiTab.CONNECT -> ConnectScreen(
                    onOpenPlans = {
                        scope.launch { pagerState.animateScrollToPage(GodjiTab.PLANS.ordinal) }
                    }
                )
                GodjiTab.SERVERS -> ServersScreen()
                GodjiTab.PLANS -> PlansScreen(
                    onOpenSupport = { navController.navigate(GodjiDestinations.SUPPORT_LIST) }
                )
                GodjiTab.SETTINGS -> SettingsScreen(
                    onLoggedOut = {
                        navController.navigate(GodjiDestinations.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenPingSettings = { navController.navigate(GodjiDestinations.PING_SETTINGS) },
                    onOpenLogLevel = { navController.navigate(GodjiDestinations.LOG_LEVEL) },
                    onOpenAppTunneling = { navController.navigate(GodjiDestinations.APP_TUNNELING) },
                    onOpenSupport = { navController.navigate(GodjiDestinations.SUPPORT_LIST) }
                )
            }
        }

        GlassTabBar(
            tabs = tabs,
            selectedIndex = pagerState.currentPage,
            // Во время активного свайпа currentPage уже "подъезжает" к соседней странице —
            // подсвечиваем итоговую цель (targetPage), а не текущую, иначе иконка в панели
            // визуально перескакивает on раньше, чем палец долистает до конца жеста.
            targetIndex = pagerState.targetPage,
            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** Плавающий "остров" вкладок — капсула из стекла (см. godjiGlassBar), не приклеенная к краям
 *  экрана (визуальный язык iOS 26), с отступом снизу под системный жест возврата/навигацию. */
@Composable
private fun GlassTabBar(
    tabs: List<GodjiTab>,
    selectedIndex: Int,
    targetIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 20.dp)
            .height(68.dp)
            .godjiGlassBar(shape = RoundedCornerShape(34.dp))
    ) {
        // "Линза" выбранной вкладки — один скользящий индикатор вместо независимого fade-in/out
        // у каждого таба (как раньше): все 4 вкладки равной ширины, поэтому её offset считается
        // без onGloballyPositioned — просто targetIndex * ширина одной вкладки (maxWidth уже
        // известен из BoxWithConstraints), никакого риска рассинхронизации.
        val tabWidth = maxWidth / tabs.size
        val lensOffset by animateDpAsState(
            targetValue = tabWidth * targetIndex,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 380f),
            label = "tabLensOffset"
        )
        Box(
            Modifier
                .offset(x = lensOffset)
                .width(tabWidth)
                .fillMaxHeight()
                .padding(vertical = 6.dp, horizontal = 10.dp)
                .background(GodjiColors.TealDeep.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
        )
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == targetIndex
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(28.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.15f else 1f,
                        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                        label = "tabIconScale"
                    )
                    Icon(
                        imageVector = if (selected) tab.iconSelected else tab.iconUnselected,
                        contentDescription = tab.label(),
                        tint = if (selected) GodjiColors.TealDeep else GodjiColors.TextSecondary,
                        modifier = Modifier.size(22.dp).scale(scale)
                    )
                    Text(
                        tab.label(),
                        fontSize = 10.5.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (selected) GodjiColors.TealDeep else GodjiColors.TextSecondary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
