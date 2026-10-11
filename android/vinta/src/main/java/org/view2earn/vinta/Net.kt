package org.view2earn.vinta

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/** One served ad, as returned by POST /vinta/v1/ad. */
internal class VintaAd(
    val serveId: String,
    val format: String,
    val mediaType: String,
    val mediaUrl: String,
    val headline: String?,
    val clickUrl: String,
    val rewardName: String?,
    val rewardAmount: Int?,
    val refreshSec: Int?,
    val test: Boolean,
    // native assets
    val body: String? = null,
    val cta: String? = null,
    val iconUrl: String? = null,
    val advertiserName: String? = null,
) {
    private val loadedAt = System.currentTimeMillis()
    // Blueprint 3.4: creatives expire after about an hour.
    val expired: Boolean get() = System.currentTimeMillis() - loadedAt > 60 * 60 * 1000L

    companion object {
        fun from(j: JSONObject) = VintaAd(
            serveId = j.getString("serveId"),
            format = j.getString("format"),
            mediaType = j.optString("mediaType", "image"),
            mediaUrl = j.getString("mediaUrl"),
            headline = j.optStringOrNull("headline"),
            clickUrl = j.getString("clickUrl"),
            rewardName = j.optStringOrNull("rewardName"),
            rewardAmount = if (j.isNull("rewardAmount") || !j.has("rewardAmount")) null else j.getInt("rewardAmount"),
            refreshSec = if (j.isNull("refreshSec") || !j.has("refreshSec")) null else j.getInt("refreshSec"),
            test = j.optBoolean("test", false),
            body = j.optStringOrNull("body"),
            cta = j.optStringOrNull("cta"),
            iconUrl = j.optStringOrNull("iconUrl"),
            advertiserName = j.optStringOrNull("advertiserName"),
        )
    }
}

private fun JSONObject.optStringOrNull(k: String): String? = if (!has(k) || isNull(k)) null else getString(k)

/** All network work off the main thread; every callback back on it. */
internal object Net {
    private val io = Executors.newFixedThreadPool(3) { r -> Thread(r, "vinta-io").apply { isDaemon = true } }
    private val mainHandler = Handler(Looper.getMainLooper())
    private const val AD_TIMEOUT_MS = 4000 // well under a mediation's ~5s timeout

    fun main(block: () -> Unit) { mainHandler.post { runCatching(block) } }
    fun mainDelayed(ms: Long, block: () -> Unit) { mainHandler.postDelayed({ runCatching(block) }, ms) }
    fun background(block: () -> Unit) = io.execute { runCatching(block) }

    private class Res(val code: Int, val body: JSONObject?)

    private fun call(method: String, path: String, body: JSONObject?, timeoutMs: Int): Res {
        val conn = URL(Vinta.endpoint + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            if (body != null) {
                val text = body.toString()
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                // Signed requests (blueprint 10.1): the signature covers exactly these bytes.
                for ((k, v) in Identity.headers(path.substringBefore("?"), text)) conn.setRequestProperty(k, v)
                conn.outputStream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return Res(code, if (text.isBlank()) null else runCatching { JSONObject(text) }.getOrNull())
        } finally {
            conn.disconnect()
        }
    }

    fun fetchConfig(cb: (VintaConfig?) -> Unit) = background {
        val c = runCatching {
            val r = call("GET", "/vinta/v1/init?appKey=" + java.net.URLEncoder.encode(Vinta.appKey, "UTF-8"), null, 10_000)
            val j = r.body ?: return@runCatching null
            if (r.code != 200) return@runCatching null
            val units = j.getJSONArray("units")
            VintaConfig(
                name = j.getString("name"),
                status = j.getString("status"),
                country = j.optStringOrNull("country"),
                units = (0 until units.length()).map { i ->
                    val u = units.getJSONObject(i)
                    VintaUnit(u.getString("id"), u.getString("name"), u.getString("format"), u.optStringOrNull("rewardName"),
                        if (u.isNull("rewardAmount")) null else u.getInt("rewardAmount"))
                },
            )
        }.getOrNull()
        main { cb(c) }
    }

    /** Ad request. cb gets an ad or an error (NO_FILL on 204). */
    fun requestAd(unitId: String, userId: String?, customData: String?, cb: (VintaAd?, VintaError?) -> Unit) {
        if (!Vinta.isInitialized) return main { cb(null, VintaError(VintaError.INVALID_REQUEST, "Call Vinta.init first")) }
        background {
            val result: Pair<VintaAd?, VintaError?> = try {
                val body = JSONObject()
                    .put("appKey", Vinta.appKey)
                    .put("adUnitId", unitId)
                    .put("deviceId", Vinta.deviceId)
                    .put("test", Vinta.test)
                    .put("context", JSONObject(Vinta.deviceContext()).put("interests", org.json.JSONArray(VintaInterests.get())))
                    .put("signals", JSONObject().put("emulator", Identity.isEmulator))
                    .put("privacy", JSONObject(Vinta.privacy()))
                if (userId != null) body.put("userId", userId)
                if (customData != null) body.put("customData", customData)
                val r = call("POST", "/vinta/v1/ad", body, AD_TIMEOUT_MS)
                when {
                    r.code == 204 -> null to VintaError(VintaError.NO_FILL, "No fill")
                    r.code == 200 && r.body != null -> VintaAd.from(r.body) to null
                    r.code in 400..499 -> null to VintaError(VintaError.INVALID_REQUEST, r.body?.optString("error") ?: "HTTP ${r.code}")
                    else -> null to VintaError(VintaError.NETWORK, "HTTP ${r.code}")
                }
            } catch (t: Throwable) {
                null to VintaError(VintaError.NETWORK, t.message ?: "network error")
            }
            main { cb(result.first, result.second) }
        }
    }

    /**
     * Event (impression / click / complete) with retry + backoff (blueprint 3.4).
     * The server dedups by serve id, so a retry can never bill twice.
     * ponytail: in-memory retries only; add a persisted offline queue + batching
     * when events must survive app restarts.
     */
    fun track(ad: VintaAd, event: String, cb: ((JSONObject?) -> Unit)? = null) = background {
        val body = JSONObject().put("serveId", ad.serveId).put("deviceId", Vinta.deviceId).put("event", event)
        var res: JSONObject? = null
        for (attempt in 0 until 4) {
            val r = runCatching { call("POST", "/vinta/v1/events", body, 10_000) }.getOrNull()
            if (r != null && r.code in 200..499) {
                res = r.body
                break
            }
            Thread.sleep(1000L shl attempt)
        }
        if (cb != null) main { cb(res) }
    }

    fun bitmap(url: String): Bitmap? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            conn.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Pre-cache video to disk (blueprint 3.4). Old files are cleared after a day. */
    fun videoFile(url: String): File? = runCatching {
        val dir = File(Vinta.appContext!!.cacheDir, "vinta").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 24 * 3600_000L }?.forEach { it.delete() }
        val name = MessageDigest.getInstance("SHA-1").digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".mp4"
        val file = File(dir, name)
        if (!file.exists() || file.length() == 0L) {
            val tmp = File(dir, "$name.part")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            try {
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } finally {
                conn.disconnect()
            }
            tmp.renameTo(file)
        }
        file
    }.getOrNull()
}
