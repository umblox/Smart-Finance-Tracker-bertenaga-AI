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
            Anda adalah Otak Kecerdasan buatan pelacak keuangan. Tugas Anda mengekstrak kalimat menjadi objek JSON perintah.
            
            KATEGORI DATABASE:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS SAAT INI:
            $debtContext

            ATURAN MUTLAK JALUR PINJAMAN:
            1. Jika kalimat user bertema HUTANG / PIUTANG BARU (Contoh: "hutang ke samsul 50000" atau "saya pinjamkan budi uang 10000"):
               Anda WAJIB memakai action_type "DEBT_RECORD".
               - "amount": nominal angka.
               - "contact_name": nama subjek orang (Contoh: "SAMSUL", "BUDI").
               - "debt_type": Isi "DEBT" jika user yang meminjam/berhutang (ID Kategori 12). Isi "RECEIVABLE" jika user yang meminjamkan uang ke orang lain (ID Kategori 13).
            2. Jika transaksi biasa (Contoh: "gaji 770000" atau "beli seblak 15000"):
               Set action_type menjadi "TRANSACTION". Ingat kata "gaji", "tips", "bonus" tipe harganya adalah "INCOME".
               
            Respons HANYA berupa JSON mentah satu baris tanpa tambahan kata lain:
            {"action_type":"DEBT_RECORD", "amount":50000, "contact_name":"SAMSUL", "debt_type":"DEBT"}
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
                val rawJsonResult = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.executeSmartJsonCommand(rawJsonResult)
            } else {
                return@withContext "⚠️ Gangguan jaringan Groq Server (${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Koneksi lambat, coba ulangi beberapa saat lagi."
        }
    }
}
