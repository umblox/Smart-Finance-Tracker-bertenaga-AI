package com.smartfinance.tracker.ai

import android.content.Context
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiClient(private val context: Context, private val assistant: FinancialAssistant) {

    private val sharedPreferences = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
    
    // API Key Default dari Anda jika di pengaturan masih kosong
    private val defaultApiKey = "AIzaSyBVOhVsXnPHX7O2U6hIXH0BaUuEeIbfGNg"

    private fun getApiKey(): String {
        return sharedPreferences.getString("gemini_api_key", defaultApiKey) ?: defaultApiKey
    }

    suspend fun sendMessageToAI(userMessage: String): String = withContext(Dispatchers.IO) {
        try {
            val currentApiKey = getApiKey()
            
            // Inisialisasi Model Gemini 2.5 Flash (Cocok untuk kecepatan tinggi & hemat kuota di Mobile)
            val model = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = currentApiKey,
                systemInstruction = content { text(assistant.systemInstruction) }
            )

            // Jalankan validasi skop asisten keuangan secara lokal & AI hybrid
            val localResponse = assistant.processNaturalLanguage(userMessage)
            
            // Jika instruksi lokal mendeteksi penolakan atau sudah memproses database, langsung kembalikan hasilnya
            if (!localResponse.contains("Maaf, sebagai asisten finansial")) {
                return@withContext localResponse
            }

            // Kirim ke Gemini untuk pemrosesan konteks bahasa natural yang lebih luas (jika lolos pengaman lokal)
            val response = model.generateContent(userMessage)
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

