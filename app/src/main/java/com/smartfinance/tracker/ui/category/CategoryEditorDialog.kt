package com.smartfinance.tracker.ui.category

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.HashMap
import java.util.Locale
import com.smartfinance.tracker.utils.FirebaseManager

class CategoryEditorDialog(
    private val categoryData: HashMap<String, Any>?,
    private val activeTypeFilter: String,
    private val onSavedAction: () -> Unit
) : DialogFragment() {

    private val firestore = FirebaseManager.getFirestore()
    private var allCategoriesCache = ArrayList<Map<String, Any>>()
    private var availableParents = ArrayList<Map<String, Any>>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val docId = categoryData?.get("docId") as? String ?: ""
        val currentNumericId = categoryData?.get("id") as? Long
        val currentName = categoryData?.get("name") as? String ?: ""
        val isLocked = categoryData?.get("isLocked") as? Boolean ?: false
        val currentParentId = (categoryData?.get("parentCategoryId") as? Number)?.toLong()

        val editorContainer = RelativeLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // TOP BAR DESIGN PREMIUM
        val topBar = RelativeLayout(context).apply {
            id = View.generateViewId()
            setBackgroundColor(Color.WHITE)
            setPadding((16f * density).toInt(), (12f * density).toInt(), (16f * density).toInt(), (12f * density).toInt())
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.WRAP_CONTENT)
        }

        val btnClose = TextView(context).apply {
            text = "✕"; textSize = 20f; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD)
            setPadding((4f * density).toInt(), (4f * density).toInt(), (16f * density).toInt(), (4f * density).toInt())
            setOnClickListener { dismiss() }
        }
        topBar.addView(btnClose)

        val tvTitle = TextView(context).apply {
            text = if (categoryData == null) "Tambah Kategori Baru" else "Ubah Detail Kategori"
            textSize = 15.5f; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD)
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.CENTER_IN_PARENT)
            }
        }
        topBar.addView(tvTitle)

        val btnDelete = TextView(context).apply {
            text = "HAPUS"; textSize = 13f; setTextColor(Color.parseColor("#EF4444")); setTypeface(null, Typeface.BOLD)
            setPadding((16f * density).toInt(), (6f * density).toInt(), (4f * density).toInt(), (6f * density).toInt())
            visibility = if (categoryData != null && !isLocked) View.VISIBLE else View.GONE
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
        }
        topBar.addView(btnDelete)
        editorContainer.addView(topBar)

        // FORM FILL LAYOUT
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20f * density).toInt(), (20f * density).toInt(), (20f * density).toInt(), (20f * density).toInt())
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply {
                addRule(RelativeLayout.BELOW, topBar.id)
            }
        }

        formLayout.addView(TextView(context).apply { text = "Nama Kategori Resmi"; setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setTypeface(null, Typeface.BOLD) })
        val etName = EditText(context).apply {
            setText(currentName)
            setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f
            hint = "Masukkan nama kategori cloud"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, (10f * density).toInt(), 0, (10f * density).toInt())
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
            if (categoryData != null && isLocked) {
                isEnabled = false; setTextColor(Color.GRAY)
            }
        }
        formLayout.addView(etName)

        formLayout.addView(TextView(context).apply { text = "Kelompok Rumpun Induk (Kosongkan jika ini kategori utama)"; setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setTypeface(null, Typeface.BOLD); setPadding(0, (20f * density).toInt(), 0, (6f * density).toInt()) })
        
        val spinnerParent = Spinner(context).apply {
            setPadding((10f * density).toInt(), (10f * density).toInt(), (10f * density).toInt(), (10f * density).toInt())
            if (categoryData != null && isLocked) isEnabled = false
        }
        formLayout.addView(spinnerParent)
        editorContainer.addView(formLayout)

        val btnSave = MaterialButton(context).apply {
            text = "SIMPAN KE SERVER CLOUD"
            textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            cornerRadius = (22f * density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            visibility = if (categoryData != null && isLocked) View.GONE else View.VISIBLE
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, (46f * density).toInt()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins((24f * density).toInt(), 0, (24f * density).toInt(), (24f * density).toInt())
            }
        }
        editorContainer.addView(btnSave)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(editorContainer)
            .create()

        lifecycleScope.launch {
            try {
                val snapshot = firestore.collection("categories").get().await()
                allCategoriesCache.clear()
                availableParents.clear()

                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    allCategoriesCache.add(data)
                }

                // Filter rumpun induk yang sah dari server
                val typedParents = allCategoriesCache.filter { 
                    it["parentCategoryId"] == null && 
                    (it["type"] as? String) == activeTypeFilter && 
                    (it["id"] as? Long) != currentNumericId 
                }
                availableParents.addAll(typedParents)

                val listNames = mutableListOf("[Tanpa Induk / Kategori Utama]")
                availableParents.forEach { listNames.add(it["name"] as String) }

                val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listNames)
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerParent.adapter = spinnerAdapter

                currentParentId?.let { pId ->
                    val matchIdx = availableParents.indexOfFirst { (it["id"] as? Number)?.toLong() == pId }
                    if (matchIdx != -1) spinnerParent.setSelection(matchIdx + 1)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal sinkronisasi data dropdown induk", Toast.LENGTH_SHORT).show()
            }
        }

        btnDelete.setOnClickListener {
            if (categoryData != null && !isLocked && docId.isNotEmpty()) {
                lifecycleScope.launch {
                    firestore.collection("categories").document(docId).delete().await()
                    Toast.makeText(context, "Kategori sukses dilenyapkan!", Toast.LENGTH_SHORT).show()
                    onSavedAction()
                    dialog.dismiss()
                }
            }
        }

        btnSave.setOnClickListener {
            val finalName = etName.text.toString().trim()
            if (finalName.isNotEmpty()) {
                val selectedPos = spinnerParent.selectedItemPosition
                val finalParentId = if (selectedPos == 0 || availableParents.isEmpty()) null else availableParents[selectedPos - 1]["id"] as Long

                val targetDocId = if (docId.isEmpty()) "cat_${System.currentTimeMillis()}" else docId
                val targetNumericId = currentNumericId ?: System.currentTimeMillis()

                val categoryMap = hashMapOf(
                    "id" to targetNumericId,
                    "name" to finalName,
                    "type" to activeTypeFilter,
                    "iconName" to (categoryData?.get("iconName") as? String ?: "ic_custom"),
                    "parentCategoryId" to finalParentId,
                    "isLocked" to false
                )

                lifecycleScope.launch {
                    firestore.collection("categories").document(targetDocId).set(categoryMap).await()
                    Toast.makeText(context, "Kategori sukses disimpan ke Cloud!", Toast.LENGTH_SHORT).show()
                    onSavedAction()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Nama kategori tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }
}

