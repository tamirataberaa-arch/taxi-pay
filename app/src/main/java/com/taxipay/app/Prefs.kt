package com.taxipay.app

import android.content.Context
import android.provider.Settings

object Prefs {
    const val DEFAULT_SENDERS = "127,telebirr,cbe,cbebirr,cbe birr,cbe-birr"
    const val FREE_TRIAL = 20
    fun sp(c: Context) = c.getSharedPreferences("taxipay", Context.MODE_PRIVATE)
    fun enabled(c: Context) = sp(c).getBoolean("on", true)
    fun lang(c: Context) = sp(c).getString("lang", "en") ?: "en"
    fun senders(c: Context) = sp(c).getString("senders", DEFAULT_SENDERS) ?: DEFAULT_SENDERS

    /** Letters in the list match if the sender name contains them (CBE matches "CBE-Alert"); numbers must match exactly. */
    fun allowed(c: Context, from: String): Boolean {
        val f = from.trim().lowercase()
        return senders(c).split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            .any { s -> if (s.all { ch -> ch.isDigit() }) f == s else f == s || f.contains(s) }
    }

    /** True the first time a transaction reference is seen, false if it was already announced. */
    @Synchronized
    fun markSeen(c: Context, ref: String): Boolean {
        val l = (sp(c).getString("refs", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (l.contains(ref)) return false
        l.add(ref)
        while (l.size > 100) l.removeAt(0)
        sp(c).edit().putString("refs", l.joinToString(",")).commit()
        return true
    }

    fun trial(c: Context) = sp(c).getInt("trial", FREE_TRIAL)
    fun credits(c: Context) = sp(c).getInt("credits", 0)
    fun left(c: Context) = trial(c) + credits(c)
    fun outAt(c: Context) = sp(c).getLong("outAt", 0L)

    /** Uses one announcement (free trial first). False when nothing is left. */
    @Synchronized
    fun consume(c: Context): Boolean {
        val t = trial(c)
        if (t > 0) { sp(c).edit().putInt("trial", t - 1).commit(); return true }
        val cr = credits(c)
        if (cr > 0) { sp(c).edit().putInt("credits", cr - 1).commit(); return true }
        return false
    }

    /** Adds purchased announcements once per transaction number. */
    @Synchronized
    fun addCredits(c: Context, tx: String, n: Int): Boolean {
        val l = (sp(c).getString("applied", "") ?: "").split(",").filter { it.isNotEmpty() }.toMutableList()
        if (l.contains(tx)) return false
        l.add(tx)
        sp(c).edit().putInt("credits", credits(c) + n).putString("applied", l.joinToString(",")).commit()
        return true
    }

    fun device(c: Context): String = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown-device"
}
