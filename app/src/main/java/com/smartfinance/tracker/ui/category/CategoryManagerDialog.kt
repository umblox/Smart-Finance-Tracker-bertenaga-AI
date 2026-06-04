package com.smartfinance.tracker.ui.category

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CategoryManagerDialog : DialogFragment() {

    private lateinit var db: AppDatabase
    private lateinit var containerList: LinearLayout
    private var currentTypeFilter = "EXPENSE"

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)
        val density = context.resources.displayMetrics.density

        // Layout Utama Manajemen Kategori (Mengikuti Tema Utama Aplikasi: Terang & Kontras)
        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // TABS ATAS FILTER: PENGELUARAN / PEMASUKAN
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).toInt())
            weightSum = 2f
            setBackgroundColor(Color.WHITE)
        }

        val btnExpense = TextView(context).apply {
            text = "PENGELUARAN"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#008080")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        val btnIncome = TextView(context).apply {
            text = "PEMASUKAN"; gravity = Gravity.CENTER; textSize = 13f; setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#718096")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        btnExpense.setOnClickListener {
            currentTypeFilter = "EXPENSE"
            btnExpense.setTypeface(null, Typeface.BOLD); btnExpense.setTextColor(Color.parseColor("#008080"))
            btnIncome.setTypeface(null, Typeface.NORMAL); btnIncome.setTextColor(Color.parseColor("#718096"))
            renderHierarchy()
        }

        btnIncome.setOnClickListener {
            currentTypeFilter = "INCOME"
            btnIncome.setTypeface(null, Typeface.BOLD); btnIncome.setTextColor(Color.parseColor("#008080"))
            btnExpense.setTypeface(null, Typeface.NORMAL); btnExpense.setTextColor(Color.parseColor("#718096"))
            renderHierarchy()
        }

        tabLayout.addView(btnExpense)
        tabLayout.addView(btnIncome)
        mainLayout.addView(tabLayout)

        // TOMBOL TAMBAH KATEGORI BARU
        val btnAdd = Button(context).apply {
            text = "＋ KATEGORI BARU"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            }
            setOnClickListener { showFullScreenEditor(null) }
        }
        mainLayout.addView(btnAdd)

        val scrollView = ScrollView(context).apply { isFillViewport = true }
        containerList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (16 * density).toInt())
        }
        scrollView.addView(containerList)
        mainLayout.addView(scrollView)

        renderHierarchy()

        return AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(mainLayout)
            .create()
    }

    private fun renderHierarchy() {
        lifecycleScope.launch {
            val allCategories = db.categoryDao().getAllCategories().first()
            val filtered = allCategories.filter { it.type == currentTypeFilter }
            
            containerList.removeAllViews()
            val density = requireContext().resources.displayMetrics.density

            val parentCategories = filtered.filter { it.parentCategoryId == null }
            val subCategories = filtered.filter { it.parentCategoryId != null }

            parentCategories.forEach { parent ->
                val parentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
                    setOnClickListener { showFullScreenEditor(parent) }
                }

                val iconView = TextView(context).apply { text = "📁"; textSize = 18f; setPadding(0, 0, (12 * density).toInt(), 0) }
                val titleView = TextView(context).apply {
                    text = parent.name; setTextColor(Color.parseColor("#2D3748")); textSize = 15f; setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                parentRow.addView(iconView)
                parentRow.addView(titleView)
                containerList.addView(parentRow)

                val kids = subCategories.filter { it.parentCategoryId == parent.id }
                kids.forEach { child ->
                    val childRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding((16 * density).toInt(), (10 * density).toInt(), 0, (10 * density).toInt())
                        setOnClickListener { showFullScreenEditor(child) }
                    }

                    val treeLine = View(context).apply {
                        setBackgroundColor(Color.parseColor("#CBD5E0"))
                        layoutParams = LinearLayout.LayoutParams((2 * density).toInt(), (24 * density).toInt()).apply { rightMargin = (16 * density).toInt() }
                    }
                    childRow.addView(treeLine)

                    val childIcon = TextView(context).apply { text = "💰"; textSize = 14f; setPadding(0, 0, (10 * density).toInt(), 0) }
                    val childTitle = TextView(context).apply {
                        text = child.name; setTextColor(Color.parseColor("#4A5568")); textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    childRow.addView(childIcon)
                    childRow.addView(childTitle)
                    containerList.addView(childRow)
                }

                containerList.addView(View(context).apply {
                    setBackgroundColor(Color.parseColor("#E2E8F0"))
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (4 * density).toInt() }
                })
            }
        }
    }

    // ========================================================
    // 🛠️ HALAMAN EDITOR FULL SCREEN SESUAI MODEL 1000179831.png
    // ========================================================
    private fun showFullScreenEditor(category: CategoryEntity?) {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val editorContainer = RelativeLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // --- TOP BAR HIERARKI ---
        val topBar = RelativeLayout(context).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        }

        // Tombol Silang (X) Kiri Atas
        val btnClose = TextView(context).apply {
            text = "✕"; textSize = 20f; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
        }
        topBar.addView(btnClose)

        // Judul Tengah Halaman
        val tvTitle = TextView(context).apply {
            text = if (category == null) "Tambah kategori" else "Ubah kategori"
            textSize = 16f; setTextColor(Color.parseColor("#1A202C")); setTypeface(null, Typeface.BOLD)
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
            layoutParams = lp
        }
        topBar.addView(tvTitle)

        // Tombol Tong Sampah / Teks HAPUS Kanan Atas
        val btnDelete = TextView(context).apply {
            text = "HAPUS"; textSize = 13f; setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD)
            setPadding((16 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
            visibility = if (category != null) View.VISIBLE else View.GONE
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = lp
        }
        topBar.addView(btnDelete)
        editorContainer.addView(topBar)

        // --- FORM CONTENT DATA KONTRAS ---
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
                addRule(RelativeLayout.BELOW, topBar.id)
            }
            layoutParams = lp
        }

        formLayout.addView(TextView(context).apply { text = "Nama Kategori"; setTextColor(Color.parseColor("#718096")); textSize = 12f })
        val etName = EditText(context).apply {
            setText(category?.name ?: "")
            setTextColor(Color.parseColor("#2D3748"))
            textSize = 15f
            hint = "Masukkan nama kategori"
            setHintTextColor(Color.parseColor("#A0AEC0"))
            background.mutate().setColorFilter(Color.parseColor("#CBD5E0"), android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        formLayout.addView(etName)

        formLayout.addView(TextView(context).apply { text = "Kategori Induk (Pilih jika ini sub-kategori)"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (20 * density).toInt(), 0, (4 * density).toInt()) })
        
        val spinnerParent = Spinner(context).apply {
            setBackgroundColor(Color.WHITE)
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
        }
        formLayout.addView(spinnerParent)
        editorContainer.addView(formLayout)

        // Ambil list parent dari DB
        lifecycleScope.launch {
            val allCats = db.categoryDao().getAllCategories().first()
            val parents = allCats.filter { it.parentCategoryId == null && it.type == currentTypeFilter && it.id != category?.id }
            
            val listNames = mutableListOf("[Tanpa Induk / Kategori Utama]")
            parents.forEach { listNames.add(it.name) }
            
            val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerParent.adapter = spinnerAdapter
            
            category?.parentCategoryId?.let { parentId ->
                val matchIdx = parents.indexOfFirst { it.id == parentId }
                if (matchIdx != -1) spinnerParent.setSelection(matchIdx + 1)
            }
        }

        // --- TOMBOL SIMPAN DI UJUNG BAWAH TENGAH ---
        val btnSave = Button(context).apply {
            text = "Simpan"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080")) // Tema utama hijau teal aplikasi kita
            cornerRadius = (22 * density).toInt() // Oval bentuk elips cantik
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins((24 * density).toInt(), 0, (24 * density).toInt(), (24 * density).toInt())
            }
            layoutParams = lp
        }
        editorContainer.addView(btnSave)

        // Eksekusi Tampilan Editor Dialog Full-Screen
        val editorDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(editorContainer)
            .create()

        btnClose.setOnClickListener { editorDialog.dismiss() }

        btnDelete.setOnClickListener {
            if (category != null) {
                lifecycleScope.launch {
                    db.categoryDao().deleteCategory(category)
                    renderHierarchy()
                    editorDialog.dismiss()
                }
            }
        }

        btnSave.setOnClickListener {
            val finalName = etName.text.toString().trim()
            if (finalName.isNotEmpty()) {
                lifecycleScope.launch {
                    val allCats = db.categoryDao().getAllCategories().first()
                    val parents = allCats.filter { it.parentCategoryId == null && it.type == currentTypeFilter && it.id != category?.id }
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
                    editorDialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Nama kategori tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }

        editorDialog.show()
    }
}
