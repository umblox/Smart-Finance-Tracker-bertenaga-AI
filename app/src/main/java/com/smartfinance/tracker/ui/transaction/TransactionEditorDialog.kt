package com.smartfinance.tracker.ui.transaction

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TransactionEditorDialog(
    // 🔥 FULL CLOUD: Menerima data transaksi berbasis Map dari Firestore Snapshot dokumen
    private val transactionData: HashMap<String, Any>,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var categoryListCloud = listOf<Map<String, Any>>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val docId = transactionData["id"] as? String ?: ""
        val currentAmount = (transactionData["amount"] as? Number)?.toLong() ?: 0L
        val currentNote = transactionData["note"] as? String ?: ""
        val currentTimestamp = (transactionData["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val currentCategoryId = (transactionData["categoryId"] as? Number)?.toLong() ?: 0L

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
            text = "Ubah Transaksi"; textSize = 16f; setTextColor(Color.parseColor("#1A202C")); setTypeface(null, Typeface.BOLD)
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply { addRule(RelativeLayout.CENTER_IN_PARENT) }
            layoutParams = lp
        }
        topBar.addView(tvTitle)

        val btnDelete = TextView(context).apply {
            text = "HAPUS"; textSize = 13f; setTextColor(Color.parseColor("#E53E3E")); setTypeface(null, Typeface.BOLD)
            setPadding((16 * density).toInt(), (6 * density).toInt(), (4 * density).toInt(), (6 * density).toInt())
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.ALIGN_PARENT_RIGHT)
                addRule(RelativeLayout.CENTER_VERTICAL)
            }
            layoutParams = lp
        }
        topBar.addView(btnDelete)
        editorContainer.addView(topBar)

        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply { addRule(RelativeLayout.BELOW, topBar.id) }
            layoutParams = lp
        }

        formLayout.addView(TextView(context).apply { text = "Nominal Transaksi (Rp)"; setTextColor(Color.parseColor("#718096")); textSize = 12f })
        val etAmount = EditText(context).apply {
            setText(currentAmount.toString())
            setTextColor(Color.parseColor("#2D3748")); textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        }
        formLayout.addView(etAmount)

        formLayout.addView(TextView(context).apply { text = "Catatan"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, 0) })
        val etNote = EditText(context).apply {
            setText(currentNote)
            setTextColor(Color.parseColor("#2D3748")); textSize = 15f
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        }
        formLayout.addView(etNote)

        formLayout.addView(TextView(context).apply { text = "Tanggal (YYYY-MM-DD)"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, 0) })
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val etDate = EditText(context).apply {
            setText(sdf.format(Date(currentTimestamp)))
            setTextColor(Color.parseColor("#2D3748")); textSize = 15f
            background.mutate().colorFilter = androidx.core.graphics.BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
                Color.parseColor("#CBD5E0"), androidx.core.graphics.BlendModeCompat.SRC_ATOP
            )
        }
        formLayout.addView(etDate)

        formLayout.addView(TextView(context).apply { text = "Kategori Pengikat"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, (4 * density).toInt()) })
        val spinnerCategory = Spinner(context).apply { setBackgroundColor(Color.WHITE); setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt()) }
        formLayout.addView(spinnerCategory)
        editorContainer.addView(formLayout)

        // 🔥 FULL CLOUD: Tarik data master kategori langsung dari Firebase Firestore Cloud
        lifecycleScope.launch {
            try {
                val snapshot = firestore.collection("categories").get().await()
                val list = ArrayList<Map<String, Any>>()
                for (doc in snapshot.documents) {
                    val data = doc.data ?: continue
                    val mutableData = HashMap(data)
                    mutableData["id"] = doc.getLong("id") ?: 0L
                    list.add(mutableData)
                }
                categoryListCloud = list
            } catch (e: Exception) {
                categoryListCloud = listOf(
                    mapOf("id" to 15L, "name" to "Lain-lain / Umum", "type" to "EXPENSE")
                )
            }

            val listNames = categoryListCloud.map { it["name"] as String }
            val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = spinnerAdapter

            val selectedIdx = categoryListCloud.indexOfFirst { (it["id"] as Long) == currentCategoryId }
            if (selectedIdx != -1) spinnerCategory.setSelection(selectedIdx)
        }

        val btnSave = Button(context).apply {
            text = "Simpan Perubahan"
            textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 22 * density; setColor(Color.parseColor("#008080")) }
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                setMargins((24 * density).toInt(), 0, (24 * density).toInt(), (24 * density).toInt())
            }
            editorContainer.addView(this)
            layoutParams = lp
        }

        val editorDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(editorContainer).create()

        btnClose.setOnClickListener { editorDialog.dismiss() }

        // 🔥 FULL CLOUD ACTION: Lenyapkan transaksi kas langsung dari server Firestore
        btnDelete.setOnClickListener {
            if (docId.isNotEmpty()) {
                firestore.collection("transactions").document(docId).delete().addOnSuccessListener {
                    Toast.makeText(context, "Berhasil dihapus dari Cloud!", Toast.LENGTH_SHORT).show()
                    onUpdateAction()
                    editorDialog.dismiss()
                }
            }
        }

        // 🔥 FULL CLOUD ACTION: Update payload transaksi kas langsung menuju server Firestore
        btnSave.setOnClickListener {
            val amountVal = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()

            if (amountVal > 0.0 && noteVal.isNotEmpty() && dateVal.isNotEmpty() && categoryListCloud.isNotEmpty() && docId.isNotEmpty()) {
                val parsedDate = try { sdf.parse(dateVal)?.time ?: currentTimestamp } catch (e: Exception) { currentTimestamp }
                val selectedCategory = categoryListCloud[spinnerCategory.selectedItemPosition]
                val catId = selectedCategory["id"] as Long
                val catName = selectedCategory["name"] as String
                val catType = selectedCategory["type"] as String

                val updatedTxMap = hashMapOf(
                    "id" to docId,
                    "amount" to amountVal,
                    "note" to noteVal.uppercase(Locale.ROOT),
                    "timestamp" to parsedDate,
                    "categoryId" to catId,
                    "categoryName" to catName,
                    "type" to catType
                )

                firestore.collection("transactions").document(docId).set(updatedTxMap).addOnSuccessListener {
                    Toast.makeText(context, "Perubahan sukses disimpan di Cloud!", Toast.LENGTH_SHORT).show()
                    onUpdateAction()
                    editorDialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Semua data wajib diisi secara valid!", Toast.LENGTH_SHORT).show()
            }
        }

        return editorDialog
    }
}
