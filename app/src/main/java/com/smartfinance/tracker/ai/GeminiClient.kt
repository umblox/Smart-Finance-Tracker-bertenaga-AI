package com.smartfinance.tracker.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiClient(private val context: Context, private val assistant: FinancialAssistant) {

    private val sharedPreferences = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
    
    // API Key Default dari Anda
    private val defaultApiKey = "AIzaSyBVOhVsXnPHX7O2U6hIXH0BaUuEeIbfGNg"

    private fun getApiKey(): String {
        return sharedPreferences.getString("gemini_api_key", defaultApiKey) ?: defaultApiKey
    }

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            val currentApiKey = getApiKey()
            
            val config = generationConfig {
                // Tempat konfigurasi parameter tambahan jika diperlukan
            }

            // Inisialisasi Model secara bersih tanpa parameter systemInstruction yang tidak dikenal versi SDK ini
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = currentApiKey,
                generationConfig = config
            )

            // 1. Jalankan validasi skop asisten keuangan secara lokal (Proteksi Utama)
            val localResponse = assistant.processNaturalLanguage(userMessage)
            
            // Jika instruksi lokal mendeteksi penolakan atau sukses memproses database, langsung kembalikan hasilnya
            if (!localResponse.contains("Maaf, sebagai asisten finansial")) {
                return@withContext localResponse
            }

            // 2. Gabungkan instruksi sistem ke dalam prompt sebagai konteks pembungkus (Trik Hybrid SDK Lama)
            val hybridPrompt = """
                CONTEXT & SYSTEM INSTRUCTION:
                ${assistant.systemInstruction}
                
                USER MESSAGE:
                $userMessage
            """.trimIndent()

            // Kirim ke Gemini untuk pemrosesan teks bahasa natural yang aman
            val response = model.generateContent(hybridPrompt)
            response.text ?: "Maaf, saya tidak dapat memahami pesan tersebut."
            
        } catch (e: Exception) {
            "Gagal terhubung ke Gemini AI. Pastikan API Key Anda di Pengaturan valid dan internet aktif. Error: ${e.localizedMessage}"
        }
    }
    
    // Fungsi untuk memperbarui API Key dari fragment pengaturan aplikasi
    fun saveCustomApiKey(newKey: String) {
        sharedPreferences.edit().putString("gemini_api_key", newKey).apply()
    }
    
    fun getSavedApiKey(): String {
        return sharedPreferences.getString("gemini_api_key", "") ?: ""
    }
}
