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
            Anda adalah Asisten Keuangan Pribadi yang super cerdas, ramah, dan sangat ahli dalam akuntansi Indonesia. 
            Jawablah pesan dari user dengan bahasa alami yang sangat santun, jelas, dan solutif.
            
            KATEGORI SISTEM YANG TERSEDIA DI HP USER:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS SAAT INI:
            $debtContext

            LOGIKA MATEMATIKA INDONESIA:
            - "rb" atau "ribu" artinya dikali 1.000 (ex: 50rb = 50000).
            - "jt" atau "juta" artinya dikali 1.000.000 (ex: 1.5jt = 1500000).
            - Kata "gaji", "gajian", "tips", "bonus", "cuan", "upah" MUTLAK adalah INCOME (Pemasukan). Jangan keliru!

            👤 TOLERANSI TYPO NAMA ORANG:
            Jika user menyebut nama orang (misal "ariant", "aryanto", "samsul", "samshul"), cocokkan secara cerdas dengan nama terdekat di DAFTAR PINJAMAN BELUM LUNAS di atas.

            TUGAS TAMBAHAN (STRUKTUR DATA BACKGROUND):
            Setelah Anda menulis jawaban ramah untuk user, Anda WAJIB menyertakan objek JSON satu baris di baris paling akhir dari jawaban Anda untuk dibaca oleh sistem Android.
            
            Aturan JSON Akhir:
            1. Jika transaksi biasa: {"action_type":"TRANSACTION", "amount":77000, "type":"INCOME", "category_id":1, "category_name":"Gaji & Pendapatan", "clean_note":"GAJI"}
            2. Jika pelunasan/cicilan hutang: {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":150000}
            3. Jika buat kategori baru: {"action_type":"CREATE_CATEGORY", "target_name":"Tips Kurir", "category_type":"INCOME"}

            INGAT: Jangan pakai kata kunci rahasia lagi. Cukup tulis jawaban normal Anda, lalu taruh objek JSON-nya di baris paling akhir.
        """.trimIndent()

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
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
                
                // Kirim respons mentah utuh ke asisten untuk dipilah secara regex aman
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Jaringan sibuk, silakan kirim ulang pesan Anda."
        }
    }
}
