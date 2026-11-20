package com.taxipay.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.*

/** Owner-only: record each word once. Phones download the result automatically. */
class VoiceStudioActivity : Activity() {
    private val NAVY = Color.parseColor("#6B0A24")
    private val YELLOW = Color.parseColor("#FFC107")
    private val BG = Color.parseColor("#F3F5F9")
    private val GREEN = Color.parseColor("#1FA85A")
    private val RED = Color.parseColor("#D64545")
    private val GRAY = Color.parseColor("#6B7280")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var lang = "en"
    private var idx = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun lp(top: Int = 0, w: Int = MATCH) = LinearLayout.LayoutParams(w, WRAP).apply { setMargins(0, dp(top), 0, 0) }
    private fun tv(t: String, size: Float = 15f, color: Int = Color.BLACK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun pill(t: String, fill: Int, fg: Int, f: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(fg); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = shape(fill, 14); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { f() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = NAVY
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(28), dp(20), dp(28)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        render()
    }

    private fun render() {
        root.removeAllViews()
        val prompts = Voice.prompts(lang)
        val keys = prompts.keys.toList()
        if (idx >= keys.size) idx = keys.size - 1
        val key = keys[idx]
        val done = Voice.count(this, lang)

        root.addView(tv("Voice Studio", 26f, NAVY, true))
        root.addView(tv("Record each word once. Hold the button while you speak, release when finished.", 14f, GRAY), lp(4))

        val adminKey = EditText(this).apply { hint = "Admin key (the wg-... key)"; setText(Prefs.sp(this@VoiceStudioActivity).getString("adminKey", "")); textSize = 14f; setSingleLine() }
        root.addView(adminKey, lp(14))
        root.addView(pill("Save admin key", Color.parseColor("#EEF0F4"), NAVY) {
            Prefs.sp(this).edit().putString("adminKey", adminKey.text.toString().trim()).apply(); Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        }, lp(6))

        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(label: String, code: String) = tv(label, 15f, if (lang == code) NAVY else GRAY, true).apply {
            gravity = Gravity.CENTER; background = shape(if (lang == code) YELLOW else Color.parseColor("#E5E7EB"), 12); setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { lang = code; idx = 0; render() }
        }
        langRow.addView(chip("English", "en"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, dp(6), 0) })
        langRow.addView(chip("አማርኛ", "am"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        root.addView(langRow, lp(16))

        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(Color.WHITE, 20); setPadding(dp(20), dp(20), dp(20), dp(20)); elevation = dp(3).toFloat() }
        card.addView(tv("Word ${idx + 1} of ${keys.size}   ·   recorded on this phone: $done", 13f, GRAY))
        card.addView(tv("Say:", 14f, GRAY), lp(14))
        val hint = if (key == "teen") "  (start of አስራ አምስት)" else ""
        card.addView(tv(prompts[key] + hint, 34f, NAVY, true))
        status = tv(if (Voice.has(this, lang, key)) "Already recorded ✓  (you can record again)" else "Not recorded yet", 14f, if (Voice.has(this, lang, key)) GREEN else GRAY, true)
        card.addView(status, lp(8))
        root.addView(card, lp(16))

        val hold = tv("HOLD TO RECORD", 18f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = shape(RED, 20); setPadding(dp(16), dp(30), dp(16), dp(30)) }
        hold.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        Recorder.start(); status.text = "Recording... speak now"; v.alpha = 0.7f
                    } else Toast.makeText(this, "Allow the microphone first", Toast.LENGTH_SHORT).show()
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    if (Recorder.active) finishRecording(key)
                    true
                }
                else -> true
            }
        }
        root.addView(hold, lp(16))

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(pill("◀ Back", Color.parseColor("#E5E7EB"), NAVY) { if (idx > 0) { idx--; render() } }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, dp(6), 0) })
        nav.addView(pill("▶ Play", NAVY, Color.WHITE) { if (Voice.has(this, lang, key)) Voice.playPcm(Voice.file(this, lang, key).readBytes()) {} }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        nav.addView(pill("Next ▶", YELLOW, NAVY) { if (idx < keys.size - 1) { idx++; render() } }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        root.addView(nav, lp(12))

        root.addView(pill("Test: \"Abebe Kebede sent 25 Birr\"", GREEN, Color.WHITE) {
            Announcer.announce(this, Pay("Test", "Abebe Kebede", 25.0, null), lang)
        }, lp(24))
        root.addView(tv("Phones download your voice automatically the next time they open the app with internet. After that it works offline. Words that are not recorded yet are spoken by the phone voice.", 12f, GRAY), lp(14))
    }

    private fun finishRecording(key: String) {
        val pcm = Voice.trimNormalize(Recorder.stop())
        if (pcm.isEmpty()) { status.setTextColor(RED); status.text = "Too quiet or too short. Hold the button and try again."; return }
        val f = Voice.file(this, lang, key)
        f.parentFile?.mkdirs(); f.writeBytes(pcm)
        Voice.playPcm(pcm) {}
        status.setTextColor(GRAY); status.text = "Saved. Uploading..."
        val l = lang
        Thread {
            val ok = Voice.upload(this, l, key, pcm)
            runOnUiThread {
                status.setTextColor(if (ok) GREEN else RED)
                status.text = if (ok) "Saved and uploaded ✓" else "Saved on this phone. Upload failed: check the admin key and internet."
            }
        }.start()
    }
}
