package com.taxipay.app

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Entry(val t: Long, val name: String, val phone: String, val amount: Double, val method: String)

/** Today's payments, kept on this phone only. A new day starts at midnight Ethiopia time (ከሌሊቱ 6 ሰዓት). */
object Ledger {
    private val ZONE = TimeZone.getTimeZone("Africa/Addis_Ababa")

    private fun dayStart(): Long = Calendar.getInstance(ZONE).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun time(t: Long): String = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = ZONE }.format(Date(t))
    fun money(d: Double): String = if (d == Math.floor(d)) String.format(Locale.US, "%,d", d.toLong()) else String.format(Locale.US, "%,.2f", d)

    private fun load(c: Context): JSONArray = try { JSONArray(Prefs.sp(c).getString("ledger", "[]")) } catch (e: Exception) { JSONArray() }

    /** Saves a payment that was announced. Lists older than 7 days are deleted. */
    @Synchronized
    fun add(c: Context, p: Pay) {
        val keep = System.currentTimeMillis() - 7 * 86400000L
        val old = load(c)
        val a = JSONArray()
        for (i in 0 until old.length()) { val o = old.getJSONObject(i); if (o.getLong("t") >= keep) a.put(o) }
        a.put(JSONObject().put("t", System.currentTimeMillis()).put("n", p.name ?: "").put("p", p.phone ?: "").put("a", p.amount).put("m", p.method))
        Prefs.sp(c).edit().putString("ledger", a.toString()).commit()
    }

    @Synchronized
    fun today(c: Context): List<Entry> {
        val s = dayStart()
        val a = load(c)
        val out = ArrayList<Entry>()
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            if (o.getLong("t") >= s) out.add(Entry(o.getLong("t"), o.optString("n"), o.optString("p"), o.getDouble("a"), o.optString("m")))
        }
        return out.sortedByDescending { it.t }
    }

    fun total(l: List<Entry>): Double = l.sumOf { it.amount }

    private fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""

    /** Saves today's list as a spreadsheet file (CSV) in Downloads. */
    fun download(a: Activity) {
        val list = today(a).sortedBy { it.t }
        if (list.isEmpty()) { Toast.makeText(a, "No payments to download yet", Toast.LENGTH_SHORT).show(); return }
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = ZONE }
        val sb = StringBuilder("\uFEFFTime,Name,Phone,Amount (Birr),Method\n")
        for (e in list) sb.append(fmt.format(Date(e.t))).append(',').append(q(e.name)).append(',').append(q(e.phone)).append(',').append(e.amount).append(',').append(q(e.method)).append('\n')
        sb.append(",,Total,").append(total(list)).append(",\n")
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = ZONE }.format(Date())
        val name = "taxi-pay-$day.csv"
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "text/csv"); put(MediaStore.MediaColumns.RELATIVE_PATH, "Download")
                }
                val uri = a.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
                a.contentResolver.openOutputStream(uri)!!.use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(a, "Saved in Downloads: $name", Toast.LENGTH_LONG).show()
            } else share(a, sb.toString())
        } catch (e: Exception) { share(a, sb.toString()) }
    }

    private fun share(a: Activity, text: String) {
        a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share list"))
    }
}
