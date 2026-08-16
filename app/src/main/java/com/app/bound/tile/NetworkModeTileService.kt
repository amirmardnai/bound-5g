package com.app.bound.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.app.bound.network.BoundNetworkMode
import com.app.bound.network.ShizukuBandManager
import com.app.bound.network.SwitchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NetworkModeTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var currentMode = BoundNetworkMode.NR_LTE

    override fun onStartListening() {
        super.onStartListening()
        renderTile()
    }

    override fun onClick() {
        super.onClick()
        val next = when (currentMode) {
            BoundNetworkMode.NR_ONLY -> BoundNetworkMode.NR_LTE
            BoundNetworkMode.NR_LTE -> BoundNetworkMode.LTE_ONLY
            BoundNetworkMode.LTE_ONLY -> BoundNetworkMode.NR_ONLY
        }
        currentMode = next
        renderTile()

        scope.launch {
            val manager = ShizukuBandManager(applicationContext)
            val res = manager.switchNetworkMode(next)
            CoroutineScope(Dispatchers.Main).launch {
                when (res) {
                    is SwitchResult.Success -> Toast.makeText(applicationContext, "✅ ${next.name}", Toast.LENGTH_SHORT).show()
                    is SwitchResult.Failure -> Toast.makeText(applicationContext, "⚠️ ${res.reason}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun renderTile() {
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "5G Mode Switcher"
            subtitle = when (currentMode) {
                BoundNetworkMode.NR_ONLY -> "1. 5G SA"
                BoundNetworkMode.NR_LTE -> "2. 5G NSA"
                BoundNetworkMode.LTE_ONLY -> "3. 4G LTE"
            }
            updateTile()
        }
    }
}
