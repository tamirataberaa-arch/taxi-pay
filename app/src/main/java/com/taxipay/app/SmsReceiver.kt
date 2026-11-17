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
        val pay = Parser.parse(ms.joinToString("") { it.messageBody ?: "" }) ?: return
        if (!Prefs.allowed(c, from)) {   // looks like a payment but sender is not on the list: remember it so the user can allow it
            Prefs.sp(c).edit().putString("blocked", from).apply()
            return
        }
        if (!Prefs.enabled(c)) return
        if (pay.ref != null && !Prefs.markSeen(c, pay.ref)) return
        val time = SimpleDateFormat("HH:mm", Locale.US).format(Date())
        Prefs.sp(c).edit().putString("last", (pay.name ?: pay.method) + " " + Speaker.fmt(pay.amount) + " Birr at " + time).apply()
        val pr = goAsync()
        Speaker.announce(c, pay) { pr.finish() }
    }
}
