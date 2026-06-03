package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.BuildConfig
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
        val apiKey = BuildConfig.GROQ_API_KEY

        if (apiKey.isEmpty()) {
            return@withContext "{\"ai_response\":\"⚠️ API Key Groq aman tidak ditemukan dalam sistem build.\"}"
        }

        val db = AppDatabase.getDatabase(context)
        
        // Load Kategori
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = java.lang.StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        // Load Hutang Aktif
        val debts = db.debtDao().getAllDebts().first()
        val debtContext = java.lang.StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Kontak: ${it.contactName}, Sisa: Rp ${it.remainingAmount}, Tipe: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah core engine AI finansial pintar berbasis aturan linguistik akuntansi kaku untuk database SQLite.
            Tugas Anda adalah membedakan SUBJEK (Pelaku) dan OBJEK (Target) dari kalimat user agar tipe transaksi tidak tertukar.

            KATEGORI DI DATABASE:
            $catContext
            
            DAFTAR PINJAMAN AKTIF DI DATABASE (Gunakan ID ini untuk DEBT_PAYMENT):
            $debtContext

            MATRIKS LOGIKA SUBJEK PENTING (BACA DENGAN TELITI SEBELUM MENJAWAB):

            1. KATEGORI: DEBT_RECORD (Pencatatan Hutang/Piutang Baru)
               A. Tipe: "DEBT" (Hutang Saya / Uang Masuk ke Saya / Kewajiban Membayar)
                  Terjadi JIKA:
                  - Saya meminjam/berhutang KEPADA [Nama]. (Subjek: Saya, Objek: Orang Lain)
                  - [Nama] meminjamkan uangnya KEPADA SAYA. (Subjek: Orang Lain, Objek: Saya)
                  - Contoh: "saya ngutang ke arneta 500rb", "adit minjemin saya duit 40rb".
               B. Tipe: "RECEIVABLE" (Piutang Saya / Uang Keluar dari Saya / Hak Menagih)
                  Terjadi JIKA:
                  - Saya meminjamkan uang KEPADA [Nama]. (Subjek: Saya, Objek: Orang Lain)
                  - [Nama] meminjam/berhutang KEPADA SAYA. (Subjek: Orang Lain, Objek: Saya)
                  - Contoh: "arneta meminjam uang saya 500000", "saya pinjemin adit duit 40000".

            2. KATEGORI: DEBT_PAYMENT (Pembayaran Cicilan / Pelunasan)
               Cari nama kontak di daftar aktif di atas. Tentukan siapa yang menyerahkan uang:
               A. JIKA: User/Saya yang membayar ke orang lain (Ex: "saya nyicil utang ke adit 20rb")
                  -> Cari kontak bernama ADIT yang bertipe "DEBT". Ambil ID-nya, set ke 'debt_id'.
               B. JIKA: Orang lain yang membayar ke saya (Ex: "arneta bayar cicilan utangnya ke saya 50rb")
                  -> Cari kontak bernama ARNETA yang bertipe "RECEIVABLE". Ambil ID-nya, set ke 'debt_id'.

            FORMAT RESPONS HARUS BERBENTUK JSON MURNI TANPA MARKDOWN (```json).
            PILIHAN STRUKTUR JSON:
            - Catat Hutang Baru: {"action_type":"DEBT_RECORD", "amount":100000, "contact_name":"BUDI", "debt_type":"DEBT", "ai_response":"Hutang baru dicatat. Anda berhutang Rp 100.000 kepada Budi."}
            - Catat Piutang Baru: {"action_type":"DEBT_RECORD", "amount":50000, "contact_name":"DANI", "debt_type":"RECEIVABLE", "ai_response":"Piutang dicatat. Dani berhutang Rp 50.000 kepada Anda."}
            - Cicilan: {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":20000, "ai_response":"Pembayaran cicilan berhasil diperbarui ke database."}
            - Transaksi Biasa: {"action_type":"TRANSACTION", "amount":15000, "type":"EXPENSE", "category_id":15, "category_name":"Lain-lain / Umum", "clean_note":"BELI KOPI", "ai_response":"Pengeluaran dicatat."}
        """.trimIndent()

        try {
            val url = URL("[https://api.groq.com/openai/v1/chat/completions](https://api.groq.com/openai/v1/chat/completions)")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.0) // Suhu 0 murni agar berpikir kaku secara matematis, anti-ngawur
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "{\"ai_response\":\"⚠️ Koneksi Groq terputus (HTTP ${conn.responseCode})\"}"
            }
        } catch (e: Exception) {
            return@withContext "{\"ai_response\":\"⚠️ Cloud Groq penuh. Silakan ulangi pesan Anda.\"}"
        }
    }
}
