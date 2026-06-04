package com.smartfinance.tracker.ui.category

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CategoryFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var containerList: LinearLayout
    private var currentTypeFilter = "EXPENSE"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212")) // Dark mode sekelas Money Lover premium
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // TABS ATAS: PENGELUARAN / PEMASUKAN
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).toInt())
            weightSum = 2f
            setBackgroundColor(Color.parseColor("#1A1A1A"))
        }

        val btnExpense = TextView(context).apply {
            text = "PENGELUARAN"; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { currentTypeFilter = "EXPENSE"; renderHierarchy() }
        }
        val btnIncome = TextView(context).apply {
            text = "PEMASUKAN"; gravity = Gravity.CENTER; textSize = 12f; setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#A0AEC0")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            setOnClickListener { currentTypeFilter = "INCOME"; renderHierarchy() }
        }
        tabLayout.addView(btnExpense)
        tabLayout.addView(btnIncome)
        root.addView(tabLayout)

        // TOMBOL TAMBAH KATEGORI BARU
        val btnAdd = Button(context).apply {
            text = "＋ KATEGORI BARU"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#2F855A"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            }
            setOnClickListener { showEditOrCreateDialog(null) }
        }
        root.addView(btnAdd)

        val nsv = NestedScrollView(context).apply { isFillViewport = true }
        containerList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (16 * density).toInt())
        }
        nsv.addView(containerList)
        root.addView(nsv)

        renderHierarchy()
        return root
    }

    private fun renderHierarchy() {
        lifecycleScope.launch {
            val allCategories = db.categoryDao().getAllCategories().first()
            val filtered = allCategories.filter { it.type == currentTypeFilter }
            
            containerList.removeAllViews()
            val density = requireContext().resources.displayMetrics.density

            // Pisahkan antara Induk (parent == null) dan Anak
            val parentCategories = filtered.filter { it.parentCategoryId == null }
            val subCategories = filtered.filter { it.parentCategoryId != null }

            parentCategories.forEach { parent ->
                // Row untuk Kategori Induk
                val parentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
                    setOnClickListener { showEditOrCreateDialog(parent) }
                }

                val iconView = TextView(context).apply { text = "📁"; textSize = 18f; setPadding(0, 0, (12 * density).toInt(), 0) }
                val titleView = TextView(context).apply {
                    text = parent.name; textColor = Color.WHITE; textSize = 15f; setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                parentRow.addView(iconView)
                parentRow.addView(titleView)
                containerList.addView(parentRow)

                // Ambil semua sub-kategori milik induk ini
                val kids = subCategories.filter { it.parentCategoryId == parent.id }
                kids.forEachIndexed { index, child ->
                    val childRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding((16 * density).toInt(), (10 * density).toInt(), 0, (10 * density).toInt())
                        setOnClickListener { showEditOrCreateDialog(child) }
                    }

                    // Trik Garis Vertikal Pohon Sesuai Gambar `1000179819.png`
                    val treeLine = View(context).apply {
                        setBackgroundColor(Color.parseColor("#4A5568"))
                        layoutParams = LinearLayout.LayoutParams((2 * density).toInt(), (24 * density).toInt()).apply { rightMargin = (16 * density).toInt() }
                    }
                    childRow.addView(treeLine)

                    val childIcon = TextView(context).apply { text = "💰"; textSize = 14f; setPadding(0, 0, (10 * density).toInt(), 0) }
                    val childTitle = TextView(context).apply {
                        text = child.name; setTextColor(Color.parseColor("#CBD5E0")); textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    childRow.addView(childIcon)
                    childRow.addView(childTitle)
                    containerList.addView(childRow)
                }

                // Garis pembatas antar kelompok induk
                containerList.addView(View(context).apply {
                    setBackgroundColor(Color.parseColor("#2D3748"))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (4 * density).toInt() }
                })
            }
        }
    }

    // ========================================================
    // 🛠️ DIALOG EDITOR DINAMIS (SESUAI BOX 2: 1000179831.png)
    // ========================================================
    private fun showEditOrCreateDialog(category: CategoryEntity?) {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        
        val builder = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog)
        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
        }

        dialogLayout.addView(TextView(context).apply { text = "Nama Kategori"; setTextColor(Color.GRAY); textSize = 12f })
        val etName = EditText(context).apply { 
            setText(category?.name ?: "")
            setTextColor(Color.WHITE)
            hint = "Masukkan nama kategori"
            setHintTextColor(Color.DKGRAY)
        }
        dialogLayout.addView(etName)

        dialogLayout.addView(TextView(context).apply { text = "Kategori Induk (Pilih jika ini sub-kategori)"; setTextColor(Color.GRAY); textSize = 12f; setPadding(0, (12 * density).toInt(), 0, 0) })
        
        val spinnerParent = Spinner(context)
        lifecycleScope.launch {
            val allCats = db.categoryDao().getAllCategories().first()
            val parents = allCats.filter { it.parentCategoryId == null && it.type == currentTypeFilter }
            
            val listNames = mutableListOf("[Tanpa Induk / Kategori Utama]")
            parents.forEach { listNames.add(it.name) }
            
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerParent.adapter = adapter
            
            category?.parentCategoryId?.let { parentId ->
                val matchIdx = parents.indexOfFirst { it.id == parentId }
                if (matchIdx != -1) spinnerParent.setSelection(matchIdx + 1)
            }
        }
        dialogLayout.addView(spinnerParent)

        builder.setView(dialogLayout)
        builder.setTitle(if (category == null) "Tambah Kategori" else "Ubah Kategori")
        builder.setPositiveButton("Simpan") { dialog, _ ->
            val finalName = etName.text.toString().trim()
            if (finalName.isNotEmpty()) {
                lifecycleScope.launch {
                    val allCats = db.categoryDao().getAllCategories().first()
                    val parents = allCats.filter { it.parentCategoryId == null && it.type == currentTypeFilter }
                    val selectedPos = spinnerParent.selectedItemPosition
                    val finalParentId = if (selectedPos == 0) null else parents[selectedPos - 1].id

                    val newCat = CategoryEntity(
                        id = category?.id ?: 0,
                        name = finalName,
                        type = currentTypeFilter,
                        iconName = category?.iconName ?: "ic_custom",
                        parentCategoryId = finalParentId
                    )
                    db.categoryDao().insertCategory(newCat)
                    renderHierarchy()
                }
            }
        }
        if (category != null) {
            builder.setNegativeButton("Hapus") { _, _ ->
                lifecycleScope.launch {
                    db.categoryDao().deleteCategory(category)
                    renderHierarchy()
                }
            }
        }
        builder.show()
    }
}
