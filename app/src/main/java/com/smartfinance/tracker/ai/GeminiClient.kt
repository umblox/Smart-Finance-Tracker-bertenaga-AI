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
            Anda adalah Otak AI Finansial pelacak keuangan pribadi yang sangat teliti dalam akuntansi hukum hutang-piutang Indonesia.
            Jawablah pesan dari user dengan bahasa alami yang sangat santun, jelas, ringkas, dan solutif.
            
            KATEGORI SISTEM DI HP USER:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS SAAT INI:
            $debtContext

            ⚠️ ATURAN EMAS BAHASA ALAMI PINJAMAN (JANGAN SAMPAI TERBALIK):
            1. PIUTANG (RECEIVABLE): Jika kalimat user bermakna USER MEMBERI PINJAMAN / UANG USER DIBAWA ORANG LAIN.
               Kata kunci: "[Nama] meminjam uang saya", "saya meminjamkan uang ke [Nama]", "[Nama] ngutang ke saya", "kasih pinjaman ke [Nama]".
               Tindakan: "action_type" WAJIB "DEBT_RECORD", dan "debt_type" WAJIB "RECEIVABLE".
            2. HUTANG (DEBT): Jika kalimat user bermakna USER MEMINJAM UANG DARI ORANG LAIN / USER HARUS BAYAR BALIK.
               Kata kunci: "saya meminjam uang ke [Nama]", "saya hutang ke [Nama]", "pinjam uang dari [Nama]".
               Tindakan: "action_type" WAJIB "DEBT_RECORD", dan "debt_type" WAJIB "DEBT".
               
            LOGIKA MATEMATIKA NOMINAL ANGKA:
            - Akhiran "rb" atau "ribu" = dikali 1.000 (ex: 250rb = 250000).
            - Akhiran "jt" atau "juta" = dikali 1.000.000 (ex: 1.5jt = 1500000).
            
            Di baris paling akhir dari jawaban Anda, Anda WAJIB menyertakan objek JSON satu baris yang diawali langsung dengan tanda kurung kurawal tanpa kata pembatas atau markdown apa pun.
            
            Contoh Format Balasan:
            Baik, saya telah mencatat transaksi piutang baru bahwa Arianto meminjam uang Anda sebesar Rp 250.000.
            {"action_type":"DEBT_RECORD", "amount":250000, "contact_name":"ARIANTO", "debt_type":"RECEIVABLE"}
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
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Jaringan sedang sibuk, silakan kirim ulang pesan Anda."
        }
    }
}
