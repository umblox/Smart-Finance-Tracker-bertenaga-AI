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
            debtContext.append("- ID: ${it.id}, Nama Kontak: ${it.contactName}, Sisa Hutang: Rp ${it.remainingAmount}, Jenis: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah Asisten AI Finansial pribadi yang super cerdas, interaktif, ramah, dan sangat ahli dalam akuntansi Indonesia.
            Jawablah setiap keluhan, pertanyaan, atau pencatatan transaksi dari user dengan kalimat bahasa alami manusia yang sangat santun dan solutif.

            KATEGORI DATABASE AKTIF DI HP USER:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS SAAT INI:
            $debtContext

            📋 LOGIKA AKUNTANSI MANDIRI:
            - "rb" atau "ribu" = dikali 1.000 (ex: 50rb = 50000).
            - "jt" atau "juta" = dikali 1.000.000 (ex: 1.5jt = 1500000).
            - Kata "gaji", "gajian", "tips", "bonus", "upah" MUTLAK bertipe INCOME (Pemasukan).
            - "Dani pinjam uang saya" = PIUTANG (RECEIVABLE). "Saya pinjam uang ke Dani" = HUTANG (DEBT).
            - "Dani bayar cicilan" = DEBT_PAYMENT. Cocokkan nama kontak dari daftar di atas (toleransi typo).

            TUGAS SISTEM UTAMA:
            Setiap kali selesai menulis jawaban ramah untuk user, Anda WAJIB menyertakan baris instruksi terstruktur satu baris di paling bawah yang dibungkus dengan tag <EXEC> dan </EXEC>.
            
            Contoh Format Output Anda:
            Tentu Umam, pengeluaran belanja Anda sebesar Rp 50.000 sudah berhasil saya catat dengan rapi.
            <EXEC>{"action_type":"TRANSACTION", "amount":50000, "type":"EXPENSE", "category_id":15, "category_name":"Lain-lain / Umum", "clean_note":"BELANJA"}</EXEC>
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
                put("temperature", 0.2) // Naikkan sedikit kreativitas agar bernalar normal
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq Cloud terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Server Groq sedang sibuk, silakan kirim ulang pesan."
        }
    }
}
