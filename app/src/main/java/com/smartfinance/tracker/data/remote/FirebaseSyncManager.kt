package com.smartfinance.tracker.data.remote

import android.content.Context
import android.widget.Toast
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FirebaseSyncManager(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val firestore = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(Dispatchers.IO)

    /**
     * 1. UPLOAD INSTAN: Kirim transaksi baru ke Cloud
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
            firestore.collection("transactions")
                .document("tx_${transaction.id}")
                .set(txMap)
        }
    }

    /**
     * 2. HAPUS INSTAN: Hapus data di Cloud
     */
    fun deleteTransactionFromCloud(transactionId: Long) {
        scope.launch {
            firestore.collection("transactions")
                .document("tx_$transactionId")
                .delete()
        }
    }

    /**
     * 3. SINKRONISASI UTAMA (Dua Arah): Backup data lokal ke Cloud, atau Restore jika lokal kosong
     */
    fun runFullMigrationBackup() {
        scope.launch {
            try {
                val localTransactions = db.transactionDao().getAllTransactions().first()
                
                if (localTransactions.isEmpty()) {
                    // JIKA LOKAL KOSONG, TARIK DATA DARI FIREBASE
                    downloadDataFromCloud()
                } else {
                    // JIKA LOKAL ADA ISI, JALANKAN BACKUP SEPERTI BIASA KE CLOUD
                    uploadLocalDataToCloud(localTransactions)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * 4. FUNGSI UPLOAD (Lokal -> Cloud)
     */
    private suspend fun uploadLocalDataToCloud(localTransactions: List<TransactionEntity>) {
        // Backup Kategori
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

        // Backup Transaksi
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
    }

    /**
     * 5. FUNGSI RESTORE (Cloud -> Lokal Room HP)
     */
    private fun downloadDataFromCloud() {
        // Ambil Transaksi dari Cloud Firestore
        firestore.collection("transactions").get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    scope.launch {
                        querySnapshot.documents.forEach { doc ->
                            val tx = TransactionEntity(
                                id = doc.getLong("id") ?: 0L,
                                amount = doc.getDouble("amount") ?: 0.0,
                                type = doc.getString("type") ?: "EXPENSE",
                                categoryId = doc.getLong("categoryId") ?: 0L,
                                categoryName = doc.getString("categoryName") ?: "Umum",
                                note = doc.getString("note") ?: "",
                                timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                            )
                            db.transactionDao().insertTransaction(tx)
                        }
                        
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "🔄 Data transaksi berhasil dipulihkan dari Cloud!", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

        // Ambil Kategori Kustom dari Cloud Firestore
        firestore.collection("categories").get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    scope.launch {
                        querySnapshot.documents.forEach { doc ->
                            val isLocked = doc.getBoolean("isLocked") ?: false
                            if (!isLocked) {
                                // FIX: Menggunakan pengambilan objek berjenis Long yang aman dari null pointer eksekusi
                                val parentId = doc.get("parentCategoryId") as? Long
                                val cat = CategoryEntity(
                                    id = doc.getLong("id") ?: 0L,
                                    name = doc.getString("name") ?: "",
                                    type = doc.getString("type") ?: "EXPENSE",
                                    iconName = doc.getString("iconName") ?: "ic_custom",
                                    parentCategoryId = parentId,
                                    isLocked = false
                                )
                                db.categoryDao().insertCategory(cat)
                            }
                        }
                    }
                }
            }
    }

    /**
     * 6. CHAT CLOUD BACKUP: Mengunggah riwayat chat yang bersih ke Firebase Cloud
     */
    fun syncChatHistoryToCloud(messages: List<com.smartfinance.tracker.data.model.ChatMessage>) {
        scope.launch {
            val chatList = ArrayList<HashMap<String, Any>>()
            messages.forEach { msg ->
                val msgMap = hashMapOf<String, Any>(
                    "text" to msg.text,
                    "isUser" to msg.isUser,
                    "timestamp" to System.currentTimeMillis()
                )
                chatList.add(msgMap)
            }

            val cloudBody = hashMapOf<String, Any>(
                "updatedAt" to System.currentTimeMillis(),
                "history" to chatList
            )

            // Disimpan dalam satu dokumen statis 'main_chat_history' agar hemat kuota database
            firestore.collection("user_chat")
                .document("main_chat_history")
                .set(cloudBody)
        }
    }

    /**
     * 7. CHAT CLOUD DELETE: Menghapus permanen riwayat chat di server awan
     */
    fun clearChatHistoryFromCloud(onComplete: () -> Unit = {}) {
        scope.launch {
            firestore.collection("user_chat")
                .document("main_chat_history")
                .delete()
                .addOnSuccessListener {
                    onComplete()
                }
        }
    }
}
