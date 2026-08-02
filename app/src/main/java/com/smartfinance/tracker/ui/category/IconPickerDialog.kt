package com.smartfinance.tracker.ui.category

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.tabs.TabLayout
import com.smartfinance.tracker.R
import com.smartfinance.tracker.utils.FinanceIcon
import com.smartfinance.tracker.utils.IconProvider

class IconPickerDialog(
    private val currentIconName: String,
    private val onIconSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var rvIcons: RecyclerView
    private lateinit var tabLayoutGroups: TabLayout
    private lateinit var iconAdapter: IconAdapter

    private val allGroups = IconProvider.getAllIconGroups()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_icon_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
        rvIcons.layoutManager = GridLayoutManager(requireContext(), 4)
        iconAdapter = IconAdapter { selectedIcon ->
            onIconSelected(selectedIcon.iconName)
            dismiss() // Tutup dialog setelah ikon dipilih
        }
        rvIcons.adapter = iconAdapter
        
        // Load default tab (index 0)
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
                gravity = android.view.Gravity.CENTER
                setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
            }

            val imageView = androidx.appcompat.widget.AppCompatImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams((48 * density).toInt(), (48 * density).toInt())
                setPadding((12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt(), (12 * density).toInt())
                scaleType = androidx.appcompat.widget.AppCompatImageView.ScaleType.FIT_CENTER
            }
            layout.addView(imageView)

            return IconViewHolder(layout, imageView)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val icon = icons[position]
            val context = holder.itemView.context
            
            // Render gambar (Akan crash/samar jika file XML di drawable rusak)
            holder.imageView.setImageResource(icon.resId)

            val isSelected = icon.iconName == selectedIconName
            val colorPrimary = androidx.core.content.ContextCompat.getColor(context, R.color.primary)
            val colorTextSec = androidx.core.content.ContextCompat.getColor(context, R.color.text_secondary)

            if (isSelected) {
                // Ikon terpilih: Background bulat warna utama, ikon warna putih
                holder.imageView.background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(colorPrimary)
                }
                holder.imageView.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
            } else {
                // Ikon tidak terpilih: Background transparan, ikon warna abu-abu
                holder.imageView.background = null
                holder.imageView.imageTintList = android.content.res.ColorStateList.valueOf(colorTextSec)
            }
            
            holder.itemView.setOnClickListener { onIconClick(icon) }
        }

        override fun getItemCount(): Int = icons.size

        inner class IconViewHolder(view: View, val imageView: androidx.appcompat.widget.AppCompatImageView) : RecyclerView.ViewHolder(view)
    }
}
