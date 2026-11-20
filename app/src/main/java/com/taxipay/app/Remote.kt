package com.taxipay.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** News, update info, payment settings, registration and reports. The app works offline otherwise. */
object Remote {
    const val BASE = "https://wingame-sgma.onrender.com/taxipay/app/"

    fun cfg(c: Context): JSONObject = try { JSONObject(Prefs.sp(c).getString("cfg", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
    fun requireReg(c: Context) = cfg(c).optBoolean("requireReg", true)
    fun pay(c: Context): JSONObject = cfg(c).optJSONObject("pay") ?: JSONObject()

    @Suppress("DEPRECATION")
    fun installed(c: Context): Int = try { c.packageManager.getPackageInfo(c.packageName, 0).versionCode } catch (e: Exception) { 0 }

    private fun call(url: String, body: String? = null, fast: Boolean = false): String? = try {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = if (fast) 6000 else 20000; h.readTimeout = if (fast) 8000 else 60000
        if (body != null) {
            h.requestMethod = "POST"; h.doOutput = true; h.setRequestProperty("Content-Type", "application/json")
            h.outputStream.use { it.write(body.toByteArray()) }
        }
        (if (h.responseCode >= 400) h.errorStream else h.inputStream).bufferedReader().readText()
    } catch (e: Exception) { null }

    /** Gets settings and news (at most every 10 minutes), tells the server this phone is active and sends daily totals if allowed. */
    fun refresh(c: Context, done: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (now - Prefs.sp(c).getLong("cfgAt", 0L) < 30000L) return
        Thread {
            val t = call(BASE + "config?d=" + Prefs.device(c) + "&v=" + installed(c))
            if (t != null && t.trim().startsWith("{")) {
                val sp = Prefs.sp(c)
                val ed = sp.edit().putString("cfg", t).putLong("cfgAt", now)
                try {
                    val trial = JSONObject(t).optJSONObject("pay")?.optInt("trial", -1) ?: -1
                    if (trial >= 0 && !sp.contains("trial") && !sp.contains("trialInit")) ed.putInt("trial", trial).putBoolean("trialInit", true)
                } catch (e: Exception) { }
                ed.apply()
                if ((sp.getString("regPhone", "") ?: "").isNotEmpty()) {
                    val m = call(BASE + "me?d=" + Prefs.device(c))
                    if (m != null && m.trim().startsWith("{")) sp.edit().putString("me", m).apply()
                }
                if (sp.getBoolean("statsConsent", false)) postStats(c)
                done()
            }
        }.start()
    }

    /** Fetches the latest settings right now (before showing prices) and calls back even if offline. */
    fun refreshNow(c: Context, cb: () -> Unit) {
        Thread {
            val t = call(BASE + "config?d=" + Prefs.device(c) + "&v=" + installed(c), null, true)
            if (t != null && t.trim().startsWith("{")) Prefs.sp(c).edit().putString("cfg", t).putLong("cfgAt", System.currentTimeMillis()).apply()
            cb()
        }.start()
    }

    /** Sends only the number of payments and their total per day (never names or messages). */
    private fun postStats(c: Context) {
        val arr = try { JSONArray(Prefs.sp(c).getString("ledger", "[]")) } catch (e: Exception) { return }
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("Africa/Addis_Ababa") }
        val n = HashMap<String, Int>()
        val t = HashMap<String, Double>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val d = fmt.format(Date(o.getLong("t")))
            n[d] = (n[d] ?: 0) + 1
            t[d] = (t[d] ?: 0.0) + o.getDouble("a")
        }
        if (n.isEmpty()) return
        val days = JSONArray()
        for (d in n.keys) days.put(JSONObject().put("d", d).put("n", n[d]).put("t", t[d]))
        call(BASE + "stats", JSONObject().put("device", Prefs.device(c)).put("days", days).toString())
    }

    fun register(c: Context, phone: String, name: String, city: String, ref: String, cb: (Boolean, String) -> Unit) {
        Thread {
            val b = JSONObject().put("phone", phone).put("name", name).put("city", city).put("device", Prefs.device(c)).put("version", installed(c)).put("ref", ref.trim()).toString()
            val t = call(BASE + "register", b)
            if (t == null) cb(false, "No internet. Please try again.")
            else try {
                val j = JSONObject(t)
                if (j.optBoolean("ok")) { Prefs.sp(c).edit().putString("regPhone", j.optString("phone")).putString("myCode", j.optString("code")).putBoolean("statsConsent", true).putLong("cfgAt", 0L).apply(); cb(true, "") }
                else cb(false, j.optString("msg", "Could not register."))
            } catch (e: Exception) { cb(false, "Server is busy. Please try again.") }
        }.start()
    }

    /** Sends a problem report: phone model, settings and the one unreadable payment message (if any). */
    fun report(c: Context, cb: (Boolean) -> Unit) {
        Thread {
            val b = JSONObject().put("device", Prefs.device(c)).put("phone", Prefs.sp(c).getString("regPhone", "") ?: "")
                .put("info", Diag.summary(c)).put("sms", Prefs.sp(c).getString("unread", "") ?: "").toString()
            val t = call(BASE + "report", b)
            cb(t != null && t.contains("true"))
        }.start()
    }
}
