package com.taxipay.app

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object Billing {
    const val API = "https://wingame-sgma.onrender.com/taxipay/activate"
    const val PHONE = "0923934630"

    /** Checks the Telebirr transaction number with the server and adds the announcements. Runs in the background. */
    fun activate(c: Context, txRaw: String, cb: (Boolean, String) -> Unit) {
        val tx = txRaw.replace(" ", "").uppercase()
        Thread {
            try {
                val body = JSONObject().put("device", Prefs.device(c)).put("txid", tx).toString().toByteArray()
                val h = URL(API).openConnection() as HttpURLConnection
                h.connectTimeout = 20000; h.readTimeout = 70000; h.requestMethod = "POST"; h.doOutput = true
                h.setRequestProperty("Content-Type", "application/json")
                h.outputStream.use { it.write(body) }
                val txt = (if (h.responseCode >= 400) h.errorStream else h.inputStream).bufferedReader().readText()
                val j = JSONObject(txt)
                if (j.optBoolean("ok")) {
                    val n = j.optInt("credits")
                    val added = Prefs.addCredits(c, tx, n)
                    cb(true, if (added) "Done! +$n announcements added." else "This payment was already added.")
                } else cb(false, j.optString("msg", "Could not confirm the payment."))
            } catch (e: Exception) {
                cb(false, "No internet or the server is waking up. Please try again.")
            }
        }.start()
    }
}
