package org.view2earn.vinta

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID

/**
 * Per-install signing identity (blueprint 10.1, request signing). Same scheme
 * as the Web SDK and convex/vinta/signing.ts:
 * - ECDSA P-256 key, private key in the Android Keystore (API 23+); on API
 *   21-22 a software key in app-private storage
 * - deviceId = first 32 hex of SHA-256(public key SPKI)
 * - each request signs  ts \n nonce \n path \n body
 * If anything fails the SDK falls back to unsigned requests (served, never billed).
 */
internal object Identity {
    private const val ALIAS = "vinta_device_key"
    private const val PREFS = "vinta"

    private class Keys(val spkiB64: String, val privateKey: PrivateKey, val deviceId: String)

    private val keys: Keys? by lazy { runCatching { loadOrCreate() }.getOrNull() }

    val signing: Boolean get() = keys != null

    val deviceId: String by lazy {
        keys?.deviceId ?: run {
            val prefs = Vinta.appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs?.getString("device_id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
                prefs?.edit()?.putString("device_id", it)?.apply()
            }
        }
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun loadOrCreate(): Keys {
        val ctx = Vinta.appContext ?: error("not initialized")
        val (spki, priv) = if (Build.VERSION.SDK_INT >= 23) keystoreKey() else softwareKey(ctx)
        return Keys(b64(spki), priv, hex(MessageDigest.getInstance("SHA-256").digest(spki)).take(32))
    }

    private fun keystoreKey(): Pair<ByteArray, PrivateKey> {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
            gen.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            gen.generateKeyPair()
        }
        val entry = ks.getEntry(ALIAS, null) as KeyStore.PrivateKeyEntry
        return entry.certificate.publicKey.encoded to entry.privateKey
    }

    // API 21-22: no EC support in the Keystore. Key file in app-private storage.
    private fun softwareKey(ctx: Context): Pair<ByteArray, PrivateKey> {
        val pubFile = File(ctx.noBackupFilesDirCompat(), "vinta_pub.der")
        val privFile = File(ctx.noBackupFilesDirCompat(), "vinta_priv.der")
        if (!pubFile.exists() || !privFile.exists()) {
            val gen = KeyPairGenerator.getInstance("EC")
            gen.initialize(ECGenParameterSpec("secp256r1"))
            val kp = gen.generateKeyPair()
            pubFile.writeBytes(kp.public.encoded)
            privFile.writeBytes(kp.private.encoded)
        }
        val priv = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(privFile.readBytes()))
        return pubFile.readBytes() to priv
    }

    private fun Context.noBackupFilesDirCompat(): File = if (Build.VERSION.SDK_INT >= 21) noBackupFilesDir else filesDir

    /** Headers for a signed request, or empty when signing isn't available. */
    fun headers(path: String, body: String): Map<String, String> {
        val k = keys ?: return emptyMap()
        return runCatching {
            val ts = System.currentTimeMillis().toString()
            val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }.let(::hex)
            val sig = Signature.getInstance("SHA256withECDSA").run {
                initSign(k.privateKey)
                update("$ts\n$nonce\n$path\n$body".toByteArray(Charsets.UTF_8))
                sign() // DER; the server accepts DER and raw
            }
            mapOf("X-Vinta-Key" to k.spkiB64, "X-Vinta-Ts" to ts, "X-Vinta-Nonce" to nonce, "X-Vinta-Sig" to b64(sig))
        }.getOrDefault(emptyMap())
    }

    /**
     * Emulator heuristics (blueprint 10.1 SIVT signal). Emulator traffic is
     * served but never billed. ponytail: build-property heuristics only; add
     * sensor/telephony checks if farms start spoofing these.
     */
    val isEmulator: Boolean by lazy {
        val f = Build.FINGERPRINT.lowercase()
        val m = Build.MODEL.lowercase()
        f.startsWith("generic") || f.startsWith("unknown") || f.contains("emulator") || f.contains("sdk_gphone") ||
            m.contains("emulator") || m.contains("android sdk built for") ||
            Build.HARDWARE in setOf("goldfish", "ranchu", "vbox86") ||
            Build.PRODUCT.lowercase().let { it.contains("sdk") || it.contains("vbox") || it.contains("emulator") } ||
            Build.MANUFACTURER.contains("Genymotion", ignoreCase = true)
    }
}
