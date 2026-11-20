package com.taxipay.app

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Reads a receipt screenshot on the phone (nothing is uploaded) and finds the transaction number. */
object Receipt {
    private val cand = Regex("""\b(FT[A-Z0-9]{8,12}|[A-Z0-9]{10})\b""")
    private val labelled = Regex("""(TRANSACTION|TXN|REFERENCE|REF)[A-Z ]{0,18}[^A-Z0-9]{0,6}([A-Z0-9]{8,12})""")

    fun readId(c: Context, uri: Uri, cb: (String) -> Unit) {
        try {
            val img = InputImage.fromFilePath(c, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
                .addOnSuccessListener { cb(pick(it.text)) }
                .addOnFailureListener { cb("") }
        } catch (e: Exception) { cb("") }
    }

    fun pick(text: String): String {
        val t = text.uppercase()
        val tokens = cand.findAll(t).map { it.value }.filter { s -> s.any { ch -> ch.isDigit() } && s.any { ch -> ch.isLetter() } }.toList()
        if (tokens.isEmpty()) return ""
        val key = labelled.find(t)?.groupValues?.get(2)
        if (key != null && tokens.contains(key)) return key
        return tokens.first()
    }
}
