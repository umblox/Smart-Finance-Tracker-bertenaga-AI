package com.smartfinance.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import com.smartfinance.tracker.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val sharedPreferences = requireContext().getSharedPreferences("smart_finance_prefs", android.content.Context.MODE_PRIVATE)
        
        // Load API Key lama jika ada
        val savedKey = sharedPreferences.getString("gemini_api_key", "")
        binding.etApiKey.setText(savedKey)

        // Tombol Simpan Konfigurasi API Key & Inisialisasi Kategori Bawaan Aplikasi
        binding.btnSaveSettings.setOnClickListener {
            val inputKey = binding.etApiKey.text.toString().trim()
            
            sharedPreferences.edit().putString("gemini_api_key", inputKey).apply()
            
            // Suntikkan kategori transaksi finansial standar langsung ke SQLite Room DB
            injectDefaultCategories()
        }
    }

    private fun injectDefaultCategories() {
        val db = AppDatabase.getDatabase(requireContext())
        
        lifecycleScope.launch {
            val dao = db.categoryDao()
            
            // Buat master data kategori transaksi
            val defaultCategories = listOf(
                CategoryEntity(1, "Gaji", "INCOME", "ic_income"),
                CategoryEntity(2, "Makanan", "EXPENSE", "ic_expense"),
                CategoryEntity(3, "Rokok", "EXPENSE", "ic_expense"),
                CategoryEntity(4, "Transportasi", "EXPENSE", "ic_expense")
            )
            
            for (cat in defaultCategories) {
                dao.insertCategory(cat)
            }
            
            Toast.makeText(requireContext(), "Konfigurasi & Master Kategori Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
