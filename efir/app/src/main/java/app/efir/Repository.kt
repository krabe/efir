package app.efir

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.GZIPInputStream

class Repository(ctx: Context) {
    private val http = OkHttpClient()
    private val cacheFile = File(ctx.cacheDir, "channels.json")

    /** Zwischengespeicherte Senderliste für schnellen Start. */
    fun cached(): List<Channel> = try {
        if (!cacheFile.exists()) emptyList() else {
            val a = JSONArray(cacheFile.readText())
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Channel(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    logo = o.optString("logo").ifEmpty { null },
                    group = o.getString("group"),
                    url = o.optString("url"),
                    slug = o.optString("slug").ifEmpty { null },
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(list: List<Channel>) {
        val a = JSONArray()
        list.forEach {
            a.put(
                JSONObject()
                    .put("id", it.id).put("name", it.name).put("logo", it.logo ?: "")
                    .put("group", it.group).put("url", it.url).put("slug", it.slug ?: "")
            )
        }
        cacheFile.writeText(a.toString())
    }

    suspend fun refreshLime(api: LimeApi): List<Channel> =
        api.channels().also { withContext(Dispatchers.IO) { save(it) } }

    suspend fun refreshM3u(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val text = http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            r.body!!.string()
        }
        M3uParser.parse(text).also { save(it) }
    }

    suspend fun loadEpg(url: String): Map<String, List<Programme>> = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val raw = r.body!!.byteStream()
            val stream = if (url.endsWith(".gz")) GZIPInputStream(raw) else raw
            XmlTvParser.parse(stream)
        }
    }
}
