package app.efir

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

object XmlTvParser {
    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun parse(input: InputStream): Map<String, List<Programme>> {
        val p = Xml.newPullParser()
        p.setInput(input, null)
        val map = HashMap<String, MutableList<Programme>>()
        val now = System.currentTimeMillis()
        var ch: String? = null
        var start = 0L
        var stop = 0L
        var title = ""
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "programme" -> {
                        ch = p.getAttributeValue(null, "channel")
                        start = ts(p.getAttributeValue(null, "start"))
                        stop = ts(p.getAttributeValue(null, "stop"))
                        title = ""
                    }
                    "title" -> if (ch != null && title.isEmpty()) title = p.nextText()
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "programme") {
                val c = ch
                if (c != null && stop > now) {
                    map.getOrPut(c) { mutableListOf() } += Programme(start, stop, title)
                }
                ch = null
            }
            ev = p.next()
        }
        return map
    }

    private fun ts(s: String?): Long = try {
        var t = s!!.trim()
        if (t.length == 14) t += " +0000"
        fmt.parse(t)!!.time
    } catch (e: Exception) {
        0L
    }
}
