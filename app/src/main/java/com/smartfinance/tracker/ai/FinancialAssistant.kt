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

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun processNaturalLanguage(input: String): String {
        val text = input.lowercase(Locale.ROOT).trim()

        // 1. Interseptor Fitur Cek Saldo Cepat secara Lokal (Menghemat Kuota API)
        if (text.contains("saldo") || text.contains("total uang")) {
            val transactions = db.transactionDao().getAllTransactions().first()
            var income = 0.0
            var expense = 0.0
            for (tx in transactions) {
                if (tx.type == "INCOME") income += tx.amount else expense += tx.amount
            }
            return "Saldo Anda saat ini: Rp ${String.format("%,.0f", income - expense)}"
        }

        // 2. Ambil API Key Gemini untuk pemrosesan cerdas
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""
        if (apiKey.isEmpty()) {
            return "Format kurang spesifik. (API Key belum diatur di Pengaturan untuk analisis cerdas)."
        }

        // 3. Ambil daftar kategori riil dari SQLite agar Gemini tahu pilihan kategori yang tersedia di HP-mu
        val categories = db.categoryDao().getAllCategories().first()
        val categoryContext = StringBuilder()
        categories.forEach { 
            categoryContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n")
        }

        // 4. Buat System Prompt ketat agar Gemini mengembalikan struktur JSON murni
        val systemPrompt = """
            Anda adalah mesin ekstraksi data keuangan bertenaga AI. Tugas Anda adalah menganalisis kalimat transaksi dari pengguna, memperbaiki typo jika ada, lalu mencocokkannya ke kategori yang paling sesuai dari daftar kategori di bawah ini.

            DAFTAR KATEGORI YANG TERSEDIA DI DATABASE:
            $categoryContext
            
            Aturan ekstraksi:
            1. Jika nominal angka TIDAK DITEMUKAN atau LUPA disebutkan oleh pengguna, Anda WAJIB mengisi bidang "amount" dengan nilai 0.
            2. Di bidang "clean_note", bersihkan kata-kata tidak penting. Isi HANYA dengan nama subjek barang/jasa yang dibeli (Contoh: "Rokok", "Bensin Pertamax", "Nasi Goreng"). Jangan masukkan seluruh kalimat user.
            3. Di bidang "category_id", pilihlah ID Kategori yang paling logis dan mendekati subjek barang dari daftar di atas. Jika benar-benar tidak ada yang cocok, gunakan ID kategori "Lain-lain" atau ID terkecil yang tersedia.

            Anda WAJIB merespons HANYA dalam format JSON mentah seperti ini, tanpa basa-basi, tanpa markdown ```json:
            {"amount": 50000.0, "type": "EXPENSE", "category_id": 2, "category_name": "Makanan", "clean_note": "Nasi Goreng", "status": "SUCCESS", "message": "Berhasil mencatat transaksi Nasi Goreng."}
            
            Jika nominal tidak ada, set status menjadi "NEED_AMOUNT" dan berikan pesan meminta nominal uang di bidang "message".
        """.trimIndent()

        // 5. Kirim HTTP Request langsung ke Google Gemini 2.5 Flash
        try {
            val url = URL("[https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey](https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey)")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
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
                val responseStr = reader.readText()
                val jsonResponse = JSONObject(responseStr)
                val candidate = jsonResponse.getJSONArray("candidates").getJSONObject(0)
                val rawAiJson = candidate.getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text").trim()
                
                // Parse JSON response dari Gemini
                val resultObj = JSONObject(rawAiJson)
                val status = resultObj.optString("status", "FAILED")
                val message = resultObj.optString("message", "")

                if (status == "SUCCESS") {
                    val amount = resultObj.optDouble("amount", 0.0)
                    val type = resultObj.optString("type", "EXPENSE")
                    val catId = resultObj.optLong("category_id", 1L)
                    val catName = resultObj.optString("category_name", "Umum")
                    val cleanNote = resultObj.optString("clean_note", "Transaksi AI")

                    if (amount > 0.0) {
                        // Suntikkan data bersih hasil olahan otak Gemini langsung ke SQLite Room DB
                        val tx = TransactionEntity(
                            amount = amount,
                            type = type,
                            categoryId = catId,
                            categoryName = catName,
                            note = cleanNote.uppercase(Locale.ROOT),
                            timestamp = System.currentTimeMillis()
                        )
                        db.transactionDao().insertTransaction(tx)
                        
                        return "📝 **BERHASIL DICATAT OLEH GEMINI!**\n\n" +
                               "▪️ **Keterangan**: $cleanNote\n" +
                               "▪️ **Kategori (Match)**: $catName\n" +
                               "▪️ **Nominal**: Rp ${String.format("%,.0f", amount)}"
                    }
                } else if (status == "NEED_AMOUNT") {
                    return message // Kembalikan pesan peringatan sopan dari Gemini jika nominal kosong
                }
                
                return message
            } else {
                return "Format kurang spesifik. Mengalihkan ke pemrosesan teks standar..."
            }
        } catch (e: Exception) {
            return "Format kurang spesifik. Gagal memproses kecerdasan AI lokal: ${e.message}"
        }
    }
}
