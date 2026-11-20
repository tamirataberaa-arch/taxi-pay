package com.taxipay.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** Remembers what happened to the last SMS, so "why no sound?" can be answered. */
object Diag {
    fun seen(c: Context, from: String, result: String) {
        val sp = Prefs.sp(c)
        sp.edit().putLong("d_at", System.currentTimeMillis()).putString("d_from", from).putString("d_res", result).putInt("d_n", sp.getInt("d_n", 0) + 1).apply()
    }
    fun tts(c: Context, s: String) { Prefs.sp(c).edit().putString("d_tts", s).apply() }

    fun lastLine(c: Context): String {
        val sp = Prefs.sp(c)
        val at = sp.getLong("d_at", 0L)
        if (at == 0L) return "No SMS seen yet by Zound Pay"
        return Ledger.time(at) + "  ·  from " + sp.getString("d_from", "?") + "  ·  " + sp.getString("d_res", "")
    }

    fun volumeOk(c: Context): Boolean = (c.getSystemService(Context.AUDIO_SERVICE) as AudioManager).getStreamVolume(AudioManager.STREAM_MUSIC) > 0

    fun summary(c: Context): String {
        val sp = Prefs.sp(c)
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val sms = c.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val bat = (c.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(c.packageName)
        return "Zound Pay build " + Remote.installed(c) + "  |  " + Build.MANUFACTURER + " " + Build.MODEL + "  |  Android " + Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")\n" +
            "SMS permission: " + sms + "  |  battery unrestricted: " + bat + "\n" +
            "Media volume: " + am.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) + "  |  speech engine: " + Settings.Secure.getString(c.contentResolver, "tts_default_synth") + "\n" +
            "Speech status: " + sp.getString("d_tts", "unknown") + "  |  voice pack: " + Voice.status(c) + "\n" +
            "Last SMS: " + lastLine(c) + "  |  SMS seen: " + sp.getInt("d_n", 0) + "\n" +
            "Language: " + Prefs.lang(c) + "  |  senders: " + Prefs.senders(c) + "  |  announcements left: " + Prefs.left(c)
    }
}

/** A silent on-screen note for every announced payment (also helps if the voice fails). */
object Notifier {
    fun post(c: Context, text: String) {
        try {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("pay", "Payments", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null); enableVibration(false) })
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, "pay") else Notification.Builder(c)
            b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Zound Pay").setContentText(text).setAutoCancel(true)
            nm.notify((System.currentTimeMillis() % 100000).toInt(), b.build())
        } catch (e: Exception) { }
    }
}
