package app.efir

data class Channel(
    val id: String,
    val name: String,
    val logo: String?,
    val group: String,
    /** Direkte Stream-URL (M3U-Quelle). Bei der LimeHD-API leer. */
    val url: String,
    /** Kanal-Slug (LimeHD-API). Die Stream-URL wird beim Öffnen geholt. */
    val slug: String? = null,
)

data class Programme(val start: Long, val stop: Long, val title: String)
