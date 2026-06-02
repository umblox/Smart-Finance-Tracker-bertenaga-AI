package com.smartfinance.tracker.ui.dashboard

import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupManualInputForm()
        loadRealDashboardData()
    }

    private fun setupManualInputForm() {
        val context = requireContext()
        val db = AppDatabase.getDatabase(context)
        val viewGroup = binding.root as? ViewGroup
        
        viewGroup?.let { parent ->
            if (parent.findViewWithTag<View>("manual_tx_form") == null) {
                val formContainer = LinearLayout(context).apply {
                    tag = "manual_tx_form"
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 16, 16, 16)
                }

                val tvTitle = TextView(context).apply {
                    text = "➕ INPUT TRANSAKSI MANUAL"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#2D3748"))
                }
                formContainer.addView(tvTitle)

                val etAmount = EditText(context).apply { hint = "Nominal Uang (Rp)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                formContainer.addView(etAmount)

                val etNote = EditText(context).apply { hint = "Keterangan (ex: Beli Bakso)" }
                formContainer.addView(etNote)

                val spinnerCategory = Spinner(context)
                formContainer.addView(spinnerCategory)

                // AMBIL KATEGORI NYATA DARI SQLITE UNTUK DROPDOWN SPINNER
                lifecycleScope.launch {
                    val categories = db.categoryDao().getAllCategories().first()
                    val catNames = categories.map { "${it.id} - ${it.name} (${it.type})" }
                    
                    val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, catNames.ifEmpty { listOf("1 - Makanan/Umum (EXPENSE)", "2 - Gaji (INCOME)") })
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCategory.adapter = adapter
                }

                val btnSave = Button(context).apply {
                    text = "SIMPAN TRANSAKSI"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
                    setOnClickListener {
                        val amount = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                        val note = etNote.text.toString().trim()
                        val selectedCat = spinnerCategory.selectedItem?.toString() ?: ""

                        if (amount > 0.0 && selectedCat.isNotEmpty()) {
                            lifecycleScope.launch {
                                val parts = selectedCat.split("-")
                                val catId = parts[0].trim().toLongOrNull() ?: 1L
                                val isIncome = selectedCat.contains("INCOME")
                                val catName = if (parts.size > 1) parts[1].split("(")[0].trim() else "Umum"

                                val newTx = TransactionEntity(
                                    amount = amount,
                                    type = if (isIncome) "INCOME" else "EXPENSE",
                                    categoryId = catId,
                                    categoryName = catName,
                                    note = if (note.isEmpty()) "INPUT MANUAL" else note.uppercase(),
                                    timestamp = System.currentTimeMillis()
                                )
                                db.transactionDao().insertTransaction(newTx)
                                Toast.makeText(context, "Transaksi Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
                                
                                etAmount.setText("")
                                etNote.setText("")
                                loadRealDashboardData()
                            }
                        }
                    }
                }
                formContainer.addView(btnSave)
                
                val targetLinearLayout = parent.getChildAt(0) as? LinearLayout
                targetLinearLayout?.addView(formContainer, 2)
            }
        }
    }

    private fun loadRealDashboardData() {
        val db = AppDatabase.getDatabase(requireContext())
        
        lifecycleScope.launch {
            val allTransactions = db.transactionDao().getAllTransactions().first()
            
            var totalIncome = 0.0
            var totalExpense = 0.0
            var harian = 0.0
            var mingguan = 0.0
            var bulanan = 0.0

            val now = System.currentTimeMillis()
            val calTx = Calendar.getInstance()
            val calNow = Calendar.getInstance().apply { timeInMillis = now }

            for (tx in allTransactions) {
                if (tx.type == "INCOME") totalIncome += tx.amount else totalExpense += tx.amount

                calTx.timeInMillis = tx.timestamp
                val diffDays = (now - tx.timestamp) / (1000 * 60 * 60 * 24)

                if (diffDays <= 0) harian += if (tx.type == "EXPENSE") tx.amount else 0.0
                if (diffDays <= 7) mingguan += if (tx.type == "EXPENSE") tx.amount else 0.0
                if (calTx.get(Calendar.MONTH) == calNow.get(Calendar.MONTH)) {
                    bulanan += if (tx.type == "EXPENSE") tx.amount else 0.0
                }
            }

            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            binding.tvTotalBalance.text = formatRupiah.format(totalIncome - totalExpense)
            binding.tvIncomeSummary.text = formatRupiah.format(totalIncome)
            binding.tvExpenseSummary.text = formatRupiah.format(totalExpense)

            val reportBuilder = StringBuilder()
            reportBuilder.append("📋 PENGELUARAN BERKALA\n")
            reportBuilder.append("▪️ Hari Ini: ${formatRupiah.format(harian)}\n")
            reportBuilder.append("▪️ 7 Hari Terakhir: ${formatRupiah.format(mingguan)}\n")
            reportBuilder.append("▪️ Bulan Ini: ${formatRupiah.format(bulanan)}\n\n")
            
            reportBuilder.append("🕒 HISTORY TRANSAKSI TERBARU (KLIK UNTUK DETAIL)\n")
            if (allTransactions.isEmpty()) {
                reportBuilder.append("Belum ada transaksi.")
            } else {
                allTransactions.take(5).forEach { tx ->
                    val sign = if (tx.type == "INCOME") "🟢 +" else "🔴 -"
                    reportBuilder.append("$sign ${tx.categoryName}: ${formatRupiah.format(tx.amount)} (${tx.note})\n")
                }
            }

            binding.tvDashboardReport.text = reportBuilder.toString()
            setupReportChart(totalIncome.toFloat(), totalExpense.toFloat())
        }
    }

    private fun setupReportChart(income: Float, expense: Float) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(1f, if(income == 0f) 1f else income))
        entries.add(BarEntry(2f, if(expense == 0f) 1f else expense))

        val dataSet = BarDataSet(entries, "Pemasukan vs Pengeluaran")
        dataSet.colors = arrayListOf(Color.parseColor("#2F855A"), Color.parseColor("#C53030"))
        binding.reportBarChart.data = BarData(dataSet)
        binding.reportBarChart.description.isEnabled = false
        binding.reportBarChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
