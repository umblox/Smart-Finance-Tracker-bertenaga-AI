package com.smartfinance.tracker.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.utils.ColorTemplate
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
        
        // 1. Set data ringkasan teks di Dashboard
        binding.tvTotalBalance.text = "Rp 5.000.000"
        binding.tvIncomeSummary.text = "Rp 7.500.000"
        binding.tvExpenseSummary.text = "Rp 2.500.000"

        // 2. Setup Grafik BarChart agar tidak kosong dan tidak memicu mental/crash
        setupDummyChart()
    }

    private fun setupDummyChart() {
        // Membuat data pura-pura (dummy) untuk Pemasukan (Bar 1) dan Pengeluaran (Bar 2)
        val entries = ArrayList<BarEntry>()
        entries.add(BarEntry(1f, 7500000f)) // Pemasukan
        entries.add(BarEntry(2f, 2500000f)) // Pengeluaran

        val dataSet = BarDataSet(entries, "Laporan Keuangan (Rp)")
        
        // Memberi warna: Hijau untuk pemasukan, Merah untuk pengeluaran
        dataSet.setColors(intArrOf(
            android.graphics.Color.parseColor("#2F855A"), // Hijau
            android.graphics.Color.parseColor("#C53030")  // Merah
        ), context)
        
        dataSet.valueTextSize = 12f

        val barData = BarData(dataSet)
        
        // Masukkan data ke dalam komponen XML BarChart
        binding.reportBarChart.data = barData
        binding.reportBarChart.description.isEnabled = false
        binding.reportBarChart.animateY(1000) // Efek animasi naik yang mulus
        binding.reportBarChart.invalidate()  // Refresh grafik
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Fungsi pembantu konversi warna internal
    private fun intArrOf(vararg elements: Int): IntArray = elements
}
