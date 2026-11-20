package com.taxipay.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val ms = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (ms == null || ms.isEmpty()) return
        val from = ms[0].originatingAddress ?: return
        val body = ms.joinToString("") { it.messageBody ?: "" }
        val allowed = Prefs.allowed(c, from)
        val pay = Parser.parse(body)
        if (pay == null) {
            Diag.seen(c, from, if (allowed) "not recognized as a payment message" else "ignored: other sender")
            // keep one unreadable money message from a payment sender so the user can send it to us
            if (allowed && Regex("""\d[\d,]*\.\d{2}""").containsMatchIn(body) && !Regex("(?i)transferred|paid to|purchase|airtime|sent to").containsMatchIn(body))
                Prefs.sp(c).edit().putString("unread", body.take(600)).apply()
            return
        }
        if (!allowed) {   // looks like a payment but the sender is not on the list: remember it so the user can allow it
            Prefs.sp(c).edit().putString("blocked", from).apply()
            Diag.seen(c, from, "payment message but sender is not on the list")
            return
        }
        if (!Prefs.enabled(c)) { Diag.seen(c, from, "voice is switched off"); return }
        if (pay.ref != null && !Prefs.markSeen(c, pay.ref)) { Diag.seen(c, from, "already announced"); return }
        val pr = goAsync()
        if (!Prefs.consume(c)) {   // no announcements left: warn by voice, at most once every 30 minutes
            Diag.seen(c, from, "no announcements left")
            val now = System.currentTimeMillis()
            if (now - Prefs.outAt(c) > 30 * 60 * 1000L) {
                Prefs.sp(c).edit().putLong("outAt", now).apply()
                Speaker.speak(c, "Zound Pay announcements finished. Please recharge.", "የZound Pay ማሳወቂያ አልቋል። እባክዎ ይሙሉ።", Prefs.lang(c)) { pr.finish() }
            } else pr.finish()
            return
        }
        Diag.seen(c, from, "announced")
        val left = Prefs.left(c)
        val low = left == 50 || left == 10
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        Ledger.add(c, pay)
        Prefs.sp(c).edit().putString("last", (pay.name ?: pay.method) + "  ·  " + Speaker.fmt(pay.amount) + " Birr  ·  " + time).apply()
        Notifier.post(c, (pay.name ?: pay.method) + "  ·  " + Speaker.fmt(pay.amount) + " Birr")
        Announcer.announce(c, pay, Prefs.lang(c), if (low) " Only $left announcements left." else "", if (low) " $left ማሳወቂያ ብቻ ቀርቷል።" else "") { pr.finish() }
    }
}
