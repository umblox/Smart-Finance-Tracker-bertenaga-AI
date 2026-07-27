package com.smartfinance.tracker.ui.settings

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.smartfinance.tracker.databinding.DialogExportBottomSheetBinding
import com.smartfinance.tracker.utils.ExportUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ExportBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogExportBottomSheetBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: ExportViewModel

    private val sdfDisplayDate = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))

    private val timeOptions = listOf("Bulan Ini", "Minggu Ini", "Hari Ini", "Semua Waktu", "Pilih Tanggal Kustom")
    private val timeEnums = listOf(ExportTimeRange.MONTHLY, ExportTimeRange.WEEKLY, ExportTimeRange.DAILY, ExportTimeRange.ALL, ExportTimeRange.CUSTOM)
    private val typeOptions = listOf("Semua Transaksi", "Pemasukan Saja", "Pengeluaran Saja", "Hutang & Piutang Saja")
    private val typeEnums = listOf(ExportType.ALL, ExportType.INCOME_ONLY, ExportType.EXPENSE_ONLY, ExportType.DEBT_ONLY)

    // Menyimpan referensi file PDF cache yang sedang di-preview
    private var currentTempPdf: File? = null

    // Launcher Simpan PDF (Dialog bawaan Android)
    private val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { targetUri ->
            currentTempPdf?.let { tempFile ->
                lifecycleScope.launch {
                    try {
                        ExportUtils.copyFileToUri(requireContext(), tempFile, targetUri)
                        Toast.makeText(requireContext(), "✅ PDF berhasil disimpan!", Toast.LENGTH_LONG).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "❌ Gagal menyimpan PDF", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogExportBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Memaksa BottomSheet Expand agar layar preview punya ruang
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        viewModel = ViewModelProvider(this)[ExportViewModel::class.java]
        setupUI()
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener { dismiss() }

        // Setup Spinner
        binding.spinnerTime.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, timeOptions)
        binding.spinnerType.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, typeOptions)
        
        // 🔥 FITUR BARU: Setup Spinner Kategori secara dinamis
        val categoryOptions = mutableListOf("Semua Kategori")
        categoryOptions.addAll(viewModel.getAvailableCategories())
        val categoryAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, categoryOptions)
        binding.spinnerCategory.adapter = categoryAdapter
        
        // Jaring pengaman (Safety Net) jika data dari Cloud sedikit butuh waktu untuk termuat
        viewLifecycleOwner.lifecycleScope.launch {
            kotlinx.coroutines.delay(600)
            val newCats = viewModel.getAvailableCategories()
            if (newCats.isNotEmpty() && categoryOptions.size == 1) {
                categoryOptions.addAll(newCats)
                categoryAdapter.notifyDataSetChanged()
            }
        }

        binding.spinnerTime.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isCustom = timeEnums[position] == ExportTimeRange.CUSTOM
                binding.layoutCustomDate.visibility = if (isCustom) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Setup Custom Date
        val cal = Calendar.getInstance()
        binding.btnStartDate.text = sdfDisplayDate.format(cal.time)
        binding.btnEndDate.text = sdfDisplayDate.format(cal.time)

        binding.btnStartDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val startCal = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                viewModel.customStartDate = startCal.timeInMillis
                binding.btnStartDate.text = sdfDisplayDate.format(startCal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnEndDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                val endCal = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                viewModel.customEndDate = endCal.timeInMillis
                binding.btnEndDate.text = sdfDisplayDate.format(endCal.time)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        
        // 1. KLIK "TAMPILKAN PREVIEW"
        binding.btnGeneratePreview.setOnClickListener {
            val selectedTime = timeEnums[binding.spinnerTime.selectedItemPosition]
            val selectedType = typeEnums[binding.spinnerType.selectedItemPosition]
            
            // 🔥 FITUR BARU: Ambil Kategori yang dipilih
            val selectedCategory = binding.spinnerCategory.selectedItem.toString()
            
            // Validasi data kosong (Filter baru sudah diterapkan)
            val data = viewModel.getFilteredTransactions(selectedTime, selectedType, selectedCategory)
            if (data.isEmpty()) {
                Toast.makeText(requireContext(), "Tidak ada data pada rentang dan kategori ini!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Ganti Tampilan
            binding.layoutSetup.visibility = View.GONE
            binding.layoutPreview.visibility = View.VISIBLE
            binding.tvHeaderTitle.text = "📄 Pratinjau Dokumen"
            
            generatePreviewDataAndRender(data)
        }

        // 2. KLIK "KEMBALI EDIT FILTER"
        binding.btnBackToSetup.setOnClickListener {
            binding.layoutPreview.visibility = View.GONE
            binding.layoutSetup.visibility = View.VISIBLE
            binding.tvHeaderTitle.text = "🖨️ Pengaturan Cetak PDF"
            
            // Bersihkan memori cache gambar
            binding.ivPdfPreview.setImageBitmap(null)
            currentTempPdf?.delete()
            currentTempPdf = null
        }

        // 3. KLIK "SIMPAN SEBAGAI PDF"
        binding.btnSavePdf.setOnClickListener {
            if (currentTempPdf != null) {
                val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale("id", "ID"))
                exportPdfLauncher.launch("Laporan_Keuangan_${sdf.format(Date())}.pdf")
            }
        }
    }

    // Fungsi Render PDF
    private fun generatePreviewDataAndRender(data: List<com.smartfinance.tracker.data.model.Transaction>) {
        binding.progressBarPreview.visibility = View.VISIBLE
        binding.ivPdfPreview.visibility = View.INVISIBLE
        binding.btnSavePdf.isEnabled = false

        val selectedTime = timeEnums[binding.spinnerTime.selectedItemPosition]
        val timeLabel = if (selectedTime == ExportTimeRange.CUSTOM) "Tanggal Kustom" else timeOptions[binding.spinnerTime.selectedItemPosition]

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Tulis PDF ke file cache
                val file = ExportUtils.generatePdfToTempFile(requireContext(), data, "Laporan Transaksi - $timeLabel")
                currentTempPdf = file

                // 2. Render halaman pertama menjadi Gambar (Bitmap)
                val bitmap = renderPdfPageToBitmap(file)
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        binding.ivPdfPreview.setImageBitmap(bitmap)
                        binding.ivPdfPreview.visibility = View.VISIBLE
                        binding.btnSavePdf.isEnabled = true
                    }
                    binding.progressBarPreview.visibility = View.GONE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.progressBarPreview.visibility = View.GONE
                    Toast.makeText(requireContext(), "Gagal merender PDF", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun renderPdfPageToBitmap(file: File): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(fd)
                val page = renderer.openPage(0) 

                // Resolusi render yang jernih
                val bitmap = Bitmap.createBitmap(page.width * 2, page.height * 2, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                
                page.close()
                renderer.close()
                fd.close()
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        currentTempPdf?.delete() // Hapus cache saat ditutup
        _binding = null
    }
}
