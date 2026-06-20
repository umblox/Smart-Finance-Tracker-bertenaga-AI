package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.google.firebase.firestore.DocumentSnapshot
// 🔥 IMPORT FIREBASE MANAGER YANG BENAR
import com.smartfinance.tracker.utils.FirebaseManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class RecurringTxListDialog : DialogFragment() {

    // 🔥 FIX 1: GUNAKAN JALUR VIP!
    private val firestore = FirebaseManager.getFirestore()
    private val allCategories = mutableListOf<Map<String, Any>>()
    
    private var startDateCal = Calendar.getInstance()
    private var endDateCal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }

    // State
    private var editingDocId: String? = null
    private var selectedCategoryMap: Map<String, Any>? = null

    // UI Komponen
    private lateinit var layoutList: LinearLayout
    private lateinit var layoutForm: ScrollView
    private lateinit var listContainer: LinearLayout
    private lateinit var tvFormTitle: TextView
    private lateinit var etNote: EditText
    private lateinit var etAmount: EditText
    private lateinit var etContact: EditText
    private lateinit var btnSelectCategory: MaterialButton 
    private lateinit var spinnerInterval: Spinner
    private lateinit var switchEnd: SwitchMaterial
    private lateinit var btnStartDate: MaterialButton
    private lateinit var btnEndDate: MaterialButton
    private lateinit var btnDelete: MaterialButton

    private val sdf = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID"))
    private val intervals = listOf("Harian" to "DAILY", "Mingguan" to "WEEKLY", "Bulanan" to "MONTHLY", "Tahunan" to "YEARLY")

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
            radius = 16 * density; setCardBackgroundColor(Color.WHITE)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, (600 * density).toInt()).apply {
                setMargins((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
            }
        }

        val rootContainer = FrameLayout(context)

        // ==========================================
        // 1. TAMPILAN MODE DAFTAR (LIST)
        // ==========================================
        layoutList = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) }
        layoutList.addView(TextView(context).apply { text = "⏳ Transaksi Berkala"; textSize = 20f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() } })

        listContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        layoutList.addView(ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f); addView(listContainer) })

        layoutList.addView(MaterialButton(context).apply {
            text = "+ BUAT JADWAL BARU"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (16 * density).toInt() }
            setOnClickListener { openFormMode(null) } 
        })

        // ==========================================
        // 2. TAMPILAN MODE FORM (INPUT & EDIT)
        // ==========================================
        layoutForm = ScrollView(context).apply { visibility = View.GONE }
        val formRoot = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) }

        val headerForm = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() } }
        headerForm.addView(TextView(context).apply { text = "⬅️ Kembali"; textSize = 14f; setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.BOLD); setOnClickListener { layoutForm.visibility = View.GONE; layoutList.visibility = View.VISIBLE } })
        tvFormTitle = TextView(context).apply { text = "Buat Jadwal Baru"; textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")); gravity = Gravity.END; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        headerForm.addView(tvFormTitle)
        formRoot.addView(headerForm)

        etNote = EditText(context).apply { hint = "Catatan (cth: Langganan WiFi)"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        etAmount = EditText(context).apply { hint = "Nominal (Rp)"; textSize = 14f; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        formRoot.addView(etNote); formRoot.addView(etAmount)

        formRoot.addView(TextView(context).apply { text = "Kategori:"; textSize = 12f; setTextColor(Color.GRAY) })
        
        btnSelectCategory = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Pilih Kategori"
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
            setOnClickListener { showCategoryPickerDialog() }
        }
        formRoot.addView(btnSelectCategory)

        val contactLayout = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
        etContact = EditText(context).apply { hint = "Kontak Terkait (Bisa Kosong)"; textSize = 14f; layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) }
        contactLayout.addView(etContact)
        contactLayout.addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { 
            text = "📖"; setPadding(0,0,0,0); layoutParams = LinearLayout.LayoutParams((50 * density).toInt(), (50 * density).toInt()).apply { leftMargin = (8 * density).toInt() }
            setOnClickListener { pickContactLauncher.launch(null) }
        })
        formRoot.addView(contactLayout)

        formRoot.addView(TextView(context).apply { text = "Diulang Setiap:"; textSize = 12f; setTextColor(Color.GRAY) })
        spinnerInterval = Spinner(context).apply { adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, intervals.map { it.first }); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (16 * density).toInt() } }
        formRoot.addView(spinnerInterval)

        btnStartDate = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Mulai: ${sdf.format(startDateCal.time)}"; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { DatePickerDialog(context, { _, y, m, d -> startDateCal.set(y, m, d, 0, 0, 0); text = "Mulai: ${sdf.format(startDateCal.time)}" }, startDateCal.get(Calendar.YEAR), startDateCal.get(Calendar.MONTH), startDateCal.get(Calendar.DAY_OF_MONTH)).show() } }
        formRoot.addView(btnStartDate)

        // 🔥 FIX 2: DEFAULT OFF! isChecked di-set false, btnEndDate visibility di-set VISIBLE.
        switchEnd = SwitchMaterial(context).apply { text = "Ulangi Terus Tanpa Batas"; isChecked = false; layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() } }
        btnEndDate = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply { text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"; visibility = View.VISIBLE; gravity = Gravity.START or Gravity.CENTER_VERTICAL; setOnClickListener { DatePickerDialog(context, { _, y, m, d -> endDateCal.set(y, m, d, 23, 59, 59); text = "Berhenti Pada: ${sdf.format(endDateCal.time)}" }, endDateCal.get(Calendar.YEAR), endDateCal.get(Calendar.MONTH), endDateCal.get(Calendar.DAY_OF_MONTH)).show() } }
        switchEnd.setOnCheckedChangeListener { _, isChecked -> btnEndDate.visibility = if (isChecked) View.GONE else View.VISIBLE }
        formRoot.addView(switchEnd); formRoot.addView(btnEndDate)

        btnDelete = MaterialButton(context).apply {
            text = "HAPUS JADWAL INI"
            setBackgroundColor(Color.parseColor("#EF4444"))
            setTextColor(Color.WHITE)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (12 * density).toInt() }
            setOnClickListener { deleteCurrentSchedule() }
        }
        formRoot.addView(btnDelete)

        formRoot.addView(MaterialButton(context).apply {
            text = "SIMPAN JADWAL"
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (8 * density).toInt() }
            setOnClickListener { saveSchedule() }
        })

        layoutForm.addView(formRoot)
        rootContainer.addView(layoutList); rootContainer.addView(layoutForm)
        mainCard.addView(rootContainer)

        fetchCategoriesFromCloud()
        loadScheduledTransactions()

        return mainCard
    }

    private fun openFormMode(doc: DocumentSnapshot?) {
        layoutList.visibility = View.GONE
        layoutForm.visibility = View.VISIBLE

        if (doc == null) {
            editingDocId = null
            tvFormTitle.text = "Buat Jadwal Baru"
            btnDelete.visibility = View.GONE
            
            etNote.text.clear()
            etAmount.text.clear()
            etContact.text.clear()
            
            selectedCategoryMap = null
            btnSelectCategory.text = "Pilih Kategori"
            
            startDateCal = Calendar.getInstance()
            endDateCal = Calendar.getInstance().apply { add(Calendar.YEAR, 1) }
            btnStartDate.text = "Mulai: ${sdf.format(startDateCal.time)}"
            btnEndDate.text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
            
            // 🔥 Kunci OFF saat buat jadwal baru
            switchEnd.isChecked = false
            btnEndDate.visibility = View.VISIBLE
            
            spinnerInterval.setSelection(2) // Bulanan
        } else {
            editingDocId = doc.id
            tvFormTitle.text = "Edit Jadwal"
            btnDelete.visibility = View.VISIBLE

            etNote.setText(doc.getString("note") ?: "")
            val amt = doc.getDouble("amount") ?: 0.0
            etAmount.setText(if (amt > 0) amt.toLong().toString() else "")
            etContact.setText(doc.getString("contactName") ?: "")

            val catId = doc.getLong("categoryId")
            selectedCategoryMap = allCategories.find { (it["id"] as? Number)?.toLong() == catId }
            val catName = doc.getString("categoryName") ?: "Pilih Kategori"
            btnSelectCategory.text = catName

            val intervalCode = doc.getString("interval") ?: "MONTHLY"
            val intervalIndex = intervals.indexOfFirst { it.second == intervalCode }
            if (intervalIndex >= 0) spinnerInterval.setSelection(intervalIndex)

            startDateCal.timeInMillis = doc.getLong("nextExecutionTime") ?: System.currentTimeMillis()
            btnStartDate.text = "Mulai: ${sdf.format(startDateCal.time)}"

            val hasEndDate = doc.getBoolean("hasEndDate") ?: true
            switchEnd.isChecked = !hasEndDate
            btnEndDate.visibility = if (switchEnd.isChecked) View.GONE else View.VISIBLE
            
            if (hasEndDate) {
                endDateCal.timeInMillis = doc.getLong("endDate") ?: System.currentTimeMillis()
                btnEndDate.text = "Berhenti Pada: ${sdf.format(endDateCal.time)}"
            }
        }
    }

    private fun showCategoryPickerDialog() {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val dialogLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
        }

        val tabOuterBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42f * density).toInt()).apply {
                setMargins((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (8f * density).toInt())
            }
            background = GradientDrawable().apply { cornerRadius = 12f * density; setColor(Color.parseColor("#E2E8F0")) }
            weightSum = 3f
        }

        val btnTabExpense = MaterialButton(context).apply { text = "Pengeluaran"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabIncome = MaterialButton(context).apply { text = "Pemasukan"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabDebt = MaterialButton(context).apply { text = "Hutang/Piutang"; textSize = 10f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }

        tabOuterBox.addView(btnTabExpense); tabOuterBox.addView(btnTabIncome); tabOuterBox.addView(btnTabDebt)
        dialogLayout.addView(tabOuterBox)

        val scrollView = ScrollView(context)
        val containerList = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        scrollView.addView(containerList)
        dialogLayout.addView(scrollView)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogLayout)
            .create()

        fun renderList(typeFilter: String) {
            containerList.removeAllViews()

            val activeBg = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
            val inactiveBg = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            
            btnTabExpense.apply { backgroundTintList = if(typeFilter == "EXPENSE") activeBg else inactiveBg; setTextColor(if(typeFilter == "EXPENSE") Color.WHITE else Color.parseColor("#64748B")) }
            btnTabIncome.apply { backgroundTintList = if(typeFilter == "INCOME") activeBg else inactiveBg; setTextColor(if(typeFilter == "INCOME") Color.WHITE else Color.parseColor("#64748B")) }
            btnTabDebt.apply { backgroundTintList = if(typeFilter == "DEBT") activeBg else inactiveBg; setTextColor(if(typeFilter == "DEBT") Color.WHITE else Color.parseColor("#64748B")) }

            val targetTypes = if (typeFilter == "DEBT") listOf("DEBT", "RECEIVABLE") else listOf(typeFilter)
            val filteredList = allCategories.filter { targetTypes.contains((it["type"] as? String)?.uppercase(Locale.ROOT)) }
            
            val parentCategories = filteredList.filter { it["parentCategoryId"] == null }.sortedBy { it["name"] as? String ?: "" }
            val subCategories = filteredList.filter { it["parentCategoryId"] != null }

            if (parentCategories.isEmpty()) {
                containerList.addView(TextView(context).apply { text = "Belum ada kategori terdaftar."; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(0, 40, 0, 40) })
                return
            }

            parentCategories.forEach { parent ->
                val blockCard = MaterialCardView(context).apply {
                    radius = 14f * density; cardElevation = 1f * density; strokeWidth = 0
                    setCardBackgroundColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
                }
                val cardContentContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

                val parentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                    setOnClickListener {
                        selectedCategoryMap = parent
                        btnSelectCategory.text = parent["name"] as? String ?: ""
                        dialog.dismiss() 
                    }
                }
                parentRow.addView(TextView(context).apply { text = "📁"; textSize = 16f; setPadding(0, 0, (12 * density).toInt(), 0) })
                parentRow.addView(TextView(context).apply {
                    text = parent["name"] as? String ?: ""
                    setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f; setTypeface(null, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                })
                cardContentContainer.addView(parentRow)

                val parentId = (parent["id"] as? Number)?.toLong() ?: 0L
                val kids = subCategories.filter { (it["parentCategoryId"] as? Number)?.toLong() == parentId }.sortedBy { it["name"] as? String ?: "" }

                if (kids.isNotEmpty()) {
                    cardContentContainer.addView(View(context).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })
                }

                kids.forEach { child ->
                    val childRow = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt())
                        setBackgroundColor(Color.parseColor("#FAFAFA"))
                        setOnClickListener {
                            selectedCategoryMap = child
                            btnSelectCategory.text = child["name"] as? String ?: ""
                            dialog.dismiss() 
                        }
                    }
                    val treeLine = View(context).apply {
                        setBackgroundColor(Color.parseColor("#CBD5E0"))
                        layoutParams = LinearLayout.LayoutParams((1.5f * density).toInt(), (16 * density).toInt()).apply { rightMargin = (12 * density).toInt(); leftMargin = (6 * density).toInt() }
                    }
                    childRow.addView(treeLine)
                    childRow.addView(TextView(context).apply { text = "💰"; textSize = 13f; setPadding(0, 0, (10 * density).toInt(), 0) })
                    childRow.addView(TextView(context).apply {
                        text = child["name"] as? String ?: ""
                        setTextColor(Color.parseColor("#475569")); textSize = 13.5f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    cardContentContainer.addView(childRow)
                }
                blockCard.addView(cardContentContainer)
                containerList.addView(blockCard)
            }
        }

        btnTabExpense.setOnClickListener { renderList("EXPENSE") }
        btnTabIncome.setOnClickListener { renderList("INCOME") }
        btnTabDebt.setOnClickListener { renderList("DEBT") }

        val currentType = (selectedCategoryMap?.get("type") as? String)?.uppercase(Locale.ROOT) ?: "EXPENSE"
        val initialTab = if (currentType == "RECEIVABLE") "DEBT" else currentType
        renderList(initialTab)

        dialog.show()
    }

    private fun saveSchedule() {
        val amountText = etAmount.text.toString()
        val noteText = etNote.text.toString()
        
        if (selectedCategoryMap == null) { Toast.makeText(context, "Harap pilih Kategori terlebih dahulu!", Toast.LENGTH_SHORT).show(); return }
        if (amountText.isEmpty() || noteText.isEmpty()) { Toast.makeText(context, "Harap isi Catatan dan Nominal!", Toast.LENGTH_SHORT).show(); return }

        val typeCode = (selectedCategoryMap!!["type"] as? String)?.uppercase(Locale.ROOT) ?: "EXPENSE"
        val catId = (selectedCategoryMap!!["id"] as? Number)?.toLong() ?: 15L
        
        // Memastikan tipe datanya 100% kompatibel dengan RecurringTxWorker
        val finalData = hashMapOf(
            "note" to noteText, 
            "amount" to (amountText.toDoubleOrNull() ?: 0.0), 
            "type" to typeCode,
            "categoryId" to catId, 
            "categoryName" to (selectedCategoryMap!!["name"] as? String ?: "Umum"),
            "contactName" to etContact.text.toString().trim(), 
            "interval" to intervals[spinnerInterval.selectedItemPosition].second,
            "nextExecutionTime" to startDateCal.timeInMillis, 
            "hasEndDate" to !switchEnd.isChecked,
            "endDate" to if (!switchEnd.isChecked) endDateCal.timeInMillis else null, 
            "isActive" to true
        )

        val task = if (editingDocId == null) {
            finalData["createdAt"] = System.currentTimeMillis()
            firestore.collection("recurring_transactions").add(finalData)
        } else {
            firestore.collection("recurring_transactions").document(editingDocId!!).update(finalData as Map<String, Any>)
        }

        task.addOnSuccessListener {
            Toast.makeText(context, "✅ Jadwal Tersimpan!", Toast.LENGTH_SHORT).show()
            loadScheduledTransactions()
            layoutForm.visibility = View.GONE; layoutList.visibility = View.VISIBLE
        }.addOnFailureListener {
            Toast.makeText(context, "❌ Gagal Menyimpan", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteCurrentSchedule() {
        val docId = editingDocId ?: return
        AlertDialog.Builder(requireContext())
            .setTitle("Hapus Jadwal")
            .setMessage("Anda yakin ingin menghentikan & menghapus jadwal transaksi otomatis ini?")
            .setPositiveButton("Hapus") { _, _ ->
                firestore.collection("recurring_transactions").document(docId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "🗑️ Jadwal Dihapus!", Toast.LENGTH_SHORT).show()
                        loadScheduledTransactions()
                        layoutForm.visibility = View.GONE; layoutList.visibility = View.VISIBLE
                    }
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun fetchCategoriesFromCloud() {
        lifecycleScope.launch {
            try {
                val snap = firestore.collection("categories").get().await()
                allCategories.clear()
                for (doc in snap.documents) { doc.data?.let { allCategories.add(it) } }
                
                if (editingDocId != null && selectedCategoryMap == null) {
                    val currentText = btnSelectCategory.text.toString()
                    if (currentText != "Pilih Kategori") {
                         selectedCategoryMap = allCategories.find { it["name"] == currentText }
                    }
                }
            } catch (e: Exception) {}
        }
    }

    private fun loadScheduledTransactions() {
        val context = context ?: return
        val density = context.resources.displayMetrics.density
        listContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val snap = firestore.collection("recurring_transactions").orderBy("createdAt").get().await()
                if (snap.isEmpty) {
                    listContainer.addView(TextView(context).apply { text = "Belum ada transaksi terjadwal.\nKlik tombol di bawah untuk membuat baru."; setTextColor(Color.GRAY); textSize = 14f; textAlignment = View.TEXT_ALIGNMENT_CENTER; setPadding(0, 40, 0, 40) })
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
                        setOnClickListener { openFormMode(doc) }
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
