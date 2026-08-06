package com.smartfinance.tracker.ui.settings

import android.app.DatePickerDialog
import android.app.Dialog
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.smartfinance.tracker.R
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

    // 🔥 FIX: Locale default agar format bulan beradaptasi dengan bahasa HP
    private val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private lateinit var intervals: List<Pair<String, String>>

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
                Toast.makeText(context, getString(R.string.recurring_toast_contact_fail), Toast.LENGTH_SHORT).show()
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
        // 🔥 FIX: Upgrade UI
        val dialog = MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()

        viewModel = ViewModelProvider(requireActivity())[RecurringTxViewModel::class.java]
        editingDocId = arguments?.getString("DOC_ID")

        // Inisialisasi daftar interval dengan string dinamis
        intervals = listOf(
            getString(R.string.recurring_interval_daily) to "DAILY",
            getString(R.string.recurring_interval_weekly) to "WEEKLY",
            getString(R.string.recurring_interval_monthly) to "MONTHLY",
            getString(R.string.recurring_interval_yearly) to "YEARLY"
        )

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
                binding.btnStartDate.text = getString(R.string.recurring_start_date_btn, sdf.format(startDateCal.time))
            }, startDateCal.get(Calendar.YEAR), startDateCal.get(Calendar.MONTH), startDateCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.switchEnd.setOnCheckedChangeListener { _, isChecked ->
            binding.btnEndDate.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        binding.btnEndDate.setOnClickListener {
            DatePickerDialog(requireContext(), { _, y, m, d ->
                endDateCal.set(y, m, d, 23, 59, 59)
                binding.btnEndDate.text = getString(R.string.recurring_end_date_btn, sdf.format(endDateCal.time))
            }, endDateCal.get(Calendar.YEAR), endDateCal.get(Calendar.MONTH), endDateCal.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.btnDelete.setOnClickListener { deleteCurrentSchedule() }
        binding.btnSave.setOnClickListener { saveSchedule() }
    }

    private fun loadDataIfEditing() {
        val docId = editingDocId
        if (docId == null) {
            binding.tvFormTitle.text = getString(R.string.recurring_form_title_new)
            binding.btnDelete.visibility = View.GONE
            binding.btnStartDate.text = getString(R.string.recurring_start_date_btn, sdf.format(startDateCal.time))
            binding.btnEndDate.text = getString(R.string.recurring_end_date_btn, sdf.format(endDateCal.time))
            binding.switchEnd.isChecked = false
            binding.btnEndDate.visibility = View.GONE
            binding.spinnerInterval.setSelection(2) 
        } else {
            val doc = viewModel.schedules.value.find { it.id == docId } ?: return
            binding.tvFormTitle.text = getString(R.string.recurring_form_title_edit)
            binding.btnDelete.visibility = View.VISIBLE

            binding.etNote.setText(doc.note)
            binding.etAmount.setText(if (doc.amount > 0) doc.amount.toLong().toString() else "")
            binding.etContact.setText(doc.contactName)

            selectedCategoryMap = viewModel.categories.value.find { it.id == doc.categoryId }
            binding.btnSelectCategory.text = doc.categoryName

            val intervalIndex = intervals.indexOfFirst { it.second == doc.interval }
            if (intervalIndex >= 0) binding.spinnerInterval.setSelection(intervalIndex)

            startDateCal.timeInMillis = if (doc.nextExecutionTime > 0) doc.nextExecutionTime else System.currentTimeMillis()
            binding.btnStartDate.text = getString(R.string.recurring_start_date_btn, sdf.format(startDateCal.time))

            binding.switchEnd.isChecked = doc.hasEndDate
            binding.btnEndDate.visibility = if (doc.hasEndDate) View.VISIBLE else View.GONE
            
            if (doc.hasEndDate && doc.endDate != null) {
                endDateCal.timeInMillis = doc.endDate
                binding.btnEndDate.text = getString(R.string.recurring_end_date_btn, sdf.format(endDateCal.time))
            }
        }
    }

    private fun saveSchedule() {
        lifecycleScope.launch {
            try {
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
                Toast.makeText(context, getString(R.string.recurring_toast_saved), Toast.LENGTH_SHORT).show()
                dismiss() 
            } catch (e: Exception) {
                Toast.makeText(context, "❌ ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun deleteCurrentSchedule() {
        val docId = editingDocId ?: return
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.recurring_delete_title))
            .setMessage(getString(R.string.recurring_delete_message))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                lifecycleScope.launch {
                    try {
                        viewModel.deleteSchedule(docId)
                        Toast.makeText(context, getString(R.string.recurring_toast_deleted), Toast.LENGTH_SHORT).show()
                        dismiss()
                    } catch (e: Exception) {
                        Toast.makeText(context, getString(R.string.recurring_toast_delete_fail), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.action_cancel), null).show()
    }

    private fun showCategoryPickerDialog() {
        val currentFilter = selectedCategoryMap?.type ?: "EXPENSE"
        val currentSelectedId = selectedCategoryMap?.id

        com.smartfinance.tracker.ui.category.CategoryPickerDialog(currentFilter, currentSelectedId) { selectedCat ->
            selectedCategoryMap = selectedCat
            binding.btnSelectCategory.text = selectedCat.name
        }.show(parentFragmentManager, "CategoryPickerDialog")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
