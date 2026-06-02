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
            Anda adalah core engine AI finansial untuk Smart Finance Tracker. 
            Tugas Anda adalah membaca kalimat bahasa alami user, menganalisis maksud akuntansinya secara cerdas, lalu menghasilkan output HANYA berupa objek JSON murni satu baris.
            TIDAK BOLEH MENULIS TEKS DI LUAR STRUKTUR JSON. JANGAN BERIKAN MARKDOWN KODE (```json).

            KATEGORI TERSEDIA DI HP USER:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS SAAT INI:
            $debtContext

            📋 LOGIKA MATEMATIKA INDONESIA:
            - "rb" atau "ribu" = dikali 1.000 (ex: 50rb = 50000).
            - "jt" atau "juta" = dikali 1.000.000 (ex: 1.5jt = 1500000).
            - Kata "gaji", "gajian", "tips", "bonus", "cuan", "upah" MUTLAK bertipe INCOME (Pemasukan).

            📋 LOGIKA BAHASA ALAMI PINJAMAN:
            - PIUTANG (RECEIVABLE): Jika uang user dipinjam orang lain (ex: "Dani pinjam uang saya 50000"). Set action_type "DEBT_RECORD", debt_type "RECEIVABLE".
            - HUTANG (DEBT): Jika user meminjam uang dari orang lain. Set action_type "DEBT_RECORD", debt_type "DEBT".
            - CICILAN/PELUNASAN: Jika seseorang membayar/mencicil pinjaman (ex: "Dani bayar cicilan hutang 50000"). Cari nama terdekat di daftar pinjaman aktif di atas (toleransi typo), gunakan action_type "DEBT_PAYMENT", set debt_id sesuai data di atas, dan hitung nominal bayarnya.

            Tulis kalimat balasan ramah, santun, dan solutif Anda kepada user di dalam field "ai_response".

            STRUKTUR JSON WAJIB (PILIH SALAH SATU):
            1. Transaksi Biasa: {"action_type":"TRANSACTION", "amount":77000, "type":"INCOME", "category_id":1, "category_name":"Gaji & Pendapatan", "clean_note":"GAJI", "ai_response":"Halo Umam! Gaji sebesar Rp 77.000 telah berhasil saya catat ke dalam pemasukan Anda."}
            2. Pinjaman Baru: {"action_type":"DEBT_RECORD", "amount":50000, "contact_name":"DANI", "debt_type":"RECEIVABLE", "ai_response":"Catatan piutang baru berhasil disimpan. Dani memiliki pinjaman sebesar Rp 50.000 kepada Anda."}
            3. Bayar Cicilan: {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":50000, "ai_response":"Terima kasih! Pembayaran cicilan dari Dani sebesar Rp 50.000 berhasil dicatat dan memperbarui sisa pinjamannya."}
            4. Buat Kategori: {"action_type":"CREATE_CATEGORY", "target_name":"Tips Kurir", "category_type":"INCOME", "ai_response":"Kategori baru 'Tips Kurir' berhasil dibuat sebagai bagian dari Pemasukan."}
        """.trimIndent()

        try {
            val url = URL("[https://api.groq.com/openai/v1/chat/completions](https://api.groq.com/openai/v1/chat/completions)")
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
                
                // Teruskan JSON murni ini ke FinancialAssistant untuk dieksekusi murni
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Jaringan sibuk, silakan ulangi pesan Anda."
        }
    }
}
