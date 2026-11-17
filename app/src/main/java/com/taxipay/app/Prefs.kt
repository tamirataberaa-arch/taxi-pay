package com.taxipay.app

import android.content.Context

object Prefs {
    const val DEFAULT_SENDERS = "127,telebirr,cbe,cbebirr,cbe birr,cbe-birr"
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
}
