package com.smartfinance.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.smartfinance.tracker.databinding.DialogExportBottomSheetBinding
import com.smartfinance.tracker.utils.ExportUtils
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogExportBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ExportViewModel

    // Persiapan Opsi Spinner
    private val timeOptions = listOf("Semua Waktu", "Bulan Ini", "Minggu Ini", "Hari Ini")
    private val timeEnums = listOf(ExportTimeRange.ALL, ExportTimeRange.MONTHLY, ExportTimeRange.WEEKLY, ExportTimeRange.DAILY)

    private val typeOptions = listOf("Semua Transaksi", "Pemasukan Saja", "Pengeluaran Saja")
    private val typeEnums = listOf(ExportType.ALL, ExportType.INCOME_ONLY, ExportType.EXPENSE_ONLY)

    // Launcher Simpan PDF
    private val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { executeExport(it, isPdf = true) }
    }

    // Launcher Simpan CSV
    private val exportCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { executeExport(it, isPdf = false) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogExportBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ExportViewModel::class.java]

        setupUI()
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener { dismiss() }

        // Setup Spinner
        binding.spinnerTime.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, timeOptions)
        binding.spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeOptions)
        
        // Default ke Bulan Ini
        binding.spinnerTime.setSelection(1)

        binding.btnExport.setOnClickListener {
            val selectedTime = timeEnums[binding.spinnerTime.selectedItemPosition]
            val selectedType = typeEnums[binding.spinnerType.selectedItemPosition]
            
            // Cek apakah ada data di filter tersebut
            val dataToExport = viewModel.getFilteredTransactions(selectedTime, selectedType)
            
            if (dataToExport.isEmpty()) {
                Toast.makeText(requireContext(), "TIdak ada data transaksi pada rentang waktu tersebut!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
            val isPdf = binding.radioPdf.isChecked
            
            if (isPdf) {
                exportPdfLauncher.launch("SmartFinance_Report_${sdf.format(Date())}.pdf")
            } else {
                exportCsvLauncher.launch("SmartFinance_Data_${sdf.format(Date())}.csv")
            }
        }
    }

    private fun executeExport(uri: android.net.Uri, isPdf: Boolean) {
        val selectedTime = timeEnums[binding.spinnerTime.selectedItemPosition]
        val selectedType = typeEnums[binding.spinnerType.selectedItemPosition]
        val timeLabel = timeOptions[binding.spinnerTime.selectedItemPosition]
        
        val dataToExport = viewModel.getFilteredTransactions(selectedTime, selectedType)

        lifecycleScope.launch {
            try {
                if (isPdf) {
                    val title = "Laporan Transaksi - $timeLabel"
                    ExportUtils.generatePdf(requireContext(), uri, dataToExport, title)
                } else {
                    ExportUtils.generateCsv(requireContext(), uri, dataToExport)
                }
                Toast.makeText(requireContext(), "✅ File berhasil disimpan!", Toast.LENGTH_LONG).show()
                dismiss() // Tutup BottomSheet jika berhasil
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "❌ Gagal menyimpan file: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
