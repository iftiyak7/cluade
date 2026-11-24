package com.hayat.app

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hayat.core.designsystem.theme.HayatTheme
import com.hayat.core.model.ThemeMode
import com.hayat.core.navigation.CalendarRoute
import com.hayat.core.navigation.HomeRoute
import com.hayat.core.navigation.OnboardingRoute
import com.hayat.core.navigation.ProgressRoute
import com.hayat.core.navigation.SettingsRoute
import com.hayat.core.ui.LocalFormatter
import com.hayat.core.ui.rememberHayatFormatter
import com.hayat.feature.lock.LockScreen

@Composable
fun HayatRoot(vm: MainViewModel, appVersion: String) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val pending by vm.pendingRoute.collectAsStateWithLifecycle()
    val p = prefs ?: return // splash screen stays until preferences are loaded

    val dark = when (p.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    HayatTheme(darkTheme = dark, highContrast = p.highContrast, dynamicColor = p.dynamicColor, reduceMotion = p.reduceMotion) {
        CompositionLocalProvider(LocalFormatter provides rememberHayatFormatter(p.timeFormat, p.dateFormat, p.digitStyle)) {
            val nav = rememberNavController()
            val entry by nav.currentBackStackEntryAsState()
            val destination = entry?.destination
            val topLevel = TopLevel.entries.firstOrNull { t -> destination?.hasRoute(t.route::class) == true }
            var showAdd by rememberSaveable { mutableStateOf(false) }

            val lockActive = locked && p.onboardingCompleted
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    // While locked, content behind the overlay is neither drawn nor exposed to accessibility services.
                    modifier = if (lockActive) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier,
                    containerColor = MaterialTheme.colorScheme.background,
                    bottomBar = {
                        if (topLevel != null) HayatBottomBar(
                            current = topLevel,
                            onSelect = { t ->
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onAdd = { showAdd = true },
                        )
                    },
                ) { pad ->
                    NavHost(
                        navController = nav,
                        startDestination = if (p.onboardingCompleted) HomeRoute else OnboardingRoute,
                        modifier = Modifier.padding(bottom = pad.calculateBottomPadding()).consumeWindowInsets(pad),
                        // Short, GPU-cheap transitions; none at all when the user asked for reduced motion.
                        enterTransition = { if (p.reduceMotion) EnterTransition.None else fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(tween(260)) { it / 14 } },
                        exitTransition = { if (p.reduceMotion) ExitTransition.None else fadeOut(tween(140)) },
                        popEnterTransition = { if (p.reduceMotion) EnterTransition.None else fadeIn(tween(220, delayMillis = 40)) + slideInHorizontally(tween(260)) { -it / 14 } },
                        popExitTransition = { if (p.reduceMotion) ExitTransition.None else fadeOut(tween(140)) },
                    ) {
                        hayatGraphs(nav, appVersion, onQuickAdd = { showAdd = true })
                    }
                }
                if (showAdd) AddSheet(onDismiss = { showAdd = false }, onNavigate = { route -> showAdd = false; nav.navigate(route) }, islamic = p.islamicModuleEnabled)
                if (lockActive) LockScreen(biometricEnabled = p.appLock.biometricEnabled, onUnlocked = vm::unlock)
            }

            LaunchedEffect(pending, locked) {
                val route = pending ?: return@LaunchedEffect
                if (locked || !p.onboardingCompleted) return@LaunchedEffect
                nav.navigate(route) { launchSingleTop = true }
                vm.consumeRoute()
            }
        }
    }
}

enum class TopLevel(val route: Any) { HOME(HomeRoute), CALENDAR(CalendarRoute()), PROGRESS(ProgressRoute), SETTINGS(SettingsRoute) }
