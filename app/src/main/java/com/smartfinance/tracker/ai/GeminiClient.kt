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
        
        // 1. Ambil Kategori untuk bahan otak Groq
        val categories = db.categoryDao().getAllCategories().first()
        val catContext = StringBuilder()
        categories.forEach { catContext.append("- ID: ${it.id}, Nama: ${it.name}, Tipe: ${it.type}\n") }

        // 2. Ambil semua riwayat pinjaman belum lunas (UNTUK SEMUA NAMA ORANG SIAP DETEKSI TYPO)
        val debts = db.debtDao().getAllDebts().first()
        val debtContext = StringBuilder()
        debts.filter { !it.isPaid }.forEach { 
            debtContext.append("- ID: ${it.id}, Nama Akurat: ${it.contactName}, Sisa Hutang: Rp ${it.remainingAmount}, Jenis Tabel: ${it.type}\n")
        }

        val systemPrompt = """
            Anda adalah Otak AI Finansial super cerdas yang menguasai logika matematika bahasa alami Indonesia dan deteksi nama toleransi typo (Fuzzy Matching).
            Tugas Anda adalah membaca kalimat user, mengekstrak data keuangan, lalu menghasilkan kesimpulan berupa JSON di baris akhir.

            KATEGORI DATABASE APLIKASI:
            $catContext

            DAFTAR PINJAMAN AKTIF DI SQLITE (SEMUA ORANG):
            $debtContext

            📋 ATURAN MATEMATIKA ANGKA & SINGKATAN UMUM:
            - Jika user menyebut "rb" atau "ribu", kalikan angka di depannya dengan 1.000 (Contoh: "50rb" atau "50 ribu" = 50000).
            - Jika user menyebut "jt" atau "juta", kalikan dengan 1.000.000 (Contoh: "1.5jt" atau "1 setengah juta" = 1500000).
            - Jika user menyebut "ratusan" atau "puluhan", hitung secara matematis nominal riilnya.

            👤 ATURAN DETEKSI NAMA & TOLERANSI TYPO (FUZZY MATCHING):
            - Jika user menyebut suatu nama (misal: "ariant", "arianto", "aryanto", "samsul", "samshul", dll), Anda WAJIB menyisir "DAFTAR PINJAMAN AKTIF" di atas.
            - Cari nama yang memiliki kemiripan karakter paling dekat (toleransi salah ketik hingga 3 huruf). Jika "ariant" mirip dengan "Arianto" yang punya sisa hutang, maka anggap subjek tersebut adalah "Arianto".

            ALUR DETERMINASI TINDAKAN (ACTION_TYPE):
            1. DEBT_PAYMENT (Pelunasan/Cicilan): Jika user/orang lain membayar hutang (Contoh: "ariant bayar hutangnya 150rb" atau "arianto lunas semua"). Jika kata "lunas semua" atau "semua hutangnya", set "pay_amount" sesuai dengan Sisa Hutang riil milik orang tersebut yang tertera di daftar atas secara matematis.
            2. DEBT_RECORD (Pinjaman Baru): Jika mencatat hutang/piutang baru dari orang baru atau orang lama (Contoh: "saya hutang ke iwan 50000"). Set "debt_type" menjadi "DEBT" (jika user berhutang) atau "RECEIVABLE" (jika user meminjamkan uang).
            3. TRANSACTION (Transaksi Biasa): Pengeluaran/pemasukan biasa (Contoh: "beli bensin 20rb"). Ingat kata "gaji", "tips", "bonus" harganya MUTLAK bertipe "INCOME".

            Format respons wajib menyelipkan token pembatas khusus [JSON_CMD] di baris paling akhir diikuti data JSON murni satu baris tanpa markdown.
            Format:
            Pesan narasi ringkas penjelasan Anda ke user (Gunakan format mata uang Rupiah yang rapi).
            [JSON_CMD]{"action_type":"DEBT_PAYMENT", "debt_id":1, "pay_amount":150000}
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
                put("temperature", 0.1) // Kunci suhu rendah agar kepatuhan kalkulasi matematis stabil
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
                    
                    val executionResult = assistant.executeSmartJsonCommand(jsonCommand)
                    return@withContext "[EXEC_RESULT]$aiNarration\n\n$executionResult"
                }
                return@withContext rawResponse
            } else {
                return@withContext "⚠️ Koneksi Groq terputus (HTTP ${conn.responseCode})"
            }
        } catch (e: Exception) {
            return@withContext "⚠️ Sistem Cloud Groq penuh. Silakan ulangi beberapa saat lagi."
        }
    }
}
