package com.smartfinance.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.smartfinance.tracker.ai.FinancialAssistant
import com.smartfinance.tracker.ai.GeminiClient
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.repository.FinanceRepository
import com.smartfinance.tracker.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var geminiClient: GeminiClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Setup repository dan client AI untuk menghubungkan SharedPreferences
        val db = AppDatabase.getDatabase(requireContext())
        val repo = FinanceRepository(db.categoryDao(), db.transactionDao(), db.debtDao())
        val assistant = FinancialAssistant(repo)
        geminiClient = GeminiClient(requireContext(), assistant)

        // Tampilkan API key yang sedang tersimpan (jika ada)
        val savedKey = geminiClient.getSavedApiKey()
        if (savedKey.isNotEmpty()) {
            binding.etApiKey.setText(savedKey)
        }

        // Aksi simpan perubahan key baru dari user
        binding.btnSaveSettings.setOnClickListener {
            val newKey = binding.etApiKey.text.toString().trim()
            if (newKey.isNotEmpty()) {
                geminiClient.saveCustomApiKey(newKey)
                Toast.makeText(context, "API Key pribadi berhasil disimpan!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Kolom API Key tidak boleh kosong!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

