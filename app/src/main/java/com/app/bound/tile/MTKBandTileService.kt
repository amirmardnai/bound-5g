package com.app.bound.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.app.bound.network.MTKBandResolver
import com.app.bound.network.ShizukuBandManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MTKBandTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = "MTK BandMode"
            subtitle = "Open Band Select"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val shizuku = ShizukuBandManager(applicationContext)
        scope.launch {
            MTKBandResolver.launchFirstWorking(this@MTKBandTileService, MTKBandResolver.MTK_BAND_COMPONENTS, shizuku) { success, _ ->
                qsTile?.apply {
                    state = if (success) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    subtitle = if (success) "Opened MTK" else "Failed"
                    updateTile()
                }
            }
        }
    }
}
