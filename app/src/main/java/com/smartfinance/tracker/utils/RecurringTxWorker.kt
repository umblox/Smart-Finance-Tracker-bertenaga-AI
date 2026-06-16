package com.smartfinance.tracker.utils

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.Calendar

object RecurringTxWorker {

    suspend fun checkAndExecuteDueTransactions() {
        val db = FirebaseFirestore.getInstance()
        val now = System.currentTimeMillis()

        try {
            // Tarik semua jadwal transaksi yang berstatus AKTIF
            val snapshot = db.collection("recurring_transactions")
                .whereEqualTo("isActive", true)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val nextExecutionTime = doc.getLong("nextExecutionTime") ?: Long.MAX_VALUE
                
                // Jika waktu sekarang sudah melewati batas waktu eksekusi
                if (now >= nextExecutionTime) {
                    val amount = doc.getDouble("amount") ?: 0.0
                    val type = doc.getString("type") ?: "EXPENSE"
                    val catId = doc.getLong("categoryId") ?: 15L
                    val catName = doc.getString("categoryName") ?: "Umum"
                    val note = doc.getString("note") ?: "Transaksi Otomatis"
                    val interval = doc.getString("interval") ?: "MONTHLY" // DAILY, WEEKLY, MONTHLY

                    // 1. Catat ke Database Transaksi
                    val txId = "tx_${System.currentTimeMillis()}"
                    val txMap = hashMapOf(
                        "id" to txId, "amount" to amount, "type" to type, 
                        "categoryId" to catId, "categoryName" to catName, 
                        "note" to "$note (Otomatis)", "timestamp" to now
                    )
                    db.collection("transactions").document(txId).set(txMap).await()

                    // 2. Hitung Waktu Eksekusi Berikutnya
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = now
                    when (interval) {
                        "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                    }

                    // 3. Update Jadwal di Cloud
                    db.collection("recurring_transactions").document(doc.id)
                        .update("nextExecutionTime", cal.timeInMillis, "lastExecutedTime", now)
                        .await()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

