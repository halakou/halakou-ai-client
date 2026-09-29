package com.example.halakou.data.security

import android.os.Build
import okhttp3.CertificatePinner
import java.io.File

/**
 * Enterprise security configurations:
 * - SSL Pinning for Cloudflare Edge Proxy connections.
 * - Device Integrity & Anti-Tamper inspection (preventing API scraping bots).
 */
object SecurityConfig {

    /**
     * Builds strict CertificatePinner for halakou Cloudflare edge workers.
     */
    fun createCertificatePinner(): CertificatePinner {
        return CertificatePinner.Builder()
            // Cloudflare Global Root CA & DigiCert Pins
            .add("*.workers.dev", "sha256/yDptvIkmfHsmO/F1Wc/7vB5s1/M8t4+yF8m3n8hQ5qA=")
            .add("api.atria-asi.ai", "sha256/47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU=")
            .build()
    }

    /**
     * Evaluates device integrity to protect free APIs from automated exploitation.
     */
    fun isDeviceTamperedOrRooted(): Boolean {
        // 1. Check for test-keys build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }

        // 2. Check for standard su binary paths
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        for (path in paths) {
            if (File(path).exists()) return true
        }

        return false
    }
}
