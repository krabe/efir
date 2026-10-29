package app.efir

import android.content.Context

class Settings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("efir", Context.MODE_PRIVATE)

    /** "limehd" oder "m3u" */
    var source: String
        get() = sp.getString("source", "limehd") ?: "limehd"
        set(v) = sp.edit().putString("source", v).apply()

    var region: String
        get() = sp.getString("region", "77") ?: "77"
        set(v) = sp.edit().putString("region", v).apply()

    var tz: String
        get() = sp.getString("tz", "3") ?: "3"
        set(v) = sp.edit().putString("tz", v).apply()

    val deviceId: String
        get() = sp.getString("device_id", null)
            ?: LimeApi.newDeviceId().also { sp.edit().putString("device_id", it).apply() }

    var playlistUrl: String
        get() = sp.getString("playlist", "") ?: ""
        set(v) = sp.edit().putString("playlist", v).apply()

    var epgUrl: String
        get() = sp.getString("epg", "") ?: ""
        set(v) = sp.edit().putString("epg", v).apply()

    /** 0 = Auto, sonst maximale Videohöhe in Pixeln. */
    var maxHeight: Int
        get() = sp.getInt("max_height", 0)
        set(v) = sp.edit().putInt("max_height", v).apply()
}
