package com.taxipay.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (i.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val ms = Telephony.Sms.Intents.getMessagesFromIntent(i)
        if (ms == null || ms.isEmpty()) return
        val from = ms[0].originatingAddress ?: return
        if (!Prefs.enabled(c) || !Prefs.allowed(c, from)) return
        val pay = Parser.parse(ms.joinToString("") { it.messageBody ?: "" }) ?: return
        if (pay.ref != null && !Prefs.markSeen(c, pay.ref)) return
        val pr = goAsync()
        Speaker.announce(c, pay) { pr.finish() }
    }
}
