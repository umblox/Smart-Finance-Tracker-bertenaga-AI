package com.smartfinance.tracker.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
        loadRealDashboardData()
    }

    private fun loadRealDashboardData() {
        val db = AppDatabase.getDatabase(requireContext())
        
        lifecycleScope.launch {
            val allTransactions = db.transactionDao().getAllTransactions().first()
            
            var totalIncome = 0.0
            var totalExpense = 0.0

            // Penampung Filter Laporan Berkala
            var harian = 0.0
            var mingguan = 0.0
            var bulanan = 0.0
            var tahunan = 0.0

            val now = System.currentTimeMillis()
            val calTx = Calendar.getInstance()
            val calNow = Calendar.getInstance().apply { timeInMillis = now }

            for (tx in allTransactions) {
                if (tx.type == "INCOME") totalIncome += tx.amount else totalExpense += tx.amount

                // Filter Waktu Skema Berkala
                calTx.timeInMillis = tx.timestamp
                val diffMillis = now - tx.timestamp
                val diffDays = diffMillis / (1000 * 60 * 60 * 24)

                if (diffDays <= 0) harian += if (tx.type == "EXPENSE") tx.amount else 0.0
                if (diffDays <= 7) mingguan += if (tx.type == "EXPENSE") tx.amount else 0.0
                if (calTx.get(Calendar.MONTH) == calNow.get(Calendar.MONTH) && calTx.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)) {
                    bulanan += if (tx.type == "EXPENSE") tx.amount else 0.0
                }
                if (calTx.get(Calendar.YEAR) == calNow.get(Calendar.YEAR)) {
                    tahunan += if (tx.type == "EXPENSE") tx.amount else 0.0
                }
            }

            val totalBalance = totalIncome - totalExpense
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            
            // Set Nilai Atas
            binding.tvTotalBalance.text = formatRupiah.format(totalBalance)
            binding.tvIncomeSummary.text = formatRupiah.format(totalIncome)
            binding.tvExpenseSummary.text = formatRupiah.format(totalExpense)

            // CETAK DAFTAR LAPORAN TRANSAKSI DAN RIWAYAT BERKALA KE LAYAR
            val reportBuilder = StringBuilder()
            reportBuilder.append("📋 **RINGKASAN PENGELUARAN BERKALA**\n")
            reportBuilder.append("▪️ Hari Ini: ${formatRupiah.format(harian)}\n")
            reportBuilder.append("▪️ 7 Hari Terakhir: ${formatRupiah.format(mingguan)}\n")
            reportBuilder.append("▪️ Bulan Ini: ${formatRupiah.format(bulanan)}\n")
            reportBuilder.append("▪️ Tahun Ini: ${formatRupiah.format(tahunan)}\n\n")
            
            reportBuilder.append("🕒 **DAFTAR RIWAYAT TRANSAKSI TERBARU**\n")
            if (allTransactions.isEmpty()) {
                reportBuilder.append("Belum ada transaksi tercatat. Ketik di Chat AI untuk menambahkan.")
            } else {
                allTransactions.take(10).forEach { tx ->
                    val sign = if (tx.type == "INCOME") "🟢 +" else "🔴 -"
                    reportBuilder.append("$sign ${tx.categoryName} (${tx.note}): ${formatRupiah.format(tx.amount)}\n")
                }
            }

            // Meminjam space layout visual secara aman di bawah Chart
            setupReportChart(totalIncome.toFloat(), totalExpense.toFloat())
        }
    }

    private fun setupReportChart(income: Float, expense: Float) {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(1f, if(income == 0f) 1f else income))
        entries.add(BarEntry(2f, if(expense == 0f) 1f else expense))

        val dataSet = BarDataSet(entries, "Pemasukan vs Pengeluaran (Rp)")
        val colorsList = ArrayList<Int>().apply {
            add(Color.parseColor("#2F855A"))
            add(Color.parseColor("#C53030"))
        }
        dataSet.colors = colorsList
        dataSet.valueTextSize = 11f

        binding.reportBarChart.data = BarData(dataSet)
        binding.reportBarChart.description.isEnabled = false
        binding.reportBarChart.setFitBars(true)
        binding.reportBarChart.animateY(500)
        binding.reportBarChart.invalidate()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
