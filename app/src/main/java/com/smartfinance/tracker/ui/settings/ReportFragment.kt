package com.smartfinance.tracker.ui.settings

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class ReportFragment : Fragment() {

    private lateinit var db: AppDatabase
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        // ROOT CONTAINER UTAMA
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. HEADER TITLE
        val tvTitle = TextView(context).apply {
            text = "📊 ANALISIS LAPORAN KEUANGAN"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3748"))
            setPadding(44, 44, 44, 20)
        }
        root.addView(tvTitle)

        // 2. TAB NAVIGASI ATAS (TRANSAKSI VS HUTANG)
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(44, 0, 44, 20)
        }
        val btnTabTx = Button(context).apply { text = "Kategori Transaksi"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnTabDebt = Button(context).apply { text = "Hutang Piutang"; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        tabLayout.addView(btnTabTx)
        tabLayout.addView(btnTabDebt)
        root.addView(tabLayout)

        // 3. SCROLL CONTAINER UNTUK ISI KONTEN LAPORAN
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 0, 44, 44)
        }
        scrollView.addView(contentContainer)
        root.addView(scrollView)

        // WARNA DEFAULT TAB AKTIF
        btnTabTx.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
        btnTabDebt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0"))

        // LOGIK EVENT KLIK SWITCH TAB LAPORAN
        btnTabTx.setOnClickListener {
            btnTabTx.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            btnTabDebt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0"))
            loadTransactionReport(contentContainer)
        }

        btnTabDebt.setOnClickListener {
            btnTabDebt.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            btnTabTx.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0"))
            loadDebtReport(contentContainer)
        }

        // LOAD AWAL: LAPORAN TRANSAKSI KATEGORI
        loadTransactionReport(contentContainer)

        return root
    }

    // ==========================================
    // TAB 1: LAPORAN TRANSAKSI BERDASARKAN KATEGORI
    // ==========================================
    private fun loadTransactionReport(container: LinearLayout) {
        container.removeAllViews()
        val context = requireContext()

        lifecycleScope.launch {
            val transactions = db.transactionDao().getAllTransactions().first()
            
            var totalIncome = 0.0
            var totalExpense = 0.0
            val categoryMap = HashMap<String, Double>()

            transactions.forEach { tx ->
                if (tx.type == "INCOME") {
                    totalIncome += tx.amount
                } else {
                    totalExpense += tx.amount
                    // Kelompokkan total pengeluaran per nama kategori
                    val currentAmount = categoryMap[tx.categoryName] ?: 0.0
                    categoryMap[tx.categoryName] = currentAmount + tx.amount
                }
            }

            // RINGKASAN SALDO CARD TINGKAT TINGGI
            val summaryCard = createCardContainer()
            summaryCard.addView(createTextView("Pemasukan Bersih", 13f, "#718096"))
            summaryCard.addView(createTextView(formatRupiah.format(totalIncome), 20f, "#2F855A", true))
            summaryCard.addView(createTextView("\nPengeluaran Struktur", 13f, "#718096"))
            summaryCard.addView(createTextView(formatRupiah.format(totalExpense), 20f, "#C53030", true))
            container.addView(summaryCard)

            // DAFTAR GRAFIK BATANG PERSENTASE KATEGORI EXPENSE
            container.addView(createTextView("\nDistribusi Pengeluaran Per Kategori:", 14f, "#2D3748", true))

            if (categoryMap.isEmpty()) {
                container.addView(createTextView("\nBelum ada data pengeluaran tercatat.", 14f, "#A0AEC0"))
                return@launch
            }

            categoryMap.forEach { (catName, amount) ->
                val percentage = if (totalExpense > 0) (amount / totalExpense * 100).toInt() else 0
                
                val catItemRow = createCardContainer().apply { orientation = LinearLayout.VERTICAL }
                
                // Baris Info Teks Kategori
                val textRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                textRow.addView(createTextView(catName, 14f, "#4A5568", true).apply { 
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) 
                })
                textRow.addView(createTextView("$percentage%", 14f, "#008080", true))
                catItemRow.addView(textRow)

                // Simulasi Grafik Batang Elegan Menggunakan View Kosong Berwarna
                val barTrack = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 16).apply { topMargin = 12; bottomMargin = 8 }
                }
                val barProgress = View(context).apply {
                    setBackgroundColor(Color.parseColor("#008080"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, percentage.toFloat().coerceAtLeast(1f))
                }
                val barEmpty = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, (100 - percentage).toFloat().coerceAtLeast(0f))
                }
                barTrack.addView(barProgress)
                barTrack.addView(barEmpty)
                catItemRow.addView(barTrack)

                // Sub-info nominal nominal di bawah baris grafik
                catItemRow.addView(createTextView("Total Alokasi: ${formatRupiah.format(amount)}", 12f, "#A0AEC0"))
                
                container.addView(catItemRow)
            }
        }
    }

    // ==========================================
    // TAB 2: LAPORAN HUTANG PIUTANG MENDALAM
    // ==========================================
    private fun loadDebtReport(container: LinearLayout) {
        container.removeAllViews()
        
        lifecycleScope.launch {
            val debts = db.debtDao().getAllDebts().first()

            var totalHutangSaya = 0.0 // Tipe DEBT
            var totalPiutangOrang = 0.0 // Tipe RECEIVABLE

            debts.filter { !it.isPaid }.forEach { debt ->
                if (debt.type == "DEBT") {
                    totalHutangSaya += debt.remainingAmount
                } else {
                    totalPiutangOrang += debt.remainingAmount
                }
            }

            // RINGKASAN KARTU STRUKTUR PINJAMAN
            val summaryCard = createCardContainer()
            summaryCard.addView(createTextView("Piutang Saya (Uang Ada Di Orang)", 13f, "#718096"))
            summaryCard.addView(createTextView(formatRupiah.format(totalPiutangOrang), 18f, "#2B6CB0", true))
            summaryCard.addView(createTextView("\nHutang Saya (Wajib Dibayar Balik)", 13f, "#718096"))
            summaryCard.addView(createTextView(formatRupiah.format(totalHutangSaya), 18f, "#D69E2E", true))
            container.addView(summaryCard)

            // LIST DETAIL RINCIAN PER ORANG
            container.addView(createTextView("\nDaftar Rincian Kontak Aktif:", 14f, "#2D3748", true))

            val activeDebts = debts.filter { !it.isPaid }
            if (activeDebts.isEmpty()) {
                container.addView(createTextView("\nBebas Hutang! Tidak ada catatan pinjaman aktif.", 14f, "#2F855A"))
                return@launch
            }

            activeDebts.forEach { item ->
                val itemCard = createCardContainer().apply { orientation = LinearLayout.HORIZONTAL }
                
                val leftLayout = LinearLayout(requireContext()).apply { 
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                leftLayout.addView(createTextView(item.contactName, 15f, "#2D3748", true))
                
                val labelJenis = if (item.type == "DEBT") "⚠️ Hutang Anda" else "💰 Piutang Anda"
                leftLayout.addView(createTextView(labelJenis, 12f, if (item.type == "DEBT") "#D69E2E" else "#2B6CB0"))
                itemCard.addView(leftLayout)

                // Sisi Kanan Nominal Sisa Pinjaman
                val tvNominal = createTextView(formatRupiah.format(item.remainingAmount), 14f, "#4A5568", true).apply {
                    gravity = Gravity.END
                }
                itemCard.addView(tvNominal)

                container.addView(itemCard)
            }
        }
    }

    // ==========================================
    // RUMUS UTILITY VIEW GENERATOR (ANTI-ERROR)
    // ==========================================
    private fun createCardContainer(): LinearLayout {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            background.setTint(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12 }
        }
    }

    private fun createTextView(textStr: String, size: Float, colorHex: String, isBold: Boolean = false): TextView {
        return TextView(requireContext()).apply {
            text = textStr
            textSize = size
            setTextColor(Color.parseColor(colorHex))
            if (isBold) setTypeface(null, Typeface.BOLD)
        }
    }
}
