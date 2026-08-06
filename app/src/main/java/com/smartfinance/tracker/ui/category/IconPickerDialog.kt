package com.smartfinance.tracker.ui.category

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.smartfinance.tracker.R
import com.smartfinance.tracker.utils.FinanceIcon
import com.smartfinance.tracker.utils.IconProvider

class IconPickerDialog : BottomSheetDialogFragment() {

    companion object {
        fun newInstance(currentIconName: String): IconPickerDialog {
            val args = Bundle().apply {
                putString("CURRENT_ICON", currentIconName)
            }
            val fragment = IconPickerDialog()
            fragment.arguments = args
            return fragment
        }
    }

    private lateinit var rvIcons: RecyclerView
    private lateinit var tabLayoutGroups: TabLayout
    private lateinit var iconAdapter: IconAdapter

    // 🔥 FIX: Dideklarasikan sebagai lateinit agar aman dari Lifecycle Crash
    private lateinit var allGroups: List<Pair<String, List<FinanceIcon>>>
    
    private var currentIconName = "ic_custom"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_icon_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 🔥 FIX: Mengambil data Ikon dwibahasa HANYA SETELAH view tercipta dan Context sudah siap!
        allGroups = IconProvider.getAllIconGroups(requireContext())
        
        currentIconName = arguments?.getString("CURRENT_ICON") ?: "ic_custom"

        rvIcons = view.findViewById(R.id.rvIcons)
        tabLayoutGroups = view.findViewById(R.id.tabLayoutGroups)

        setupTabs()
        setupRecyclerView()
    }

    private fun setupTabs() {
        allGroups.forEach { group ->
            tabLayoutGroups.addTab(tabLayoutGroups.newTab().setText(group.first))
        }

        tabLayoutGroups.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.position?.let { updateIconGrid(it) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }

    private fun setupRecyclerView() {
        rvIcons.layoutManager = GridLayoutManager(requireContext(), 5)
        iconAdapter = IconAdapter { selectedIcon ->
            if (isAdded && !isStateSaved) {
                parentFragmentManager.setFragmentResult("icon_request", Bundle().apply {
                    putString("selected_icon", selectedIcon.iconName)
                })
                dismissAllowingStateLoss()
            }
        }
        rvIcons.adapter = iconAdapter
        updateIconGrid(0)
    }

    private fun updateIconGrid(groupIndex: Int) {
        val selectedGroupIcons = allGroups.getOrNull(groupIndex)?.second ?: emptyList()
        iconAdapter.setIcons(selectedGroupIcons, currentIconName)
    }

    // ==============================================================================
    // ADAPTER RECYCLERVIEW
    // ==============================================================================
    inner class IconAdapter(private val onIconClick: (FinanceIcon) -> Unit) : RecyclerView.Adapter<IconAdapter.IconViewHolder>() {
        
        private var icons: List<FinanceIcon> = emptyList()
        private var selectedIconName: String = ""

        fun setIcons(newIcons: List<FinanceIcon>, currentSelected: String) {
            icons = newIcons
            selectedIconName = currentSelected
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            val density = parent.context.resources.displayMetrics.density
            val layout = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            }

            val imageView = AppCompatImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt())
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            }
            layout.addView(imageView)

            return IconViewHolder(layout, imageView)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val icon = icons[position]
            val context = holder.itemView.context
            
            holder.imageView.setImageResource(icon.resId)

            val isSelected = icon.iconName == selectedIconName

            if (isSelected) {
                holder.imageView.setBackgroundResource(R.drawable.bg_circle_icon)
                holder.imageView.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.primary))
                holder.imageView.imageTintList = ColorStateList.valueOf(Color.WHITE)
            } else {
                holder.imageView.background = null
                holder.imageView.backgroundTintList = null
                holder.imageView.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.text_primary))
            }
            
            holder.itemView.setOnClickListener { onIconClick(icon) }
        }

        override fun getItemCount(): Int = icons.size

        inner class IconViewHolder(view: View, val imageView: AppCompatImageView) : RecyclerView.ViewHolder(view)
    }
}
