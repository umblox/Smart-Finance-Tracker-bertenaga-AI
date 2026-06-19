package com.smartfinance.tracker.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

object FirebaseManager {

    fun init(context: Context, jsonString: String): Boolean {
        return try {
            val jsonObj = JSONObject(jsonString)
            val projectInfo = jsonObj.getJSONObject("project_info")
            val clientInfo = jsonObj.getJSONArray("client").getJSONObject(0).getJSONObject("client_info")
            val apiKey = jsonObj.getJSONArray("client").getJSONObject(0).getJSONArray("api_key").getJSONObject(0).getString("current_key")
            
            val options = FirebaseOptions.Builder()
                .setProjectId(projectInfo.getString("project_id"))
                .setApplicationId(clientInfo.getString("mobilesdk_app_id"))
                .setApiKey(apiKey)
                .build()

            // 🔥 RAHASIA UTAMA: Jangan pernah ganggu/delete [DEFAULT] app.
            // Kita buat Jalur VIP dengan nama "SmartFinanceApp"
            try {
                val oldApp = FirebaseApp.getInstance("SmartFinanceApp")
                oldApp.delete() // Bersihkan hanya jika user upload JSON yang beda lagi
            } catch (e: Exception) {
                // Belum ada instance VIP, abaikan.
            }
            
            // Inisialisasi Database murni dari JSON lu!
            FirebaseApp.initializeApp(context, options, "SmartFinanceApp")
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getFirestore(): FirebaseFirestore {
        return try {
            // 🔥 PAKSA semua fragment untuk pakai Jalur VIP ini
            val app = FirebaseApp.getInstance("SmartFinanceApp")
            FirebaseFirestore.getInstance(app)
        } catch (e: Exception) {
            // Jika user baru instal & belum upload JSON, 
            // kasih koneksi tumbal biar aplikasi nggak Force Close
            FirebaseFirestore.getInstance()
        }
    }
}
