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
            return@withContext "⚠️ API Key Groq aman tidak ditemukan dalam sistem build."
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
            
            DAFTAR PINJAMAN AKTIF DI DATABASE:
            $debtContext

            MATRIKS LOGIKA SUBJEK PENTING:
            1. KATEGORI: DEBT_RECORD (Pencatatan Hutang/Piutang Baru)
               A. Tipe: "DEBT" (Hutang Saya / Kewajiban Membayar) -> Terjadi jika: Saya meminjam/berhutang KEPADA [Nama], atau [Nama] meminjamkan uangnya KEPADA SAYA.
               B. Tipe: "RECEIVABLE" (Piutang Saya / Hak Menagih) -> Terjadi jika: Saya meminjamkan uang KEPADA [Nama], atau [Nama] meminjam/berhutang KEPADA SAYA.

            2. KATEGORI: DEBT_PAYMENT (Pembayaran Cicilan / Pelunasan)
               A. User/Saya membayar ke orang lain -> Cari kontak bertipe "DEBT", ambil ID-nya, set ke 'debt_id'.
               B. Orang lain membayar ke saya -> Cari kontak bertipe "RECEIVABLE", ambil ID-nya, set ke 'debt_id'.

            FORMAT RESPONS HARUS BERBENTUK JSON MURNI TANPA MARKDOWN (```json).
            PILIHAN STRUKTUR JSON:
            - Catat Hutang Baru: {"action_type":"DEBT_RECORD", "amount":100000, "contact_name":"BUDI", "debt_type":"DEBT", "ai_response":"Hutang baru dicatat. Anda berhutang Rp 100.000 kepada Budi."}
            - Catat Piutang Baru: {"action_type":"DEBT_RECORD", "amount":50000, "contact_name":"DANI", "debt_type":"RECEIVABLE", "ai_response":"Piutang dicatat. Dani berhutang Rp 50.000 kepada Anda."}
            - Cicilan: {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":20000, "ai_response":"Pembayaran cicilan berhasil diperbarui ke database."}
            - Transaksi Biasa: {"action_type":"TRANSACTION", "amount":15000, "type":"EXPENSE", "category_id":15, "category_name":"Lain-lain / Umum", "clean_note":"BELI BARANG", "ai_response":"Pengeluaran dicatat."}
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
                put("temperature", 0.0)
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                // Teruskan data ke asisten lokal untuk dieksekusi
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                val errorReader = BufferedReader(InputStreamReader(conn.errorStream ?: conn.inputStream))
                val errorString = errorReader.readText()
                return@withContext "⚠️ Eror Server Groq (HTTP ${conn.responseCode}): $errorString"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Gangguan Jaringan Lokal: ${e.localizedMessage ?: "Koneksi Timeout"}"
        }
    }
}
