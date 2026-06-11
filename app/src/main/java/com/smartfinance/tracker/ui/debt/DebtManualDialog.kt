package com.smartfinance.tracker.ui.debt

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

class DebtManualDialog(
    private val initialTabFilter: String,
    private val onSavedAction: () -> Unit
) : DialogFragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))
    private var activeContactEditText: EditText? = null

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            requireContext().contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    if (nameIndex != -1) activeContactEditText?.setText(cursor.getString(nameIndex))
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) launchNativeContactPicker()
        else Toast.makeText(context, "Akses kontak ditolak. Silakan ketik nama manual.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val viewInflated = LayoutInflater.from(context).inflate(R.layout.dialog_transaction_premium, null, false)
        val innerLayout = viewInflated.findViewById<LinearLayout>(viewInflated.id) ?: (viewInflated as ViewGroup).getChildAt(0) as LinearLayout

        val etAmount = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxAmount)
        val etNote = viewInflated.findViewById<TextInputEditText>(R.id.etPremiumTxNote)
        val spinnerCategory = viewInflated.findViewById<Spinner>(R.id.spinnerPremiumTxCategory)
        val rgType = viewInflated.findViewById<RadioGroup>(R.id.rgPremiumTxType)
        val rbDebt = viewInflated.findViewById<RadioButton>(R.id.rbPremiumTxExpense)
        val rbReceivable = viewInflated.findViewById<RadioButton>(R.id.rbPremiumTxIncome)

        val tvCategoryLabel = viewInflated.findViewWithTag<TextView>("tvCategoryLabel") ?: (spinnerCategory.parent as ViewGroup).getChildAt((spinnerCategory.parent as ViewGroup).indexOfChild(spinnerCategory) - 1) as? TextView

        spinnerCategory.visibility = View.GONE
        tvCategoryLabel?.visibility = View.GONE

        rbDebt.text = "Saya Berhutang (Hutang)"
        rbReceivable.text = "Orang Lain Berhutang (Piutang)"
        if (initialTabFilter == "DEBT") rbDebt.isChecked = true else rbReceivable.isChecked = true

        innerLayout.addView(TextView(context).apply {
            text = "Nama Kontak Terkait:"
            textSize = 12f; setTextColor(Color.parseColor("#64748B")); setTypeface(null, Typeface.BOLD)
            setPadding((20 * density).toInt(), (14 * density).toInt(), 0, (4 * density).toInt())
        }, 2)

        val contactRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * density).toInt(), 0, (20 * density).toInt(), 0)
        }
        
        val tilContact = TextInputLayout(context, null, com.google.android.material.R.style.Widget_MaterialComponents_TextInputLayout_OutlinedBox).apply {
            boxStrokeColor = Color.parseColor("#0D9488")
            setBoxCornerRadii(12 * density, 12 * density, 12 * density, 12 * density)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val etContact = TextInputEditText(context).apply {
            hint = "Contoh: JOKO atau ADIT"
            setTextColor(Color.parseColor("#1E293B"))
        }
        activeContactEditText = etContact
        tilContact.addView(etContact)
        contactRow.addView(tilContact)
        
        val btnPick = MaterialButton(context).apply {
            text = "👥 HUBUNG"; textSize = 11f; cornerRadius = (10 * density).toInt()
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#475569"))
            setOnClickListener { checkContactPermissionAndLaunch() }
        }
        btnPick.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (54 * density).toInt()).apply { leftMargin = (10 * density).toInt() }
        contactRow.addView(btnPick)
        innerLayout.addView(contactRow, 3)

        // ✅ TATA PANDUAN PREMIUM: Menghapus teks YYYY-MM-DD kaku
        innerLayout.addView(TextView(context).apply { 
            text = "Tanggal & Waktu Pinjaman (DD-MM-YYYY • HH:mm)"
            textSize = 12f; setTextColor(Color.parseColor("#64748B")); setPadding((20 * density).toInt(), (14 * density).toInt(), 0, (4 * density).toInt())
        })
        
        val etDate = EditText(context).apply {
            setText(sdfPremium.format(Date()))
            setTextColor(Color.parseColor("#2D3748")); textSize = 14.5f
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (12 * density).toInt())
        }
        innerLayout.addView(etDate)

        val actionButtonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; weightSum = 2f
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { 
                setMargins((20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt(), (20 * density).toInt()) 
            }
        }
        val btnCancel = MaterialButton(context).apply { text = "Batal"; textSize = 14f; cornerRadius = 24; setTextColor(Color.parseColor("#475569")); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = (12 * density).toInt() } }
        val btnSave = MaterialButton(context).apply { text = "Simpan Pinjaman"; textSize = 14f; cornerRadius = 24; setTextColor(Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488")); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        actionButtonsRow.addView(btnCancel)
        actionButtonsRow.addView(btnSave)
        innerLayout.addView(actionButtonsRow)

        val dialog = AlertDialog.Builder(context).setView(viewInflated).create()
        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val name = etContact.text.toString().trim().uppercase(Locale.ROOT)
            val amountVal = etAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteText = etNote.text.toString().trim()
            val dateVal = etDate.text.toString().trim()
            val selectedType = if (rgType.checkedRadioButtonId == rbDebt.id) "DEBT" else "RECEIVABLE"

            if (name.isNotEmpty() && amountVal > 0.0 && dateVal.isNotEmpty()) {
                lifecycleScope.launch {
                    val targetTimestamp = try { sdfPremium.parse(dateVal)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }
                    val debtId = "debt_${System.currentTimeMillis()}"
                    
                    val debtMap = hashMapOf(
                        "id" to debtId,
                        "contactName" to name,
                        "contactPhoneNumber" to "0812",
                        "amount" to amountVal,
                        "remainingAmount" to amountVal,
                        "type" to selectedType,
                        "note" to noteText.ifEmpty { "Input Manual Buku Utang" },
                        "timestamp" to targetTimestamp,
                        "isPaid" to false
                    )
                    firestore.collection("debts").document(debtId).set(debtMap).await()

                    val flowType = if (selectedType == "RECEIVABLE") "EXPENSE" else "INCOME"
                    val catId = if (selectedType == "RECEIVABLE") 104L else 101L
                    val catName = if (selectedType == "RECEIVABLE") "Piutang" else "Hutang"
                    val txId = "tx_${System.currentTimeMillis()}"
                    
                    val txMap = hashMapOf(
                        "id" to txId,
                        "amount" to amountVal,
                        "type" to flowType,
                        "categoryId" to catId,
                        "categoryName" to catName,
                        "note" to "[$catName] $name - ${noteText.ifEmpty { "INPUT MANUAL PINJAMAN" }.uppercase(Locale.ROOT)}",
                        "timestamp" to targetTimestamp,
                        "debtId" to debtId
                    )
                    firestore.collection("transactions").document(txId).set(txMap).await()
                    
                    Toast.makeText(context, "Pinjaman Berhasil Tersimpan ke Cloud!", Toast.LENGTH_SHORT).show()
                    onSavedAction()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Mohon lengkapi nominal dan nama kontak!", Toast.LENGTH_SHORT).show()
            }
        }
        return dialog
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
}

