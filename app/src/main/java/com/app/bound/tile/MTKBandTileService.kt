package com.app.bound.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.app.bound.network.MTKBandResolver
import com.app.bound.network.ShizukuBandManager

class MTKBandTileService : TileService() {

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
        MTKBandResolver.launchFirstWorking(this, MTKBandResolver.MTK_BAND_COMPONENTS, shizuku) { success, _ ->
            qsTile?.apply {
                state = if (success) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                subtitle = if (success) "Opened MTK" else "Failed"
                updateTile()
            }
        }
    }
}
