package com.smartfinance.tracker.ui.category

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.DialogCategoryManagerBinding
import kotlinx.coroutines.launch

class CategoryManagerDialog : DialogFragment() {

    private var _binding: DialogCategoryManagerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CategoryViewModel

    private fun getThemeColor(resId: Int): Int = ContextCompat.getColor(requireContext(), resId)

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogCategoryManagerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[CategoryViewModel::class.java]
        
        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnTabExpense.setOnClickListener { switchTab("EXPENSE") }
        binding.btnTabIncome.setOnClickListener { switchTab("INCOME") }
        binding.btnTabDebt.setOnClickListener { switchTab("DEBT") }
        
        switchTab("EXPENSE")

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderList(state) }
        }
    }

    private fun switchTab(targetFilter: String) {
        viewModel.setFilter(targetFilter)
        
        val activeColor = getThemeColor(R.color.primary)
        val inactiveColor = getThemeColor(R.color.text_secondary)
        val transparent = Color.TRANSPARENT

        binding.tvTabExpense.setTextColor(if (targetFilter == "EXPENSE") activeColor else inactiveColor)
        binding.indicatorExpense.setBackgroundColor(if (targetFilter == "EXPENSE") activeColor else transparent)
        binding.tvTabExpense.setTypeface(null, if (targetFilter == "EXPENSE") Typeface.BOLD else Typeface.NORMAL)

        binding.tvTabIncome.setTextColor(if (targetFilter == "INCOME") activeColor else inactiveColor)
        binding.indicatorIncome.setBackgroundColor(if (targetFilter == "INCOME") activeColor else transparent)
        binding.tvTabIncome.setTypeface(null, if (targetFilter == "INCOME") Typeface.BOLD else Typeface.NORMAL)

        binding.tvTabDebt.setTextColor(if (targetFilter == "DEBT") activeColor else inactiveColor)
        binding.indicatorDebt.setBackgroundColor(if (targetFilter == "DEBT") activeColor else transparent)
        binding.tvTabDebt.setTypeface(null, if (targetFilter == "DEBT") Typeface.BOLD else Typeface.NORMAL)
    }

    private fun renderList(state: CategoryUiState) {
        binding.containerList.removeAllViews()
        val density = requireContext().resources.displayMetrics.density
        
        val typedValue = android.util.TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
        val safeRippleId = typedValue.resourceId

        // TOMBOL "+ KATEGORI BARU"
        val btnAddNew = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20f * density).toInt(), (16f * density).toInt(), (20f * density).toInt(), (16f * density).toInt())
            setBackgroundResource(safeRippleId)
            isClickable = true
            isFocusable = true
            setOnClickListener { 
                CategoryEditorDialog.newInstance(null, state.currentFilter).show(parentFragmentManager, "CategoryEditorDialog")
            }
        }
        btnAddNew.addView(TextView(requireContext()).apply { 
            text = "＋"
            textSize = 20f
            setTextColor(getThemeColor(R.color.primary))
            setPadding(0, 0, (16f * density).toInt(), 0)
        })
        btnAddNew.addView(TextView(requireContext()).apply { 
            text = "KATEGORI BARU"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(getThemeColor(R.color.primary))
        })
        binding.containerList.addView(btnAddNew)
        binding.containerList.addView(View(requireContext()).apply { setBackgroundColor(getThemeColor(R.color.divider_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1f * density).toInt()) })

        // RENDER DAFTAR HIERARKI
        state.parentCategories.forEach { parent ->
            val parentRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((20f * density).toInt(), (14f * density).toInt(), (20f * density).toInt(), (14f * density).toInt())
                setBackgroundResource(safeRippleId)
                isClickable = true
                isFocusable = true
                setOnClickListener { CategoryEditorDialog.newInstance(parent, state.currentFilter).show(parentFragmentManager, "CategoryEditorDialog") }
            }
            
            // 🔥 INJEKSI IKON INDUK DINAMIS
            parentRow.addView(android.widget.ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt()).apply { rightMargin = (16 * density).toInt() }
                setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(parent.iconName))
                imageTintList = android.content.res.ColorStateList.valueOf(getThemeColor(R.color.primary))
            })
            
            parentRow.addView(TextView(requireContext()).apply {
                text = parent.name
                setTextColor(getThemeColor(R.color.text_primary))
                textSize = 16f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (parent.isLocked) parentRow.addView(TextView(requireContext()).apply { text = "🔒"; textSize = 13f; setTextColor(getThemeColor(R.color.text_secondary)) })
            binding.containerList.addView(parentRow)

            val kids = state.subCategories.filter { it.parentCategoryId == parent.id }.sortedBy { it.name }
            kids.forEach { child ->
                val childRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((30f * density).toInt(), (10f * density).toInt(), (20f * density).toInt(), (10f * density).toInt())
                    setBackgroundResource(safeRippleId)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { CategoryEditorDialog.newInstance(child, state.currentFilter).show(parentFragmentManager, "CategoryEditorDialog") }
                }
                val treeLine = View(requireContext()).apply {
                    setBackgroundColor(getThemeColor(R.color.divider_color))
                    layoutParams = LinearLayout.LayoutParams((2f * density).toInt(), (24f * density).toInt()).apply { rightMargin = (16f * density).toInt() }
                }
                childRow.addView(treeLine)
                
                // 🔥 INJEKSI IKON SUB-KATEGORI DINAMIS
                childRow.addView(android.widget.ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((24 * density).toInt(), (24 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                    setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(child.iconName))
                    imageTintList = android.content.res.ColorStateList.valueOf(getThemeColor(R.color.text_secondary))
                })
                
                childRow.addView(TextView(requireContext()).apply {
                    text = child.name
                    setTextColor(getThemeColor(R.color.text_primary))
                    textSize = 15f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                if (child.isLocked) childRow.addView(TextView(requireContext()).apply { text = "🔒"; textSize = 11f; setTextColor(getThemeColor(R.color.text_secondary)) })
                binding.containerList.addView(childRow)
            }
            binding.containerList.addView(View(requireContext()).apply { setBackgroundColor(getThemeColor(R.color.background_color)); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1f * density).toInt()) })
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
