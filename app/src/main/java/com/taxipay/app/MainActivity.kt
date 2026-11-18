package com.taxipay.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val NAVY = Color.parseColor("#0F1B3D")
    private val YELLOW = Color.parseColor("#FFC107")
    private val BG = Color.parseColor("#F3F5F9")
    private val GREEN = Color.parseColor("#1FA85A")
    private val ORANGE = Color.parseColor("#F08A24")
    private val RED = Color.parseColor("#D64545")
    private val GRAY = Color.parseColor("#6B7280")
    private val INK = Color.parseColor("#111827")
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var root: LinearLayout
    private var advanced = false
    private var taps = 0

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun shape(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(r).toFloat() }
    private fun lp(w: Int = MATCH, h: Int = WRAP, top: Int = 0, side: Int = 0) = LinearLayout.LayoutParams(w, h).apply { setMargins(dp(side), dp(top), dp(side), 0) }
    private fun tv(t: String, size: Float = 15f, color: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; background = shape(Color.WHITE, 20); setPadding(dp(20), dp(18), dp(20), dp(18)); elevation = dp(3).toFloat()
    }
    private fun pill(t: String, fill: Int, fg: Int, f: () -> Unit) = TextView(this).apply {
        text = t; setTextColor(fg); textSize = 16f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
        background = shape(fill, 14); setPadding(dp(16), dp(14), dp(16), dp(14)); setOnClickListener { f() }
    }
    private fun add(v: View, top: Int = 12) = root.addView(v, lp(top = top, side = 16))

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.statusBarColor = NAVY
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(32)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
    }

    override fun onResume() { super.onResume(); Voice.syncIfDue(this); render() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(c, p, g); render() }

    private fun row(title: String, ok: Boolean, fix: () -> Unit): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        val mark = tv(if (ok) "✓" else "!", 15f, Color.WHITE, true).apply { gravity = Gravity.CENTER; background = shape(if (ok) GREEN else ORANGE, 14) }
        addView(mark, LinearLayout.LayoutParams(dp(28), dp(28)))
        addView(tv(title, 16f), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(12), 0, 0, 0) })
        if (!ok) { addView(tv("Tap to fix", 14f, ORANGE, true)); setOnClickListener { fix() } }
    }

    private fun render() {
        root.removeAllViews()
        val sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val bat = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        val on = Prefs.enabled(this)
        val left = Prefs.left(this)
        val good = sms && on && left > 0
        val state = when { !sms -> "Allow SMS below to start"; !on -> "Voice is off"; left <= 0 -> "Recharge needed"; else -> "Listening for payments" }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(36), dp(24), dp(28))
            background = GradientDrawable().apply { setColor(NAVY); val r = dp(28).toFloat(); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        }
        head.addView(tv("Taxi Pay", 30f, Color.WHITE, true).apply { setOnClickListener { taps++; if (taps >= 7) { taps = 0; startActivity(Intent(this@MainActivity, VoiceStudioActivity::class.java)) } } })
        head.addView(tv("Hear every payment  ·  ክፍያ በድምፅ", 14f, YELLOW))
        head.addView(tv("●  $state", 14f, Color.WHITE, true).apply { background = shape(if (good) GREEN else ORANGE, 20); setPadding(dp(14), dp(8), dp(14), dp(8)) }, lp(w = WRAP, top = 16))
        root.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        val bal = card()
        bal.addView(tv("Announcements left", 14f, GRAY))
        bal.addView(tv(left.toString(), 44f, if (left <= 50) RED else INK, true))
        val t = Prefs.trial(this)
        if (t > 0) bal.addView(tv("Includes $t free trial announcements", 13f, GRAY))
        val frac = (minOf(left, 1000).toFloat() / 1000f).coerceIn(0.001f, 0.999f)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = shape(Color.parseColor("#E5E7EB"), 6) }
        bar.addView(View(this).apply { background = shape(YELLOW, 6) }, LinearLayout.LayoutParams(0, dp(10), frac))
        bar.addView(View(this), LinearLayout.LayoutParams(0, dp(10), 1f - frac))
        bal.addView(bar, lp(top = 12))
        bal.addView(pill("Recharge  ·  50 Birr = 1000", YELLOW, NAVY) { showRecharge() }, lp(top = 16))
        add(bal, 16)

        val blocked = Prefs.sp(this).getString("blocked", "") ?: ""
        if (blocked.isNotEmpty()) {
            val w = card()
            w.addView(tv("A payment SMS from \"$blocked\" was ignored", 15f, INK, true))
            w.addView(pill("Allow this sender", ORANGE, Color.WHITE) {
                Prefs.sp(this).edit().putString("senders", Prefs.senders(this) + "," + blocked).remove("blocked").apply(); render()
            }, lp(top = 12))
            add(w)
        }

        val setup = card()
        setup.addView(tv("Setup", 17f, INK, true))
        setup.addView(row("SMS permission", sms) { requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1) }, lp(top = 12))
        setup.addView(row("Keep running in background", bat) {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        }, lp(top = 12))
        add(setup)

        val voice = card()
        val vh = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        vh.addView(tv("Voice announcements", 17f, INK, true), LinearLayout.LayoutParams(0, WRAP, 1f))
        vh.addView(Switch(this).apply {
            isChecked = on
            setOnCheckedChangeListener { _, c -> Prefs.sp(this@MainActivity).edit().putBoolean("on", c).apply(); render() }
        })
        voice.addView(vh)
        val cur = Prefs.lang(this)
        val langRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun chip(label: String, code: String) = tv(label, 15f, if (cur == code) NAVY else GRAY, true).apply {
            gravity = Gravity.CENTER; background = shape(if (cur == code) YELLOW else Color.parseColor("#EEF0F4"), 12); setPadding(dp(8), dp(12), dp(8), dp(12))
            setOnClickListener { Prefs.sp(this@MainActivity).edit().putString("lang", code).apply(); render() }
        }
        langRow.addView(chip("English", "en"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, dp(6), 0) })
        langRow.addView(chip("አማርኛ", "am"), LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(6), 0, 0, 0) })
        voice.addView(langRow, lp(top = 14))
        voice.addView(pill("▶  Test voice", NAVY, Color.WHITE) { Announcer.announce(this, Pay("Test", "Abebe Kebede", 25.0, null)) }, lp(top = 12))
        voice.addView(tv(Voice.status(this), 13f, GRAY), lp(top = 12))
        add(voice)

        val lastP = Prefs.sp(this).getString("last", "") ?: ""
        val lc = card()
        lc.addView(tv("Last payment heard", 14f, GRAY))
        lc.addView(tv(if (lastP.isEmpty()) "Nothing yet" else lastP, 18f, INK, true), lp(top = 4))
        add(lc)

        add(tv(if (advanced) "Advanced settings  ▲" else "Advanced settings  ▼", 14f, GRAY, true).apply {
            gravity = Gravity.CENTER; setPadding(dp(8), dp(14), dp(8), dp(14)); setOnClickListener { advanced = !advanced; render() }
        }, 8)
        if (advanced) {
            val adv = card()
            adv.addView(tv("Only announce messages from these senders", 14f, GRAY))
            val senders = EditText(this).apply { setText(Prefs.senders(this@MainActivity)); textSize = 14f }
            adv.addView(senders)
            adv.addView(pill("Save senders", NAVY, Color.WHITE) {
                Prefs.sp(this).edit().putString("senders", senders.text.toString().trim()).apply()
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
            }, lp(top = 8))
            adv.addView(tv("Check an SMS: paste it to see what is read", 14f, GRAY), lp(top = 20))
            val box = EditText(this).apply { minLines = 3; gravity = Gravity.TOP; textSize = 14f }
            val out = tv("", 14f, INK)
            adv.addView(box)
            adv.addView(pill("Check this SMS", NAVY, Color.WHITE) {
                val p = Parser.parse(box.text.toString())
                out.text = if (p == null) "Not recognized as a payment SMS. It would be ignored."
                else "Method: ${p.method}\nName: ${p.name ?: "(none in this SMS)"}\nAmount: ${Speaker.fmt(p.amount)}\nReference: ${p.ref ?: "-"}"
            }, lp(top = 8))
            adv.addView(out, lp(top = 8))
            add(adv)
        }
        add(tv("Everything is read on this phone only. An SMS shows a payment was sent to you; confirm large payments in your Telebirr or bank app.", 12f, GRAY).apply { gravity = Gravity.CENTER }, 16)
    }

    private fun showRecharge() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        box.addView(tv("Recharge Taxi Pay", 22f, INK, true))
        box.addView(tv("50 Birr = 1000 announcements  ·  100 Birr = 2000", 14f, GRAY))
        box.addView(tv("1.  Send 50 Birr by Telebirr to", 16f, INK, true), lp(top = 20))
        box.addView(tv("50 ብር በቴሌብር ወደዚህ ቁጥር ይላኩ", 13f, GRAY))
        box.addView(tv(Billing.PHONE, 30f, NAVY, true), lp(top = 6))
        box.addView(pill("Copy number", Color.parseColor("#EEF0F4"), NAVY) {
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("phone", Billing.PHONE))
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
        }, lp(top = 8))
        box.addView(tv("2.  Type the Transaction number from the Telebirr SMS you get", 16f, INK, true), lp(top = 22))
        box.addView(tv("በቴሌብር መልዕክት ውስጥ ያለውን የግብይት ቁጥር ያስገቡ", 13f, GRAY))
        val tx = EditText(this).apply { hint = "Example: DKH0AT8XBO"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; textSize = 18f }
        box.addView(tx, lp(top = 6))
        val result = tv("", 15f, GRAY, true)
        box.addView(result, lp(top = 8))
        val dlg = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(box) }).create()
        var busy = false
        box.addView(pill("3.  Activate  ·  አግብር", YELLOW, NAVY) {
            if (busy) return@pill
            if (tx.text.toString().trim().length < 6) { result.setTextColor(RED); result.text = "Please type the transaction number."; return@pill }
            busy = true; result.setTextColor(GRAY); result.text = "Checking your payment... please wait"
            Billing.activate(this, tx.text.toString()) { ok, msg ->
                runOnUiThread {
                    busy = false; result.setTextColor(if (ok) GREEN else RED); result.text = msg
                    if (ok) { render(); Toast.makeText(this, msg, Toast.LENGTH_LONG).show(); dlg.dismiss() }
                }
            }
        }, lp(top = 16))
        dlg.show()
        dlg.window?.setBackgroundDrawable(shape(Color.WHITE, 24))
    }
}
