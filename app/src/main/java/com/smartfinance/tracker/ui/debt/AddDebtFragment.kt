package com.smartfinance.tracker.ui.debt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.ContactsContract
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.util.Locale

class AddDebtFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private var currentTab = "DEBT"
    private var activeContactEditText: EditText? = null
    private var debtListenerRegistration: ListenerRegistration? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            
            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val contactName = cursor.getString(nameIndex)
                        activeContactEditText?.setText(contactName)
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchNativeContactPicker()
        } else {
            Toast.makeText(context, "Izin kontak ditolak. Anda tetap bisa mengetik manual.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. HEADER VISUAL MODERN
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(context).apply {
            text = "🤝 Catatan Pinjaman"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val btnAddManual = MaterialButton(context).apply {
            text = "➕ PINJAMAN"
            textSize = 12f
            cornerRadius = (10 * density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            setTextColor(Color.WHITE)
            insetTop = 0
            insetBottom = 0
        }
        headerLayout.addView(tvTitle)
        headerLayout.addView(btnAddManual)
        root.addView(headerLayout)

        // 2. SUMMARY CARDS
        val summaryLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (16 * density).toInt())
            weightSum = 2f
        }
        val cardDebt = createPremiumSummaryCard(context, "Hutang Saya", "#DFA526")
        val cardReceivable = createPremiumSummaryCard(context, "Piutang (Di Orang)", "#0284C7")
        
        summaryLayout.addView(cardDebt)
        summaryLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), 1) })
        summaryLayout.addView(cardReceivable)
        root.addView(summaryLayout)

        // 3. TABS CONTROL
        val tabOuterContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                leftMargin = (16 * density).toInt()
                rightMargin = (16 * density).toInt()
                bottomMargin = (16 * density).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(Color.parseColor("#E2E8F0"))
            }
        }

        val btnTabDebt = MaterialButton(context).apply {
            text = "Hutang Saya"
            textSize = 13f
            cornerRadius = (10 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            insetTop = 0; insetBottom = 0
        }
        val btnTabReceivable = MaterialButton(context).apply {
            text = "Piutang / Tagihan"
            textSize = 13f
            cornerRadius = (10 * density).toInt()
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            insetTop = 0; insetBottom = 0
        }
        tabOuterContainer.addView(btnTabDebt)
        tabOuterContainer.addView(btnTabReceivable)
        root.addView(tabOuterContainer)

        // 4. DATA SCROLL CONTAINER
        val scrollView = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (24 * density).toInt())
        }
        scrollView.addView(listContainer)
        root.addView(scrollView)

        btnAddManual.setOnClickListener {
            showAddDebtManualDialog(listContainer, cardDebt, cardReceivable)
        }

        setPremiumTabStyles(context, btnTabDebt, btnTabReceivable)

        btnTabDebt.setOnClickListener {
            currentTab = "DEBT"
            setPremiumTabStyles(context, btnTabDebt, btnTabReceivable)
            refreshDebtListLive(listContainer, cardDebt, cardReceivable)
        }

        btnTabReceivable.setOnClickListener {
            currentTab = "RECEIVABLE"
            setPremiumTabStyles(context, btnTabReceivable, btnTabDebt)
            refreshDebtListLive(listContainer, cardDebt, cardReceivable)
        }

        refreshDebtListLive(listContainer, cardDebt, cardReceivable)
        return root
    }

    private fun createPremiumSummaryCard(ctx: Context, title: String, valueColorHex: String): MaterialCardView {
        val density = ctx.resources.displayMetrics.density
        return MaterialCardView(ctx).apply {
            radius = 14 * density
            cardElevation = 2 * density
            strokeWidth = 1
            setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            val layout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            }
            val tvTitle = TextView(ctx).apply { text = title; setTextColor(Color.parseColor("#64748B")); textSize = 12f }
            val tvValue = TextView(ctx).apply { text = "Rp 0"; setTextColor(Color.parseColor(valueColorHex)); textSize = 16f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) }
            
            layout.addView(tvTitle)
            layout.addView(tvValue)
            addView(layout)
        }
    }

    private fun setPremiumTabStyles(ctx: Context, active: MaterialButton, inactive: MaterialButton) {
        active.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
        active.setTextColor(Color.WHITE)
        active.setTypeface(null, Typeface.BOLD)
        
        inactive.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        inactive.setTextColor(Color.parseColor("#64748B"))
        inactive.setTypeface(null, Typeface.NORMAL)
    }

    private fun checkContactPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            launchNativeContactPicker()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }

    private fun launchNativeContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun showAddDebtManualDialog(listContainer: LinearLayout, cardDebt: MaterialCardView, cardReceivable: MaterialCardView) {
        val context = requireContext()
        val density = context.resources.displayMetrics.density
        
        val formLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (16 * density).toInt(), (20 * density).toInt(), (10 * density).toInt())
        }

        val rowContact = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // 🔥 FIX MUTLAK: Menggunakan style constructor XML asli bawaan untuk mengunci rupa Outlined Box premium
        val tilName = TextInputLayout(context, null, com.google.android.material.R.attr.textInputLayoutOutlinedStyle).apply {
            hint = "Nama Kontak Orang"
            setBoxStrokeColor(Color.parseColor("#0D9488"))
            val r = 12 * density
            setBoxCornerRadii(r, r, r, r)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (10 * density).toInt() }
        }
        val etName = TextInputEditText(context).apply { setTextColor(Color.parseColor("#1E293B")) }
        tilName.addView(etName)
        activeContactEditText = etName

        val btnPickContact = MaterialButton(context).apply {
            text = "👥 HUBUNG"
            textSize = 11f
            cornerRadius = (10 * density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#475569"))
            setTextColor(Color.WHITE)
            setOnClickListener { checkContactPermissionAndLaunch() }
            insetTop = 0; insetBottom = 0
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (50 * density).toInt())
        }
        rowContact.addView(tilName)
        rowContact.addView(btnPickContact)
        formLayout.addView(rowContact)

        // 🔥 FIX MUTLAK: Menggunakan Outlined Style bawaan Google yang sah
        val tilAmount = TextInputLayout(context, null, com.google.android.material.R.attr.textInputLayoutOutlinedStyle).apply {
            hint = "Nominal Transaksi (Rp)"
            setBoxStrokeColor(Color.parseColor("#0D9488"))
            val r = 12 * density
            setBoxCornerRadii(r, r, r, r)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (14 * density).toInt() }
        }
        val etAmount = TextInputEditText(context).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(Color.parseColor("#1E293B")) }
        tilAmount.addView(etAmount)
        formLayout.addView(tilAmount)

        formLayout.addView(TextView(context).apply { 
            text = "Jenis Pencatatan Pinjaman:"
            textSize = 12f
            setTextColor(Color.parseColor("#64748B"))
            setTypeface(null, Typeface.BOLD)
            setPadding(0, (16 * density).toInt(), 0, (6 * density).toInt()) 
        })
        
        val rgType = RadioGroup(context).apply {
            orientation = RadioGroup.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * density).toInt() }
        }
        val rbDebt = RadioButton(context).apply { text = "Saya Berhutang"; id = View.generateViewId(); textSize = 13.5f; setTextColor(Color.parseColor("#1E293B")); isChecked = true }
        val rbReceivable = RadioButton(context).apply { text = "Orang Lain Berhutang"; id = View.generateViewId(); textSize = 13.5f; setTextColor(Color.parseColor("#1E293B")); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = (16 * density).toInt() } }
        rgType.addView(rbDebt)
        rgType.addView(rbReceivable)
        formLayout.addView(rgType)

        AlertDialog.Builder(context).apply {
            setTitle("📝 Tambah Catatan Baru")
            setView(formLayout)
            setPositiveButton("Simpan Ke Cloud") { _, _ ->
                val name = etName.text.toString().trim().uppercase(Locale.ROOT)
                val amountValue = etAmount.text.toString().toDoubleOrNull() ?: 0.0
                val selectedType = if (rgType.checkedRadioButtonId == rbDebt.id) "DEBT" else "RECEIVABLE"

                if (name.isNotEmpty() && amountValue > 0.0) {
                    val debtId = "debt_${System.currentTimeMillis()}"
                    val debtMap = hashMapOf(
                        "id" to debtId,
                        "contactName" to name,
                        "contactPhoneNumber" to "0812",
                        "amount" to amountValue,
                        "remainingAmount" to amountValue,
                        "type" to selectedType,
                        "note" to "Input Manual Premium Cloud",
                        "timestamp" to System.currentTimeMillis(),
                        "isPaid" to false
                    )

                    firestore.collection("debts").document(debtId).set(debtMap)

                    val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
                    val catId = if (selectedType == "RECEIVABLE") 104L else 101L
                    val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
                    val txId = "tx_${System.currentTimeMillis()}"
                    
                    val txMap = hashMapOf(
                        "id" to txId,
                        "amount" to amountValue,
                        "type" to flowType,
                        "categoryId" to catId,
                        "categoryName" to catName,
                        "note" to "[${catName.uppercase(Locale.ROOT)}] $name - INPUT MANUAL PINJAMAN",
                        "timestamp" to System.currentTimeMillis()
                    )
                    firestore.collection("transactions").document(txId).set(txMap)
                    Toast.makeText(context, "Pinjaman Berhasil Tersimpan di Cloud!", Toast.LENGTH_SHORT).show()
                }
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    private fun refreshDebtListLive(container: LinearLayout, cardDebt: MaterialCardView, cardReceivable: MaterialCardView) {
        val density = requireContext().resources.displayMetrics.density
        debtListenerRegistration?.remove()

        debtListenerRegistration = firestore.collection("debts")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                container.removeAllViews()
                var totalDebtSum = 0.0
                var totalReceivableSum = 0.0

                val allDebtsList = ArrayList<HashMap<String, Any>>()
                
                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val isPaid = data["isPaid"] as? Boolean ?: false
                    val type = data["type"] as? String ?: "DEBT"
                    val remainingAmount = (data["remainingAmount"] as? Number)?.toDouble() ?: 0.0

                    if (!isPaid) {
                        if (type == "DEBT") totalDebtSum += remainingAmount else totalReceivableSum += remainingAmount
                    }
                    
                    val itemMap = HashMap(data)
                    itemMap["id"] = doc.id
                    allDebtsList.add(itemMap)
                }

                allDebtsList.sortByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }

                (((cardDebt.getChildAt(0) as LinearLayout).getChildAt(1)) as TextView).text = formatRupiah.format(totalDebtSum)
                (((cardReceivable.getChildAt(0) as LinearLayout).getChildAt(1)) as TextView).text = formatRupiah.format(totalReceivableSum)

                val filteredList = allDebtsList.filter { (it["type"] as? String) == currentTab }

                if (filteredList.isEmpty()) {
                    val tvEmpty = TextView(requireContext()).apply {
                        text = "\nTidak ada data catatan aktif pada kategori ini."
                        textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                        setTypeface(null, Typeface.ITALIC)
                    }
                    container.addView(tvEmpty)
                } else {
                    filteredList.forEach { debtItem ->
                        val itemCard = MaterialCardView(requireContext()).apply {
                            radius = 14 * density
                            cardElevation = 2 * density
                            strokeWidth = 0
                            setCardBackgroundColor(Color.WHITE)
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * density).toInt() }
                        }

                        val cardInsideLayout = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding((16 * density).toInt(), (18 * density).toInt(), (16 * density).toInt(), (18 * density).toInt())
                            gravity = Gravity.CENTER_VERTICAL
                        }

                        val leftInfo = LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        
                        val contactName = (debtItem["contactName"] as? String) ?: "TEMAN"
                        val amountOriginal = (debtItem["amount"] as? Number)?.toDouble() ?: 0.0
                        val remainingAmount = (debtItem["remainingAmount"] as? Number)?.toDouble() ?: 0.0
                        val isPaid = (debtItem["isPaid"] as? Boolean) ?: false
                        val docId = (debtItem["id"] as? String) ?: ""
                        val debtType = (debtItem["type"] as? String) ?: "DEBT"

                        leftInfo.addView(TextView(requireContext()).apply { text = contactName; textSize = 15.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")) })
                        
                        val statusLabel = if (isPaid) "LUNAS ✅" else "Sisa tagihan: ${formatRupiah.format(remainingAmount)}"
                        leftInfo.addView(TextView(requireContext()).apply { text = statusLabel; textSize = 12f; setTextColor(if (isPaid) Color.parseColor("#10B981") else Color.parseColor("#64748B")); setPadding(0, (2 * density).toInt(), 0, 0) })
                        cardInsideLayout.addView(leftInfo)

                        val tvOriginalAmount = TextView(requireContext()).apply {
                            text = formatRupiah.format(amountOriginal); textSize = 14.5f; setTypeface(null, Typeface.BOLD)
                            setTextColor(if (currentTab == "DEBT") Color.parseColor("#D97706") else Color.parseColor("#0284C7")); gravity = Gravity.END
                        }
                        cardInsideLayout.addView(tvOriginalAmount)
                        
                        itemCard.addView(cardInsideLayout)
                        itemCard.setOnClickListener { 
                            showDebtActionOptionsCloud(docId, contactName, amountOriginal, remainingAmount, isPaid, debtType)
                        }
                        container.addView(itemCard)
                    }
                }
            }
    }

    private fun showDebtActionOptionsCloud(docId: String, contactName: String, originalAmount: Double, remainingAmount: Double, isPaid: Boolean, debtType: String) {
        val options = arrayOf("✏️ Bayar / Cicil Pinjaman", "🗑️ Hapus Catatan Ini")
        val density = requireContext().resources.displayMetrics.density
        
        AlertDialog.Builder(requireContext()).apply {
            setTitle("Aksi Kontak: $contactName")
            setItems(options) { _, which ->
                if (which == 0) {
                    if (isPaid) {
                        Toast.makeText(context, "Pinjaman ini sudah lunas sepenuhnya!", Toast.LENGTH_SHORT).show()
                        return@setItems
                    }
                    
                    val wrapperLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), 0)
                    }
                    
                    // 🔥 FIX MUTLAK: Menggunakan Outlined Style bawaan Google yang sah
                    val tilPay = TextInputLayout(context, null, com.google.android.material.R.attr.textInputLayoutOutlinedStyle).apply {
                        hint = "Masukkan Jumlah Pembayaran (Rp)"
                        setBoxStrokeColor(Color.parseColor("#0D9488"))
                        val r = 12 * density
                        setBoxCornerRadii(r, r, r, r)
                    }
                    val etPay = TextInputEditText(context).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setTextColor(Color.parseColor("#1E293B")) }
                    tilPay.addView(etPay)
                    wrapperLayout.addView(tilPay)

                    AlertDialog.Builder(context).apply {
                        setTitle("Bayar / Cicil Pinjaman")
                        setMessage("Sisa tanggungan saat ini: ${formatRupiah.format(remainingAmount)}")
                        setView(wrapperLayout)
                        setPositiveButton("Proses") { _, _ ->
                            val payValue = etPay.text.toString().toDoubleOrNull() ?: 0.0
                            if (payValue > 0.0) {
                                val newRemaining = (remainingAmount - payValue).coerceAtLeast(0.0)
                                
                                firestore.collection("debts").document(docId).update(
                                    "remainingAmount", newRemaining,
                                    "isPaid", newRemaining <= 0.0
                                )

                                val flowType = if (debtType == "DEBT") "EXPENSE" else "INCOME"
                                val targetCatId = if (debtType == "DEBT") 102L else 103L
                                val targetCatName = if (debtType == "DEBT") "Pembayaran kembali" else "Penagihan Utang"
                                val txId = "tx_${System.currentTimeMillis()}"

                                val payTransactionMap = hashMapOf(
                                    "id" to txId,
                                    "amount" to payValue,
                                    "type" to flowType,
                                    "categoryId" to targetCatId,
                                    "categoryName" to targetCatName,
                                    "note" to "[$targetCatName] ${contactName.uppercase(Locale.ROOT)} - CICILAN MANUAL CLOUD",
                                    "timestamp" to System.currentTimeMillis()
                                )
                                firestore.collection("transactions").document(txId).set(payTransactionMap)
                                Toast.makeText(context, "Cicilan Berhasil Tercatat!", Toast.LENGTH_SHORT).show()
                            }
                        }
                        setNegativeButton("Batal", null)
                        show()
                    }
                } else if (which == 1) {
                    AlertDialog.Builder(context).apply {
                        setTitle("Hapus Data")
                        setMessage("Apakah Anda yakin ingin menghapus permanen catatan pinjaman dari $contactName?")
                        setPositiveButton("Hapus") { _, _ ->
                            firestore.collection("debts").document(docId).delete()
                            Toast.makeText(context, "Catatan berhasil dihapus dari awan!", Toast.LENGTH_SHORT).show()
                        }
                        setNegativeButton("Batal", null)
                        show()
                    }
                }
            }
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        debtListenerRegistration?.remove()
    }
}
