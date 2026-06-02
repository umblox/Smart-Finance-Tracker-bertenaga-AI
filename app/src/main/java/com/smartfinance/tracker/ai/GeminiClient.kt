package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GeminiClient(private val context: Context, private val assistant: FinancialAssistant) {

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq belum diatur di menu Pengaturan."
        }

        val db = AppDatabase.getDatabase(context)
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        val debts = db.debtDao().getAllDebts().first()
        val debtContext = StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID Pinjaman: ${it.id}, Nama Orang: ${it.contactName}, Sisa Hutang: Rp ${it.remainingAmount}, Jenis: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah Otak AI Finansial paling cerdas. Tugas Anda menganalisis kalimat bahasa alami pengguna secara nalar luas, lalu menghasilkan kesimpulan data terstruktur.

            DAFTAR KATEGORI NYATA DI HP USER SAAT INI:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS SAAT INI:
            $debtContext

            ATURAN STRUKTUR:
            1. PENCATATAN PINJAMAN: Jika bertema hutang/piutang baru, gunakan action_type "DEBT_RECORD", isi "amount", "contact_name", dan "debt_type" ("DEBT" atau "RECEIVABLE").
            2. PELUNASAN: Jika bertema pembayaran hutang, cari nama di daftar di atas, gunakan action_type "DEBT_PAYMENT", isi "debt_id" dan "pay_amount".
            3. KATEGORI BARU: Jika ingin membuat kategori baru, gunakan action_type "CREATE_CATEGORY", tentukan "target_name" dan "category_type" ("INCOME" atau "EXPENSE"). Kata "tips", "gaji", "bonus" adalah INCOME.
            4. TRANSAKSI BIASA: Jika pengeluaran/pemasukan biasa, gunakan action_type "TRANSACTION". Pilih "category_id" dan "category_name" paling cocok dari daftar di atas secara fleksibel (pahami konteks barang belanjaan bebas dari user).

            Format respons Anda wajib disisipkan token pembatas rahasia [JSON_CMD] di baris paling akhir diikuti objek data JSON murni satu baris tanpa markdown.
            Format:
            Jawaban penjelasan santun Anda di sini mengenai pengelompokan transaksi.
            [JSON_CMD]{"action_type":"TRANSACTION", "amount":50000, "type":"EXPENSE", "category_id":2, "category_name":"Makanan & Minuman", "clean_note":"BELANJA"}
        """.trimIndent()

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.1) 
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                if (rawResponse.contains("[JSON_CMD]")) {
                    val textParts = rawResponse.split("[JSON_CMD]")
                    val aiNarration = textParts[0].trim()
                    val jsonCommand = textParts[1].trim()
                    
                    // Jalankan biner SQLite, lalu gabungkan dengan token penanda internal baru [EXEC_RESULT]
                    val executionResult = assistant.executeSmartJsonCommand(jsonCommand)
                    return@withContext "[EXEC_RESULT]$aiNarration\n\n$executionResult"
                }
                return@withContext rawResponse
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Sistem Cloud Groq penuh."
        }
    }
}
