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
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.smartfinance.tracker.R
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

    // 🔥 FIX: Locale default agar format bulan mengikuti sistem
    private val sdfDisplayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private lateinit var timeOptions: Array<String>
    private val timeEnums = listOf(ExportTimeRange.MONTHLY, ExportTimeRange.WEEKLY, ExportTimeRange.DAILY, ExportTimeRange.ALL, ExportTimeRange.CUSTOM)
    private lateinit var typeOptions: Array<String>
    private val typeEnums = listOf(ExportType.ALL, ExportType.INCOME_ONLY, ExportType.EXPENSE_ONLY, ExportType.DEBT_ONLY)

    private var currentTempPdf: File? = null

    private val exportPdfLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        uri?.let { targetUri ->
            currentTempPdf?.let { tempFile ->
                lifecycleScope.launch {
                    try {
                        ExportUtils.copyFileToUri(requireContext(), tempFile, targetUri)
                        Toast.makeText(requireContext(), getString(R.string.export_toast_success), Toast.LENGTH_LONG).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), getString(R.string.export_toast_fail_save), Toast.LENGTH_SHORT).show()
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
        
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        viewModel = ViewModelProvider(this)[ExportViewModel::class.java]
        
        // Mengisi array dari String Resources
        timeOptions = resources.getStringArray(R.string.export_time_options)
        typeOptions = resources.getStringArray(R.string.export_type_options)

        setupUI()
    }

    private fun createThemedAdapter(items: List<String>): ArrayAdapter<String> {
        return object : ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item, items) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                return view
            }
            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                return view
            }
        }
    }

    private fun setupUI() {
        binding.btnClose.setOnClickListener { dismiss() }

        binding.spinnerTime.adapter = createThemedAdapter(timeOptions.toList())
        binding.spinnerType.adapter = createThemedAdapter(typeOptions.toList())
        
        val categoryOptions = mutableListOf(getString(R.string.export_category_all))
        categoryOptions.addAll(viewModel.getAvailableCategories())
        val categoryAdapter = createThemedAdapter(categoryOptions)
        binding.spinnerCategory.adapter = categoryAdapter
        
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
        
        binding.btnGeneratePreview.setOnClickListener {
            val selectedTime = timeEnums[binding.spinnerTime.selectedItemPosition]
            val selectedType = typeEnums[binding.spinnerType.selectedItemPosition]
            val selectedCategory = binding.spinnerCategory.selectedItem.toString()
            
            val data = viewModel.getFilteredTransactions(selectedTime, selectedType, selectedCategory)
            if (data.isEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.export_toast_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.layoutSetup.visibility = View.GONE
            binding.layoutPreview.visibility = View.VISIBLE
            binding.tvHeaderTitle.text = getString(R.string.export_preview_title)
            
            generatePreviewDataAndRender(data)
        }

        binding.btnBackToSetup.setOnClickListener {
            binding.layoutPreview.visibility = View.GONE
            binding.layoutSetup.visibility = View.VISIBLE
            binding.tvHeaderTitle.text = getString(R.string.export_setup_title)
            
            binding.ivPdfPreview.setImageBitmap(null)
            currentTempPdf?.delete()
            currentTempPdf = null
        }

        binding.btnSavePdf.setOnClickListener {
            if (currentTempPdf != null) {
                // Jangan gunakan locale default untuk nama file teknis agar seragam di semua sistem file
                val sdf = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
                exportPdfLauncher.launch("Laporan_Keuangan_${sdf.format(Date())}.pdf")
            }
        }
    }

    private fun generatePreviewDataAndRender(data: List<com.smartfinance.tracker.data.model.Transaction>) {
        binding.progressBarPreview.visibility = View.VISIBLE
        binding.ivPdfPreview.visibility = View.INVISIBLE
        binding.btnSavePdf.isEnabled = false

        val selectedTime = timeEnums[binding.spinnerTime.selectedItemPosition]
        // Jika kustom, kita tetap tampilkan pilihan tanggalnya (akan lebih aman dan spesifik).
        // Tapi sementara kita pakai string default yang telah disiapkan di String XML "Laporan Transaksi - %s"
        val timeLabel = if (selectedTime == ExportTimeRange.CUSTOM) "Tanggal Kustom" else timeOptions[binding.spinnerTime.selectedItemPosition]

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val file = ExportUtils.generatePdfToTempFile(requireContext(), data, getString(R.string.export_doc_title_prefix, timeLabel))
                currentTempPdf = file

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
                    Toast.makeText(requireContext(), getString(R.string.export_toast_fail_render), Toast.LENGTH_SHORT).show()
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
        currentTempPdf?.delete() 
        _binding = null
    }
}
