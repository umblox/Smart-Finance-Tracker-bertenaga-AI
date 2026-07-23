package com.smartfinance.tracker.ui.transaction

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.databinding.DialogTransactionManualPremiumBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class TransactionManualDialog(private val onSaved: () -> Unit) : DialogFragment() {

    private var _binding: DialogTransactionManualPremiumBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: TransactionViewModel
    
    private var allCategoriesCloud = listOf<Map<String, Any>>()
    private var selectedCategoryMap: Map<String, Any>? = null

    private val sdfPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri: Uri? = result.data?.data
            contactUri?.let { uri ->
                val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                requireContext().contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        if (nameIdx != -1) binding.etManualPremiumContact.setText(cursor.getString(nameIdx))
                    }
                }
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openContactPicker()
        else Toast.makeText(context, "Akses kontak ditolak.", Toast.LENGTH_SHORT).show()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogTransactionManualPremiumBinding.inflate(layoutInflater)
        val dialog = AlertDialog.Builder(requireContext()).setView(binding.root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        viewModel = ViewModelProvider(this)[TransactionViewModel::class.java]

        binding.etManualPremiumDate.setText(sdfPremium.format(Date()))

        binding.btnManualCancel.setOnClickListener { dialog.dismiss() }
        binding.btnManualPremiumPick.setOnClickListener { checkContactPermissionAndOpen() }
        binding.btnCategoryPicker.setOnClickListener { showCategoryPickerDialog() }

        lifecycleScope.launch {
            try {
                allCategoriesCloud = viewModel.getCategoriesForDropdown()
            } catch (e: Exception) {
                allCategoriesCloud = listOf(
                    mapOf("id" to 101L, "name" to "Hutang", "type" to "DEBT"),
                    mapOf("id" to 104L, "name" to "Piutang", "type" to "DEBT"),
                    mapOf("id" to 15L, "name" to "Lain-lain / Umum", "type" to "EXPENSE")
                )
            }
        }

        binding.btnManualSave.setOnClickListener {
            val amountVal = binding.etManualPremiumAmount.text.toString().toDoubleOrNull() ?: 0.0
            val noteVal = binding.etManualPremiumNote.text.toString().trim()
            val dateVal = binding.etManualPremiumDate.text.toString().trim()
            val contactVal = binding.etManualPremiumContact.text.toString().trim()

            if (selectedCategoryMap == null) {
                Toast.makeText(context, "Harap pilih Kategori terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val catId = (selectedCategoryMap!!["id"] as? Number)?.toLong() ?: 15L
            val catName = selectedCategoryMap!!["name"] as? String ?: "Umum"
            val typeRaw = (selectedCategoryMap!!["type"] as? String)?.uppercase(Locale.ROOT) ?: "EXPENSE"

            val isDebtTransaction = typeRaw == "DEBT" || typeRaw == "RECEIVABLE" || catId == 101L || catId == 102L || catId == 103L || catId == 104L

            if (isDebtTransaction && contactVal.isEmpty()) {
                Toast.makeText(context, "Nama kontak wajib diisi untuk transaksi Utang-Piutang!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (amountVal > 0.0 && noteVal.isNotEmpty()) {
                lifecycleScope.launch {
                    val targetTime = try { sdfPremium.parse(dateVal)?.time ?: System.currentTimeMillis() } catch (e: Exception) { System.currentTimeMillis() }

                    val finalType = when (catId) {
                        101L, 103L -> "INCOME"     
                        102L, 104L -> "EXPENSE"    
                        else -> if (typeRaw == "INCOME") "INCOME" else "EXPENSE"
                    }

                    val finalNote = if (isDebtTransaction) "[$catName] $contactVal - $noteVal".uppercase(Locale.ROOT) else noteVal.uppercase(Locale.ROOT)
                    val txId = "tx_${System.currentTimeMillis()}"
                    val generatedDebtId = if (isDebtTransaction) "debt_${System.currentTimeMillis()}" else null

                    val txMap = HashMap<String, Any>()
                    txMap["id"] = txId
                    txMap["amount"] = amountVal
                    txMap["type"] = finalType
                    txMap["categoryId"] = catId
                    txMap["categoryName"] = catName
                    txMap["note"] = finalNote
                    txMap["timestamp"] = targetTime
                    if (generatedDebtId != null) txMap["debtId"] = generatedDebtId
                    
                    viewModel.saveTransaction(txId, txMap)

                    if (isDebtTransaction && generatedDebtId != null) {
                        val selectedDebtType = if (catId == 104L || typeRaw == "RECEIVABLE") "RECEIVABLE" else "DEBT"
                        val debtMap = HashMap<String, Any>()
                        debtMap["id"] = generatedDebtId
                        debtMap["contactName"] = contactVal.uppercase(Locale.ROOT)
                        debtMap["contactPhoneNumber"] = "0812"
                        debtMap["amount"] = amountVal
                        debtMap["remainingAmount"] = amountVal
                        debtMap["type"] = selectedDebtType
                        debtMap["note"] = "Input Manual Form Cloud"
                        debtMap["timestamp"] = targetTime
                        debtMap["isPaid"] = false
                        
                        viewModel.saveDebt(generatedDebtId, debtMap)
                    }

                    Toast.makeText(context, "Berhasil Disimpan Langsung ke Cloud Server!", Toast.LENGTH_SHORT).show()
                    onSaved()
                    dialog.dismiss()
                }
            } else {
                Toast.makeText(context, "Mohon lengkapi nominal dan nama transaksi!", Toast.LENGTH_SHORT).show()
            }
        }

        return dialog
    }

    private fun showCategoryPickerDialog() {
        val density = requireContext().resources.displayMetrics.density
        val dialogLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F8FAFC")) }
        
        val tabOuterBox = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; setPadding((4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt(), (4f * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (42f * density).toInt()).apply { setMargins((16f * density).toInt(), (16f * density).toInt(), (16f * density).toInt(), (8f * density).toInt()) }
            background = GradientDrawable().apply { cornerRadius = 12f * density; setColor(Color.parseColor("#E2E8F0")) }
            weightSum = 3f
        }

        val btnTabExpense = MaterialButton(requireContext()).apply { text = "Pengeluaran"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabIncome = MaterialButton(requireContext()).apply { text = "Pemasukan"; textSize = 11.5f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabDebt = MaterialButton(requireContext()).apply { text = "Hutang/Piutang"; textSize = 10f; cornerRadius = (10f * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }

        tabOuterBox.addView(btnTabExpense); tabOuterBox.addView(btnTabIncome); tabOuterBox.addView(btnTabDebt)
        dialogLayout.addView(tabOuterBox)

        val scrollView = ScrollView(requireContext())
        val containerList = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), (8 * density).toInt(), (16 * density).toInt(), (16 * density).toInt()) }
        scrollView.addView(containerList)
        dialogLayout.addView(scrollView)

        val dialog = AlertDialog.Builder(requireContext()).setView(dialogLayout).create()

        fun renderList(typeFilter: String) {
            containerList.removeAllViews()
            val activeBg = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
            val inactiveBg = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            
            btnTabExpense.apply { backgroundTintList = if(typeFilter == "EXPENSE") activeBg else inactiveBg; setTextColor(if(typeFilter == "EXPENSE") Color.WHITE else Color.parseColor("#64748B")) }
            btnTabIncome.apply { backgroundTintList = if(typeFilter == "INCOME") activeBg else inactiveBg; setTextColor(if(typeFilter == "INCOME") Color.WHITE else Color.parseColor("#64748B")) }
            btnTabDebt.apply { backgroundTintList = if(typeFilter == "DEBT") activeBg else inactiveBg; setTextColor(if(typeFilter == "DEBT") Color.WHITE else Color.parseColor("#64748B")) }

            val targetTypes = if (typeFilter == "DEBT") listOf("DEBT", "RECEIVABLE") else listOf(typeFilter)
            val filteredList = allCategoriesCloud.filter { targetTypes.contains((it["type"] as? String)?.uppercase(Locale.ROOT)) }
            val parentCategories = filteredList.filter { it["parentCategoryId"] == null }.sortedBy { it["name"] as? String ?: "" }
            val subCategories = filteredList.filter { it["parentCategoryId"] != null }

            if (parentCategories.isEmpty()) {
                containerList.addView(TextView(requireContext()).apply { text = "Belum ada kategori terdaftar."; setTextColor(Color.GRAY); gravity = Gravity.CENTER; setPadding(0, 40, 0, 40) })
                return
            }

            parentCategories.forEach { parent ->
                val blockCard = MaterialCardView(requireContext()).apply { radius = 14f * density; cardElevation = 1f * density; strokeWidth = 0; setCardBackgroundColor(Color.WHITE); layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() } }
                val cardContentContainer = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
                val parentRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
                    setOnClickListener { selectedCategoryMap = parent; binding.btnCategoryPicker.text = parent["name"] as? String ?: ""; dialog.dismiss() }
                }
                parentRow.addView(TextView(requireContext()).apply { text = "📁"; textSize = 16f; setPadding(0, 0, (12 * density).toInt(), 0) })
                parentRow.addView(TextView(requireContext()).apply { text = parent["name"] as? String ?: ""; setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f; setTypeface(null, Typeface.BOLD); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
                cardContentContainer.addView(parentRow)

                val parentId = (parent["id"] as? Number)?.toLong() ?: 0L
                val kids = subCategories.filter { (it["parentCategoryId"] as? Number)?.toLong() == parentId }.sortedBy { it["name"] as? String ?: "" }

                if (kids.isNotEmpty()) cardContentContainer.addView(View(requireContext()).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()) })

                kids.forEach { child ->
                    val childRow = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((14 * density).toInt(), (10 * density).toInt(), (14 * density).toInt(), (10 * density).toInt()); setBackgroundColor(Color.parseColor("#FAFAFA"))
                        setOnClickListener { selectedCategoryMap = child; binding.btnCategoryPicker.text = child["name"] as? String ?: ""; dialog.dismiss() }
                    }
                    val treeLine = View(requireContext()).apply { setBackgroundColor(Color.parseColor("#CBD5E0")); layoutParams = LinearLayout.LayoutParams((1.5f * density).toInt(), (16 * density).toInt()).apply { rightMargin = (12 * density).toInt(); leftMargin = (6 * density).toInt() } }
                    childRow.addView(treeLine); childRow.addView(TextView(requireContext()).apply { text = "💰"; textSize = 13f; setPadding(0, 0, (10 * density).toInt(), 0) })
                    childRow.addView(TextView(requireContext()).apply { text = child["name"] as? String ?: ""; setTextColor(Color.parseColor("#475569")); textSize = 13.5f; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
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
        renderList(if (currentType == "RECEIVABLE") "DEBT" else currentType)

        dialog.show()
    }

    private fun checkContactPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) openContactPicker()
        else requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
