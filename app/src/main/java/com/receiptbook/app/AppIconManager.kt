package com.receiptbook.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Lets the person pick the launcher icon from designs bundled with the app. Android can't swap an
 * installed app's icon directly, so the manifest declares one <activity-alias> per design (all
 * pointing at MainActivity) and we enable exactly one of them at a time.
 */
object AppIconManager {
    class Option(val id: String, val alias: String, val nameRes: Int, val previewRes: Int)

    val options = listOf(
        Option("classic", ".IconClassic", R.string.icon_classic, R.drawable.ic_icon_classic),
        Option("dark", ".IconDark", R.string.icon_dark, R.drawable.ic_icon_dark),
        Option("gold", ".IconGold", R.string.icon_gold, R.drawable.ic_icon_gold),
        Option("blue", ".IconBlue", R.string.icon_blue, R.drawable.ic_icon_blue),
    )

    private fun component(ctx: Context, o: Option) = ComponentName(ctx.packageName, ctx.packageName + o.alias)

    /** The design currently active, read from the system itself rather than remembered separately. */
    fun current(ctx: Context): String {
        val pm = ctx.packageManager
        options.forEach {
            if (pm.getComponentEnabledSetting(component(ctx, it)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) return it.id
        }
        return options.first().id // nothing explicitly enabled = the manifest default (classic)
    }

    /** Enables [id] first and only then disables the rest, so there is never a moment with no launcher entry. */
    fun set(ctx: Context, id: String) {
        val pm = ctx.packageManager
        val chosen = options.firstOrNull { it.id == id } ?: return
        pm.setComponentEnabledSetting(component(ctx, chosen), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        options.filter { it !== chosen }.forEach {
            pm.setComponentEnabledSetting(component(ctx, it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
