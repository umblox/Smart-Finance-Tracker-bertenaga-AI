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

        val systemPrompt = """
            Anda adalah Otak AI Pemroses Transaksi Keuangan untuk Smart Finance Tracker.
            Tugas utama Anda adalah mengekstrak kalimat percakapan pengguna ke dalam bentuk JSON perintah terstruktur.
            
            DAFTAR KATEGORI NYATA DI APLIKASI SAAT INI:
            $catContext
            
            Tentukan jenis tindakan berdasarkan aturan ini:
            1. Jika user ingin MENCATAT TRANSAKSI UTANG/PIUTANG BARU (misal: "hutang ke budi 50000" atau "budi pinjam uang saya 100000"):
               Set "action_type" menjadi "DEBT_RECORD", "amount" nominal uang, "contact_name" nama orang tersebut, dan "debt_type" ("DEBT" jika user berhutang, "RECEIVABLE" jika user meminjamkan uang).
            2. Jika user ingin MEMBUAT KATEGORI BARU (misal: "tambah kategori jajan"):
               Set "action_type" menjadi "CREATE_CATEGORY", "target_name" nama kategori baru, dan "category_type" ("INCOME" atau "EXPENSE").
            3. Jika user ingin MENCATAT TRANSAKSI PENGELUARAN/PEMASUKAN BIASA (misal: "beli pertamax 20000"):
               Set "action_type" menjadi "TRANSACTION", "amount" nominal, dan pilih "category_id" serta "category_name" yang paling cocok dari daftar di atas. Perbaiki typo jika ada. Di bidang "clean_note", isi HANYA nama subjek barangnya (contoh: "PERTAMAX").
            4. Jika user hanya BERTANYA biasa atau meminta rekap:
               Set "action_type" menjadi "CHAT_ONLY".
               
            Anda WAJIB merespons HANYA dalam bentuk JSON mentah tanpa markdown, contoh:
            {"action_type":"TRANSACTION", "amount":20000, "category_id":3, "category_name":"Bahan Bakar & Transportasi", "clean_note":"PERTAMAX", "feedback":"Berhasil mencatat pengeluaran Pertamax Rp 20.000"}
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
                put("temperature", 0.1) // Set suhu rendah agar format JSON konsisten stabil
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawJsonResult = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                // Kirim JSON murni hasil olahan Groq ke FinancialAssistant untuk dieksekusi ke SQLite
                return@withContext assistant.executeSmartJsonCommand(rawJsonResult)
            } else {
                return@withContext "⚠️ Server Groq penuh. Mencoba beralih ke sistem lokal...\n\n" + 
                        assistant.processLocalFallback(userMessage, "HTTP ${conn.responseCode}")
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Koneksi lambat. Mencoba beralih ke sistem lokal...\n\n" + 
                    assistant.processLocalFallback(userMessage, e.message ?: "")
        }
    }
}
