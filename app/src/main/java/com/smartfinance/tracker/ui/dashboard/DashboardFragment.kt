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
    private var selectedFilter = "BULAN INI" // Default filter pasaran tracker premium

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            setPadding(44, 44, 44, 44)
        }

        // HEADER & BUTTON SORTIR
        val headerRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tvTitle = TextView(context).apply { text = "📊 RINGKASAN KAS"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        
        btnFilterTime = Button(context).apply {
            text = "📅 $selectedFilter"
            textSize = 12f
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            setTextColor(Color.WHITE)
            setOnClickListener { showFilterDialog() }
        }
        headerRow.addView(tvTitle)
        headerRow.addView(btnFilterTime)
        root.addView(headerRow)
        root.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(1, 30) })

        // KARTU KAS UTAMA
        val cardMain = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
            background.setTint(Color.parseColor("#1A202C"))
        }
        cardMain.addView(TextView(context).apply { text = "SALDO BERSIH"; textColor = Color.parseColor("#A0AEC0"); textSize = 11f })
        tvBalance = TextView(context).apply { text = "Rp 0"; textColor = Color.WHITE; textSize = 22f; setTypeface(null, Typeface.BOLD) }
        cardMain.addView(tvBalance)
        
        val rowFlow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, 20, 0, 0) }
        val colIn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        colIn.addView(TextView(context).apply { text = "PEMASUKAN"; textColor = Color.parseColor("#48BB78"); textSize = 10f })
        tvIncome = TextView(context).apply { text = "Rp 0"; textColor = Color.WHITE; textSize = 14f; setTypeface(null, Typeface.BOLD) }
        colIn.addView(tvIncome)
        
        val colOut = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        colOut.addView(TextView(context).apply { text = "PENGELUARAN"; textColor = Color.parseColor("#F56565"); textSize = 10f })
        tvExpense = TextView(context).apply { text = "Rp 0"; textColor = Color.WHITE; textSize = 14f; setTypeface(null, Typeface.BOLD) }
        colOut.addView(tvExpense)
        
        rowFlow.addView(colIn)
        rowFlow.addView(colOut)
        cardMain.addView(rowFlow)
        root.addView(cardMain)

        root.addView(TextView(context).apply { text = "\nRIWAYAT TRANSAKSI TERFILTER:"; textSize = 12f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#718096")) })

        // SCROLL CONTAINER DATA
        val sv = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        sv.addView(listContainer)
        root.addView(sv)

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
                listContainer.addView(TextView(context).apply { text = "\nBelum ada transaksi di periode ini."; gravity = Gravity.CENTER; setTextColor(Color.GRAY) })
                return@launch
            }

            filteredTx.forEach { item ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 20, 20, 20)
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                    background.setTint(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 8 }
                }
                val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                left.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); textColor = Color.BLACK })
                left.addView(TextView(context).apply { text = "${item.categoryName} • ${sdf.format(Date(item.timestamp))}"; textSize = 11f; textColor = Color.GRAY })
                
                val right = TextView(context).apply {
                    text = "${if (item.type == "INCOME") "+" else "-"} ${formatRupiah.format(item.amount)}"
                    setTextColor(if (item.type == "INCOME") Color.parseColor("#2F855A") else Color.parseColor("#C53030"))
                    setTypeface(null, Typeface.BOLD)
                }
                row.addView(left)
                row.addView(right)
                listContainer.addView(row)
            }
        }
    }

    private fun filterTransactionsByTime(list: List<TransactionEntity>): List<TransactionEntity> {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        
        return list.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
            when (selectedFilter) {
                "HARI INI" -> {
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
                    tx.timestamp >= cal.timeInMillis
                }
                "MINGGU INI" -> {
                    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                    tx.timestamp >= cal.timeInMillis
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
