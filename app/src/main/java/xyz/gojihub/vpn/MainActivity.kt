package xyz.gojihub.vpn

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dagger.hilt.android.AndroidEntryPoint
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
import xyz.gojihub.vpn.ui.theme.GodjiColors
import xyz.gojihub.vpn.ui.theme.GodjiVpnTheme
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

private data class BottomTab(val route: String, val label: String, val icon: String)

@Composable
fun GodjiApp(startLoggedIn: Boolean, authRepository: AuthRepository) {
    // Системную тёмную тему устройства мы всегда игнорировали (GodjiVpnTheme), но верхнюю
    // строку состояния/нижнюю навигационную панель никто раньше не трогал — они держались
    // системных дефолтов (обычно светлых) независимо от переключателя темы в Настройках,
    // из-за чего при включённой тёмной теме приложения строка состояния оставалась светлой.
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
    val tabs = listOf(
        BottomTab(GodjiDestinations.CONNECT, Loc.s.tabHome, "🏠"),
        BottomTab(GodjiDestinations.SERVERS, Loc.s.tabServers, "🌐"),
        BottomTab(GodjiDestinations.PLANS, Loc.s.tabPlans, "💳"),
        BottomTab(GodjiDestinations.SETTINGS, Loc.s.tabSettings, "⚙️"),
    )
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute in tabs.map { it.route }

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

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = GodjiColors.Chip) {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                // Иконка чуть подрастает и "оседает" при выборе вкладки — раньше
                                // переключение было совсем безжизненным (голый текст без иконок
                                // и анимации).
                                val scale by animateFloatAsState(
                                    targetValue = if (selected) 1.22f else 1f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
                                    label = "tabIconScale"
                                )
                                Text(tab.icon, fontSize = 19.sp, modifier = Modifier.scale(scale))
                            },
                            label = { Text(tab.label, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = GodjiColors.TealDeep,
                                unselectedTextColor = GodjiColors.TextSecondary,
                                indicatorColor = GodjiColors.Surface
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (startLoggedIn) GodjiDestinations.CONNECT else GodjiDestinations.LOGIN,
            modifier = Modifier.padding(padding)
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
                        navController.navigate(GodjiDestinations.CONNECT) {
                            popUpTo(GodjiDestinations.LOGIN) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                GodjiDestinations.CONNECT,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) {
                ConnectScreen(
                    onOpenPlans = {
                        navController.navigate(GodjiDestinations.PLANS) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
            composable(
                GodjiDestinations.SERVERS,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) { ServersScreen() }
            composable(
                GodjiDestinations.PLANS,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) {
                PlansScreen()
            }
            composable(
                GodjiDestinations.SETTINGS,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None }
            ) {
                SettingsScreen(
                    onLoggedOut = {
                        navController.navigate(GodjiDestinations.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onOpenPingSettings = { navController.navigate(GodjiDestinations.PING_SETTINGS) },
                    onOpenLogLevel = { navController.navigate(GodjiDestinations.LOG_LEVEL) },
                    onOpenAppTunneling = { navController.navigate(GodjiDestinations.APP_TUNNELING) }
                )
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
}
