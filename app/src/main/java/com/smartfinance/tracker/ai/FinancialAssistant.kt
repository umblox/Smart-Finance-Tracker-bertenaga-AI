package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    suspend fun executeSmartJsonCommand(jsonStr: String): String {
        try {
            val json = JSONObject(jsonStr.trim())
            val actionType = json.optString("action_type", "CHAT_ONLY")

            when (actionType) {
                "TRANSACTION" -> {
                    val amount = json.optDouble("amount", 0.0)
                    var catId = json.optLong("category_id", 15L)
                    var catName = json.optString("category_name", "Lain-lain / Umum")
                    var cleanNote = json.optString("clean_note", "Transaksi AI").uppercase(Locale.ROOT)
                    var type = json.optString("type", "EXPENSE").uppercase(Locale.ROOT)

                    // HARD INTERCEPTOR PROTECTION: Kunci mati logika arah uang untuk kata kunci sensitif
                    if (cleanNote.contains("GAJI") || cleanNote.contains("TIPS") || cleanNote.contains("BONUS") || cleanNote.contains("UPAH")) {
                        type = "INCOME"
                        if (catId == 15L || catName.contains("Lain-lain")) {
                            catId = 1L
                            catName = "Gaji & Pendapatan"
                        }
                    }

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = type, categoryId = catId, categoryName = catName,
                            note = cleanNote, timestamp = System.currentTimeMillis()
                        ))
                        
                        val label = if (type == "INCOME") "🟢 PEMASUKAN" else "🔴 PENGELUARAN"
                        return "✅ **TERCATAT DI DASHBOARD:**\n▪️ **Jenis**: $label\n▪️ **Kategori**: $catName\n▪️ **Keterangan**: $cleanNote\n▪️ **Nominal**: ${formatRupiah.format(amount)}"
                    }
                }
                
                "DEBT_RECORD" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val name = json.optString("contact_name", "Teman").uppercase(Locale.ROOT)
                    val debtType = json.optString("debt_type", "DEBT").uppercase(Locale.ROOT) // DEBT atau RECEIVABLE

                    if (amount > 0.0) {
                        // SUNTIKKAN SECARA NYATA KE TABEL UTANG PIUTANG (DEBT_DAO)
                        db.debtDao().insertDebt(DebtEntity(
                            contactName = name, contactPhoneNumber = "0812", amount = amount,
                            remainingAmount = amount, type = debtType, note = "Dicatat otomatis via AI",
                            timestamp = System.currentTimeMillis(), isPaid = false
                        ))

                        // Sinkronisasikan efeknya langsung memotong atau menambah saldo dashboard
                        val isReceivable = debtType == "RECEIVABLE"
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = if (isReceivable) "EXPENSE" else "INCOME",
                            categoryId = if (isReceivable) 13L else 12L,
                            categoryName = if (isReceivable) "Piutang (Memberi Pinjaman)" else "Hutang (Saya Meminjam)",
                            note = if (isReceivable) "PINJAMAN KELUAR KE $name" else "PINJAMAN MASUK DARI $name",
                            timestamp = System.currentTimeMillis()
                        ))

                        val jenisLabel = if (debtType == "DEBT") "⚠️ Hutang (Uang Masuk Dashboard)" else "💰 Piutang (Uang Keluar Dashboard)"
                        return "✅ **TRANSAKSI PINJAMAN BERHASIL DISIMPAN!**\n▪️ **Jenis**: $jenisLabel\n▪️ **Nama Kontak**: $name\n▪️ **Nominal**: ${formatRupiah.format(amount)}"
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
                            
                            db.debtDao().insertDebt(matchDebt.copy(
                                remainingAmount = nextRemaining,
                                isPaid = nextRemaining <= 0.0
                            ))

                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = payAmount, type = txType, categoryId = 11L, categoryName = "Cicilan & Pinjaman",
                                note = "CICILAN PINJAMAN OLEH ${matchDebt.contactName}", timestamp = System.currentTimeMillis()
                            ))

                            return "✅ **UPDATE CICILAN SUKSES!**\n▪️ **Kontak**: ${matchDebt.contactName}\n▪️ **Dibayarkan**: ${formatRupiah.format(payAmount)}\n▪️ **Sisa Pinjaman**: ${formatRupiah.format(nextRemaining)}"
                        }
                    }
                }

                "CREATE_CATEGORY" -> {
                    val targetName = json.optString("target_name", "").trim()
                    var catType = json.optString("category_type", "EXPENSE").uppercase(Locale.ROOT)
                    
                    if (targetName.lowercase().contains("tips") || targetName.lowercase().contains("gaji")) {
                        catType = "INCOME"
                    }

                    if (targetName.isNotEmpty()) {
                        db.categoryDao().insertCategory(CategoryEntity(name = targetName, type = catType, iconName = "ic_custom"))
                        val label = if (catType == "INCOME") "🟢 INCOME (Pemasukan)" else "🔴 EXPENSE (Pengeluaran)"
                        return "✅ **KATEGORI BARU SUKSES DAFTAR TEMPEL!**\n▪️ **Nama**: \"$targetName\"\n▪️ **Sistem Logika**: $label"
                    }
                }
            }
        } catch (e: Exception) {
            return "Bahasa alami dimengerti, namun eksekusi query SQLite Room terhambat."
        }
        return "Format instruksi diproses."
    }

    suspend fun processLocalFallback(input: String, debugError: String): String {
        return "🤖 **Sistem Cadangan Lokal**: Layanan Groq API Cloud sedang tidak stabil. Silakan gunakan menu form input manual pada halaman utama dashboard untuk pencatatan instan."
    }
}
