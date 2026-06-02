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
import kotlinx.coroutines.flow.first
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
        val db = AppDatabase.getDatabase(context)
        val sharedPreferences = context.getSharedPreferences("smart_finance_prefs", android.content.Context.MODE_PRIVATE)
        binding.etApiKey.setText(sharedPreferences.getString("gemini_api_key", ""))

        val containerLayout = binding.root as? ViewGroup
        
        containerLayout?.let { layout ->
            if (layout.findViewWithTag<View>("manual_category_section") == null) {
                val innerContainer = LinearLayout(context).apply {
                    tag = "manual_category_section"
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 24, 0, 0)
                }

                val tvHeader = TextView(context).apply { 
                    text = "🗂️ KELOLA MASTER KATEGORI"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                innerContainer.addView(tvHeader)

                val etCatName = EditText(context).apply { hint = "Nama Kategori Baru" }
                innerContainer.addView(etCatName)

                val spinner = Spinner(context)
                spinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("EXPENSE (Pengeluaran)", "INCOME (Pemasukan)"))
                innerContainer.addView(spinner)

                // TextView untuk memunculkan daftar list kategori terdaftar
                val tvCatList = TextView(context).apply { 
                    text = "\nDaftar Kategori Tersimpan:\nMemuat data..."
                    textSize = 13f
                }

                val btnAddCat = Button(context).apply {
                    text = "SIMPAN KATEGORI BARU"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#008080"))
                    setOnClickListener {
                        val catName = etCatName.text.toString().trim()
                        val type = if (spinner.selectedItemPosition == 0) "EXPENSE" else "INCOME"
                        
                        if (catName.isNotEmpty()) {
                            lifecycleScope.launch {
                                db.categoryDao().insertCategory(CategoryEntity(name = catName, type = type, iconName = "ic_custom"))
                                Toast.makeText(context, "Kategori '$catName' disimpan!", Toast.LENGTH_SHORT).show()
                                etCatName.setText("")
                                refreshCategoryList(db, tvCatList)
                            }
                        }
                    }
                }
                innerContainer.addView(btnAddCat)

                // Tombol Hapus Kategori Berdasarkan ID inputan
                val etDeleteId = EditText(context).apply { hint = "Ketik ID Kategori untuk dihapus"; inputType = android.text.InputType.TYPE_CLASS_NUMBER }
                innerContainer.addView(etDeleteId)

                val btnDeleteCat = Button(context).apply {
                    text = "HAPUS KATEGORI BY ID"
                    backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#C53030"))
                    setOnClickListener {
                        val targetId = etDeleteId.text.toString().toLongOrNull()
                        if (targetId != null) {
                            lifecycleScope.launch {
                                // Menghapus menggunakan entitas penampung ID target
                                db.categoryDao().insertCategory(CategoryEntity(id = targetId, name = "Dihapus", type = "EXPENSE", iconName = ""))
                                Toast.makeText(context, "ID Kategori $targetId Dihapus/Dibersihkan!", Toast.LENGTH_SHORT).show()
                                etDeleteId.setText("")
                                refreshCategoryList(db, tvCatList)
                            }
                        }
                    }
                }
                innerContainer.addView(btnDeleteCat)
                innerContainer.addView(tvCatList)
                
                layout.addView(innerContainer)
                refreshCategoryList(db, tvCatList) // Jalankan load awal list kategori
            }
        }

        binding.btnSaveSettings.setOnClickListener {
            val inputKey = binding.etApiKey.text.toString().trim()
            sharedPreferences.edit().putString("gemini_api_key", inputKey).apply()
            Toast.makeText(context, "API Key Konfigurasi Tersimpan!", Toast.LENGTH_SHORT).show()
            
            // Pemicu otomatis penyuntikan 15 kategori default
            injectDefaultCategories(tvCatListContainer = containerLayout?.findViewWithTag<LinearLayout>("manual_category_section")?.getChildAt(7) as? TextView ?: binding.root.findViewById(android.R.id.text1))
        }
    }

    private fun injectDefaultCategories(tvCatListContainer: TextView?) {
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(requireContext())
            val dao = db.categoryDao()
            
            val defaultCats = listOf(
                CategoryEntity(1, "Gaji & Pendapatan", "INCOME", "ic_income"),
                CategoryEntity(2, "Makanan & Minuman", "EXPENSE", "ic_food"),
                CategoryEntity(3, "Bahan Bakar & Transportasi", "EXPENSE", "ic_fuel"),
                CategoryEntity(4, "Tagihan & Utilitas", "EXPENSE", "ic_bill"),
                CategoryEntity(5, "Rokok & Hiburan Pribadi", "EXPENSE", "ic_smoke"),
                CategoryEntity(6, "Belanja Kebutuhan Rumah", "EXPENSE", "ic_home"),
                CategoryEntity(7, "Kesehatan & Medis", "EXPENSE", "ic_medical"),
                CategoryEntity(8, "Pendidikan & Buku", "EXPENSE", "ic_education"),
                CategoryEntity(9, "Pakaian & Gaya Hidup", "EXPENSE", "ic_fashion"),
                CategoryEntity(10, "Investasi & Tabungan", "EXPENSE", "ic_invest"),
                CategoryEntity(11, "Cicilan & Pinjaman", "EXPENSE", "ic_debt_pay"),
                CategoryEntity(12, "Hutang (Saya Meminjam)", "INCOME", "ic_debt_get"),
                CategoryEntity(13, "Piutang (Memberi Pinjaman)", "EXPENSE", "ic_receivable"),
                CategoryEntity(14, "Bonus & Hadiah", "INCOME", "ic_gift"),
                CategoryEntity(15, "Lain-lain / Umum", "EXPENSE", "ic_generic")
            )
            
            defaultCats.forEach { dao.insertCategory(it) }
            Toast.makeText(requireContext(), "15 Kategori Master Berhasil Disinkronkan!", Toast.LENGTH_SHORT).show()
            
            if (tvCatListContainer != null) {
                refreshCategoryList(db, tvCatListContainer)
            }
        }
    }

    private fun refreshCategoryList(db: AppDatabase, tvList: TextView) {
        lifecycleScope.launch {
            val categories = db.categoryDao().getAllCategories().first()
            val builder = StringBuilder("\nDaftar Kategori Tersimpan di HP:\n")
            categories.forEach { 
                builder.append("ID: ${it.id} -> ${it.name} [${it.type}]\n")
            }
            tvList.text = builder.toString()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
