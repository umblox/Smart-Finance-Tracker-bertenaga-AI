package com.smartfinance.tracker.utils // 🔥 Wajib pakai utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Locale

class RecurringTxWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val firestore = FirebaseManager.getFirestore()
        val now = System.currentTimeMillis()

        try {
            // 1. Tarik semua jadwal yang aktif dan waktunya sudah lewat/pas
            val snap = firestore.collection("recurring_transactions")
                .whereEqualTo("isActive", true)
                .whereLessThanOrEqualTo("nextExecutionTime", now)
                .get()
                .await()

            for (doc in snap.documents) {
                val amount = doc.getDouble("amount") ?: 0.0
                val type = (doc.getString("type") ?: "EXPENSE").uppercase(Locale.ROOT)
                val catId = doc.getLong("categoryId") ?: 15L
                val catName = doc.getString("categoryName") ?: "Umum"
                val note = doc.getString("note") ?: "Transaksi Berkala"
                val interval = doc.getString("interval") ?: "MONTHLY"
                val hasEndDate = doc.getBoolean("hasEndDate") ?: false
                val endDate = doc.getLong("endDate") ?: Long.MAX_VALUE
                val contactName = doc.getString("contactName") ?: ""

                if (amount <= 0.0) continue

                // 2. 🔥 EKSEKUSI PENCATATAN TRANSAKSI (Sama tangguhnya dengan AI)
                val txId = "tx_${System.currentTimeMillis()}_${(1000..9999).random()}"
                
                if (type == "DEBT" || type == "RECEIVABLE") {
                    val debtId = "debt_${System.currentTimeMillis()}_${(1000..9999).random()}"
                    val cName = contactName.ifEmpty { "SISTEM BERKALA" }.uppercase(Locale.ROOT)
                    val flowType = if (type == "RECEIVABLE") "EXPENSE" else "INCOME"
                    val realCatId = if (type == "RECEIVABLE") 104L else 101L
                    val realCatName = if (type == "RECEIVABLE") "Piutang" else "Hutang"
                    val stNote = if (type == "RECEIVABLE") "MEMBERIKAN PINJAMAN KEPADA $cName" else "MENERIMA PINJAMAN DARI $cName"

                    val debtMap = hashMapOf("id" to debtId, "contactName" to cName, "contactPhoneNumber" to "", "amount" to amount, "remainingAmount" to amount, "type" to type, "note" to note, "timestamp" to now, "isPaid" to false)
                    firestore.collection("debts").document(debtId).set(debtMap).await()

                    val txMap = hashMapOf("id" to txId, "amount" to amount, "type" to flowType, "categoryId" to realCatId, "categoryName" to realCatName, "note" to "$stNote ($note)", "timestamp" to now, "debtId" to debtId)
                    firestore.collection("transactions").document(txId).set(txMap).await()
                } else {
                    val txMap = hashMapOf("id" to txId, "amount" to amount, "type" to type, "categoryId" to catId, "categoryName" to catName, "note" to note, "timestamp" to now)
                    firestore.collection("transactions").document(txId).set(txMap).await()
                }

                // 3. 🔥 HITUNG MATEMATIKA WAKTU BERIKUTNYA
                val cal = Calendar.getInstance()
                cal.timeInMillis = doc.getLong("nextExecutionTime") ?: now
                
                // Mencegah bug: Jika HP mati seminggu, dia akan terus maju sampai ketemu waktu di masa depan
                while (cal.timeInMillis <= now) {
                    when (interval) {
                        "DAILY" -> cal.add(Calendar.DAY_OF_YEAR, 1)
                        "WEEKLY" -> cal.add(Calendar.WEEK_OF_YEAR, 1)
                        "MONTHLY" -> cal.add(Calendar.MONTH, 1)
                        "YEARLY" -> cal.add(Calendar.YEAR, 1)
                        else -> cal.add(Calendar.MONTH, 1)
                    }
                }
                val nextTime = cal.timeInMillis

                // 4. 🔥 UPDATE STATUS JADWAL
                if (hasEndDate && nextTime > endDate) {
                    // Waktunya habis, matikan jadwalnya
                    firestore.collection("recurring_transactions").document(doc.id).update("isActive", false).await()
                } else {
                    // Update jadwal untuk eksekusi selanjutnya
                    firestore.collection("recurring_transactions").document(doc.id).update("nextExecutionTime", nextTime).await()
                }
            }
            return Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.retry() 
        }
    }
}
