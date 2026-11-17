package com.taxipay.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private lateinit var status: TextView

    private fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; isAllCaps = false; setOnClickListener { f() } }
    private fun label(t: String, size: Float = 14f) = TextView(this).apply { text = t; textSize = size; setPadding(0, 24, 0, 8) }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val sv = ScrollView(this)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 40, 40, 40) }
        sv.addView(l); setContentView(sv)

        l.addView(label("Taxi Pay", 26f))
        status = label("")
        l.addView(status)
        l.addView(button("1. Allow SMS permission") { requestPermissions(arrayOf(Manifest.permission.RECEIVE_SMS), 1) })
        l.addView(button("2. Allow background running") {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) }
            catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        })

        l.addView(Switch(this).apply {
            text = "Voice announcements"; isChecked = Prefs.enabled(this@MainActivity); setPadding(0, 30, 0, 10)
            setOnCheckedChangeListener { _, on -> Prefs.sp(this@MainActivity).edit().putBoolean("on", on).apply() }
        })

        l.addView(label("Announcement language"))
        l.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, arrayOf("English", "አማርኛ (Amharic)"))
            setSelection(if (Prefs.lang(this@MainActivity) == "am") 1 else 0)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    Prefs.sp(this@MainActivity).edit().putString("lang", if (pos == 1) "am" else "en").apply()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
        l.addView(button("Test announcement") {
            Speaker.announce(this, Pay("Test", "Abebe Kebede", 25.0, null))
        })

        l.addView(label("Only announce messages from these senders"))
        val senders = EditText(this).apply { setText(Prefs.senders(this@MainActivity)) }
        l.addView(senders)
        l.addView(button("Save senders") {
            Prefs.sp(this).edit().putString("senders", senders.text.toString().trim()).apply()
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show()
        })

        l.addView(label("Check an SMS: paste it here to see what is read"))
        val box = EditText(this).apply { minLines = 4; gravity = android.view.Gravity.TOP }
        val out = TextView(this).apply { setPadding(0, 10, 0, 10) }
        l.addView(box)
        l.addView(button("Check this SMS") {
            val p = Parser.parse(box.text.toString())
            out.text = if (p == null) "Not recognized as a payment SMS. It would be ignored."
            else "Method: ${p.method}\nName: ${p.name ?: "(none in this SMS)"}\nAmount: ${Speaker.fmt(p.amount)}\nReference: ${p.ref ?: "-"}\nWould say: ${Speaker.english(p)}"
        })
        l.addView(out)
        l.addView(label("An SMS shows that a payment was sent to you. Confirm important payments in your Telebirr or bank app."))
    }

    override fun onResume() { super.onResume(); refresh() }
    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, g: IntArray) { super.onRequestPermissionsResult(c, p, g); refresh() }

    private fun refresh() {
        val sms = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val bat = (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        status.text = "SMS permission: " + (if (sms) "OK" else "NOT allowed yet") + "\nBackground running: " + (if (bat) "OK" else "not allowed yet")
    }
}
