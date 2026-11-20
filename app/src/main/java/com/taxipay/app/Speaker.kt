package com.taxipay.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object Speaker {
    @Volatile private var probed = false

    fun fmt(a: Double): String = if (a == Math.floor(a)) a.toLong().toString() else String.format(Locale.US, "%.2f", a)

    fun english(p: Pay) = if (p.name != null) "${p.name} sent ${fmt(p.amount)} Birr." else "Payment of ${fmt(p.amount)} Birr received."
    fun amharic(p: Pay) = if (p.name != null) "ከ${p.name} ${fmt(p.amount)} ብር ደርሷል።" else "${fmt(p.amount)} ብር ደርሷል።"

    fun announce(ctx: Context, pay: Pay, done: () -> Unit = {}) {
        speak(ctx, english(pay), amharic(pay), Prefs.lang(ctx), done)
    }

    private fun beep() {
        try { ToneGenerator(AudioManager.STREAM_MUSIC, 100).startTone(ToneGenerator.TONE_PROP_ACK, 700) } catch (e: Exception) { }
    }

    /** Checks (without speaking) whether this phone has a working speech engine and English voice. */
    fun probe(ctx: Context) {
        if (probed) return
        probed = true
        val app = ctx.applicationContext
        var t: TextToSpeech? = null
        t = TextToSpeech(app) { st ->
            val x = t
            if (st == TextToSpeech.SUCCESS && x != null) Diag.tts(app, if (x.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_AVAILABLE) "ok" else "English voice missing")
            else Diag.tts(app, "Speech engine missing or not working")
            try { x?.shutdown() } catch (e: Exception) { }
        }
    }

    fun speak(ctx: Context, en: String, am: String, lang: String, done: () -> Unit) = run(ctx, en, am, lang, true, done)
    fun speakQuiet(ctx: Context, en: String, am: String, lang: String, done: () -> Unit) = run(ctx, en, am, lang, false, done)

    /** Speaks in the chosen language; falls back to English, and to a beep if the phone cannot speak at all. */
    private fun run(ctx: Context, en: String, am: String, lang: String, beepOnFail: Boolean, done: () -> Unit) {
        val app = ctx.applicationContext
        val h = Handler(Looper.getMainLooper())
        var tts: TextToSpeech? = null
        var finished = false
        fun finish() {
            if (finished) return
            finished = true
            try { tts?.shutdown() } catch (e: Exception) { }
            done()
        }
        tts = TextToSpeech(app) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                var text = en
                if (lang == "am" && t.setLanguage(Locale("am", "ET")) >= TextToSpeech.LANG_AVAILABLE) text = am
                var voiceOk = true
                if (text == en && t.setLanguage(Locale.US) < TextToSpeech.LANG_AVAILABLE) voiceOk = false
                if (voiceOk) {
                    Diag.tts(app, "ok")
                    t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                    t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {}
                        override fun onDone(utteranceId: String?) { h.post { finish() } }
                        override fun onError(utteranceId: String?) { h.post { finish() } }
                    })
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pay")
                } else {
                    Diag.tts(app, "English voice missing")
                    if (beepOnFail) beep()
                    h.post { finish() }
                }
            } else {
                Diag.tts(app, "Speech engine missing or not working")
                if (beepOnFail) beep()
                h.post { finish() }
            }
        }
        h.postDelayed({ finish() }, 15000)
    }
}
