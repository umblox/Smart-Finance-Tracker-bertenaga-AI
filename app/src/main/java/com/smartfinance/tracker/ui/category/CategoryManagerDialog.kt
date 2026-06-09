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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class CategoryManagerDialog : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var categoryListenerRegistration: ListenerRegistration? = null
    
    private lateinit var containerList: LinearLayout
    private var currentTypeFilter = "EXPENSE"
    
    // 🔥 FULL CLOUD: Menampung data kategori master berbasis struktur data Map dari Firestore Cloud
    private var allCategoriesCloud = listOf<Map<String, Any>>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // TABS ATAS FILTER: PENGELUARAN / PEMASUKAN / HUTANG/PINJAMAN
        val tabLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (48 * density).toInt())
            weightSum = 3f
            setBackgroundColor(Color.WHITE)
        }

        val btnExpense = TextView(context).apply {
            text = "PENGELUARAN"; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#008080")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        val btnIncome = TextView(context).apply {
            text = "PEMASUKAN"; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#718096")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }
        val btnDebt = TextView(context).apply {
            text = "HUTANG/PINJAMAN"; gravity = Gravity.CENTER; textSize = 11f; setTypeface(null, Typeface.NORMAL)
            setTextColor(Color.parseColor("#718096")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
        }

        btnExpense.setOnClickListener {
            currentTypeFilter = "EXPENSE"
            btnExpense.setTypeface(null, Typeface.BOLD); btnExpense.setTextColor(Color.parseColor("#008080"))
            btnIncome.setTypeface(null, Typeface.NORMAL); btnIncome.setTextColor(Color.parseColor("#718096"))
            btnDebt.setTypeface(null, Typeface.NORMAL); btnDebt.setTextColor(Color.parseColor("#718096"))
            renderHierarchyCloud()
        }

        btnIncome.setOnClickListener {
            currentTypeFilter = "INCOME"
            btnIncome.setTypeface(null, Typeface.BOLD); btnIncome.setTextColor(Color.parseColor("#008080"))
            btnExpense.setTypeface(null, Typeface.NORMAL); btnExpense.setTextColor(Color.parseColor("#718096"))
            btnDebt.setTypeface(null, Typeface.NORMAL); btnDebt.setTextColor(Color.parseColor("#718096"))
            renderHierarchyCloud()
        }

        btnDebt.setOnClickListener {
            currentTypeFilter = "DEBT"
            btnDebt.setTypeface(null, Typeface.BOLD); btnDebt.setTextColor(Color.parseColor("#008080"))
            btnExpense.setTypeface(null, Typeface.NORMAL); btnExpense.setTextColor(Color.parseColor("#718096"))
            btnIncome.setTypeface(null, Typeface.NORMAL); btnIncome.setTextColor(Color.parseColor("#718096"))
            renderHierarchyCloud()
        }

        tabLayout.addView(btnExpense)
        tabLayout.addView(btnIncome)
        tabLayout.addView(btnDebt)
        mainLayout.addView(tabLayout)

        val btnAdd = Button(context).apply {
            text = "＋ KATEGORI BARU"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (8 * density).toInt())
            }
            setOnClickListener { showFullScreenEditorCloud(null) }
        }
        mainLayout.addView(btnAdd)

        val scrollView = ScrollView(context).apply { isFillViewport = true }
        containerList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (16 * density).toInt())
        }
        scrollView.addView(containerList)
        mainLayout.addView(scrollView)

        // 🔥 REAL-TIME ATTACHMENT: Mengikat SnapshotListener secara reaktif ke koleksi Firebase
        observeCategoriesCloudLive()

        return AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(mainLayout)
            .create()
    }

    private fun observeCategoriesCloudLive() {
        if (!isAdded) return
        
        categoryListenerRegistration?.remove()
        categoryListenerRegistration = firestore.collection("categories")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                val list = ArrayList<Map<String, Any>>()
                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val mutableData = HashMap(data)
                    mutableData["docId"] = doc.id
                    mutableData["id"] = doc.getLong("id") ?: 0L
                    list.add(mutableData)
                }
                allCategoriesCloud = list
                renderHierarchyCloud()
            }
    }

    private fun renderHierarchyCloud() {
        containerList.removeAllViews()
        val context = context ?: return
        val density = context.resources.displayMetrics.density

        // Filter data Map berdasarkan jenis aliran kas aktif di Tab Layout
        val filtered = allCategoriesCloud.filter { (it["type"] as? String) == currentTypeFilter }

        val parentCategories = filtered.filter { it["parentCategoryId"] == null }
        val subCategories = filtered.filter { it["parentCategoryId"] != null }

        parentCategories.forEach { parent ->
            val parentRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, (14 * density).toInt(), 0, (14 * density).toInt())
                setOnClickListener { showFullScreenEditorCloud(parent) }
            }

            val iconView = TextView(context).apply { text = "📁"; textSize = 18f; setPadding(0, 0, (12 * density).toInt(), 0) }
            val titleView = TextView(context).apply {
                text = parent["name"] as? String ?: ""; setTextColor(Color.parseColor("#2D3748")); textSize = 15f; setTypeface(null, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            parentRow.addView(iconView)
            parentRow.addView(titleView)

            val isLocked = parent["isLocked"] as? Boolean ?: false
            if (isLocked) {
                val lockView = TextView(context).apply { text = "🔒"; textSize = 14f; setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0) }
                parentRow.addView(lockView)
            }
            containerList.addView(parentRow)

            val parentId = parent["id"] as Long
            val kids = subCategories.filter { (it["parentCategoryId"] as? Number)?.toLong() == parentId }
            kids.forEach { child ->
                val childRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * density).toInt(), (10 * density).toInt(), 0, (10 * density).toInt())
                    setOnClickListener { showFullScreenEditorCloud(child) }
                }

                val treeLine = View(context).apply {
                    setBackgroundColor(Color.parseColor("#CBD5E0"))
                    layoutParams = LinearLayout.LayoutParams((2 * density).toInt(), (24 * density).toInt()).apply { rightMargin = (16 * density).toInt() }
                }
                childRow.addView(treeLine)

                val childIcon = TextView(context).apply { text = "💰"; textSize = 14f; setPadding(0, 0, (10 * density).toInt(), 0) }
                val childTitle = TextView(context).apply {
                    text = child["name"] as? String ?: ""; setTextColor(Color.parseColor("#4A5568")); textSize = 14f
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                childRow.addView(childIcon)
                childRow.addView(childTitle)

                val isChildLocked = child["isLocked"] as? Boolean ?: false
                if (isChildLocked) {
                    val childLock = TextView(context).apply { text = "🔒"; textSize = 12f; setPadding((8 * density).toInt(), 0, (4 * density).toInt(), 0) }
                    childRow.addView(childLock)
                }
                containerList.addView(childRow)
            }

            containerList.addView(View(context).apply {
                setBackgroundColor(Color.parseColor("#E2E8F0"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (4 * density).toInt() }
            })
        }
    }

    private fun showFullScreenEditorCloud(category: Map<String, Any>?) {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val docId = category?.get("docId") as? String ?: ""
        val currentId = category?.get("id") as? Long
        val currentName = category?.get("name") as? String ?: ""
        val isLocked = category?.get("isLocked") as? Boolean ?: false
        val currentParentId = (category?.get("parentCategoryId") as? Number)?.toLong()

        val editorContainer = RelativeLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val topBar = RelativeLayout(context).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnClose = TextView(context).apply {
            text = "✕"; textSize = 20f; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD)
            setPadding((4 * density).toInt(), (4 * density).toInt(), (16 * density).toInt(), (4 * density).toInt())
        }
        topBar.addView(btnClose)

        val tvTitle = TextView(context).apply {
            text = if (category == null) "Tambah kategori" else "Ubah kategori"
            textSize = 16f; setTextColor(Color.parseColor("#1A202C")); setTypeface(null, Typeface.BOLD)
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        }
        topBar.addView(tvTitle)

        val btnDelete = TextView(context).apply {
            text = "HAPUS"; textSize = 13f; setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD)
            setPadding((16 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
            
            visibility = if (category != null && !isLocked) View.VISIBLE else View.GONE
            
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        topBar.addView(btnDelete)
        editorContainer.addView(topBar)

        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
                addRule(RelativeLayout.BELOW, topBar.id)
            }
        }

        formLayout.addView(TextView(context).apply { text = "Nama Kategori"; setTextColor(Color.parseColor("#718096")); textSize = 12f })
        val etName = EditText(context).apply {
            setText(currentName)
            setTextColor(Color.parseColor("#2D3748"))
            textSize = 15f
            hint = "Masukkan nama kategori"
            setHintTextColor(Color.parseColor("#A0AEC0"))
            
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), 
                androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
            
            if (category != null && isLocked) {
                isEnabled = false
                setTextColor(Color.GRAY)
            }
        }
        formLayout.addView(etName)

        formLayout.addView(TextView(context).apply { text = "Kategori Induk (Pilih jika ini sub-kategori)"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (20 * density).toInt(), 0, (4 * density).toInt()) })
        
        val spinnerParent = Spinner(context).apply {
            setBackgroundColor(Color.WHITE)
            setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt())
            if (category != null && isLocked) isEnabled = false
        }
        formLayout.addView(spinnerParent)
        editorContainer.addView(formLayout)

        // Menyusun dropdown spinner parent kustom dari cache lokal map Cloud
        val parents = allCategoriesCloud.filter { it["parentCategoryId"] == null && (it["type"] as? String) == currentTypeFilter && (it["id"] as Long) != currentId }
        val listNames = mutableListOf("[Tanpa Induk / Kategori Utama]")
        parents.forEach { listNames.add(it["name"] as String) }
        
        val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listNames)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerParent.adapter = spinnerAdapter
        
        currentParentId?.let { pId ->
            val matchIdx = parents.indexOfFirst { (it["id"] as Long) == pId }
            if (matchIdx != -1) spinnerParent.setSelection(matchIdx + 1)
        }

        val btnSave = Button(context).apply {
            text = "Simpan"
            textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 22 * density
                setColor(Color.parseColor("#008080"))
            }
            
            visibility = if (category != null && isLocked) View.GONE else View.VISIBLE
            
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins((24 * density).toInt(), 0, (24 * density).toInt(), (24 * density).toInt())
            }
        }
        editorContainer.addView(btnSave)

        val editorDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(editorContainer)
            .create()

        btnClose.setOnClickListener { editorDialog.dismiss() }

        // 🔥 FULL CLOUD ACTION: Lenyapkan berkas kategori dari Firestore Server
        btnDelete.setOnClickListener {
            if (category != null && !isLocked && docId.isNotEmpty()) {
                firestore.collection("categories").document(docId).delete().addOnSuccessListener {
                    Toast.makeText(context, "Kategori berhasil dihapus dari Cloud!", Toast.LENGTH_SHORT).show()
                    editorDialog.dismiss()
                }
            }
        }

        // 🔥 FULL CLOUD ACTION: Buat dokumen baru atau perbarui dokumen di server Firestore
        btnSave.setOnClickListener {
            val finalName = etName.text.toString().trim()
            if (finalName.isNotEmpty()) {
                val selectedPos = spinnerParent.selectedItemPosition
                val finalParentId = if (selectedPos == 0) null else parents[selectedPos - 1]["id"] as Long

                val targetDocId = if (docId.isEmpty()) "cat_${System.currentTimeMillis()}" else docId
                val targetNumericId = currentId ?: System.currentTimeMillis()

                val categoryMap = hashMapOf(
                    "id" to targetNumericId,
                    "name" to finalName,
                    "type" to currentTypeFilter,
                    "iconName" to (category?.get("iconName") as? String ?: "ic_custom"),
                    "parentCategoryId" to finalParentId,
                    "isLocked" to false
                )

                firestore.collection("categories").document(targetDocId).set(categoryMap).addOnSuccessListener {
                    Toast.makeText(context, "Kategori sukses disimpan ke Cloud!", Toast.LENGTH_SHORT).show()
                    editorDialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Nama kategori tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }

        editorDialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        categoryListenerRegistration?.remove()
    }
}
