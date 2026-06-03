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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GeminiClient(private val context: Context, private val assistant: FinancialAssistant) {

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "") ?: ""

        if (apiKey.isEmpty()) {
            return@withContext "⚠️ API Key Groq belum diatur di menu Pengaturan."
        }

        val db = AppDatabase.getDatabase(context)
        val sdf = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

        // 1. Ambil Data Kategori
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        // 2. Ambil Data Hutang/Piutang Aktif (Belum Lunas)
        val debts = db.debtDao().getAllDebts().first()
        val debtContext = StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Kontak: ${it.contactName}, Sisa Pinjaman: Rp ${it.remainingAmount}, Jenis: ${it.type}\n")
        }

        // 3. AMBIL DATA TRANSAKSI RIIL (KUNCI AGAR AI TIDAK NGAWUR)
        val transactions = db.transactionDao().getAllTransactions().first()
        val txContext = StringBuilder()
        transactions.take(40).forEach { // Ambil 40 transaksi terakhir sebagai referensi laporan
            val tDate = sdf.format(Date(it.timestamp))
            txContext.append("- Tanggal: $tDate, Nominal: Rp ${it.amount}, Tipe: ${it.type}, Kategori: ${it.categoryName}, Ket: ${it.note}\n")
        }

        val systemPrompt = """
            Anda adalah Akuntan AI Pribadi untuk Smart Finance Tracker. Anda WAJIB menganalisis data keuangan riil user di bawah ini. JANGAN PERNAH MENGARANG ATAU BERHALUSINASI JIKA DATA TIDAK ADA!

            DATA TRANSAKSI RIIL DI SQLITE HP USER SAAT INI:
            $txContext

            DAFTAR PINJAMAN/HUTANG AKTIF:
            $debtContext

            KATEGORI TERSEDIA:
            $catContext

            📋 ATURAN UTAMA:
            1. Jika user meminta laporan/rekap, hitung secara matematis dari DATA TRANSAKSI RIIL di atas. Jika data kosong, katakan dengan jujur bahwa belum ada riwayat transaksi tersemat.
            2. Jika user menyuruh mencatat cicilan (ex: "Dani bayar cicilan 50rb"), cari nama "DANI" di DAFTAR PINJAMAN AKTIF. Lihat ID-nya (misal ID-nya 2), lalu wajib buat tag <EXEC> dengan action_type "DEBT_PAYMENT" dan masukkan debt_id yang sesuai.

            Setiap kali selesai memberikan narasi analisis/jawaban, sertakan baris instruksi terstruktur satu baris di paling bawah dibungkus tag <EXEC>...</EXEC>.
            
            Contoh respons jika mencatat cicilan:
            Baik, pembayaran cicilan dari Dani sebesar Rp 50.000 telah saya terima dan dimasukkan ke sistem.
            <EXEC>{"action_type":"DEBT_PAYMENT", "debt_id":2, "pay_amount":50000}</EXEC>
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
                put("temperature", 0.1) // Kunci suhu rendah agar AI fokus pada data factual, anti-ngawur
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
            return@withContext "⚠️ Jaringan sibuk, silakan ulangi."
        }
    }
}
