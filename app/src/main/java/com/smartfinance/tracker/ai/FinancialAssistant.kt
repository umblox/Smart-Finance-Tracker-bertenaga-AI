package com.smartfinance.tracker.ai

import android.content.Context
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import java.util.Locale

class FinancialAssistant(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)

    suspend fun processNaturalLanguage(input: String): String {
        val text = input.lowercase(Locale.ROOT).trim()

        // 1. PENANGANAN FITUR CRUD KATEGORI LEWAT CHAT AI
        if (text.contains("buat kategori") || text.contains("tambah kategori")) {
            val cleanName = input.replace("buat kategori", "", ignoreCase = true)
                .replace("tambah kategori", "", ignoreCase = true).trim()
            if (cleanName.isNotEmpty()) {
                val type = if (cleanName.lowercase().contains("pemasukan")) "INCOME" else "EXPENSE"
                val finalName = cleanName.replace("pemasukan", "", ignoreCase = true)
                    .replace("pengeluaran", "", ignoreCase = true).trim()
                
                db.categoryDao().insertCategory(CategoryEntity(name = finalName, type = type))
                return "✅ Berhasil membuat kategori baru bernama: \"$finalName\" dengan tipe [$type]."
            }
            return "Silakan sebutkan nama kategori yang ingin dibuat. Contoh: \"buat kategori Jajan Pengeluaran\""
        }

        if (text.contains("hapus kategori")) {
            val targetName = input.replace("hapus kategori", "", ignoreCase = true).trim()
            val allCats = db.categoryDao().getAllCategories().first()
            val match = allCats.find { it.name.equals(targetName, ignoreCase = true) }
            
            return if (match != null) {
                db.categoryDao().deleteCategory(match)
                "🗑️ Kategori \"${match.name}\" telah berhasil dihapus dari sistem database."
            } else {
                "❌ Kategori bernama \"$targetName\" tidak ditemukan di dalam aplikasi."
            }
        }

        if (text == "lihat kategori" || text == "daftar kategori") {
            val allCats = db.categoryDao().getAllCategories().first()
            if (allCats.isEmpty()) return "Belum ada kategori yang terdaftar."
            val sb = StringBuilder("📋 DAFTAR KATEGORI SAAT INI:\n")
            allCats.forEach { sb.append("- ID [${it.id}] ${it.name} (${it.type})\n") }
            return sb.toString()
        }

        // 2. LOGIKA KECERDASAN DETEKSI TRANSAKSI OTOMATIS
        val isIncome = text.contains("pemasukan") || text.contains("terima") || text.contains("gaji") || text.contains("dapat uang")
        val isExpense = text.contains("beli") || text.contains("bayar") || text.contains("pengeluaran") || text.contains("belanja") || text.contains("habis")

        if (isIncome || isExpense) {
            // EKSTRAKSI NOMINAL ANGKA SECARA CERDAS
            val numberRegex = "\\d+".toRegex()
            val matches = numberRegex.findAll(text).map { it.value.toDouble() }.toList()
            
            // JIKA USER LUPA MEMASUKKAN NOMINAL -> JANGAN ERROR! Tanyakan dengan sopan
            if (matches.isEmpty()) {
                return "Saya mendeteksi Anda ingin mencatat transaksi mengenai barang tersebut, namun Anda **lupa memasukkan nominal uangnya**. \n\nBerapakah nominal pengeluaran/pemasukan yang ingin Anda catat untuk transaksi ini?"
            }
            
            val amount = matches.first()
            val type = if (isIncome) "INCOME" else "EXPENSE"

            // ANALISIS CERDAS PRODUK BARANG DAN INTEGRASI KATEGORI
            var detectedItem = "Transaksi Otomatis AI"
            var targetCategoryName = if (isIncome) "Pemasukan Utama" else "Lain-lain"
            var categoryId = if (isIncome) 1L else 2L

            if (isExpense) {
                val words = text.split(" ")
                val idxBeli = words.indexOfFirst { it == "beli" || it == "bayar" || it == "untuk" }
                if (idxBeli != -1 && idxBeli + 1 < words.size) {
                    // Ambil kata setelah kata kerja sebagai subjek keterangan barang bersih
                    detectedItem = words.subList(idxBeli + 1, words.size)
                        .joinToString(" ")
                        .replace(amount.toLong().toString(), "")
                        .replace("rp", "")
                        .replace("ribu", "")
                        .trim()
                }

                // AI melakukan klasifikasi kategori otomatis berdasarkan barang yang dibeli
                when {
                    text.contains("rokok") || text.contains("surya") || text.contains("sampoerna") || text.contains("magnum") -> {
                        targetCategoryName = "Konsumsi Pribadi"
                        detectedItem = "Rokok"
                        categoryId = 10L
                    }
                    text.contains("pertamax") || text.contains("bensin") || text.contains("pertalite") || text.contains("shell") || text.contains("oli") -> {
                        targetCategoryName = "Bahan Bakar & Transportasi"
                        detectedItem = "Bahan Bakar Kendaraan"
                        categoryId = 11L
                    }
                    text.contains("makan") || text.contains("bakso") || text.contains("nasi") || text.contains("sate") || text.contains("ayam") -> {
                        targetCategoryName = "Makanan & Minuman"
                        categoryId = 12L
                    }
                    text.contains("pulsa") || text.contains("kuota") || text.contains("wifi") || text.contains("indihome") -> {
                        targetCategoryName = "Tagihan & Komunikasi"
                        categoryId = 13L
                    }
                }
            } else {
                val words = text.split(" ")
                val idxTerima = words.indexOfFirst { it == "terima" || it == "gaji" || it == "dapat" }
                if (idxTerima != -1 && idxTerima + 1 < words.size) {
                    detectedItem = words.subList(idxTerima + 1, words.size)
                        .joinToString(" ")
                        .replace(amount.toLong().toString(), "")
                        .replace("rp", "")
                        .trim()
                }
            }

            // Pastikan entitas kategori sasaran terdaftar di database agar tidak melanggar foreign key constraint
            val existingCats = db.categoryDao().getAllCategories().first()
            val targetCategory = existingCats.find { it.id == categoryId } ?: existingCats.firstOrNull()
            val finalCatId = targetCategory?.id ?: 1L
            val finalCatName = targetCategory?.name ?: targetCategoryName

            // Simpan ke database transaksi utama dashboard
            val tx = TransactionEntity(
                amount = amount,
                type = type,
                categoryId = finalCatId,
                categoryName = finalCatName,
                note = detectedItem.uppercase(),
                timestamp = System.currentTimeMillis()
            )
            db.transactionDao().insertTransaction(tx)

            return "📝 **BERHASIL DICATAT KE DASHBOARD!**\n\n" +
                   "▪️ **Transaksi**: ${if (type == "INCOME") "Pemasukan Penuh" else "Pengeluaran Bersih"}\n" +
                   "▪️ **Keterangan Barang**: $detectedItem\n" +
                   "▪️ **Kategori Otomatis**: $finalCatName\n" +
                   "▪️ **Nominal**: Rp ${String.format("%,.0f", amount)}"
        }

        // Jika perintah tidak dikenali oleh mesin parser lokal, lempar ke server Gemini API untuk analisis luas
        return "Format kurang spesifik. Mengalihkan pencarian konteks ke kecerdasan server Gemini..."
    }
}
