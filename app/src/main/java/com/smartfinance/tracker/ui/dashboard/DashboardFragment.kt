package com.smartfinance.tracker.ui.dashboard

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.smartfinance.tracker.databinding.FragmentDashboardBinding

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
        
        // 1. Mengisi data teks statis di dashboard
        binding.tvTotalBalance.text = "Rp 5.000.000"
        binding.tvIncomeSummary.text = "Rp 7.500.000"
        binding.tvExpenseSummary.text = "Rp 2.500.000"

        // 2. Jalankan pembuatan grafik tanpa parameter error
        setupReportChart()
    }

    private fun setupReportChart() {
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(1f, 7500000f)) // Batang 1: Pemasukan
        entries.add(BarEntry(2f, 2500000f)) // Batang 2: Pengeluaran

        val dataSet = BarDataSet(entries, "Laporan Keuangan (Rp)")
        
        // PERBAIKAN MUTLAK: Menggunakan objek List biasa tanpa melempar parameter context
        val colorsList = ArrayList<Int>()
        colorsList.add(Color.parseColor("#2F855A")) // Hijau
        colorsList.add(Color.parseColor("#C53030")) // Merah
        dataSet.colors = colorsList
        
        dataSet.valueTextSize = 12f

        val barData = BarData(dataSet)
        
        // Hubungkan ke komponen XML BarChart
        binding.reportBarChart.data = barData
        binding.reportBarChart.description.isEnabled = false
        binding.reportBarChart.setFitBars(true)
        binding.reportBarChart.animateY(1000) 
        binding.reportBarChart.invalidate() 
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
