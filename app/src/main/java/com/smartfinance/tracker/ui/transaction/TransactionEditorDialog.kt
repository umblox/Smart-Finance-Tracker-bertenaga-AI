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
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionEditorDialog(
    private val transaction: TransactionEntity,
    private val onUpdateAction: () -> Unit
) : DialogFragment() {

    private lateinit var db: AppDatabase

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)
        val density = context.resources.displayMetrics.density

        val editorContainer = RelativeLayout(context).apply {
            setBackgroundColor(Color.parseColor("#F7FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // --- TOP BAR NAVIGASI ---
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

        // --- LAYOUT FORM ENTRI DATA KONTRAS ---
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
            val lp = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT).apply { addRule(RelativeLayout.BELOW, topBar.id) }
            layoutParams = lp
        }

        formLayout.addView(TextView(context).apply { text = "Nominal Transaksi (Rp)"; setTextColor(Color.parseColor("#718096")); textSize = 12f })
        val etAmount = EditText(context).apply {
            setText(transaction.amount.toLong().toString())
            setTextColor(Color.parseColor("#2D3748")); textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            background.mutate().setColorFilter(Color.parseColor("#CBD5E0"), android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        formLayout.addView(etAmount)

        formLayout.addView(TextView(context).apply { text = "Catatan"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, 0) })
        val etNote = EditText(context).apply {
            setText(transaction.note)
            setTextColor(Color.parseColor("#2D3748")); textSize = 15f
            background.mutate().setColorFilter(Color.parseColor("#CBD5E0"), android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        formLayout.addView(etNote)

        formLayout.addView(TextView(context).apply { text = "Tanggal (YYYY-MM-DD)"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, 0) })
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val etDate = EditText(context).apply {
            setText(sdf.format(Date(transaction.timestamp)))
            setTextColor(Color.parseColor("#2D3748")); textSize = 15f
            background.mutate().setColorFilter(Color.parseColor("#CBD5E0"), android.graphics.PorterDuff.Mode.SRC_ATOP)
        }
        formLayout.addView(etDate)

        formLayout.addView(TextView(context).apply { text = "Kategori Pengikat"; setTextColor(Color.parseColor("#718096")); textSize = 12f; setPadding(0, (16 * density).toInt(), 0, (4 * density).toInt()) })
        val spinnerCategory = Spinner(context).apply { setBackgroundColor(Color.WHITE); setPadding((10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt(), (10 * density).toInt()) }
        formLayout.addView(spinnerCategory)
        editorContainer.addView(formLayout)

        var categoryList = listOf<CategoryEntity>()
        lifecycleScope.launch {
            categoryList = db.categoryDao().getAllCategories().first()
            val listNames = categoryList.map { it.name }
            val spinnerAdapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listNames)
            spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinnerCategory.adapter = spinnerAdapter

            val selectedIdx = categoryList.indexOfFirst { it.id == transaction.categoryId }
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
            layoutParams = lp
        }
        editorContainer.addView(btnSave)

        val editorDialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
            .setView(editorContainer).create()

        btnClose.setOnClickListener { editorDialog.dismiss() }

        btnDelete.setOnClickListener {
            lifecycleScope.launch {
                // SEKARANG BERHASIL: deleteTransaction terdaftar legal di DAO Room
                db.transactionDao().deleteTransaction(transaction)
                onUpdateAction()
                editorDialog.dismiss()
            }
        }

        btnSave.setOnClickListener {
            val amountVal = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()

            if (amountVal > 0.0 && noteVal.isNotEmpty() && dateVal.isNotEmpty()) {
                lifecycleScope.launch {
                    val parsedDate = try { sdf.parse(dateVal)?.time ?: transaction.timestamp } catch (e: Exception) { transaction.timestamp }
                    val selectedCategory = categoryList[spinnerCategory.selectedItemPosition]

                    val updatedTx = transaction.copy(
                        amount = amountVal,
                        note = noteVal,
                        timestamp = parsedDate,
                        categoryId = selectedCategory.id,
                        categoryName = selectedCategory.name,
                        type = selectedCategory.type
                    )
                    db.transactionDao().insertTransaction(updatedTx)
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
