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
            debtContext.append("- ID Pinjaman: ${it.id}, Nama Kontak: ${it.contactName}, Sisa Hutang: Rp ${it.remainingAmount}, Jenis: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah Otak AI Finansial paling cerdas. Tugas Anda menganalisis kalimat percakapan bahasa alami pengguna dan merespons dengan narasi penjelasan santun, diikuti instruksi data JSON terstruktur di baris paling bawah.

            KATEGORI SISTEM SAAT INI:
            $catContext

            DAFTAR TRANSAKSI PINJAMAN YANG BELUM LUNAS:
            $debtContext

            TENTUKAN STRUKTUR DENGAN ATURAN TEGAS INI:
            1. PENCATATAN UTANG PIUTANG BARU: Jika user berhutang atau memberi pinjaman (Contoh: "hutang ke samsul 50000" atau "samsul pinjam uang saya 100000"), gunakan action_type "DEBT_RECORD", isi "amount", "contact_name", dan "debt_type" ("DEBT" jika user berhutang, "RECEIVABLE" jika user memberi pinjaman).
            2. PELUNASAN / CICILAN PINJAMAN: Jika user/orang lain melunasi atau menyicil hutang (Contoh: "arianto melunasi semua hutangnya"), cari Nama Kontak yang cocok di daftar pinjaman belum lunas. Gunakan action_type "DEBT_PAYMENT", isi "debt_id" dengan ID pinjamannya, dan isi "pay_amount" dengan nominal pelunasan penuh/sebagian.
            3. TAMBAH KATEGORI BARU: Jika user ingin membuat kategori baru (Contoh: "buat kategori tips kurir"), gunakan action_type "CREATE_CATEGORY", tentukan "target_name", dan "category_type" ("INCOME" atau "EXPENSE") berdasarkan analisis logika bahasa. Kata "tips", "gaji", "bonus" MUTLAK adalah INCOME.
            4. TRANSAKSI BIASA: Jika pengeluaran/pemasukan biasa (Contoh: "gaji 770000"), gunakan action_type "TRANSACTION". Pilih "category_id" yang paling mendekati dari daftar di atas. Perbaiki typo jika ada.

            Di baris paling akhir dari jawaban Anda, Anda WAJIB menyertakan baris string bertanda token khusus [JSON_CMD] diikuti objek data JSON murni satu baris tanpa markdown.
            Contoh balasan Anda:
            Kategori baru berhasil dianalisis dan ditambahkan ke dalam memori aplikasi Anda.
            [JSON_CMD]{"action_type":"CREATE_CATEGORY", "target_name":"Tips Kurir", "category_type":"INCOME"}
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
                val rawResponse = JSONObject(reader.readText()).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content").trim()
                
                if (rawResponse.contains("[JSON_CMD]")) {
                    val textParts = rawResponse.split("[JSON_CMD]")
                    val aiNarration = textParts[0].trim()
                    val jsonCommand = textParts[1].trim()
                    
                    // Jalankan perintah biner ke SQLite
                    val executionResult = assistant.executeSmartJsonCommand(jsonCommand)
                    return@withContext "$aiNarration\n\n$executionResult"
                }
                return@withContext rawResponse
            } else {
                return@withContext "⚠️ Hubungan ke Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Server Groq sedang sibuk. Silakan gunakan input manual dashboard."
        }
    }
}
