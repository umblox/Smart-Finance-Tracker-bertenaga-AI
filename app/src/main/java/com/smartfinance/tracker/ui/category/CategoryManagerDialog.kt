package com.smartfinance.tracker.ui.category

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.data.model.Category
import com.smartfinance.tracker.databinding.DialogCategoryManagerBinding
import kotlinx.coroutines.launch

class CategoryManagerDialog : DialogFragment() {

    private var _binding: DialogCategoryManagerBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: CategoryViewModel

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogCategoryManagerBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext(), android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(binding.root)
            .create()

        viewModel = ViewModelProvider(this)[CategoryViewModel::class.java]

        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnAdd.setOnClickListener { 
            CategoryEditorDialog(null, viewModel.uiState.value.currentFilter) { 
                // Biarkan kosong, ViewModel otomatis bereaksi
            }.show(parentFragmentManager, "CategoryEditorDialog")
        }

        binding.btnTabExpense.setOnClickListener { switchFilterTab("EXPENSE", binding.btnTabExpense, binding.btnTabIncome, binding.btnTabDebt) }
        binding.btnTabIncome.setOnClickListener { switchFilterTab("INCOME", binding.btnTabIncome, binding.btnTabExpense, binding.btnTabDebt) }
        binding.btnTabDebt.setOnClickListener { switchFilterTab("DEBT", binding.btnTabDebt, binding.btnTabExpense, binding.btnTabIncome) }

        // Set default UI
        switchFilterTab("EXPENSE", binding.btnTabExpense, binding.btnTabIncome, binding.btnTabDebt)

        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                renderHierarchyCloud(state)
            }
        }

        return dialog
    }

    private fun switchFilterTab(targetFilter: String, active: TextView, in1: TextView, in2: TextView) {
        val density = requireContext().resources.displayMetrics.density
        viewModel.setFilter(targetFilter) // Beri tahu ViewModel filter berubah

        active.setTextColor(Color.WHITE)
        active.setTypeface(null, Typeface.BOLD)
        active.background = GradientDrawable().apply { cornerRadius = 10f * density; setColor(Color.parseColor("#1E293B")) }
        
        in1.setTextColor(Color.parseColor("#64748B")); in1.setTypeface(null, Typeface.NORMAL); in1.background = null
        in2.setTextColor(Color.parseColor("#64748B")); in2.setTypeface(null, Typeface.NORMAL); in2.background = null
    }

    private fun renderHierarchyCloud(state: CategoryUiState) {
        binding.containerList.removeAllViews()
        val density = requireContext().resources.displayMetrics.density

        if (state.parentCategories.isEmpty()) {
            binding.containerList.addView(TextView(requireContext()).apply {
                text = "Belum ada rumpun kategori terdaftar."
                textSize = 13.5f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                setPadding(0, (40f * density).toInt(), 0, 0); setTypeface(null, Typeface.ITALIC)
            })
            return
        }

        state.parentCategories.forEach { parent ->
            val blockCard = MaterialCardView(requireContext()).apply {
                radius = 14f * density; cardElevation = 1f * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12f * density).toInt() }
            }

            val cardContentContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }

            val parentRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((14f * density).toInt(), (14f * density).toInt(), (14f * density).toInt(), (14f * density).toInt())
                setOnClickListener { 
                    CategoryEditorDialog(categoryToHashMap(parent), state.currentFilter) {}.show(parentFragmentManager, "CategoryEditorDialog")
                }
            }

            parentRow.addView(TextView(requireContext()).apply { text = "📁"; textSize = 16f; setPadding(0, 0, (12f * density).toInt(), 0) })
            parentRow.addView(TextView(requireContext()).apply {
                text = parent.name; setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            if (parent.isLocked) parentRow.addView(TextView(requireContext()).apply { text = "🔒"; textSize = 13f; setTextColor(Color.parseColor("#94A3B8")); setPadding((6f * density).toInt(), 0, 0, 0) })
            
            cardContentContainer.addView(parentRow)

            val kids = state.subCategories.filter { it.parentCategoryId == parent.id }.sortedBy { it.name }
            
            if (kids.isNotEmpty()) {
                cardContentContainer.addView(View(requireContext()).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1f * density).toInt()) })
            }

            kids.forEach { child ->
                val childRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding((14f * density).toInt(), (10f * density).toInt(), (14f * density).toInt(), (10f * density).toInt())
                    setBackgroundColor(Color.parseColor("#FAFAFA"))
                    setOnClickListener { 
                        CategoryEditorDialog(categoryToHashMap(child), state.currentFilter) {}.show(parentFragmentManager, "CategoryEditorDialog")
                    }
                }

                val treeLine = View(requireContext()).apply {
                    setBackgroundColor(Color.parseColor("#CBD5E0"))
                    layoutParams = LinearLayout.LayoutParams((1.5f * density).toInt(), (16f * density).toInt()).apply { rightMargin = (12f * density).toInt(); leftMargin = (6f * density).toInt() }
                }
                childRow.addView(treeLine)
                childRow.addView(TextView(requireContext()).apply { text = "💰"; textSize = 13f; setPadding(0, 0, (10f * density).toInt(), 0) })
                childRow.addView(TextView(requireContext()).apply {
                    text = child.name; setTextColor(Color.parseColor("#475569")); textSize = 13.5f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })

                if (child.isLocked) childRow.addView(TextView(requireContext()).apply { text = "🔒"; textSize = 11f; setTextColor(Color.parseColor("#94A3B8")); setPadding((6f * density).toInt(), 0, 0, 0) })
                
                cardContentContainer.addView(childRow)
            }

            blockCard.addView(cardContentContainer)
            binding.containerList.addView(blockCard)
        }
    }

    private fun categoryToHashMap(cat: Category): HashMap<String, Any> {
        return hashMapOf(
            "docId" to cat.docId, "id" to cat.id, "name" to cat.name,
            "type" to cat.type, "iconName" to cat.iconName,
            "isLocked" to cat.isLocked
        ).apply { cat.parentCategoryId?.let { put("parentCategoryId", it) } }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
