package com.taxipay.app

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

object Speaker {
    fun fmt(a: Double): String = if (a == Math.floor(a)) a.toLong().toString() else String.format(Locale.US, "%.2f", a)

    fun english(p: Pay) = if (p.name != null) "${p.name} sent ${fmt(p.amount)} Birr." else "Payment of ${fmt(p.amount)} Birr received."
    fun amharic(p: Pay) = if (p.name != null) "ከ${p.name} ${fmt(p.amount)} ብር ደርሷል።" else "${fmt(p.amount)} ብር ደርሷል።"

    fun announce(ctx: Context, pay: Pay, done: () -> Unit = {}) {
        speak(ctx, english(pay), amharic(pay), Prefs.lang(ctx), done)
    }

    /** Speaks in the chosen language; falls back to English if the phone has no voice for it. */
    fun speak(ctx: Context, en: String, am: String, lang: String, done: () -> Unit) {
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
                if (text == en) t.setLanguage(Locale.US)
                t.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { h.post { finish() } }
                    override fun onError(utteranceId: String?) { h.post { finish() } }
                })
                t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pay")
            } else {
                h.post { finish() }
            }
        }
        h.postDelayed({ finish() }, 15000)
    }
}
