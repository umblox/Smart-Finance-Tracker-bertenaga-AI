package com.smartfinance.tracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
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

        val context = requireContext()
        val sharedPreferences = context.getSharedPreferences("smart_finance_prefs", android.content.Context.MODE_PRIVATE)
        val savedKey = sharedPreferences.getString("gemini_api_key", "")
        binding.etApiKey.setText(savedKey)

        // DYNAMIC CONTAINER UNTUK MASTER MANAJEMEN KATEGORI BARU
        val linearLayout = binding.root.findViewById<LinearLayout>(android.R.id.id) ?: (binding.root as? ViewGroup)?.getChildAt(0) as? LinearLayout
        
        linearLayout?.let { layout ->
            val tvHeader = TextView(context).apply { 
                text = "\n🗂️ TAMBAH KATEGORI TRANSAKSI MANUAL"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            layout.addView(tvHeader)

            val etCatName = EditText(context).apply { hint = "Nama Kategori Baru (ex: Liburan)" }
            layout.addView(etCatName)

            val spinner = Spinner(context)
            val adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("EXPENSE (Pengeluaran)", "INCOME (Pemasukan)"))
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
            layout.addView(spinner)

            val btnAddCat = Button(context).apply {
                text = "SIMPAN MASTER KATEGORI"
                backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#008080"))
                setOnClickListener {
                    val catName = etCatName.text.toString().trim()
                    val type = if (spinner.selectedItemPosition == 0) "EXPENSE" else "INCOME"
                    
                    if (catName.isNotEmpty()) {
                        lifecycleScope.launch {
                            val db = AppDatabase.getDatabase(context)
                            db.categoryDao().insertCategory(CategoryEntity(name = catName, type = type, iconName = "ic_custom"))
                            Toast.makeText(context, "Kategori '$catName' Berhasil Ditambahkan!", Toast.LENGTH_SHORT).show()
                            etCatName.setText("")
                        }
                    } else {
                        Toast.makeText(context, "Nama kategori tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            layout.addView(btnAddCat)
        }

        binding.btnSaveSettings.setOnClickListener {
            val inputKey = binding.etApiKey.text.toString().trim()
            sharedPreferences.edit().putString("gemini_api_key", inputKey).apply()
            injectDefaultCategories()
        }
    }

    private fun injectDefaultCategories() {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val dao = db.categoryDao()
            dao.insertCategory(CategoryEntity(1, "Gaji", "INCOME", "ic_income"))
            dao.insertCategory(CategoryEntity(2, "Makanan", "EXPENSE", "ic_expense"))
            Toast.makeText(requireContext(), "Konfigurasi API Key Berhasil Disimpan!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
