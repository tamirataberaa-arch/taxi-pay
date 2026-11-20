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
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {
    private val NAVY = Color.parseColor("#6B0A24")
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
    private var gateSkipped = false

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
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 2)
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(32)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(BG); addView(root) })
    }

    override fun onResume() { super.onResume(); Speaker.probe(this); Voice.syncIfDue(this); Remote.refresh(this) { runOnUiThread { render() } }; render() }
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
        if (needGate()) { renderGate(); return }
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
        val brand = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.zound_mark); background = shape(Color.WHITE, 26); setPadding(dp(7), dp(9), dp(7), dp(9)) }, LinearLayout.LayoutParams(dp(54), dp(54)))
        brand.addView(tv("Zound Pay", 30f, Color.WHITE, true).apply {
            setPadding(dp(14), 0, 0, 0)
            setOnClickListener { taps++; if (taps >= 7) { taps = 0; startActivity(Intent(this@MainActivity, VoiceStudioActivity::class.java)) } }
        })
        head.addView(brand)
        head.addView(tv("Hear every payment  ·  ክፍያ በድምፅ", 14f, YELLOW))
        head.addView(tv("●  $state", 14f, Color.WHITE, true).apply { background = shape(if (good) GREEN else ORANGE, 20); setPadding(dp(14), dp(8), dp(14), dp(8)) }, lp(w = WRAP, top = 16))
        root.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))

        for (n in buildNotices()) add(n, 12)
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
        add(buildToday())

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
        setup.addView(row("Sound volume", Diag.volumeOk(this)) { (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) }, lp(top = 12))
        val ttsState = Prefs.sp(this).getString("d_tts", "") ?: ""
        val voiceOk = ttsState == "ok" || ttsState.isEmpty() || Voice.count(this, "en") == Voice.prompts("en").size
        setup.addView(row("Speech voice" + (if (voiceOk) "" else " ($ttsState)"), voiceOk) {
            try { startActivity(Intent("com.android.settings.TTS_SETTINGS")) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
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
        adv.addView(tv("Sound check", 14f, GRAY), lp(top = 20))
        adv.addView(tv(Diag.lastLine(this), 13f, INK), lp(top = 4))
        adv.addView(pill("Send a problem report", NAVY, Color.WHITE) {
            Remote.report(this) { ok -> runOnUiThread { Toast.makeText(this, if (ok) "Report sent. Thank you." else "Could not send. Check internet.", Toast.LENGTH_LONG).show() } }
        }, lp(top = 8))
            add(adv)
        }
        add(tv("Everything is read on this phone only. An SMS shows a payment was sent to you; confirm large payments in your Telebirr or bank app.", 12f, GRAY).apply { gravity = Gravity.CENTER }, 16)
    }

    private fun buildToday(): LinearLayout {
        val list = Ledger.today(this)
        val c = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val lc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lc.addView(tv("Collected today (from SMS)", 13f, GRAY))
        lc.addView(tv(Ledger.money(Ledger.total(list)) + " Birr", 26f, INK, true))
        top.addView(lc, LinearLayout.LayoutParams(0, WRAP, 1f))
        top.addView(tv("↓ Download", 14f, NAVY, true).apply {
            background = shape(YELLOW, 12); setPadding(dp(14), dp(10), dp(14), dp(10)); setOnClickListener { Ledger.download(this@MainActivity) }
        })
        c.addView(top)
        c.addView(tv("${list.size} payments  ·  a new list starts at midnight", 12f, GRAY), lp(top = 4))
        if (Prefs.left(this) <= 0) c.addView(tv("Recharge to keep recording payments", 12f, RED, true), lp(top = 4))
        if (list.isEmpty()) {
            c.addView(tv("No payments yet today", 14f, GRAY), lp(top = 12))
        } else {
            val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (e in list) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
                val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                l.addView(tv(e.name.ifEmpty { e.method + " payment" }, 15f, INK, true))
                l.addView(tv(listOf(e.phone, Ledger.time(e.t)).filter { it.isNotEmpty() }.joinToString("  ·  "), 12f, GRAY))
                r.addView(l, LinearLayout.LayoutParams(0, WRAP, 1f))
                r.addView(tv("+" + Ledger.money(e.amount), 16f, GREEN, true))
                inner.addView(r)
            }
            val sv = ScrollView(this).apply { addView(inner); setOnTouchListener { v, _ -> v.parent.requestDisallowInterceptTouchEvent(true); false } }
            c.addView(sv, LinearLayout.LayoutParams(MATCH, if (list.size > 4) dp(200) else WRAP).apply { setMargins(0, dp(10), 0, 0) })
        }
        return c
    }

    private fun needGate(): Boolean =
        Remote.requireReg(this) && !gateSkipped && (Prefs.sp(this).getString("regPhone", "") ?: "").isEmpty()

    private fun renderGate() {
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(36), dp(24), dp(28))
            background = GradientDrawable().apply { setColor(NAVY); val r = dp(28).toFloat(); cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r) }
        }
        head.addView(tv("Welcome to Zound Pay", 28f, Color.WHITE, true))
        head.addView(tv("እንኳን ደህና መጡ", 16f, YELLOW))
        root.addView(head, LinearLayout.LayoutParams(MATCH, WRAP))
        val c = card()
        c.addView(tv("Register your phone number", 18f, INK, true))
        c.addView(tv("ስልክ ቁጥርዎን ይመዝግቡ", 13f, GRAY))
        val phone = EditText(this).apply { hint = "Phone number, e.g. 0911223344"; inputType = InputType.TYPE_CLASS_PHONE; textSize = 18f }
        val name = EditText(this).apply { hint = "Name (optional)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        val city = EditText(this).apply { hint = "City (optional)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS }
        c.addView(phone, lp(top = 12)); c.addView(name, lp(top = 4)); c.addView(city, lp(top = 4))
        c.addView(tv("We use your number only to send Zound Pay updates and support. We also record how many payments you receive each day and their total (never payers' names). ስልክ ቁጥርዎን ለዝማኔና ለድጋፍ ብቻ እንጠቀማለን። የሚቀበሉትን ክፍያ ብዛትና ጠቅላላ መጠን (የከፋዮችን ስም ሳይሆን) እንመዘግባለን።", 12f, GRAY), lp(top = 10))
        val msg = tv("", 14f, RED, true)
        c.addView(msg, lp(top = 8))
        var busy = false
        c.addView(pill("Continue  ·  ቀጥል", YELLOW, NAVY) {
            if (busy) return@pill
            busy = true; msg.setTextColor(GRAY); msg.text = "Please wait..."
            Remote.register(this, phone.text.toString(), name.text.toString(), city.text.toString()) { ok, m ->
                runOnUiThread { busy = false; if (ok) render() else { msg.setTextColor(RED); msg.text = m } }
            }
        }, lp(top = 12))
        if (Prefs.sp(this).getInt("regSkips", 0) < 3) {
            c.addView(tv("Later  ·  በኋላ", 14f, GRAY, true).apply {
                gravity = Gravity.CENTER; setPadding(0, dp(14), 0, dp(4))
                setOnClickListener {
                    Prefs.sp(this@MainActivity).edit().putInt("regSkips", Prefs.sp(this@MainActivity).getInt("regSkips", 0) + 1).apply()
                    gateSkipped = true; render()
                }
            }, lp(top = 4))
        }
        add(c, 16)
    }

    private fun buildNotices(): List<View> {
        val out = ArrayList<View>()
        val cfg = Remote.cfg(this)
        val u = cfg.optJSONObject("update")
        if (u != null && u.optInt("code") > Remote.installed(this)) {
            val req = u.optBoolean("required")
            val later = Prefs.sp(this).getLong("updLater", 0L)
            if (req || System.currentTimeMillis() - later > 24 * 3600 * 1000L) {
                val c = card()
                val nm = u.optString("name")
                c.addView(tv("New version available" + (if (nm.isNotEmpty()) "  ·  $nm" else ""), 16f, INK, true))
                if (u.optString("notes").isNotEmpty()) c.addView(tv(u.optString("notes"), 13f, GRAY), lp(top = 4))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(pill("Update  ·  አዘምን", YELLOW, NAVY) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.optString("url")))) }, LinearLayout.LayoutParams(0, WRAP, 1f))
                if (!req) row.addView(pill("Later", Color.parseColor("#E5E7EB"), NAVY) {
                    Prefs.sp(this).edit().putLong("updLater", System.currentTimeMillis()).apply(); render()
                }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(8), 0, 0, 0) })
                c.addView(row, lp(top = 12))
                out.add(c)
            }
        }
        val n = cfg.optJSONObject("news")
        if (n != null && n.optString("text").isNotEmpty() && n.optString("id") != Prefs.sp(this).getString("newsSeen", "")) {
            val c = card()
            c.addView(tv("News  ·  ዜና", 13f, GRAY, true))
            c.addView(tv(n.optString("text"), 15f, INK), lp(top = 4))
            c.addView(tv("Got it  ·  ገባኝ", 14f, NAVY, true).apply {
                setPadding(0, dp(12), 0, 0)
                setOnClickListener { Prefs.sp(this@MainActivity).edit().putString("newsSeen", n.optString("id")).apply(); render() }
            })
            out.add(c)
        }
        val unread = Prefs.sp(this).getString("unread", "") ?: ""
        if (unread.isNotEmpty()) {
            val c = card()
            c.addView(tv("A payment message could not be read", 16f, INK, true))
            c.addView(tv("Send it to Zound Pay so we can fix it. Only this one message is sent.", 13f, GRAY), lp(top = 4))
            val rw = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rw.addView(pill("Send", YELLOW, NAVY) {
                Remote.report(this) { ok ->
                    runOnUiThread {
                        if (ok) Prefs.sp(this).edit().remove("unread").apply()
                        Toast.makeText(this, if (ok) "Sent. Thank you!" else "Could not send. Check internet.", Toast.LENGTH_LONG).show(); render()
                    }
                }
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            rw.addView(pill("Dismiss", Color.parseColor("#E5E7EB"), NAVY) { Prefs.sp(this).edit().remove("unread").apply(); render() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(8), 0, 0, 0) })
            c.addView(rw, lp(top = 12))
            out.add(c)
        }
        if ((Prefs.sp(this).getString("regPhone", "") ?: "").isNotEmpty() && !Prefs.sp(this).contains("statsConsent")) {
            val c = card()
            c.addView(tv("Zound Pay records how many payments you receive each day and their total. Payers' names are never sent.", 13f, INK))
            c.addView(tv("Zound Pay በየቀኑ የሚቀበሉትን ክፍያ ብዛትና ጠቅላላ መጠን ይመዘግባል። የከፋዮች ስም አይላክም።", 12f, GRAY), lp(top = 4))
            val rw = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rw.addView(pill("OK", YELLOW, NAVY) { Prefs.sp(this).edit().putBoolean("statsConsent", true).apply(); render() }, LinearLayout.LayoutParams(0, WRAP, 1f))
            rw.addView(pill("No thanks", Color.parseColor("#E5E7EB"), NAVY) { Prefs.sp(this).edit().putBoolean("statsConsent", false).apply(); render() }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(dp(8), 0, 0, 0) })
            c.addView(rw, lp(top = 12))
            out.add(c)
        }
        return out
    }

    private var onPicked: ((String) -> Unit)? = null

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (req == 77 && res == RESULT_OK && uri != null) {
            val cb = onPicked
            Receipt.readId(this, uri) { id -> runOnUiThread { cb?.invoke(id) } }
        }
    }

    private fun showRecharge() {
        val pay = Remote.pay(this)
        val price = pay.optInt("price", 50)
        val credits = pay.optInt("credits", 1000)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24)) }
        box.addView(tv("Recharge Zound Pay", 22f, INK, true))
        box.addView(tv("$price Birr = $credits announcements  ·  ${price * 2} Birr = ${credits * 2}", 14f, GRAY))
        box.addView(tv("1.  Send $price Birr to one of these", 16f, INK, true), lp(top = 20))
        box.addView(tv("$price ብር ወደ አንዱ ይላኩ", 13f, GRAY))
        val opts = pay.optJSONArray("options")
        var n = 0
        if (opts != null) for (i in 0 until opts.length()) {
            val o = opts.getJSONObject(i)
            val num = o.optString("number")
            if (num.isEmpty()) continue
            n++
            val oc = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = shape(Color.parseColor("#F3F5F9"), 16); setPadding(dp(16), dp(12), dp(16), dp(12)) }
            oc.addView(tv(o.optString("bank"), 13f, GRAY, true))
            oc.addView(tv(num, 26f, NAVY, true))
            if (o.optString("name").isNotEmpty()) oc.addView(tv(o.optString("name"), 13f, GRAY))
            oc.addView(tv("Copy number", 14f, NAVY, true).apply {
                setPadding(0, dp(8), 0, 0)
                setOnClickListener {
                    (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("pay", num))
                    Toast.makeText(this@MainActivity, "Copied", Toast.LENGTH_SHORT).show()
                }
            })
            box.addView(oc, lp(top = 10))
        }
        if (n == 0) box.addView(tv(Billing.PHONE + "  (Telebirr)", 24f, NAVY, true), lp(top = 10))
        box.addView(tv("2.  Type the transaction number from the payment SMS, or upload the receipt screenshot", 16f, INK, true), lp(top = 22))
        box.addView(tv("የግብይት ቁጥሩን ይጻፉ ወይም የደረሰኙን ፎቶ ይጫኑ", 13f, GRAY))
        val tx = EditText(this).apply { hint = "Example: DKH0AT8XBO"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS; textSize = 18f }
        box.addView(tx, lp(top = 6))
        val result = tv("", 15f, GRAY, true)
        box.addView(pill("Upload receipt screenshot", Color.parseColor("#EEF0F4"), NAVY) {
            onPicked = { id ->
                if (id.isEmpty()) { result.setTextColor(RED); result.text = "Could not read the number. Please type it." }
                else { tx.setText(id); result.setTextColor(GRAY); result.text = "Found $id. Check it, then tap Activate." }
            }
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }, 77)
        }, lp(top = 8))
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
