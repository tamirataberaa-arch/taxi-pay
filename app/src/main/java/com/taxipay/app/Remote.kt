package com.taxipay.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** News, update info and driver registration. Needs internet only briefly; the app works offline otherwise. */
object Remote {
    const val BASE = "https://wingame-sgma.onrender.com/taxipay/app/"

    fun cfg(c: Context): JSONObject = try { JSONObject(Prefs.sp(c).getString("cfg", "{}") ?: "{}") } catch (e: Exception) { JSONObject() }
    fun requireReg(c: Context) = cfg(c).optBoolean("requireReg", true)

    @Suppress("DEPRECATION")
    fun installed(c: Context): Int = try { c.packageManager.getPackageInfo(c.packageName, 0).versionCode } catch (e: Exception) { 0 }

    private fun call(url: String, body: String? = null): String? = try {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = 20000; h.readTimeout = 60000
        if (body != null) {
            h.requestMethod = "POST"; h.doOutput = true; h.setRequestProperty("Content-Type", "application/json")
            h.outputStream.use { it.write(body.toByteArray()) }
        }
        (if (h.responseCode >= 400) h.errorStream else h.inputStream).bufferedReader().readText()
    } catch (e: Exception) { null }

    /** Gets news and update info (at most every 10 minutes) and tells the server this phone is active. */
    fun refresh(c: Context, done: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        if (now - Prefs.sp(c).getLong("cfgAt", 0L) < 600000L) return
        Thread {
            val t = call(BASE + "config?d=" + Prefs.device(c) + "&v=" + installed(c))
            if (t != null && t.trim().startsWith("{")) {
                Prefs.sp(c).edit().putString("cfg", t).putLong("cfgAt", now).apply()
                done()
            }
        }.start()
    }

    fun register(c: Context, phone: String, name: String, city: String, cb: (Boolean, String) -> Unit) {
        Thread {
            val b = JSONObject().put("phone", phone).put("name", name).put("city", city).put("device", Prefs.device(c)).put("version", installed(c)).toString()
            val t = call(BASE + "register", b)
            if (t == null) cb(false, "No internet. Please try again.")
            else try {
                val j = JSONObject(t)
                if (j.optBoolean("ok")) { Prefs.sp(c).edit().putString("regPhone", j.optString("phone")).apply(); cb(true, "") }
                else cb(false, j.optString("msg", "Could not register."))
            } catch (e: Exception) { cb(false, "Server is busy. Please try again.") }
        }.start()
    }
}
