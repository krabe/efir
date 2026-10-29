package app.efir

object M3uParser {
    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parse(text: String): List<Channel> {
        val out = mutableListOf<Channel>()
        var info: String? = null
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.startsWith("#EXTINF")) {
                info = line
            } else if (line.isNotEmpty() && !line.startsWith("#") && info != null) {
                val a = attr.findAll(info).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
                val name = info.substringAfterLast(',').trim().ifEmpty { a["tvg-name"] ?: line }
                out += Channel(
                    id = a["tvg-id"].orEmpty().ifEmpty { name },
                    name = name,
                    logo = a["tvg-logo"]?.ifEmpty { null },
                    group = a["group-title"].orEmpty().ifEmpty { "Ohne Gruppe" },
                    url = line,
                )
                info = null
            }
        }
        return out
    }
}
