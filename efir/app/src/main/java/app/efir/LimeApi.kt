package app.efir

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.IOException
import kotlin.random.Random

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * Port von limehd_core.py (ohne Proxy-Unterstützung).
 * Die Schnittstelle ist inoffiziell und kann sich jederzeit ändern.
 */
class LimeApi(
    private val deviceId: String,
    private val region: String,
    private val tz: String,
) {
    companion object {
        const val BASE = "https://pl.iptv2021.com/api/v4"
        private const val REFERER = "https://limehd.tv/"
        private const val UA =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/156.0.0.0 Safari/537.36"

        fun newDeviceId(): String =
            "${Random.nextInt(100_000, 999_999)}." +
                "${Random.nextLong(10_000_000_000L, 99_999_999_999L)}-" +
                "${System.currentTimeMillis()}"
    }

    /** Header für API, Master-Playlist und Segmente. */
    val headers: Map<String, String> = mapOf(
        "User-Agent" to UA,
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "ru-RU,ru;q=0.9,en;q=0.8",
        "Referer" to REFERER,
        "x-lhd-agent" to """{"platform":"web","app":"limehd.tv","device_id":"$deviceId"}""",
    )

    private val http = OkHttpClient()

    private suspend fun getJson(path: String, params: Map<String, String>): Any {
        val url = (BASE + path).toHttpUrl().newBuilder()
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        var last: Exception? = null
        for (attempt in 0 until 3) {
            try {
                val req = Request.Builder().url(url)
                    .apply { headers.forEach { (k, v) -> header(k, v) } }
                    .build()
                http.newCall(req).execute().use { r ->
                    val body = r.body?.string().orEmpty()
                    val retryable = r.code in listOf(429, 500, 502, 503, 504)
                    if (retryable && attempt < 2) {
                        last = IOException("HTTP ${r.code}")
                    } else {
                        if (!r.isSuccessful) throw ApiException(r.code, body.take(200))
                        return JSONTokener(body).nextValue()
                    }
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: IOException) {
                last = e
            }
            delay(1000L * (attempt + 1))
        }
        throw IOException("Netzwerkfehler: ${last?.message}")
    }

    suspend fun channels(): List<Channel> = withContext(Dispatchers.IO) {
        val cats = try { categories() } catch (_: Exception) { emptyMap() }
        val seen = HashSet<String>()
        val out = ArrayList<Channel>()
        var page = 1
        while (page <= 20) {
            val data = getJson(
                "/playlist",
                mapOf("region" to region, "tz" to tz, "limit" to "1000", "page" to page.toString()),
            ) as? JSONObject ?: throw IOException("Unerwartete Antwort der Senderliste")
            val arr = data.optJSONArray("channels") ?: break
            var added = 0
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val addr = o.optString("address")
                if (addr.isEmpty() || !seen.add(addr)) continue
                out += toChannel(o, addr, cats)
                added++
            }
            if (added == 0) break
            page++
        }
        out
    }

    /** Holt die temporäre Master-Playlist-URL eines Senders. */
    suspend fun streamUrl(slug: String): String = withContext(Dispatchers.IO) {
        val o = try {
            getJson(
                "/stream/${android.net.Uri.encode(slug)}",
                mapOf(
                    "epg" to "0",
                    "podcasts" to "0",
                    "installts" to System.currentTimeMillis().toString(),
                    "region" to region,
                    "tz" to tz,
                ),
            )
        } catch (e: ApiException) {
            throw IOException(
                when (e.code) {
                    404 -> "Sender nicht gefunden (404)"
                    403 -> "API verweigert den Zugriff (403)"
                    423 -> "Zugriff gesperrt (423)"
                    else -> "HTTP ${e.code}: ${e.message}"
                }
            )
        } as? JSONObject ?: throw IOException("Unerwartete Antwort der API")

        if (o.optInt("status") == 404 || o.optString("name") == "Not Found") {
            throw IOException("Sender nicht gefunden")
        }
        if (!o.optBoolean("available", false)) {
            throw IOException("Sender nicht verfügbar (Anmeldung, Abo oder Region erforderlich)")
        }
        val url = o.optString("url").trim()
        if (url.isEmpty()) throw IOException("API lieferte keine Stream-URL")
        url
    }

    private suspend fun categories(): Map<Int, String> {
        val a = getJson("/categories", emptyMap()) as? JSONArray ?: return emptyMap()
        val m = HashMap<Int, String>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            if (!o.has("id")) continue
            m[o.optInt("id")] = firstString(o, "name_ru", "name_en", "identifier")
        }
        return m
    }

    // ANNAHME: Die Feldnamen von Name, Logo und Kategorie sind geraten und
    // werden tolerant mit mehreren Kandidaten gelesen. Mit einer echten
    // Beispielantwort lässt sich das exakt machen.
    private fun toChannel(o: JSONObject, addr: String, cats: Map<Int, String>) = Channel(
        id = addr,
        name = firstString(o, "name_ru", "name_en", "name").ifEmpty { addr },
        logo = firstString(o, "image", "logo", "logo_url", "icon").ifEmpty { null },
        group = groupOf(o, cats),
        url = "",
        slug = addr,
    )

    private fun groupOf(o: JSONObject, cats: Map<Int, String>): String {
        for (k in listOf("categories", "category_ids", "category_id", "category")) {
            val v = o.opt(k) ?: continue
            val first: Any? = if (v is JSONArray) (if (v.length() > 0) v.opt(0) else null) else v
            when (first) {
                is Number -> cats[first.toInt()]?.let { return it }
                is JSONObject -> firstString(first, "name_ru", "name_en")
                    .takeIf { it.isNotEmpty() }?.let { return it }
            }
        }
        return "Sonstige"
    }

    private fun firstString(o: JSONObject, vararg keys: String): String =
        keys.map { o.optString(it) }.firstOrNull { it.isNotEmpty() && it != "null" } ?: ""
}
