package com.smartfinance.tracker.data.remote

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FirebaseSyncManager(context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * Replikasi Transaksi Tunggal ke Awan
     */
    fun syncSingleTransactionToCloud(transaction: TransactionEntity) {
        scope.launch {
            val txMap = hashMapOf(
                "id" to transaction.id,
                "amount" to transaction.amount,
                "type" to transaction.type,
                "categoryId" to transaction.categoryId,
                "categoryName" to transaction.categoryName,
                "note" to transaction.note,
                "timestamp" to transaction.timestamp
            )
            // Menggunakan ID Room sebagai nama dokumen agar tidak terjadi duplikasi data di cloud
            firestore.collection("transactions")
                .document("tx_${transaction.id}")
                .set(txMap)
        }
    }

    /**
     * Hapus Transaksi di Cloud jika user menghapusnya dari aplikasi
     */
    fun deleteTransactionFromCloud(transactionId: Long) {
        scope.launch {
            firestore.collection("transactions")
                .document("tx_$transactionId")
                .delete()
        }
    }

    /**
     * Migrasi & Backup Masal Otomatis
     */
    fun runFullMigrationBackup() {
        scope.launch {
            try {
                // 1. Sinkronisasi Kategori
                val localCategories = db.categoryDao().getAllCategories().first()
                localCategories.forEach { cat ->
                    val catMap = hashMapOf(
                        "id" to cat.id,
                        "name" to cat.name,
                        "type" to cat.type,
                        "iconName" to cat.iconName,
                        "parentCategoryId" to cat.parentCategoryId,
                        "isLocked" to cat.isLocked
                    )
                    firestore.collection("categories").document("cat_${cat.id}").set(catMap)
                }

                // 2. Sinkronisasi Transaksi
                val localTransactions = db.transactionDao().getAllTransactions().first()
                localTransactions.forEach { tx ->
                    val txMap = hashMapOf(
                        "id" to tx.id,
                        "amount" to tx.amount,
                        "type" to tx.type,
                        "categoryId" to tx.categoryId,
                        "categoryName" to tx.categoryName,
                        "note" to tx.note,
                        "timestamp" to tx.timestamp
                    )
                    firestore.collection("transactions").document("tx_${tx.id}").set(txMap)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

