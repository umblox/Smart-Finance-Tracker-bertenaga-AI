package com.smartfinance.tracker.ui.settings

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class RecurringTxListDialog : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val categoriesList = mutableListOf<Map<String, Any>>()
    
    private var startDateCal = Calendar.getInstance()
    private var endDateCal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val mainCard = MaterialCardView(context).apply {
            radius = 16 * density
            setCardBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }
        }

        val scrollLayout = ScrollView(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt())
        }

        // --- TITLE ---
        root.addView(TextView(context).apply {
            text = "⏳ Buat Transaksi Terjadwal"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (16 * density).toInt()
            }
        })

        // --- TYPE (INCOME / EXPENSE) ---
        val typeGroup = RadioGroup(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        val rbExpense = RadioButton(context).apply { text = "Pengeluaran"; isChecked = true; setTextColor(Color.parseColor("#F43F5E")) }
        val rbIncome = RadioButton(context).apply { text = "Pemasukan"; setTextColor(Color.parseColor("#10B981")) }
        typeGroup.addView(rbExpense); typeGroup.addView(rbIncome)
        root.addView(typeGroup)

        // --- CATATAN ---
        val etNote = EditText(context).apply {
            hint = "Catatan (cth: Langganan Netflix)"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
        }
        root.addView(etNote)

        // --- NOMINAL ---
        val etAmount = EditText(context).apply {
            hint = "Nominal (Rp)"
            textSize = 14f
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
        }
        root.addView(etAmount)

        // --- KATEGORI (FETCH DARI FIRESTORE) ---
        root.addView(TextView(context).apply { text = "Kategori:"; textSize = 12f; setTextColor(Color.GRAY) })
        val spinnerCategory = Spinner(context).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        root.addView(spinnerCategory)

        // Load Categories from Firestore
        lifecycleScope.launch {
            try {
                val snap = firestore.collection("categories").get().await()
                for (doc in snap.documents) {
                    doc.data?.let { categoriesList.add(it) }
                }
                val catNames = categoriesList.map { it["name"] as? String ?: "Umum" }
                if(context != null) {
                    spinnerCategory.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, catNames)
                }
            } catch (e: Exception) {}
        }

        // --- KONTAK (OPSIONAL) ---
        val etContact = EditText(context).apply {
            hint = "Kontak (Opsional)"
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
        }
        root.addView(etContact)

        // --- INTERVAL ---
        root.addView(TextView(context).apply { text = "Diulang Setiap:"; textSize = 12f; setTextColor(Color.GRAY) })
        val intervals = listOf("Harian" to "DAILY", "Mingguan" to "WEEKLY", "Bulanan" to "MONTHLY", "Tahunan" to "YEARLY")
        val spinnerInterval = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, intervals.map { it.first })
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        root.addView(spinnerInterval)

        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))

        // --- TANGGAL MULAI ---
        val btnStartDate = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Mulai: ${sdf.format(startDateCal.time)}"
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener {
                DatePickerDialog(context, { _, y, m, d ->
                    startDateCal.set(y, m, d, 0, 0, 0)
                    text = "Mulai: ${sdf.format(startDateCal.time)}"
                }, startDateCal.get(Calendar.YEAR), startDateCal.get(Calendar.MONTH), startDateCal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        root.addView(btnStartDate)

        // --- BATAS WAKTU ---
        val switchEnd = SwitchMaterial(context).apply {
            text = "Ulangi Terus Tanpa Batas"
            isChecked = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() }
        }
        val btnEndDate = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setOnClickListener {
                DatePickerDialog(context, { _, y, m, d ->
                    endDateCal.set(y, m, d, 23, 59, 59)
                    text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
                }, endDateCal.get(Calendar.YEAR), endDateCal.get(Calendar.MONTH), endDateCal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        
        switchEnd.setOnCheckedChangeListener { _, isChecked ->
            btnEndDate.visibility = if (isChecked) View.GONE else View.VISIBLE
        }
        root.addView(switchEnd); root.addView(btnEndDate)

        // --- TOMBOL SIMPAN ---
        val btnSave = MaterialButton(context).apply {
            text = "SIMPAN JADWAL OTOMATIS"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (24 * density).toInt() }
            setOnClickListener {
                val amountText = etAmount.text.toString()
                val noteText = etNote.text.toString()

                if (amountText.isEmpty() || noteText.isEmpty()) {
                    Toast.makeText(context, "Harap isi Catatan dan Nominal!", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val selectedCatIndex = spinnerCategory.selectedItemPosition
                val selectedCatMap = if (categoriesList.isNotEmpty() && selectedCatIndex >= 0) categoriesList[selectedCatIndex] else null
                
                val finalData = hashMapOf(
                    "note" to noteText,
                    "amount" to (amountText.toDoubleOrNull() ?: 0.0),
                    "type" to if (rbExpense.isChecked) "EXPENSE" else "INCOME",
                    "categoryId" to (selectedCatMap?.get("id") as? Long ?: 15L),
                    "categoryName" to (selectedCatMap?.get("name") as? String ?: "Umum"),
                    "contactName" to etContact.text.toString().trim(),
                    "interval" to intervals[spinnerInterval.selectedItemPosition].second,
                    "nextExecutionTime" to startDateCal.timeInMillis,
                    "hasEndDate" to !switchEnd.isChecked,
                    "endDate" to if (!switchEnd.isChecked) endDateCal.timeInMillis else null,
                    "isActive" to true,
                    "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("recurring_transactions").add(finalData)
                    .addOnSuccessListener {
                        Toast.makeText(context, "✅ Jadwal Transaksi Disimpan!", Toast.LENGTH_LONG).show()
                        dismiss()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "❌ Gagal Menyimpan", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        root.addView(btnSave)

        scrollLayout.addView(root)
        mainCard.addView(scrollLayout)
        return mainCard
    }
}
