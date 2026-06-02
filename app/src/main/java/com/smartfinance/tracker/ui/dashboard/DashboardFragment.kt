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
import com.smartfinance.tracker.databinding.FragmentDashboardBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

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
        loadRealDatabaseData()
    }

    private fun loadRealDatabaseData() {
        val db = AppDatabase.getDatabase(requireContext())
        
        lifecycleScope.launch {
            // Mengambil seluruh riwayat transaksi dari database lokal
            val allTransactions = db.transactionDao().getAllTransactions().first()
            
            var totalIncome = 0.0
            var totalExpense = 0.0

            for (tx in allTransactions) {
                if (tx.type == "INCOME") totalIncome += tx.amount
                else totalExpense += tx.amount
            }

            val totalBalance = totalIncome - totalExpense

            // Format angka ke Rupiah Indonesia (Rp)
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            
            binding.tvTotalBalance.text = formatRupiah.format(totalBalance)
            binding.tvIncomeSummary.text = formatRupiah.format(totalIncome)
            binding.tvExpenseSummary.text = formatRupiah.format(totalExpense)

            // Perbarui grafik berdasarkan data nyata database
            setupReportChart(totalIncome.toFloat(), totalExpense.toFloat())
        }
    }

    private fun setupReportChart(income: Float, expense: Float) {
        val entries = ArrayList<BarEntry>()
        // Jika data masih kosong, beri nilai default 10k agar grafik tidak runtuh
        entries.add(BarEntry(1f, if(income == 0f) 10000f else income))
        entries.add(BarEntry(2f, if(expense == 0f) 10000f else expense))

        val dataSet = BarDataSet(entries, "Pemasukan vs Pengeluaran (Rp)")
        
        val colorsList = ArrayList<Int>()
        colorsList.add(Color.parseColor("#2F855A")) // Hijau
        colorsList.add(Color.parseColor("#C53030")) // Merah
        dataSet.colors = colorsList
        
        dataSet.valueTextSize = 12f
        val barData = BarData(dataSet)
        
        binding.reportBarChart.data = barData
        binding.reportBarChart.description.isEnabled = false
        binding.reportBarChart.setFitBars(true)
        binding.reportBarChart.animateY(800) 
        binding.reportBarChart.invalidate() 
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
