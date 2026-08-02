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
    // ADAPTER RECYCLERVIEW (Inner Class agar rapi)
    // ==============================================================================
    inner class IconAdapter(private val onIconClick: (FinanceIcon) -> Unit) :
        RecyclerView.Adapter<IconAdapter.IconViewHolder>() {

        private var icons: List<FinanceIcon> = emptyList()
        private var selectedIconName: String = ""

        fun setIcons(newIcons: List<FinanceIcon>, currentSelected: String) {
            icons = newIcons
            selectedIconName = currentSelected
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
            // Buat View secara dinamis tanpa XML terpisah (Lebih ringan & aman untuk CI/CD)
            val density = parent.context.resources.displayMetrics.density
            val layout = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                gravity = android.view.Gravity.CENTER
                setPadding(
                    (8 * density).toInt(), (16 * density).toInt(),
                    (8 * density).toInt(), (16 * density).toInt()
                )
            }

            val imageView = ImageView(parent.context).apply {
                id = android.R.id.icon
                layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt())
                setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
                background = ContextCompat.getDrawable(context, R.drawable.bg_circle_icon) // Pastikan file ini ada di project Anda
            }
            layout.addView(imageView)

            return IconViewHolder(layout, imageView)
        }

        override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
            val icon = icons[position]
            holder.imageView.setImageResource(icon.resId)

            // Logika UI jika Ikon ini sedang aktif (terpilih)
            if (icon.iconName == selectedIconName) {
                holder.imageView.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.context, R.color.primary)
                )
                holder.imageView.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.context, android.R.color.white)
                )
            } else {
                holder.imageView.backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.context, R.color.background_color)
                )
                holder.imageView.imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(holder.itemView.context, R.color.primary)
                )
            }

            holder.itemView.setOnClickListener { onIconClick(icon) }
        }

        override fun getItemCount(): Int = icons.size

        inner class IconViewHolder(view: View, val imageView: ImageView) : RecyclerView.ViewHolder(view)
    }
}
