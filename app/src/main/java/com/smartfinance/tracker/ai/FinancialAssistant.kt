package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.regex.Pattern

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    val systemInstruction = "Anda adalah Asisten Keuangan Pribadi di aplikasi Smart Finance Tracker."

    suspend fun processNaturalLanguage(input: String): String {
        val text = input.lowercase(Locale.ROOT).trim()

        // 1. Interseptor Cek Saldo Cepat Lokal
        if (text.contains("saldo") || text.contains("total uang")) {
            val transactions = db.transactionDao().getAllTransactions().first()
            var income = 0.0
            var expense = 0.0
            for (tx in transactions) {
                if (tx.type == "INCOME") income += tx.amount else expense += tx.amount
            }
            return "Saldo Anda saat ini: Rp ${String.format("%,.0f", income - expense)}"
        }

        // 2. Ambil Master Kategori untuk Konteks Gemini
        val categories = db.categoryDao().getAllCategories().first()
        val categoryContext = StringBuilder()
        categories.forEach { 
            categoryContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n")
        }

        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isNotEmpty()) {
            val systemPrompt = """
                Anda adalah mesin pembaca teks transaksi keuangan. Tugas Anda adalah mengekstrak kalimat dari user menjadi data terstruktur, memperbaiki typo, dan mencocokkannya ke kategori terdekat dari daftar ini:
                $categoryContext
                
                Aturan Pengisian:
                1. Jika nominal angka TIDAK ADA atau LUPA disebutkan, set "status" menjadi "NEED_AMOUNT".
                2. Di bidang "clean_note", isi HANYA dengan nama barang/jasa bersih dengan huruf kapital (Contoh: "PERTAMAX", "ROKOK SURYA", "NASI GORENG"). Jangan masukkan kalimat panjang dari user.
                
                Format Output WAJIB berupa JSON murni tanpa markdown, tanpa teks tambahan apa pun:
                {"amount": 25000.0, "type": "EXPENSE", "category_id": 2, "category_name": "Makanan", "clean_note": "NASI GORENG", "status": "SUCCESS", "message": "Berhasil"}
            """.trimIndent()

            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 6000
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    val contentsArray = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().put("text", "$systemPrompt\n\nKalimat User: $input"))
                            })
                        })
                    }
                    put("contents", contentsArray)
                }

                conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val jsonResponse = JSONObject(reader.readText())
                    val candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                    var rawAiResponse = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                    
                    // =========================================================================
                    // BLOK PENYEMBUH (ANTI-BOBOT): Bersihkan paksa tag markdown ```json jika Gemini bebal
                    // =========================================================================
                    if (rawAiResponse.contains("{")) {
                        rawAiResponse = rawAiResponse.substring(rawAiResponse.indexOf("{"), rawAiResponse.lastIndexOf("}") + 1)
                    }

                    val resultObj = JSONObject(rawAiResponse)
                    val status = resultObj.optString("status", "FAILED")
                    val message = resultObj.optString("message", "")

                    if (status == "SUCCESS") {
                        val amount = resultObj.optDouble("amount", 0.0)
                        val type = resultObj.optString("type", "EXPENSE")
                        val catId = resultObj.optLong("category_id", 1L)
                        val catName = resultObj.optString("category_name", "Umum")
                        val cleanNote = resultObj.optString("clean_note", "Belanja AI")

                        if (amount > 0.0) {
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = amount, type = type, categoryId = catId, categoryName = catName,
                                note = cleanNote.uppercase(Locale.ROOT), timestamp = System.currentTimeMillis()
                            ))
                            
                            return "📝 **BERHASIL DICATAT OLEH AI!**\n\n" +
                                   "▪️ **Keterangan**: $cleanNote\n" +
                                   "▪️ **Kategori**: $catName\n" +
                                   "▪️ **Nominal**: Rp ${String.format("%,.0f", amount)}"
                        }
                    } else if (status == "NEED_AMOUNT") {
                        return "Saya mendeteksi Anda ingin mencatat transaksi, namun **nominal harganya belum disebutkan**. Silakan ketik kembali beserta nominal uangnya ya."
                    }
                    
                    if (message.isNotEmpty()) return面 message
                }
            } catch (e: Exception) {
                // Jika koneksi gagal atau parsing JSON crash, otomatis fallback ke engine lokal bawah
            }
        }

        // =========================================================================
        // LOCAL BACKUP ENGINE (Berjalan otomatis jika internet/API bermasalah)
        // =========================================================================
        val numberPattern = Pattern.compile("\\d+")
        val numberMatcher = numberPattern.matcher(text)
        
        if (!numberMatcher.find()) {
            return "Saya mendeteksi transaksi Anda, namun **nominal uangnya tidak ditemukan**. Mohon ketik kembali beserta harganya (Contoh: 'jajan seblak 25000')."
        }
        
        val amount = numberMatcher.group().toDoubleOrNull() ?: 0.0
        val isIncome = text.contains("gaji") || text.contains("terima") || text.contains("masuk")
        val type = if (isIncome) "INCOME" else "EXPENSE"
        
        var cleanNote = input.replace(numberMatcher.group(), "", ignoreCase = true)
            .replace("rp", "", ignoreCase = true).replace("beli", "", ignoreCase = true)
            .replace("saya", "", ignoreCase = true).replace("tadi", "", ignoreCase = true)
            .replace("habis", "", ignoreCase = true).replace("jajan", "", ignoreCase = true).trim()
            
        if (cleanNote.isEmpty()) cleanNote = if (isIncome) "PEMASUKAN" else "PENGELUARAN"
        
        var catName = if (isIncome) "Gaji" else "Makanan/Umum"
        var catId = if (isIncome) 1L else 2L

        when {
            text.contains("pertamax") || text.contains("bensin") || text.contains("pertalite") -> {
                catName = "Transportasi"; catId = 4L; cleanNote = "BENSIN KENDARAAN"
            }
            text.contains("rokok") || text.contains("surya") || text.contains("udud") -> {
                catName = "Rokok/Pribadi"; catId = 3L; cleanNote = "ROKOK"
            }
            text.contains("seblak") || text.contains("makan") || text.contains("bakso") -> {
                catName = "Makanan"; catId = 2L; cleanNote = cleanNote.uppercase(Locale.ROOT)
            }
        }

        db.transactionDao().insertTransaction(TransactionEntity(
            amount = amount, type = type, categoryId = catId, categoryName = catName,
            note = cleanNote.uppercase(Locale.ROOT), timestamp = System.currentTimeMillis()
        ))

        return "📝 **BERHASIL DICATAT (LOCAL ENGINE)!**\n\n" +
               "▪️ **Keterangan**: ${cleanNote.uppercase(Locale.ROOT)}\n" +
               "▪️ **Kategori**: $catName\n" +
               "▪️ **Nominal**: Rp ${String.format("%,.0f", amount)}"
    }
}
