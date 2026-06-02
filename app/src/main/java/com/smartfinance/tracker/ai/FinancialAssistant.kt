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

    suspend fun processNaturalLanguage(input: String): String {
        val text = input.lowercase(Locale.ROOT).trim()

        // 1. Fitur Cek Saldo Cepat Lokal
        if (text.contains("saldo") || text.contains("total uang")) {
            val transactions = db.transactionDao().getAllTransactions().first()
            var income = 0.0
            var expense = 0.0
            for (tx in transactions) {
                if (tx.type == "INCOME") income += tx.amount else expense += tx.amount
            }
            return "Saldo Anda saat ini: Rp ${String.format("%,.0f", income - expense)}"
        }

        // 2. Ambil Master Kategori dari SQLite untuk Konteks
        val categories = db.categoryDao().getAllCategories().first()
        val categoryContext = StringBuilder()
        categories.forEach { 
            categoryContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n")
        }

        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isNotEmpty()) {
            val systemPrompt = """
                Anda adalah mesin ekstraksi data keuangan bertenaga AI. Tugas Anda adalah menganalisis kalimat transaksi pengguna, memperbaiki typo, dan mencocokkannya ke kategori terdekat dari daftar ini:
                $categoryContext
                
                Aturan:
                1. Jika nominal angka TIDAK DISEBUTKAN oleh pengguna, set "status" menjadi "NEED_AMOUNT".
                2. Di bidang "clean_note", bersihkan kata sampah seperti "saya", "habis", "tadi". Tulis HANYA nama barang komoditasnya (Contoh: "ROKOK", "PERTAMAX", "SEBLAK").
                
                Format Output WAJIB JSON murni:
                {"amount": 20000.0, "type": "EXPENSE", "category_id": 2, "category_name": "Makanan", "clean_note": "SEBLAK", "status": "SUCCESS", "message": "Berhasil"}
            """.trimIndent()

            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 5000
                conn.doOutput = true

                val jsonBody = JSONObject().apply {
                    val contentsArray = org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().put("text", "$systemPrompt\n\nKalimat: $input"))
                            })
                        })
                    }
                    put("contents", contentsArray)
                }

                conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

                if (conn.responseCode == 200) {
                    val reader = BufferedReader(InputStreamReader(conn.inputStream))
                    val rawAiJson = JSONObject(reader.readText()).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts")
                        .getJSONObject(0).getString("text").trim()
                    
                    val resultObj = JSONObject(rawAiJson)
                    val status = resultObj.optString("status", "FAILED")
                    
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
                            return "📝 **BERHASIL DICATAT OLEH GEMINI!**\n\n▪️ **Keterangan**: $cleanNote\n▪️ **Kategori**: $catName\n▪️ **Nominal**: Rp ${String.format("%,.0f", amount)}"
                        }
                    } else if (status == "NEED_AMOUNT") {
                        return "Saya mendeteksi Anda ingin mencatat transaksi, namun **nominal uangnya belum disebutkan**. Silakan ketik ulang beserta nominal harganya ya."
                    }
                }
            } catch (e: Exception) {
                // Tangkap error koneksi/API untuk di-fallback ke mesin lokal bawah
            }
        }

        // =========================================================================
        // FALLBACK LOCAL ENGINE (Bekerja otomatis tanpa internet jika API Google Limit / Error 429)
        // =========================================================================
        val numberPattern = Pattern.compile("\\d+")
        val numberMatcher = numberPattern.matcher(text)
        
        if (!numberMatcher.find()) {
            return "Saya mendeteksi transaksi Anda, namun **nominal uangnya tidak ditemukan**. Mohon ketik kembali beserta harganya (Contoh: 'jajan seblak 25000')."
        }
        
        val amount = numberMatcher.group().toDoubleOrNull() ?: 0.0
        val isIncome = text.contains("gaji") || text.contains("terima") || text.contains("masuk")
        val type = if (isIncome) "INCOME" else "EXPENSE"
        
        // Bersihkan kalimat sampah secara mandiri menggunakan modul pemotong teks lokal
        var cleanNote = input.replace(numberMatcher.group(), "", ignoreCase = true)
            .replace("rp", "", ignoreCase = true).replace("beli", "", ignoreCase = true)
            .replace("saya", "", ignoreCase = true).replace("tadi", "", ignoreCase = true)
            .replace("habis", "", ignoreCase = true).replace("jajan", "", ignoreCase = true).trim()
            
        if (cleanNote.isEmpty()) cleanNote = if (isIncome) "PEMASUKAN" else "PENGELUARAN"
        
        var catName = if (isIncome) "Gaji" else "Makanan/Umum"
        var catId = if (isIncome) 1L else 2L

        // Pemetaan kategori cerdas lokal berdasarkan kata kunci populer
        when {
            text.contains("pertamax") || text.contains("bensin") || text.contains("pertalite") || text.contains("bensin") -> {
                catName = "Transportasi"; catId = 4L; cleanNote = "BENSIN KENDARAAN"
            }
            text.contains("rokok") || text.contains("surya") || text.contains("udud") -> {
                catName = "Rokok/Pribadi"; catId = 3L; cleanNote = "ROKOK"
            }
            text.contains("seblak") || text.contains("makan") || text.contains("bakso") -> {
                catName = "Makanan"; catId = 2L; cleanNote = cleanNote.uppercase(Locale.ROOT)
            }
        }

        // Simpan ke SQLite
        db.transactionDao().insertTransaction(TransactionEntity(
            amount = amount, type = type, categoryId = catId, categoryName = catName,
            note = cleanNote.uppercase(Locale.ROOT), timestamp = System.currentTimeMillis()
        ))

        return "📝 **BERHASIL DICATAT (LOCAL ENGINE - API SIBUK)!**\n\n" +
               "▪️ **Keterangan Bersih**: ${cleanNote.uppercase(Locale.ROOT)}\n" +
               "▪️ **Kategori Cocok**: $catName\n" +
               "▪️ **Nominal Angka**: Rp ${String.format("%,.0f", amount)}\n\n" +
               "*(Catatan: Karena server API Gemini sedang penuh/limit, sistem beralih menggunakan modul pencatatan lokal pintar HP Anda)*"
    }
}
