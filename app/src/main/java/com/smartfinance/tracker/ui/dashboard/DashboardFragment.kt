package com.smartfinance.tracker.ui.dashboard

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var listContainer: LinearLayout
    private lateinit var tvIncome: TextView
    private lateinit var tvExpense: TextView
    private lateinit var tvBalance: TextView
    private lateinit var btnFilterTime: Button

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private var selectedFilter = "BULAN INI"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        // ROOT CONTAINER UTAMA (PUTIH BERSIH ELEGAN)
        val root = RelativeLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F7FAFC"))
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 44, 44, 44)
            layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // HEADER ROW (JUDUL & TOMBOL FILTER)
        val headerRow = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL 
        }
        val tvTitle = TextView(context).apply { 
            text = "🏦 RINGKASAN KAS"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3748"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        btnFilterTime = Button(context).apply {
            text = "📅 $selectedFilter"
            textSize = 12f
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            setTextColor(Color.WHITE)
            setOnClickListener { showFilterDialog() }
        }
        headerRow.addView(tvTitle)
        headerRow.addView(btnFilterTime)
        mainLayout.addView(headerRow)
        mainLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 30) })

        // KARTU KAS PREMIUM (TEMA TEAL CERAH MODEREN - BUKAN HITAM BUSUK)
        val cardMain = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            background.setTint(Color.parseColor("#008080")) // Warna Utama Aplikasi Kamu
        }
        
        cardMain.addView(TextView(context).apply { text = "SALDO BERSIH DOMPET"; setTextColor(Color.parseColor("#E6FFFA")); textSize = 11f; setTypeface(null, Typeface.BOLD) })
        tvBalance = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 24f; setTypeface(null, Typeface.BOLD) }
        cardMain.addView(tvBalance)
        cardMain.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 20; bottomMargin = 20 }; setBackgroundColor(Color.parseColor("#319795")) })
        
        val rowFlow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val colIn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        colIn.addView(TextView(context).apply { text = "🟢 PEMASUKAN"; setTextColor(Color.parseColor("#E6FFFA")); textSize = 10f })
        tvIncome = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD) }
        colIn.addView(tvIncome)
        
        val colOut = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        colOut.addView(TextView(context).apply { text = "🔴 PENGELUARAN"; setTextColor(Color.parseColor("#E6FFFA")); textSize = 10f })
        tvExpense = TextView(context).apply { text = "Rp 0"; setTextColor(Color.WHITE); textSize = 15f; setTypeface(null, Typeface.BOLD) }
        colOut.addView(tvExpense)
        
        rowFlow.addView(colIn)
        rowFlow.addView(colOut)
        cardMain.addView(rowFlow)
        mainLayout.addView(cardMain)

        mainLayout.addView(TextView(context).apply { text = "\n📜 RIWAYAT TRANSAKSI TERFILTER:"; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#718096")) })

        // SCROLL VIEW DAFTAR TRANSAKSI
        val sv = ScrollView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) 
        }
        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(listContainer)
        mainLayout.addView(sv)
        root.addView(mainLayout)

        // FITUR TAMBAH TRANSAKSI MANUAL CEPAT (FLOATING ACTION BUTTON - FAB MODERN)
        val btnFabAdd = Button(context).apply {
            text = "➕ TAMBAH MANUAL"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#319795"))
            val fabParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                addRule(RelativeLayout.ALIGN_PARENT_END)
                rightMargin = 50
                bottomMargin = 50
            }
            layoutParams = fabParams
            setOnClickListener { showAddManualTransactionDialog() }
        }
        root.addView(btnFabAdd)

        calculateAndRenderData()
        return root
    }

    private fun showFilterDialog() {
        val options = arrayOf("HARI INI", "MINGGU INI", "BULAN INI", "TAHUN INI", "SEMUA")
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Sortir Rentang Waktu")
            setItems(options) { _, which ->
                selectedFilter = options[which]
                btnFilterTime.text = "📅 $selectedFilter"
                calculateAndRenderData()
            }
            show()
        }
    }

    // DIALOG FORM INPUT MANUAL CEPAT YANG HILANG (KEMBALI DIRESTORE)
    private fun showAddManualTransactionDialog() {
        val context = requireContext()
        val form = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(50, 30, 50, 30) }
        
        val etAmount = EditText(context).apply { hint = "Nominal Uang (ex: 50000)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        val etNote = EditText(context).apply { hint = "Catatan Keperluan (ex: Beli Bubur Ayam)" }
        
        val spinnerType = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("PENGELUARAN (EXPENSE)", "PEMASUKAN (INCOME)"))
        }

        form.addView(TextView(context).apply { text = "Jumlah Uang:" })
        form.addView(etAmount)
        form.addView(TextView(context).apply { text = "\nKeterangan:" })
        form.addView(etNote)
        form.addView(TextView(context).apply { text = "\nJenis Transaksi:" })
        form.addView(spinnerType)

        AlertDialog.Builder(context).apply {
            setTitle("📝 Catat Transaksi Manual")
            setView(form)
            setPositiveButton("Simpan") { _, _ ->
                val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val note = etNote.text.toString().trim()
                val type = if (spinnerType.selectedItemPosition == 0) "EXPENSE" else "INCOME"
                
                if (amount > 0.0 && note.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.transactionDao().insertTransaction(TransactionEntity(
                            amount = amount, type = type, categoryId = 15L,
                            categoryName = if (type == "INCOME") "Gaji & Pendapatan" else "Lain-lain / Umum",
                            note = note.uppercase(), timestamp = System.currentTimeMillis()
                        ))
                        calculateAndRenderData()
                        Toast.makeText(context, "Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    private fun calculateAndRenderData() {
        listContainer.removeAllViews()
        val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        lifecycleScope.launch {
            val allTx = db.transactionDao().getAllTransactions().first()
            val filteredTx = filterTransactionsByTime(allTx)

            var totalIn = 0.0
            var totalOut = 0.0

            filteredTx.forEach { 
                if (it.type == "INCOME") totalIn += it.amount else totalOut += it.amount 
            }

            tvIncome.text = formatRupiah.format(totalIn)
            tvExpense.text = formatRupiah.format(totalOut)
            tvBalance.text = formatRupiah.format(totalIn - totalOut)

            if (filteredTx.isEmpty()) {
                listContainer.addView(TextView(context).apply { text = "\nBelum ada transaksi di periode ini."; gravity = Gravity.CENTER; setTextColor(Color.GRAY); textSize = 14f })
                return@launch
            }

            filteredTx.forEach { item ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(30, 30, 30, 30)
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                    background.setTint(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
                }
                val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                left.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); textSize = 15f })
                left.addView(TextView(context).apply { text = "${item.categoryName} • ${sdf.format(Date(item.timestamp))}"; textSize = 12f; setTextColor(Color.parseColor("#718096")) })
                
                val right = TextView(context).apply {
                    text = "${if (item.type == "INCOME") "+" else "-"} ${formatRupiah.format(item.amount)}"
                    setTextColor(if (item.type == "INCOME") Color.parseColor("#2F855A") else Color.parseColor("#C53030"))
                    setTypeface(null, Typeface.BOLD)
                    textSize = 15f
                }
                row.addView(left)
                row.addView(right)
                listContainer.addView(row)
            }
        }
    }

    private fun filterTransactionsByTime(list: List<TransactionEntity>): List<TransactionEntity> {
        val cal = Calendar.getInstance()
        return list.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            when (selectedFilter) {
                "HARI INI" -> {
                    val today = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }
                    tx.timestamp >= today.timeInMillis
                }
                "MINGGU INI" -> {
                    val week = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0) }
                    tx.timestamp >= week.timeInMillis
                }
                "BULAN INI" -> {
                    txCal.get(Calendar.MONTH) == cal.get(Calendar.MONTH) && txCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                }
                "TAHUN INI" -> {
                    txCal.get(Calendar.YEAR) == cal.get(Calendar.YEAR)
                }
                else -> true
            }
        }
    }
}
