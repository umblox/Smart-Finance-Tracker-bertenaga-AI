package com.smartfinance.tracker.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
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

        // SUB-MENU 1: KLIK UNTUK INPUT API KEY GROQ
        binding.menuApiKey.setOnClickListener {
            showApiKeyDialog()
        }

        // SUB-MENU 2: KLIK UNTUK KELOLA KATEGORI (CRUD LENGKAP)
        binding.menuManageCategories.setOnClickListener {
            showCategoryManagerDialog()
        }

        // SUB-MENU 3: KLIK UNTUK TENTANG APLIKASI
        binding.menuAboutApp.setOnClickListener {
            showAboutAppDialog()
        }
    }

    // ==========================================
    // SUB-MENU 1: DIALOG INPUT API KEY
    // ==========================================
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

    // ==========================================
    // SUB-MENU 2: DIALOG MANAJEMEN KATEGORI (CRUD)
    // ==========================================
    private fun showCategoryManagerDialog() {
        val context = requireContext()
        val db = AppDatabase.getDatabase(context)

        val scrollContainer = ScrollView(context)
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 30, 44, 40)
        }

        // FORM INPUT TAMBAH KATEGORI BARU
        val etName = EditText(context).apply { hint = "Nama Kategori Baru (ex: Tips Kurir)" }
        rootLayout.addView(etName)

        val spinner = Spinner(context).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, listOf("EXPENSE (Pengeluaran)", "INCOME (Pemasukan)"))
        }
        rootLayout.addView(spinner)

        val btnAdd = Button(context).apply {
            text = "➕ SIMPAN KATEGORI"
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            setTextColor(Color.WHITE)
        }
        rootLayout.addView(btnAdd)

        val tvListTitle = TextView(context).apply {
            text = "\n📋 DAFTAR KATEGORI AKTIF (KLIK UNTUK EDIT/HAPUS):"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3748"))
        }
        rootLayout.addView(tvListTitle)

        // Container tempat menampung list item kategori dari SQLite
        val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rootLayout.addView(listLayout)

        scrollContainer.addView(rootLayout)

        val dialog = AlertDialog.Builder(context).apply {
            setTitle("🗂️ Kelola Kategori Sistem")
            setView(scrollContainer)
            setNeutralButton("Injeksi 15 Kategori Default") { _, _ ->
                injectDefaultCategories(db, listLayout, etName, spinner, btnAdd)
            }
            setPositiveButton("Selesai", null)
        }.create()

        // Panggil fungsi untuk load data kategori dari Room DB
        refreshCategoryItems(db, listLayout, etName, spinner, btnAdd)
        dialog.show()
    }

    private fun refreshCategoryItems(db: AppDatabase, layout: LinearLayout, etName: EditText, spinner: Spinner, btnAdd: Button) {
        lifecycleScope.launch {
            val categories = db.categoryDao().getAllCategories().first()
            layout.removeAllViews()

            // AKSI SIMPAN KATEGORI BARU (CREATE)
            btnAdd.setOnClickListener {
                val name = etName.text.toString().trim()
                val type = if (spinner.selectedItemPosition == 0) "EXPENSE" else "INCOME"
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        db.categoryDao().insertCategory(CategoryEntity(name = name, type = type, iconName = "ic_custom"))
                        etName.setText("")
                        refreshCategoryItems(db, layout, etName, spinner, btnAdd)
                        Toast.makeText(requireContext(), "Kategori '$name' berhasil disimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // LOOPING READ DATA KATEGORI UNTUK DITAMPILKAN SECARA INDAH
            categories.forEach { cat ->
                val itemCard = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 24, 20, 24)
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                    background.setTint(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
                }

                val tvInfo = TextView(requireContext()).apply {
                    text = "${cat.name} (${if (cat.type == "INCOME") "🟢 Pemasukan" else "🔴 Pengeluaran"})"
                    textSize = 14f
                    setTextColor(Color.parseColor("#4A5568"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                itemCard.addView(tvInfo)

                // KLIK ITEM UNTUK EDIT ATAU HAPUS (UPDATE & DELETE)
                itemCard.setOnClickListener {
                    showCrudActionDialog(cat, db, layout, etName, spinner, btnAdd)
                }

                layout.addView(itemCard)
            }
        }
    }

    private fun showCrudActionDialog(cat: CategoryEntity, db: AppDatabase, layout: LinearLayout, etName: EditText, spinner: Spinner, btnAdd: Button) {
        val context = requireContext()
        val options = arrayOf("✏️ Edit Nama Kategori", "🗑️ Hapus Kategori")
        
        AlertDialog.Builder(context).apply {
            setTitle("Aksi Kategori: ${cat.name}")
            setItems(options) { _, which ->
                if (which == 0) {
                    // AKSI EDIT (UPDATE)
                    val etEdit = EditText(context).apply { setText(cat.name) }
                    AlertDialog.Builder(context).apply {
                        setTitle("Ubah Nama Kategori")
                        setView(etEdit)
                        setPositiveButton("Simpan") { _, _ ->
                            val newName = etEdit.text.toString().trim()
                            if (newName.isNotEmpty()) {
                                lifecycleScope.launch {
                                    db.categoryDao().insertCategory(cat.copy(name = newName))
                                    refreshCategoryItems(db, layout, etName, spinner, btnAdd)
                                    Toast.makeText(context, "Kategori diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        setNegativeButton("Batal", null)
                        show()
                    }
                } else {
                    // AKSI HAPUS (DELETE)
                    lifecycleScope.launch {
                        db.categoryDao().deleteCategory(cat)
                        refreshCategoryItems(db, layout, etName, spinner, btnAdd)
                        Toast.makeText(context, "Kategori berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            show()
        }
    }

    private fun injectDefaultCategories(db: AppDatabase, layout: LinearLayout, etName: EditText, spinner: Spinner, btnAdd: Button) {
        lifecycleScope.launch {
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
            defaultCats.forEach { db.categoryDao().insertCategory(it) }
            refreshCategoryItems(db, layout, etName, spinner, btnAdd)
            Toast.makeText(requireContext(), "15 Kategori Master Berhasil Dimuat!", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // SUB-MENU 3: DIALOG TENTANG APLIKASI
    // ==========================================
    private fun showAboutAppDialog() {
        AlertDialog.Builder(requireContext()).apply {
            setTitle("ℹ️ Tentang Aplikasi")
            setMessage("Smart Finance Tracker bertenaga AI\nVersion 2.5-Premium Pro\n\n" +
                    "Aplikasi pengelolaan keuangan pribadi modern dengan integrasi database SQLite lokal yang aman.\n\n" +
                    "Didukung oleh mesin asisten pintar Groq Cloud API LLama 3.1 untuk interpretasi bahasa alami akuntansi yang bebas hambatan.")
            setPositiveButton("Tutup", null)
            show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
