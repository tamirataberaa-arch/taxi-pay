package com.taxipay.app

data class Pay(val method: String, val name: String?, val amount: Double, val ref: String?)

object Parser {
    private val telebirr = Regex("""received\s+ETB\s+([\d,]+\.\d{2})\s+from\s+(.+?)\s*\(.*?\)\s+on\s+\d{2}/\d{2}/\d{4}\s+\d{2}:\d{2}:\d{2}\.\s*Your transaction number is\s+([A-Z0-9]{10})""", RegexOption.IGNORE_CASE)
    private val cbeBirr = Regex("""credited with\s+([\d,]+(?:\.\d{1,2})?)\s*Br\.?\s+from\s+(.+?)\s+on\s+\d{1,2}/\d{1,2}/\d{2,4}\s+\d{1,2}:\d{2}\s*,?\s*Txn\s*ID\s+([A-Z0-9]{8,14})""", RegexOption.IGNORE_CASE)
    private val cbeAmt = Regex("""credited with\s+ETB\s+([\d,]+\.\d{2})""", RegexOption.IGNORE_CASE)
    private val cbeRef = Regex("""BranchReceipt/(FT[A-Z0-9]{8,14})""", RegexOption.IGNORE_CASE)

    private fun num(s: String) = s.replace(",", "").toDouble()

    /** Returns null unless the message is clearly a "money received" message. Never guesses. */
    fun parse(raw: String): Pay? {
        val t = raw.replace(Regex("\\s+"), " ")
        telebirr.find(t)?.let { return Pay("Telebirr", it.groupValues[2].trim(), num(it.groupValues[1]), it.groupValues[3].uppercase()) }
        cbeBirr.find(t)?.let { return Pay("CBE Birr", it.groupValues[2].trim(), num(it.groupValues[1]), it.groupValues[3].uppercase()) }
        val a = cbeAmt.find(t)
        val r = cbeRef.find(t)
        if (a != null && r != null) return Pay("CBE", null, num(a.groupValues[1]), r.groupValues[1].uppercase())
        return null
    }
}
