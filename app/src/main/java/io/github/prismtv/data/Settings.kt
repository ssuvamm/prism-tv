package io.github.prismtv.data

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeStyle(val label: String, val blurb: String) {
    MATERIAL_YOU("Material You", "Colours from your wallpaper"),
    GLASS("Glass", "Frosted panels over a soft gradient"),
    LIQUID("Liquid", "Apple-style translucent layers"),
    AMOLED("AMOLED", "True black, easy on OLED screens"),
    NEON("Neon", "Dark with electric accents"),
    SUNSET("Sunset", "Warm coral and amber"),
}

@Serializable
enum class DarkMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

@Serializable
enum class DnsProvider(val label: String, val url: String?, val bootstrap: List<String>) {
    CLOUDFLARE(
        "Cloudflare 1.1.1.1", "https://cloudflare-dns.com/dns-query",
        listOf("1.1.1.1", "1.0.0.1", "2606:4700:4700::1111", "2606:4700:4700::1001"),
    ),
    CLOUDFLARE_SECURITY(
        "Cloudflare (blocks malware)", "https://security.cloudflare-dns.com/dns-query",
        listOf("1.1.1.2", "1.0.0.2"),
    ),
    CLOUDFLARE_FAMILY(
        "Cloudflare (family)", "https://family.cloudflare-dns.com/dns-query",
        listOf("1.1.1.3", "1.0.0.3"),
    ),
    GOOGLE("Google", "https://dns.google/dns-query", listOf("8.8.8.8", "8.8.4.4")),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query", listOf("9.9.9.9", "149.112.112.112")),
    SYSTEM("System DNS", null, emptyList()),
}

@Serializable
enum class PlayerChoice(val label: String) {
    BUILT_IN("Built-in player"),
    VLC("VLC"),
    ASK("Ask every time"),
}

@Serializable
enum class UserAgentChoice(val label: String, val value: String) {
    VLC("VLC", "VLC/3.0.21 LibVLC/3.0.21"),
    CHROME(
        "Chrome (Android)",
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36",
    ),
    EXOPLAYER("Prism TV", "PrismTV/1.0 (Linux; Android) ExoPlayer"),
}

@Serializable
data class AppSettings(
    val style: ThemeStyle = ThemeStyle.GLASS,
    val darkMode: DarkMode = DarkMode.SYSTEM,
    val accent: Long? = null,
    val gridView: Boolean = false,
    val dns: DnsProvider = DnsProvider.CLOUDFLARE,
    val preloadSeconds: Int = 8,
    val timeshiftMinutes: Int = 5,
    val autoUpdateHours: Int = 36,
    val showNsfw: Boolean = false,
    val hideGeoBlocked: Boolean = false,
    val hideNot247: Boolean = false,
    val defaultPlayer: PlayerChoice = PlayerChoice.BUILT_IN,
    val userAgent: UserAgentChoice = UserAgentChoice.VLC,
    val autoFallback: Boolean = true,
    val startLandscape: Boolean = true,
    val autoPip: Boolean = true,
    /** 0 = fit, 1 = crop to fill, 2 = stretch. */
    val resizeMode: Int = 0,
    val tvDefaultsApplied: Boolean = false,
)
