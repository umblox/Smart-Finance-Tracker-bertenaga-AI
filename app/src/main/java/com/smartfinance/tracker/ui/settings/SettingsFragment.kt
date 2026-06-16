package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.firestore.FirebaseFirestore
import com.smartfinance.tracker.R
import com.smartfinance.tracker.ai.GroqClient
import com.smartfinance.tracker.ui.category.CategoryManagerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsFragment : Fragment() {

    // ... (filePickerLauncher dan exportCsvLauncher biarkan sama seperti sebelumnya) ...

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val db = FirebaseFirestore.getInstance()

        // (Menu API Groq, Firebase JSON, Expert Mode, Category, Export CSV biarkan sama)

        // 🔥 MENU BARU: TRANSAKSI BERKALA / TERJADWAL
        view.findViewById<MaterialCardView>(R.id.menuRecurringTx)?.setOnClickListener {
            // Memanggil modul UI Transaksi Berkala
            RecurringTxListDialog().show(parentFragmentManager, "RecurringTxListDialog")
        }

        // 🔥 BIOMETRIK SINKRONISASI CLOUD
        val switchBiometric = view.findViewById<SwitchMaterial>(R.id.switchBiometric)
        switchBiometric.isChecked = prefs.getBoolean("use_biometric", false)
        
        switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            // Simpan di HP
            prefs.edit().putBoolean("use_biometric", isChecked).apply()
            
            // Simpan Permanen di Cloud
            db.collection("app_config").document("security")
                .set(hashMapOf("use_biometric" to isChecked))
                
            val status = if (isChecked) "Diaktifkan & Disimpan ke Cloud" else "Dimatikan"
            Toast.makeText(requireContext(), "Keamanan Biometrik $status", Toast.LENGTH_SHORT).show()
        }

        return view
    }
}
