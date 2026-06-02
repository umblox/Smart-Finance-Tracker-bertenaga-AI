package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
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
            val cleanJsonStr = jsonStr.replace("```json", "").replace("```", "").trim()
            val json = JSONObject(cleanJsonStr)
            val actionType = json.optString("action_type", "CHAT_ONLY")

            when (actionType) {
                "TRANSACTION" -> {
                    val amount = json.optDouble("amount", 0.0)
                    var catId = json.optLong("category_id", 15L)
                    var catName = json.optString("category_name", "Lain-lain / Umum")
                    val cleanNote = json.optString("clean_note", "Transaksi AI").uppercase(Locale.ROOT)
                    var type = json.optString("type", "EXPENSE").uppercase(Locale.ROOT)

                    if (cleanNote.contains("GAJI") || cleanNote.contains("TIPS") || cleanNote.contains("BONUS")) {
                        type = "INCOME"
                        if (catId == 15L) { catId = 1L; catName = "Gaji & Pendapatan" }
                    }

                    if (amount > 0.0) {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = type, categoryId = catId, categoryName = catName,
                            note = cleanNote, timestamp = System.currentTimeMillis()
                        ))
                        val label = if (type == "INCOME") "🟢 PEMASUKAN" else "🔴 PENGELUARAN"
                        return "✅ **TERCATAT DI DASHBOARD:**\n▪️ **Jenis**: $label\n▪️ **Kategori**: $catName\n▪️ **Nominal**: ${formatRupiah.format(amount)}"
                    }
                }
                
                "DEBT_RECORD" -> {
                    val amount = json.optDouble("amount", 0.0)
                    val name = json.optString("contact_name", "Teman").uppercase(Locale.ROOT)
                    val debtType = json.optString("debt_type", "DEBT").uppercase(Locale.ROOT)

                    if (amount > 0.0) {
                        db.debtDao().insertDebt(com.smartfinance.tracker.data.local.entity.DebtEntity(
                            contactName = name, contactPhoneNumber = "0812", amount = amount,
                            remainingAmount = amount, type = debtType, note = "Dicatat via AI Cerdas",
                            timestamp = System.currentTimeMillis(), isPaid = false
                        ))

                        val isReceivable = debtType == "RECEIVABLE"
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount,
                            type = if (isReceivable) "EXPENSE" else "INCOME",
                            categoryId = if (isReceivable) 13L else 12L,
                            categoryName = if (isReceivable) "Piutang (Memberi Pinjaman)" else "Hutang (Saya Meminjam)",
                            note = if (isReceivable) "PINJAMAN KELUAR KE $name" else "PINJAMAN MASUK DARI $name",
                            timestamp = System.currentTimeMillis()
                        ))

                        val jenisLabel = if (debtType == "DEBT") "⚠️ Hutang Baru" else "💰 Piutang Baru"
                        return "✅ **PINJAMAN DISIMPAN:**\n▪️ **Jenis**: $jenisLabel\n▪️ **Nama Orang**: $name\n▪️ **Nominal**: ${formatRupiah.format(amount)}"
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

                            // LOGIKA MATEMATIKA ARUS KAS CICILAN PINJAMAN:
                            // Jika matchDebt.type adalah DEBT (Hutang kita), saat kita mencicil bayar ke orang = EXPENSE (Uang keluar)
                            // Jika matchDebt.type adalah RECEIVABLE (Orang berhutang ke kita), saat dia mencicil bayar ke kita = INCOME (Uang masuk)
                            val txType = if (matchDebt.type == "DEBT") "EXPENSE" else "INCOME"
                            
                            db.transactionDao().insertTransaction(TransactionEntity(
                                amount = payAmount, type = txType, categoryId = 11L, categoryName = "Cicilan & Pinjaman",
                                note = "CICILAN ${matchDebt.type} OLEH ${matchDebt.contactName}", timestamp = System.currentTimeMillis()
                            ))

                            val statusLunas = if (nextRemaining <= 0.0) "LUNAS ✅" else "BELUM LUNAS ⏳"
                            return "✅ **UPDATE CICILAN SUKSES!**\n▪️ **Nama**: ${matchDebt.contactName}\n▪️ **Uang Masuk/Keluar**: Rp ${String.format("%,.0f", payAmount)}\n▪️ **Sisa Pinjaman**: ${formatRupiah.format(nextRemaining)} ($statusLunas)"
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
                        return "✅ **KATEGORI BARU SUKSES DAFTAR!**\n▪️ **Nama**: \"$targetName\"\n▪️ **Tipe**: $catType"
                    }
                }
            }
        } catch (e: Exception) {
            return "Bahasa alami dimengerti, namun eksekusi biner SQLite Room terhambat."
        }
        return "Format instruksi diproses."
    }

    suspend fun processLocalFallback(input: String, debugError: String): String {
        return "🤖 **Sistem Cadangan Lokal**: Layanan Groq API Cloud sedang tidak stabil."
    }
}
