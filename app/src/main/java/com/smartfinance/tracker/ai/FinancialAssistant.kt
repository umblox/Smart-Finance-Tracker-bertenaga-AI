package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    suspend fun executeSmartJsonCommand(jsonStr: String): String {
        try {
            // Bersihkan sisa karakter markdown penutup dari AI jika ada
            val cleanJsonStr = jsonStr.replace("```json", "").replace("
```", "").trim()
            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY")

            when (actionType) {
                "TRANSACTION" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val catId = json.optLong("category_id", 15L)
                    val catName = json.optString("category_name", "Lain-lain / Umum")
                    val cleanNote = json.optString("clean_note", "Transaksi AI")
                    
                    // PERBAIKAN MUTLAK: Ambil nilai type langsung dari kiriman cerdas JSON AI
                    val type = json.optString("type", "EXPENSE").uppercase(Locale.ROOT)

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, 
                            type = type, // Menyimpan secara tepat INCOME atau EXPENSE
                            categoryId = catId, 
                            categoryName = catName,
                            note = cleanNote.uppercase(Locale.ROOT), 
                            timestamp = System.currentTimeMillis()
                        ))
                        
                        val statusEmoji = if (type == "INCOME") "🟢 PEMASUKAN" else "🔴 PENGELUARAN"
                        return "📝 **TRANSAKSI BERHASIL DICATAT!**\n\n" +
                               "▪️ **Jenis Saldo**: $statusEmoji\n" +
                               "▪️ **Kategori Sistem**: $catName\n" +
                               "▪️ **Keterangan Bersih**: $cleanNote\n" +
                               "▪️ **Nominal**: ${formatRupiah.format(amount)}"
                    }
                }
                
                "DEBT_PAYMENT" -> {
                    val targetId = json.optLong("debt_id", -1L)
                    val payAmount = json.optDouble("pay_amount", 0.0)

                    if (targetId != -1L && payAmount > 0.0) {
                        val debts = db.debtDao().getAllDebts().first()
                        val matchDebt = debts.find { it.id == targetId }
                        
                        if (matchDebt != null) {
                            val nextRemaining = (matchDebt.remainingAmount - payAmount).coerceAtLeast(0.0)
                            
                            // 1. Perbarui sisa hutang di database pinjaman
                            val updatedDebt = matchDebt.copy(
                                remainingAmount = nextRemaining,
                                isPaid = nextRemaining <= 0.0
                            )
                            db.debtDao().insertDebt(updatedDebt)

                            // 2. Sinkronisasikan langsung ke saldo utama dashboard
                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = payAmount,
                                type = txType,
                                categoryId = 11L,
                                categoryName = "Cicilan & Pinjaman",
                                note = "PELUNASAN OTOMATIS AI OLEH ${matchDebt.contactName.uppercase()}",
                                timestamp = System.currentTimeMillis()
                            ))

                            return "🤝 **EKSEKUSI PELUNASAN BERHASIL!**\n\n" +
                                   "▪️ **Nama Kontak**: ${matchDebt.contactName}\n" +
                                   "▪️ **Nominal Dibayarkan**: ${formatRupiah.format(payAmount)}\n" +
                                   "▪️ **Sisa Hutang Tersisa**: ${formatRupiah.format(nextRemaining)}\n\n" +
                                   "*(Status pinjaman otomatis diubah menjadi ${if (nextRemaining <= 0.0) "LUNAS ✅" else "BELUM LUNAS ⏳"})*"
                        }
                    }
                    return "Saya memahami Anda ingin memproses pembayaran hutang, namun ID transaksi pinjaman tersebut tidak valid."
                }

                "CREATE_CATEGORY" -> {
                    val targetName = json.optString("target_name", "").trim()
                    val catType = json.optString("category_type", "EXPENSE").uppercase(Locale.ROOT)
                    
                    if (targetName.isNotEmpty()) {
                        db.categoryDao().insertCategory(CategoryEntity(name = targetName, type = catType, iconName = "ic_custom"))
                        
                        val typeLabel = if (catType == "INCOME") "🟢 INCOME (Pemasukan)" else "🔴 EXPENSE (Pengeluaran)"
                        return "🗂️ **KATEGORI BARU BERHASIL DIBUAT!**\n\n" +
                               "▪️ **Nama Kategori**: \"$targetName\"\n" +
                               "▪️ **Tipe Logika**: $typeLabel\n\n" +
                               "*Kategori ini sudah langsung terdaftar di sistem aplikasi Anda.*"
                    }
                }
            }
        } catch (e: Exception) {
            return "Perintah bahasa alami diterima, namun struktur biner pengolah data sedang penuh. Silakan coba kembali."
        }
        return "Format instruksi tidak spesifik."
    }

    suspend fun processLocalFallback(input: String, debugError: String): String {
        // Fallback cadangan teks reguler jika internet HP tiba-tiba mati
        return "🤖 **Sistem Cadangan Lokal**:\n\nKoneksi API Cloud Anda sedang tidak stabil. Silakan gunakan menu input transaksi manual pada halaman utama dashboard untuk pencatatan instan."
    }
                                                                      }
                                                                      
