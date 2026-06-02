package com.smartfinance.tracker.ui.debt

import android.app.Activity
import android.content.Intent
import android.provider.ContactsContract
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.DebtEntity
import com.smartfinance.tracker.databinding.FragmentAddDebtBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddDebtFragment : Fragment() {

    private var _binding: FragmentAddDebtBinding? = null
    private val binding get() = _binding!!
    
    private var selectedContactName = ""
    private var selectedContactPhone = ""

    // Launcher untuk menangkap data setelah memilih kontak dari buku telepon HP
    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@registerForActivityResult
            val cursor = requireContext().contentResolver.query(contactUri, null, null, null, null)
            
            cursor?.use { 
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    selectedContactName = it.getString(nameIndex)
                    
                    // Tempel nama kontak yang dipilih ke tombol/kolom di UI
                    binding.btnSelectContact.text = "Kontak: $selectedContactName"
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddDebtBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Klik tombol kontak untuk membuka Kontak Telepon Asli HP
        binding.btnSelectContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
            contactPickerLauncher.launch(intent)
        }

        // Menyimpan data Hutang/Piutang ke Room Database
        binding.btnSaveDebt.setOnClickListener {
            val amountStr = binding.etDebtAmount.text.toString()
            val note = binding.etDebtNote.text.toString()
            val type = if (binding.rbIsHutang.isChecked) "DEBT" else "RECEIVABLE"

            if (selectedContactName.isEmpty()) {
                Toast.makeText(context, "Pilih kontak terlebih dahulu!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (amountStr.isEmpty()) {
                Toast.makeText(context, "Nominal tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val amount = amountStr.toDouble()

            // Eksekusi penyimpanan asinkron ke database lokal
            val db = AppDatabase.getDatabase(requireContext())
            CoroutineScope(Dispatchers.IO).launch {
                db.debtDao().insertDebt(
                    DebtEntity(
                        contactName = selectedContactName,
                        contactPhoneNumber = selectedContactPhone,
                        amount = amount,
                        remainingAmount = amount,
                        type = type,
                        note = note,
                        timestamp = System.currentTimeMillis()
                    )
                )
                
                CoroutineScope(Dispatchers.Main).launch {
                    Toast.makeText(context, "Catatan Hutang Berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

