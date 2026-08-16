package com.app.bound

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.bound.network.BoundNetworkMode
import com.app.bound.network.ShizukuBandManager
import com.app.bound.network.TelemetryEngine
import com.app.bound.screens.AboutScreen
import com.app.bound.screens.BandGuideScreen
import com.app.bound.screens.DashboardScreen
import com.app.bound.screens.SettingsScreen
import com.app.bound.ui.components.BoundBottomNav
import com.app.bound.ui.theme.BoundTheme
import com.app.bound.util.AppLogger
import com.app.bound.util.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var shizukuManager: ShizukuBandManager
    private lateinit var telemetryEngine: TelemetryEngine
    private lateinit var prefs: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppLogger.i("MainActivity", "Bound MainActivity initialized")

        prefs = AppPreferences(applicationContext)
        shizukuManager = ShizukuBandManager(applicationContext)
        telemetryEngine = TelemetryEngine(applicationContext)

        // Handle deep links: bound://switch?mode=NR_ONLY
        intent?.data?.let { uri ->
            uri.getQueryParameter("mode")?.let { modeParam ->
                val mode = runCatching { BoundNetworkMode.valueOf(modeParam.uppercase()) }.getOrNull()
                if (mode != null) {
                    CoroutineScope(Dispatchers.Main).launch {
                        shizukuManager.switchNetworkMode(mode)
                    }
                }
            }
        }

        setContent {
            BoundTheme(prefs = prefs) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainAppScaffold(
                        prefs = prefs,
                        telemetryEngine = telemetryEngine,
                        shizukuManager = shizukuManager,
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shizukuManager.unbind()
        telemetryEngine.stopPolling()
    }
}

@Composable
fun MainAppScaffold(
    prefs: AppPreferences,
    telemetryEngine: TelemetryEngine,
    shizukuManager: ShizukuBandManager,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "dashboard"

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController = navController,
            startDestination = "dashboard",
            modifier = Modifier.fillMaxSize(),
        ) {
            composable("dashboard") {
                DashboardScreen(
                    prefs = prefs,
                    telemetryEngine = telemetryEngine,
                    shizukuManager = shizukuManager,
                )
            }
            composable("bands") {
                BandGuideScreen(shizukuManager = shizukuManager)
            }
            composable("settings") {
                SettingsScreen(prefs = prefs, shizukuManager = shizukuManager)
            }
            composable("about") {
                AboutScreen()
            }
        }

        BoundBottomNav(
            currentRoute = currentRoute,
            onNavigate = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }
}
