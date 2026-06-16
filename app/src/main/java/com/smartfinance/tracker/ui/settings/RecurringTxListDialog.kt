package com.smartfinance.tracker.ui.settings

import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class RecurringTxListDialog : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val allCategories = mutableListOf<Map<String, Any>>()
    private val filteredCategories = mutableListOf<Map<String, Any>>()
    
    private var startDateCal = Calendar.getInstance()
    private var endDateCal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }

    // Komponen UI Navigasi
    private lateinit var layoutList: LinearLayout
    private lateinit var layoutForm: ScrollView
    private lateinit var listContainer: LinearLayout
    private lateinit var spinnerCategory: Spinner
    private lateinit var etContact: EditText

    // Launcher untuk Pick Kontak dari HP
    private val pickContactLauncher = registerForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        uri?.let {
            try {
                val cursor = requireContext().contentResolver.query(it, null, null, null, null)
                if (cursor != null && cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        val name = cursor.getString(nameIndex)
                        etContact.setText(name)
                    }
                    cursor.close()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal mengambil kontak", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (600 * density).toInt()).apply {
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }
        }

        val rootContainer = FrameLayout(context)

        // =========================================================
        // 1. TAMPILAN MODE DAFTAR (LIST)
        // =========================================================
        layoutList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
        }

        layoutList.addView(TextView(context).apply {
            text = "⏳ Daftar Transaksi Berkala"
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        })

        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val scrollList = ScrollView(context).apply { 
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            addView(listContainer)
        }
        layoutList.addView(scrollList)

        layoutList.addView(MaterialButton(context).apply {
            text = "+ BUAT TRANSAKSI BERKALA"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (16 * density).toInt() }
            setOnClickListener { 
                layoutList.visibility = View.GONE
                layoutForm.visibility = View.VISIBLE 
            }
        })

        // =========================================================
        // 2. TAMPILAN MODE FORM (INPUT)
        // =========================================================
        layoutForm = ScrollView(context).apply { visibility = View.GONE }
        val formRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt())
        }

        val headerForm = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() }
        }
        headerForm.addView(TextView(context).apply { text = "⬅️ Batal"; textSize = 14f; setTextColor(Color.parseColor("#EF4444")); setTypeface(null, Typeface.BOLD); setOnClickListener { layoutForm.visibility = View.GONE; layoutList.visibility = View.VISIBLE } })
        headerForm.addView(TextView(context).apply { text = "Buat Jadwal Baru"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")); gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
        formRoot.addView(headerForm)

        // --- RADIO BUTTON: 4 TIPE ---
        val typeGroup1 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT) }
        val typeGroup2 = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        
        val rbExpense = RadioButton(context).apply { text = "Pengeluaran"; isChecked = true; setTextColor(Color.parseColor("#F43F5E")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val rbIncome = RadioButton(context).apply { text = "Pemasukan"; setTextColor(Color.parseColor("#10B981")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val rbDebt = RadioButton(context).apply { text = "Utang"; setTextColor(Color.parseColor("#EAB308")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val rbReceivable = RadioButton(context).apply { text = "Piutang"; setTextColor(Color.parseColor("#3B82F6")); layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }

        val typeRadios = listOf(rbExpense, rbIncome, rbDebt, rbReceivable)
        typeRadios.forEach { rb ->
            rb.setOnClickListener {
                typeRadios.forEach { if (it != rb) it.isChecked = false }
                updateCategorySpinner(rb.text.toString()) // Filter kategori tiap diganti
            }
        }

        typeGroup1.addView(rbExpense); typeGroup1.addView(rbIncome)
        typeGroup2.addView(rbDebt); typeGroup2.addView(rbReceivable)
        formRoot.addView(typeGroup1); formRoot.addView(typeGroup2)

        val etNote = EditText(context).apply { hint = "Catatan (cth: Langganan / Cicilan)"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        val etAmount = EditText(context).apply { hint = "Nominal (Rp)"; textSize = 14f; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        formRoot.addView(etNote); formRoot.addView(etAmount)

        formRoot.addView(TextView(context).apply { text = "Kategori:"; textSize = 12f; setTextColor(Color.GRAY) })
        spinnerCategory = Spinner(context).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        formRoot.addView(spinnerCategory)

        // --- KONTAK DENGAN TOMBOL BUKU ALAMAT ---
        val contactLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        etContact = EditText(context).apply { hint = "Kontak Terkait (Bisa Kosong)"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        val btnPickContact = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { 
            text = "📖"; setPadding(0,0,0,0); layoutParams = LinearLayout.LayoutParams((50 * density).toInt(), (50 * density).toInt()).apply { leftMargin = (8 * density).toInt() }
            setOnClickListener { pickContactLauncher.launch(null) }
        }
        contactLayout.addView(etContact); contactLayout.addView(btnPickContact)
        formRoot.addView(contactLayout)

        formRoot.addView(TextView(context).apply { text = "Diulang Setiap:"; textSize = 12f; setTextColor(Color.GRAY) })
        val intervals = listOf("Harian" to "DAILY", "Mingguan" to "WEEKLY", "Bulanan" to "MONTHLY", "Tahunan" to "YEARLY")
        val spinnerInterval = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, intervals.map { it.first }); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() } }
        formRoot.addView(spinnerInterval)

        val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
        val btnStartDate = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Mulai: ${sdf.format(startDateCal.time)}"; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { DatePickerDialog(context, { _, y, m, d -> startDateCal.set(y, m, d, 0, 0, 0); text = "Mulai: ${sdf.format(startDateCal.time)}" }, startDateCal.get(Calendar.YEAR), startDateCal.get(Calendar.MONTH), startDateCal.get(Calendar.DAY_OF_MONTH)).show() } }
        formRoot.addView(btnStartDate)

        val switchEnd = SwitchMaterial(context).apply { text = "Ulangi Terus Tanpa Batas"; isChecked = true; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() } }
        val btnEndDate = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"; visibility = View.GONE; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { DatePickerDialog(context, { _, y, m, d -> endDateCal.set(y, m, d, 23, 59, 59); text = "Berhenti Pada: ${sdf.format(endDateCal.time)}" }, endDateCal.get(Calendar.YEAR), endDateCal.get(Calendar.MONTH), endDateCal.get(Calendar.DAY_OF_MONTH)).show() } }
        switchEnd.setOnCheckedChangeListener { _, isChecked -> btnEndDate.visibility = if (isChecked) View.GONE else View.VISIBLE }
        formRoot.addView(switchEnd); formRoot.addView(btnEndDate)

        val btnSave = MaterialButton(context).apply {
            text = "SIMPAN JADWAL"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (24 * density).toInt() }
            setOnClickListener {
                val amountText = etAmount.text.toString(); val noteText = etNote.text.toString()
                if (amountText.isEmpty() || noteText.isEmpty()) { Toast.makeText(context, "Harap isi Catatan dan Nominal!", Toast.LENGTH_SHORT).show(); return@setOnClickListener }

                val typeCode = when { rbExpense.isChecked -> "EXPENSE"; rbIncome.isChecked -> "INCOME"; rbDebt.isChecked -> "DEBT"; else -> "RECEIVABLE" }
                val selectedCatIndex = spinnerCategory.selectedItemPosition
                val selectedCatMap = if (filteredCategories.isNotEmpty() && selectedCatIndex >= 0) filteredCategories[selectedCatIndex] else null
                
                val finalData = hashMapOf(
                    "note" to noteText, "amount" to (amountText.toDoubleOrNull() ?: 0.0), "type" to typeCode,
                    "categoryId" to (selectedCatMap?.get("id") as? Long ?: 15L), "categoryName" to (selectedCatMap?.get("name") as? String ?: "Umum"),
                    "contactName" to etContact.text.toString().trim(), "interval" to intervals[spinnerInterval.selectedItemPosition].second,
                    "nextExecutionTime" to startDateCal.timeInMillis, "hasEndDate" to !switchEnd.isChecked,
                    "endDate" to if (!switchEnd.isChecked) endDateCal.timeInMillis else null, "isActive" to true, "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("recurring_transactions").add(finalData).addOnSuccessListener {
                    Toast.makeText(context, "✅ Jadwal Disimpan!", Toast.LENGTH_SHORT).show()
                    loadScheduledTransactions() // Refresh List
                    layoutForm.visibility = View.GONE; layoutList.visibility = View.VISIBLE
                    etNote.text.clear(); etAmount.text.clear(); etContact.text.clear()
                }
            }
        }
        formRoot.addView(btnSave)
        layoutForm.addView(formRoot)

        rootContainer.addView(layoutList); rootContainer.addView(layoutForm)
        mainCard.addView(rootContainer)

        // Load Data Awal
        fetchCategoriesFromCloud()
        loadScheduledTransactions()

        return mainCard
    }

    private fun fetchCategoriesFromCloud() {
        lifecycleScope.launch {
            try {
                val snap = firestore.collection("categories").get().await()
                allCategories.clear()
                for (doc in snap.documents) { doc.data?.let { allCategories.add(it) } }
                updateCategorySpinner("Pengeluaran") // Set default
            } catch (e: Exception) {}
        }
    }

    private fun updateCategorySpinner(selectedLabel: String) {
        val targetType = when (selectedLabel) { "Pemasukan" -> "INCOME"; "Utang" -> "DEBT"; "Piutang" -> "RECEIVABLE"; else -> "EXPENSE" }
        filteredCategories.clear()
        filteredCategories.addAll(allCategories.filter { (it["type"] as? String)?.uppercase(Locale.ROOT) == targetType })
        
        val context = context ?: return
        val catNames = if (filteredCategories.isEmpty()) listOf("Umum") else filteredCategories.map { it["name"] as? String ?: "Umum" }
        spinnerCategory.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, catNames)
    }

    private fun loadScheduledTransactions() {
        val context = context ?: return
        val density = context.resources.displayMetrics.density
        listContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = firestore.collection("recurring_transactions").orderBy("createdAt").get().await()
                if (snap.isEmpty) {
                    listContainer.addView(TextView(context).apply { text = "Belum ada transaksi terjadwal. Klik tombol di bawah untuk membuat baru."; setTextColor(Color.GRAY); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER })
                    return@launch
                }
                val formatRp = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
                for (doc in snap.documents) {
                    val note = doc.getString("note") ?: "Jadwal AI"
                    val amount = doc.getDouble("amount") ?: 0.0
                    val interval = doc.getString("interval") ?: "MONTHLY"
                    
                    val card = MaterialCardView(context).apply {
                        radius = 12 * density; cardElevation = 1 * density; setCardBackgroundColor(Color.parseColor("#F8FAFC"))
                        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
                    }
                    val row = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt()) }
                    row.addView(TextView(context).apply { text = note; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 16f })
                    row.addView(TextView(context).apply { text = "${formatRp.format(amount)} • $interval"; setTextColor(Color.parseColor("#0D9488")); textSize = 14f; setPadding(0, 4, 0, 0) })
                    card.addView(row)
                    listContainer.addView(card)
                }
            } catch (e: Exception) { }
        }
    }
}
