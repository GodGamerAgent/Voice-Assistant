package com.example.service

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.example.MainActivity
import com.example.R

class VoiceAssistTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val isRunning = OverlayService.isRunning.value
        if (isRunning) {
            OverlayService.stop(this)
            updateTileState(false)
        } else {
            // Check overlay permission
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Please grant 'Display over other apps' first", Toast.LENGTH_LONG).show()
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pendingIntent)
                } else {
                    @Suppress("DEPRECATION")
                    startActivityAndCollapse(intent)
                }
                return
            }

            OverlayService.start(this)
            updateTileState(true)
        }
    }

    private fun updateTileState(forcedActive: Boolean? = null) {
        val tile = qsTile ?: return
        val active = forcedActive ?: OverlayService.isRunning.value

        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_name)
        tile.icon = android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_qs_voice_assist)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (active) getString(R.string.tile_active) else getString(R.string.tile_inactive)
        }

        tile.updateTile()
    }
}
