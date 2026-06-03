package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.databinding.FragmentSettingsBinding
import com.smartfinance.tracker.ui.category.CategoryManagerDialog // IMPORT DARI PACKAGE BARU SURGATAL

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

        // SUB-MENU 1: INPUT API KEY GROQ
        binding.menuApiKey.setOnClickListener {
            showApiKeyDialog()
        }

        // SUB-MENU 2: PANGGIL KELAS TERPISAH DARI PACKAGE BARU
        binding.menuManageCategories.setOnClickListener {
            CategoryManagerDialog(requireContext(), lifecycleScope).show()
        }

        // SUB-MENU 3: TENTANG APLIKASI
        binding.menuAboutApp.setOnClickListener {
            showAboutAppDialog()
        }
    }

    private fun showApiKeyDialog() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        
        val linearLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 30, 50, 30)
        }
        
        val etKey = EditText(context).apply {
            hint = "Masukkan API Key Groq Anda (gsk_...)"
            setText(prefs.getString("gemini_api_key", ""))
            textSize = 14f
        }
        linearLayout.addView(etKey)

        AlertDialog.Builder(context).apply {
            setTitle("🔑 API Key Groq Cloud")
            setMessage("Masukkan token resmi Groq Anda untuk mengaktifkan kecerdasan pemrosesan Bahasa Alami AI.")
            setView(linearLayout)
            setPositiveButton("Simpan") { _, _ ->
                val inputKey = etKey.text.toString().trim()
                prefs.edit().putString("gemini_api_key", inputKey).apply()
                Toast.makeText(context, "API Key Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
            }
            setNegativeButton("Batal", null)
            show()
        }
    }

    private fun showAboutAppDialog() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle("ℹ️ Tentang Aplikasi")
            setMessage("Smart Finance Tracker bertenaga AI\nVersion 2.5-Premium Pro\n\n" +
                    "Aplikasi pengelolaan keuangan pribadi modern dengan integrasi database SQLite lokal yang aman.\n\n" +
                    "Didukung oleh mesin asisten pintar Groq Cloud API Llama 3.1 untuk interpretasi bahasa alami akuntansi yang bebas hambatan.")
            setPositiveButton("Tutup", null)
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
