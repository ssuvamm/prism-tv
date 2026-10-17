package io.github.prismtv.net

import android.content.Context
import android.os.Build
import io.github.prismtv.R
import okhttp3.OkHttpClient
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Android 6.0 and 7.0 (many TV boxes) don't trust the Let's Encrypt roots, which a large share of
 * stream and logo servers use. On those versions the bundled ISRG Root X1/X2 certificates are trusted
 * in addition to the system store. Newer Android versions are left untouched.
 */
object LegacyTls {

    fun install(builder: OkHttpClient.Builder, context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) return
        runCatching {
            val tm = trustManager(context)
            val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
            builder.sslSocketFactory(ssl.socketFactory, tm)
        }
    }

    private fun trustManager(context: Context): X509TrustManager {
        val system = x509(null)
        val cf = CertificateFactory.getInstance("X.509")
        val bundled = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            listOf(R.raw.isrg_root_x1, R.raw.isrg_root_x2).forEachIndexed { i, res ->
                context.resources.openRawResource(res).use { setCertificateEntry("isrg-$i", cf.generateCertificate(it)) }
            }
        }
        val extra = x509(bundled)
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
                system.checkClientTrusted(chain, authType)

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    system.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    extra.checkServerTrusted(chain, authType)
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + extra.acceptedIssuers
        }
    }

    private fun x509(store: KeyStore?): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(store)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }
}
