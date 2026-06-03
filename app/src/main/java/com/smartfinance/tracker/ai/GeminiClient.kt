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
            Anda adalah core engine AI finansial pintar. Anda wajib merespons pesan user dalam skema JSON terstruktur berikut tanpa markdown kode atau teks bebas di luarnya.
            
            KATEGORI DI DATABASE:
            $catContext
            
            DAFTAR PINJAMAN AKTIF DI DATABASE:
            $debtContext
            
            ATURAN EKSTRAKSI DATA:
            1. Jika transaksi biasa (pemasukan/pengeluaran), tentukan tipe, amount, category_id, category_name secara logis.
            2. Jika cicilan hutang, cari kontak di daftar pinjaman aktif. Ambil 'id' pinjamannya dan set ke 'debt_id'.
            3. Selalu isi 'ai_response' dengan kalimat balasan bahasa manusia yang ramah, sopan, dan mengabarkan keberhasilan aksi akuntansi Anda.

            SKEMA FORMAT JSON YANG WAJIB DIHASILKAN (PILIH SALAH SATU):
            - Transaksi: {"action_type":"TRANSACTION", "amount":50000, "type":"EXPENSE", "category_id":2, "category_name":"Makanan & Minuman", "clean_note":"BELI NASI", "ai_response":"Siap! Pengeluaran Rp 50.000 untuk Makanan & Minuman sudah saya catat."}
            - Cicilan: {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":20000, "ai_response":"Terima kasih! Pembayaran cicilan sebesar Rp 20.000 sudah sukses diperbarui."}
            - Hutang Baru: {"action_type":"DEBT_RECORD", "amount":100000, "contact_name":"BUDI", "debt_type":"RECEIVABLE", "ai_response":"Catatan piutang baru terkunci. Budi berhutang Rp 100.000 kepada Anda."}
        """.trimIndent()

        try {
            val url = URL("https://api.groq.com/openai/v1/chat/completions")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $apiKey")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.doOutput = true

            // AKTIFKAN JSON MODE RESMI DARI GROQ CLOUD API
            val jsonBody = JSONObject().apply {
                put("model", "llama-3.1-8b-instant")
                val messagesArray = org.json.JSONArray().apply {
                    put(JSONObject().apply { put("role", "system"); put("content", systemPrompt) })
                    put(JSONObject().apply { put("role", "user"); put("content", userMessage) })
                }
                put("messages", messagesArray)
                put("temperature", 0.0)
                
                // FORCE RESMI SERVER UNTUK MERESPONS JSON OBJECT
                put("response_format", JSONObject().apply { put("type", "json_object") })
            }

            conn.outputStream.use { os -> os.write(jsonBody.toString().toByteArray(Charsets.UTF_8)) }

            if (conn.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                // Langsung kirim JSON murni ke asisten lokal Android
                return@withContext assistant.parseAndExecuteRawAiResponse(rawResponse)
            } else {
                return@withContext "⚠️ Koneksi Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Cloud Groq penuh. Silakan ulangi pesan Anda."
        }
    }
}
