package com.taxipay.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Recorded-voice pack: short clips (16-bit mono 16 kHz) joined together to say a payment. */
object Voice {
    const val BASE = "https://wingame-sgma.onrender.com/taxipay/voice/"
    const val RATE = 16000
    @Volatile private var syncing = false

    private val enWords = listOf("one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
    private val enTens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
    private val amUnits = listOf("አንድ", "ሁለት", "ሶስት", "አራት", "አምስት", "ስድስት", "ሰባት", "ስምንት", "ዘጠኝ")
    private val amTens = listOf("ሃያ", "ሰላሳ", "አርባ", "ሃምሳ", "ስልሳ", "ሰባ", "ሰማንያ", "ዘጠና")

    /** Every clip to record, in order: key -> what to say. */
    fun prompts(lang: String): LinkedHashMap<String, String> {
        val m = LinkedHashMap<String, String>()
        if (lang == "en") {
            enWords.forEachIndexed { i, w -> m["n${i + 1}"] = w }
            enTens.forEachIndexed { i, w -> m["t${(i + 2) * 10}"] = w }
            m["hundred"] = "hundred"; m["thousand"] = "thousand"; m["million"] = "million"
            m["sent"] = "sent"; m["birr"] = "birr"; m["received"] = "received"; m["and"] = "and"; m["cents"] = "cents"
        } else {
            amUnits.forEachIndexed { i, w -> m["n${i + 1}"] = w }
            m["ten"] = "አስር"; m["teen"] = "አስራ"
            amTens.forEachIndexed { i, w -> m["t${(i + 2) * 10}"] = w }
            m["hundred"] = "መቶ"; m["thousand"] = "ሺህ"; m["million"] = "ሚሊዮን"
            m["sent"] = "ልኳል"; m["birr"] = "ብር"; m["received"] = "ደርሷል"; m["and"] = "እና"; m["cents"] = "ሳንቲም"
        }
        return m
    }

    fun file(c: Context, lang: String, key: String) = File(c.filesDir, "voice/$lang/$key.pcm")
    fun has(c: Context, lang: String, key: String) = file(c, lang, key).let { it.exists() && it.length() > 0 }
    fun count(c: Context, lang: String) = prompts(lang).keys.count { has(c, lang, it) }

    fun status(c: Context): String {
        val en = count(c, "en"); val am = count(c, "am")
        if (en == 0 && am == 0) return "Studio voice: not downloaded yet (using the phone voice)"
        return "Studio voice: English $en/${prompts("en").size}  ·  አማርኛ $am/${prompts("am").size}"
    }

    private fun enNum(n: Long): List<String> {
        val out = ArrayList<String>()
        fun below(x: Int) {
            var r = x
            if (r >= 100) { out.add("n${r / 100}"); out.add("hundred"); r %= 100 }
            if (r >= 20) { out.add("t${r / 10 * 10}"); r %= 10; if (r > 0) out.add("n$r") } else if (r > 0) out.add("n$r")
        }
        var r = n
        if (r >= 1000000L) { below((r / 1000000L).toInt()); out.add("million"); r %= 1000000L }
        if (r >= 1000L) { below((r / 1000L).toInt()); out.add("thousand"); r %= 1000L }
        if (r > 0) below(r.toInt())
        return out
    }

    private fun amNum(n: Long): List<String> {
        val out = ArrayList<String>()
        fun below(x: Int) {
            var r = x
            if (r >= 100) { val h = r / 100; if (h > 1) out.add("n$h"); out.add("hundred"); r %= 100 }
            if (r >= 20) { out.add("t${r / 10 * 10}"); r %= 10; if (r > 0) out.add("n$r") }
            else if (r == 10) out.add("ten")
            else if (r > 10) { out.add("teen"); out.add("n${r - 10}") }
            else if (r > 0) out.add("n$r")
        }
        var r = n
        if (r >= 1000000L) { below((r / 1000000L).toInt()); out.add("million"); r %= 1000000L }
        if (r >= 1000L) { below((r / 1000L).toInt()); out.add("thousand"); r %= 1000L }
        if (r > 0) below(r.toInt())
        return out
    }

    /** The clips that say this payment, or null if any clip is missing (then the phone voice is used). */
    fun keysFor(c: Context, lang: String, pay: Pay): List<String>? {
        var whole = Math.floor(pay.amount).toLong()
        var cents = Math.round((pay.amount - whole) * 100).toInt()
        if (cents >= 100) { whole += 1; cents = 0 }
        if (whole <= 0L || whole >= 1000000000L) return null
        val named = pay.name != null
        val k = ArrayList<String>()
        if (lang == "am") {
            k.addAll(amNum(whole)); k.add("birr")
            if (cents > 0) { k.add("and"); k.addAll(amNum(cents.toLong())); k.add("cents") }
            k.add(if (named) "sent" else "received")
        } else {
            if (named) k.add("sent")
            k.addAll(enNum(whole)); k.add("birr")
            if (cents > 0) { k.add("and"); k.addAll(enNum(cents.toLong())); k.add("cents") }
            if (!named) k.add("received")
        }
        return if (k.all { has(c, lang, it) }) k else null
    }

    fun play(c: Context, lang: String, keys: List<String>, done: () -> Unit) {
        val out = ByteArrayOutputStream()
        val gap = ByteArray(RATE / 1000 * 40 * 2)
        for ((i, key) in keys.withIndex()) { if (i > 0) out.write(gap); out.write(file(c, lang, key).readBytes()) }
        playPcm(out.toByteArray(), done)
    }

    fun playPcm(data: ByteArray, done: () -> Unit) {
        if (data.isEmpty()) { done(); return }
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(data.size).setTransferMode(AudioTrack.MODE_STATIC).build()
            track.write(data, 0, data.size)
            val h = Handler(Looper.getMainLooper())
            var finished = false
            fun finish() {
                if (finished) return
                finished = true
                try { track.release() } catch (e: Exception) { }
                done()
            }
            track.setNotificationMarkerPosition(data.size / 2)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) { finish() }
                override fun onPeriodicNotification(t: AudioTrack?) {}
            }, h)
            track.play()
            h.postDelayed({ finish() }, (data.size / 2) * 1000L / RATE + 3000L)
        } catch (e: Exception) { done() }
    }

    /** Cuts silence at both ends and evens out the loudness. Empty result = too quiet or too short. */
    fun trimNormalize(pcm: ByteArray): ByteArray {
        val n = pcm.size / 2
        if (n < 1600) return ByteArray(0)
        val s = IntArray(n) { ((pcm[it * 2 + 1].toInt() shl 8) or (pcm[it * 2].toInt() and 0xFF)).toShort().toInt() }
        var peak = 0
        for (v in s) peak = maxOf(peak, Math.abs(v))
        if (peak < 600) return ByteArray(0)
        val th = maxOf(500, peak / 12)
        var a = 0
        while (a < n && Math.abs(s[a]) < th) a++
        var b = n - 1
        while (b > a && Math.abs(s[b]) < th) b--
        a = maxOf(0, a - 800); b = minOf(n - 1, b + 1280)
        val gain = minOf(8.0, 26000.0 / peak)
        val out = ByteArray((b - a + 1) * 2)
        for (i in a..b) {
            val v = (s[i] * gain).toInt().coerceIn(-32768, 32767)
            out[(i - a) * 2] = (v and 0xFF).toByte()
            out[(i - a) * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun http(url: String, method: String = "GET", body: ByteArray? = null, admin: String? = null): ByteArray? {
        return try {
            val h = URL(url).openConnection() as HttpURLConnection
            h.connectTimeout = 20000; h.readTimeout = 60000; h.requestMethod = method
            if (admin != null) h.setRequestProperty("x-admin-key", admin)
            if (body != null) { h.doOutput = true; h.setRequestProperty("Content-Type", "application/octet-stream"); h.outputStream.use { it.write(body) } }
            if (h.responseCode in 200..299) h.inputStream.use { it.readBytes() } else null
        } catch (e: Exception) { null }
    }

    fun upload(c: Context, lang: String, key: String, pcm: ByteArray): Boolean =
        http(BASE + "upload?lang=$lang&key=$key", "POST", pcm, Prefs.sp(c).getString("adminKey", "") ?: "") != null

    fun syncIfDue(c: Context) {
        if (System.currentTimeMillis() - Prefs.sp(c).getLong("vsync", 0L) > 6 * 3600 * 1000L) sync(c)
    }

    /** Downloads the studio voice from the server once; afterwards it works offline. */
    fun sync(c: Context) {
        if (syncing) return
        syncing = true
        Thread { try { doSync(c) } catch (e: Exception) { } finally { syncing = false } }.start()
    }

    private fun doSync(c: Context) {
        val m = http(BASE + "manifest") ?: return
        val j = JSONObject(String(m))
        val ver = j.optLong("version")
        Prefs.sp(c).edit().putLong("vsync", System.currentTimeMillis()).apply()
        if (ver == Prefs.sp(c).getLong("vver", -1L)) return
        var ok = true
        for (lang in listOf("en", "am")) {
            val arr = j.optJSONArray(lang) ?: continue
            for (i in 0 until arr.length()) {
                val k = arr.getString(i)
                val b = http(BASE + "clip?lang=$lang&key=$k")
                if (b != null && b.isNotEmpty()) { val f = file(c, lang, k); f.parentFile?.mkdirs(); f.writeBytes(b) } else ok = false
            }
        }
        if (ok) Prefs.sp(c).edit().putLong("vver", ver).apply()
    }
}

/** Records with the microphone while the studio button is held. */
object Recorder {
    private var rec: AudioRecord? = null
    private var th: Thread? = null
    private val buf = ByteArrayOutputStream()
    @Volatile var active = false

    fun start() {
        val min = AudioRecord.getMinBufferSize(Voice.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, Voice.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, 8192))
        synchronized(buf) { buf.reset() }
        rec = r; active = true
        r.startRecording()
        th = Thread {
            val b = ByteArray(4096)
            while (active) { val n = r.read(b, 0, b.size); if (n > 0) synchronized(buf) { buf.write(b, 0, n) } }
        }.also { it.start() }
    }

    fun stop(): ByteArray {
        active = false
        try { th?.join(500) } catch (e: Exception) { }
        try { rec?.stop() } catch (e: Exception) { }
        try { rec?.release() } catch (e: Exception) { }
        rec = null
        return synchronized(buf) { buf.toByteArray() }
    }
}

/** Says a payment with the studio voice when the pack is complete, otherwise with the phone voice. */
object Announcer {
    fun announce(c: Context, pay: Pay, lang: String = Prefs.lang(c), noteEn: String = "", noteAm: String = "", done: () -> Unit = {}) {
        val keys = Voice.keysFor(c, lang, pay)
        if (keys == null) {
            Speaker.speak(c, Speaker.english(pay) + noteEn, Speaker.amharic(pay) + noteAm, lang, done)
            return
        }
        val note = if (lang == "am") noteAm.trim() else noteEn.trim()
        val after = { if (note.isEmpty()) done() else Speaker.speak(c, noteEn.trim(), noteAm.trim(), lang, done) }
        val name = pay.name
        if (name != null) {
            val nameLang = if (name.any { it.code in 0x1200..0x137F }) "am" else "en"
            Speaker.speakQuiet(c, name, name, nameLang) { Voice.play(c, lang, keys, after) }
        } else Voice.play(c, lang, keys, after)
    }
}
