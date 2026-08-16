package com.app.bound

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.bound.network.BoundNetworkMode
import com.app.bound.network.ShizukuBandManager
import com.app.bound.network.TelemetryEngine
import com.app.bound.screens.*
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
                // Request Cellular & Location permissions for reading cell towers
                val permLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) {
                    telemetryEngine.refreshNow()
                }

                LaunchedEffect(Unit) {
                    val permissions = arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE,
                    )
                    val needed = permissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (needed.isNotEmpty()) {
                        permLauncher.launch(needed.toTypedArray())
                    }
                }

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
            composable("scanner") {
                ScannerScreen(
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
