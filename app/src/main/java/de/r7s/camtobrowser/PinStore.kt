package de.r7s.camtobrowser

import android.content.Context
import java.security.SecureRandom

/** Speichert PIN und Schutz-Schalter; die PIN ist 6-stellig und wird zufällig erzeugt. */
object PinStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("pin_on", true)

    fun pin(ctx: Context): String {
        val p = prefs(ctx)
        var v = p.getString("pin", null)
        if (v == null || v.length != 6) { v = generate(); p.edit().putString("pin", v).apply() }
        return v
    }

    /** Aktive PIN oder null, wenn der Schutz ausgeschaltet ist. */
    fun active(ctx: Context): String? = if (enabled(ctx)) pin(ctx) else null

    fun regenerate(ctx: Context): String {
        val v = generate()
        prefs(ctx).edit().putString("pin", v).apply()
        return v
    }

    fun setEnabled(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("pin_on", on).apply() }

    private fun generate(): String {
        val r = SecureRandom()
        return (0 until 6).joinToString("") { r.nextInt(10).toString() }
    }
}
