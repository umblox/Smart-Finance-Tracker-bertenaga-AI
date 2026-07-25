package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.databinding.DialogRecurringTxFormBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class RecurringTxFormDialog : DialogFragment() {

    companion object {
        fun newInstance(docId: String?): RecurringTxFormDialog {
            val frag = RecurringTxFormDialog()
            val args = Bundle()
            args.putString("DOC_ID", docId)
            frag.arguments = args
            return frag
        }
    }

    private var _binding: DialogRecurringTxFormBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: RecurringTxViewModel

    private var startDateCal = Calendar.getInstance()
    private var endDateCal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }

    private var editingDocId: String? = null
    private var selectedCategoryMap: Category? = null

    private val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
    private val intervals = listOf("Harian" to "DAILY", "Mingguan" to "WEEKLY", "Bulanan" to "MONTHLY", "Tahunan" to "YEARLY")

    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            try {
                val cursor = requireContext().contentResolver.query(it, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex >= 0) binding.etContact.setText(cursor.getString(nameIndex))
                    cursor.close()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengambil kontak", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogRecurringTxFormBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()

        // Gunakan requireActivity() untuk mengambil ViewModel yang sama dengan List Dialog
        viewModel = ViewModelProvider(requireActivity())[RecurringTxViewModel::class.java]
        editingDocId = arguments?.getString("DOC_ID")

        setupUI()
        loadDataIfEditing()

        return dialog
    }

    private fun setupUI() {
        binding.spinnerInterval.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, intervals.map { it.first })

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnSelectCategory.setOnClickListener { showCategoryPickerDialog() }
        binding.btnPickContact.setOnClickListener { pickContactLauncher.launch(null) }

        binding.btnStartDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                startDateCal.set(y, m, d, 0, 0, 0)
                binding.btnStartDate.text = "Mulai: ${sdf.format(startDateCal.time)}"
            }, startDateCal.get(Calendar.YEAR), startDateCal.get(Calendar.MONTH), startDateCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.switchEnd.setOnCheckedChangeListener { _, isChecked ->
            binding.btnEndDate.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnEndDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                endDateCal.set(y, m, d, 23, 59, 59)
                binding.btnEndDate.text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
            }, endDateCal.get(Calendar.YEAR), endDateCal.get(Calendar.MONTH), endDateCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnDelete.setOnClickListener { deleteCurrentSchedule() }
        binding.btnSave.setOnClickListener { saveSchedule() }
    }

    private fun loadDataIfEditing() {
        val docId = editingDocId
        if (docId == null) {
            binding.tvFormTitle.text = "Buat Jadwal Baru"
            binding.btnDelete.visibility = View.GONE
            binding.btnStartDate.text = "Mulai: ${sdf.format(startDateCal.time)}"
            binding.btnEndDate.text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
            binding.switchEnd.isChecked = false
            binding.btnEndDate.visibility = View.GONE
            binding.spinnerInterval.setSelection(2) // Default Bulanan
        } else {
            val doc = viewModel.schedules.value.find { it.id == docId } ?: return
            binding.tvFormTitle.text = "Edit Jadwal"
            binding.btnDelete.visibility = View.VISIBLE

            binding.etNote.setText(doc.note)
            binding.etAmount.setText(if (doc.amount > 0) doc.amount.toLong().toString() else "")
            binding.etContact.setText(doc.contactName)

            selectedCategoryMap = viewModel.categories.value.find { it.id == doc.categoryId }
            binding.btnSelectCategory.text = doc.categoryName

            val intervalIndex = intervals.indexOfFirst { it.second == doc.interval }
            if (intervalIndex >= 0) binding.spinnerInterval.setSelection(intervalIndex)

            startDateCal.timeInMillis = if (doc.nextExecutionTime > 0) doc.nextExecutionTime else System.currentTimeMillis()
            binding.btnStartDate.text = "Mulai: ${sdf.format(startDateCal.time)}"

            binding.switchEnd.isChecked = doc.hasEndDate
            binding.btnEndDate.visibility = if (doc.hasEndDate) View.VISIBLE else View.GONE
            
            if (doc.hasEndDate && doc.endDate != null) {
                endDateCal.timeInMillis = doc.endDate
                binding.btnEndDate.text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
            }
        }
    }

    private fun saveSchedule() {
        lifecycleScope.launch {
            try {
                // UI hanya menyerahkan data mentah ke ViewModel
                viewModel.validateAndSaveSchedule(
                    docId = editingDocId,
                    note = binding.etNote.text.toString(),
                    amountStr = binding.etAmount.text.toString(),
                    category = selectedCategoryMap,
                    contactName = binding.etContact.text.toString(),
                    interval = intervals[binding.spinnerInterval.selectedItemPosition].second,
                    nextExecutionTime = startDateCal.timeInMillis,
                    hasEndDate = binding.switchEnd.isChecked,
                    endDate = endDateCal.timeInMillis
                )
                Toast.makeText(context, "✅ Jadwal Tersimpan!", Toast.LENGTH_SHORT).show()
                dismiss() // Tutup form, otomatis kembali ke List
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCurrentSchedule() {
        val docId = editingDocId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Jadwal")
            .setMessage("Anda yakin ingin menghentikan & menghapus jadwal transaksi otomatis ini?")
            .setPositiveButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteSchedule(docId)
                        Toast.makeText(context, "🗑️ Jadwal Dihapus!", Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, "❌ Gagal menghapus jadwal", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Batal", null).show()
    }

    // Programmatic UI Kategori tetap di sini agar tidak membebani XML Form
    private fun showCategoryPickerDialog() {
        val density = requireContext().resources.displayMetrics.density
        val dialogLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F8FAFC")) }
        
        val tabOuterBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding((4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42f * density).toInt()).apply { setMargins((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (8f * density).toInt()) }
            background = GradientDrawable().apply { cornerRadius = 12f * density; setColor(Color.parseColor("#E2E8F0")) }
            weightSum = 3f
        }

        val btnTabExpense = MaterialButton(requireContext()).apply { text = "Pengeluaran"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabIncome = MaterialButton(requireContext()).apply { text = "Pemasukan"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabDebt = MaterialButton(requireContext()).apply { text = "Hutang/Piutang"; textSize = 10f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }

        tabOuterBox.addView(btnTabExpense); tabOuterBox.addView(btnTabIncome); tabOuterBox.addView(btnTabDebt)
        dialogLayout.addView(tabOuterBox)

        val scrollView = ScrollView(requireContext())
        val containerList = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) }
        scrollView.addView(containerList)
        dialogLayout.addView(scrollView)

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogLayout).create()

        fun renderList(typeFilter: String) {
            containerList.removeAllViews()
            val activeBg = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
            val inactiveBg = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            
            btnTabExpense.apply { backgroundTintList = if(typeFilter == "EXPENSE") activeBg else inactiveBg; setTextColor(if(typeFilter == "EXPENSE") Color.WHITE else Color.parseColor("#64748B")) }
            btnTabIncome.apply { backgroundTintList = if(typeFilter == "INCOME") activeBg else inactiveBg; setTextColor(if(typeFilter == "INCOME") Color.WHITE else Color.parseColor("#64748B")) }
            btnTabDebt.apply { backgroundTintList = if(typeFilter == "DEBT") activeBg else inactiveBg; setTextColor(if(typeFilter == "DEBT") Color.WHITE else Color.parseColor("#64748B")) }

            val targetTypes = if (typeFilter == "DEBT") listOf("DEBT", "RECEIVABLE") else listOf(typeFilter)
            val filteredList = viewModel.categories.value.filter { targetTypes.contains(it.type) }
            val parentCategories = filteredList.filter { it.parentCategoryId == null }.sortedBy { it.name }
            val subCategories = filteredList.filter { it.parentCategoryId != null }

            if (parentCategories.isEmpty()) {
                containerList.addView(TextView(requireContext()).apply { text = "Belum ada kategori terdaftar."; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(0, 40, 0, 40) })
                return
            }

            parentCategories.forEach { parent ->
                val blockCard = MaterialCardView(requireContext()).apply { radius = 14f * density; cardElevation = 1f * density; strokeWidth = 0; setCardBackgroundColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
                val cardContentContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                val parentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                    setOnClickListener { selectedCategoryMap = parent; binding.btnSelectCategory.text = parent.name; dialog.dismiss() }
                }
                parentRow.addView(TextView(requireContext()).apply { text = "📁"; textSize = 16f; setPadding(0, 0, (12 * density).toInt(), 0) })
                parentRow.addView(TextView(requireContext()).apply { text = parent.name; setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f; setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                cardContentContainer.addView(parentRow)

                val kids = subCategories.filter { it.parentCategoryId == parent.id }.sortedBy { it.name }
                if (kids.isNotEmpty()) cardContentContainer.addView(View(requireContext()).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })

                kids.forEach { child ->
                    val childRow = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt()); setBackgroundColor(Color.parseColor("#FAFAFA"))
                        setOnClickListener { selectedCategoryMap = child; binding.btnSelectCategory.text = child.name; dialog.dismiss() }
                    }
                    val treeLine = View(requireContext()).apply { setBackgroundColor(Color.parseColor("#CBD5E0")); layoutParams = LinearLayout.LayoutParams((1.5f * density).toInt(), (16 * density).toInt()).apply { rightMargin = (12 * density).toInt(); leftMargin = (6 * density).toInt() } }
                    childRow.addView(treeLine); childRow.addView(TextView(requireContext()).apply { text = "💰"; textSize = 13f; setPadding(0, 0, (10 * density).toInt(), 0) })
                    childRow.addView(TextView(requireContext()).apply { text = child.name; setTextColor(Color.parseColor("#475569")); textSize = 13.5f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                    cardContentContainer.addView(childRow)
                }
                blockCard.addView(cardContentContainer)
                containerList.addView(blockCard)
            }
        }

        btnTabExpense.setOnClickListener { renderList("EXPENSE") }
        btnTabIncome.setOnClickListener { renderList("INCOME") }
        btnTabDebt.setOnClickListener { renderList("DEBT") }

        val currentType = selectedCategoryMap?.type ?: "EXPENSE"
        renderList(if (currentType == "RECEIVABLE") "DEBT" else currentType)

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
