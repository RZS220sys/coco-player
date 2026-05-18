package com.player.coco.ui.appearance

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.player.coco.R

data class AppAppearancePreset(
    val id: String,
    val labelRes: Int,
    val aliasClassName: String,
)

object AppAppearancePresets {
    const val DEFAULT_ID = "coco"

    val all = listOf(
        AppAppearancePreset(
            id = DEFAULT_ID,
            labelRes = R.string.launcher_name_coco,
            aliasClassName = "com.player.coco.launcher.CocoLauncher",
        ),
        AppAppearancePreset(
            id = "coco_play",
            labelRes = R.string.launcher_name_coco_play,
            aliasClassName = "com.player.coco.launcher.CocoPlayLauncher",
        ),
        AppAppearancePreset(
            id = "cucu",
            labelRes = R.string.launcher_name_cucu,
            aliasClassName = "com.player.coco.launcher.CucuLauncher",
        ),
        AppAppearancePreset(
            id = "music_vibe",
            labelRes = R.string.launcher_name_music_vibe,
            aliasClassName = "com.player.coco.launcher.MusicVibeLauncher",
        ),
        AppAppearancePreset(
            id = "coco_fa",
            labelRes = R.string.launcher_name_coco_fa,
            aliasClassName = "com.player.coco.launcher.CocoFaLauncher",
        ),
        AppAppearancePreset(
            id = "broccoli",
            labelRes = R.string.launcher_name_broccoli,
            aliasClassName = "com.player.coco.launcher.BroccoliLauncher",
        ),
    )

    fun find(id: String): AppAppearancePreset {
        return all.firstOrNull { it.id == id } ?: all.first()
    }

    fun apply(context: Context, presetId: String) {
        val packageManager = context.packageManager
        val selected = find(presetId)

        setAliasState(
            packageManager = packageManager,
            component = ComponentName(context.packageName, selected.aliasClassName),
            enabled = true,
        )

        all.filterNot { it.id == selected.id }.forEach { preset ->
            setAliasState(
                packageManager = packageManager,
                component = ComponentName(context.packageName, preset.aliasClassName),
                enabled = false,
            )
        }
    }

    private fun setAliasState(
        packageManager: PackageManager,
        component: ComponentName,
        enabled: Boolean,
    ) {
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        packageManager.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
    }
}
