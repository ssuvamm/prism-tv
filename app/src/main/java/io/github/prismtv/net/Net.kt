package io.github.prismtv.net

import android.content.Context
import io.github.prismtv.data.DnsProvider
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Shared HTTP stack. Every request in the app (catalog sync, logos and the video streams
 * themselves) resolves hostnames through DNS over HTTPS, Cloudflare by default.
 */
class Net(private val context: Context, initial: DnsProvider) {

    private val bootstrapClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "doh"), 2L * 1024 * 1024))
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .apply { LegacyTls.install(this, context) }
        .build()

    @Volatile
    private var resolver: Dns = build(initial)

    @Volatile
    var provider: DnsProvider = initial
        private set

    private val memo = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            if (isIpLiteral(hostname)) return listOf(InetAddress.getByName(hostname))
            val now = System.currentTimeMillis()
            memo[hostname]?.let { (at, addrs) -> if (now - at < MEMO_TTL_MS) return addrs }
            val result = try {
                resolver.lookup(hostname)
            } catch (e: Exception) {
                // DoH can be blocked on some networks; never leave the user without a stream.
                Dns.SYSTEM.lookup(hostname)
            }
            memo[hostname] = now to result
            return result
        }
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .dns(dns)
        .dispatcher(Dispatcher().apply { maxRequests = 64; maxRequestsPerHost = 12 })
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .apply { LegacyTls.install(this, context) }
        .build()

    val imageClient: OkHttpClient = client.newBuilder()
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun setProvider(p: DnsProvider) {
        if (p == provider) return
        provider = p
        resolver = build(p)
        memo.clear()
    }

    private fun build(p: DnsProvider): Dns {
        val url = p.url ?: return Dns.SYSTEM
        return DnsOverHttps.Builder()
            .client(bootstrapClient)
            .url(url.toHttpUrl())
            .bootstrapDnsHosts(p.bootstrap.map { InetAddress.getByName(it) })
            .includeIPv6(true)
            .resolvePrivateAddresses(true)
            .build()
    }

    private fun isIpLiteral(host: String): Boolean =
        host.contains(':') || IPV4.matches(host)

    companion object {
        private const val MEMO_TTL_MS = 10 * 60 * 1000L
        private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
    }
}
