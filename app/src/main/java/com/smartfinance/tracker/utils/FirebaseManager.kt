package com.smartfinance.tracker.utils

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.FirebaseFirestore
import org.json.JSONObject

object FirebaseManager {
    var isInitialized = false
        private set

    // Mengembalikan True jika berhasil, False jika gagal
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

            // Hapus instance lama secara paksa agar instance baru bisa masuk
            val apps = FirebaseApp.getApps(context)
            for (app in apps) { app.delete() }
            
            FirebaseApp.initializeApp(context, options)
            isInitialized = true
            true
        } catch (e: Exception) {
            e.printStackTrace()
            isInitialized = false
            false
        }
    }

    fun getFirestore(): FirebaseFirestore {
        return try {
            // Ambil dari instance yang aktif saat ini
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback (hanya terpanggil jika ada error parah di library)
            FirebaseFirestore.getInstance()
        }
    }
}
