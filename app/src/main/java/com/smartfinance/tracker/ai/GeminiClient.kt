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
        
        // 1. Ambil Kategori Aktif
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        // 2. Ambil Riwayat Hutang Piutang Aktif agar Groq bisa menghitung pelunasan otomatis
        val debts = db.debtDao().getAllDebts().first()
        val debtContext = StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID Pinjaman: ${it.id}, Nama Orang: ${it.contactName}, Sisa Hutang: Rp ${it.remainingAmount}, Jenis: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah Otak AI Finansial super cerdas untuk aplikasi Smart Finance Tracker.
            Tugas Anda adalah mengekstrak kalimat user menjadi instruksi JSON murni yang sangat akurat.

            KATEGORI DATABASE:
            $catContext

            DAFTAR PINJAMAN BELUM LUNAS DI HP USER:
            $debtContext

            ATURAN ANALISIS JALUR PERINTAH:
            1. PENTING (Logika Pendapatan): Kata seperti "gaji", "gajian", "tips", "bonus", "upah", "cuan" SECARA MUTLAK adalah INCOME (Pemasukan). Jangan pernah mengategorikannya sebagai EXPENSE.
            2. Jika user membuat kategori baru (Contoh: "buat kategori tips kurir"):
               Analisis kata subjeknya. "Tips" adalah uang masuk, maka set "category_type" menjadi "INCOME".
            3. Jika user melakukan PELUNASAN / CICILAN HUTANG PIUTANG (Contoh: "arianto melunasi semua hutangnya" atau "arianto bayar cicilan 20000"):
               Cari nama orang yang cocok dari DAFTAR PINJAMAN BELUM LUNAS di atas. 
               Set "action_type" menjadi "DEBT_PAYMENT".
               Isi "debt_id" dengan ID Pinjaman yang cocok.
               Isi "pay_amount" dengan sisa hutang orang tersebut jika dia "melunasi semua", atau isi sesuai nominal yang disebutkan jika dia menyicil sebagian.
            4. Jika transaksi biasa (Contoh: "beli rokok 20000"):
               Set "action_type" menjadi "TRANSACTION". Tentukan "type" ("INCOME" atau "EXPENSE") sesuai logika kategori target.
               
            Format Output WAJIB JSON murni tanpa markdown:
            {"action_type":"TRANSACTION", "amount":77000, "type":"INCOME", "category_id":1, "category_name":"Gaji & Pendapatan", "clean_note":"GAJI", "feedback":"Sukses"}
            
            Format Output jika Pelunasan/Cicilan Pinjaman:
            {"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":40000, "feedback":"Pelunasan terdeteksi"}
            
            Format Output jika Tambah Kategori Baru:
            {"action_type":"CREATE_CATEGORY", "target_name":"Tips Kurir", "category_type":"INCOME", "feedback":"Membuat kategori"}
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
                return@withContext "⚠️ Hubungan ke server Groq terputus (Code ${conn.responseCode}). Menggunakan mesin lokal..."
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Batasan limit tercapai. Menggunakan pemroses lokal darurat."
        }
    }
}
