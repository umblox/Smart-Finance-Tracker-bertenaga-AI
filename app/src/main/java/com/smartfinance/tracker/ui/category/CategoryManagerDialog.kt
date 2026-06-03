package com.smartfinance.tracker.ui.category

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.CategoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryManagerDialog(private val context: Context, private val scope: CoroutineScope) {

    private val db = AppDatabase.getDatabase(context)

    fun show() {
        val scrollContainer = ScrollView(context)
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 30, 44, 40)
        }

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

        val listLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        rootLayout.addView(listLayout)
        scrollContainer.addView(rootLayout)

        val dialog = AlertDialog.Builder(context).apply {
            setTitle("🗂️ Kelola Kategori Sistem")
            setView(scrollContainer)
            setNeutralButton("Injeksi 15 Kategori Default") { _, _ ->
                injectDefaultCategories(listLayout, etName, spinner, btnAdd)
            }
            setPositiveButton("Selesai", null)
        }.create()

        refreshCategoryItems(listLayout, etName, spinner, btnAdd)
        dialog.show()
    }

    private fun refreshCategoryItems(layout: LinearLayout, etName: EditText, spinner: Spinner, btnAdd: Button) {
        scope.launch {
            val categories = withContext(Dispatchers.IO) { db.categoryDao().getAllCategories().first() }
            layout.removeAllViews()

            btnAdd.setOnClickListener {
                val name = etName.text.toString().trim()
                val type = if (spinner.selectedItemPosition == 0) "EXPENSE" else "INCOME"
                if (name.isNotEmpty()) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            db.categoryDao().insertCategory(CategoryEntity(name = name, type = type, iconName = "ic_custom"))
                        }
                        etName.setText("")
                        refreshCategoryItems(layout, etName, spinner, btnAdd)
                        Toast.makeText(context, "Kategori '$name' Tersimpan!", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            categories.forEach { cat ->
                val itemCard = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 24, 20, 24)
                    setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                    background.setTint(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 12 }
                }

                val tvInfo = TextView(context).apply {
                    text = "${cat.name} (${if (cat.type == "INCOME") "🟢 Pemasukan" else "🔴 Pengeluaran"})"
                    textSize = 14f
                    setTextColor(Color.parseColor("#4A5568"))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                itemCard.addView(tvInfo)

                itemCard.setOnClickListener {
                    showCrudActionDialog(cat, layout, etName, spinner, btnAdd)
                }
                layout.addView(itemCard)
            }
        }
    }

    private fun showCrudActionDialog(cat: CategoryEntity, layout: LinearLayout, etName: EditText, spinner: Spinner, btnAdd: Button) {
        val options = arrayOf("✏️ Edit Nama Kategori", "🗑️ Hapus Kategori")
        AlertDialog.Builder(context).apply {
            setTitle("Aksi Kategori: ${cat.name}")
            setItems(options) { _, which ->
                if (which == 0) {
                    val etEdit = EditText(context).apply { setText(cat.name) }
                    AlertDialog.Builder(context).apply {
                        setTitle("Ubah Nama Kategori")
                        setView(etEdit)
                        setPositiveButton("Simpan") { _, _ ->
                            val newName = etEdit.text.toString().trim()
                            if (newName.isNotEmpty()) {
                                scope.launch {
                                    withContext(Dispatchers.IO) { db.categoryDao().insertCategory(cat.copy(name = newName)) }
                                    refreshCategoryItems(layout, etName, spinner, btnAdd)
                                    Toast.makeText(context, "Kategori diperbarui!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        setNegativeButton("Batal", null)
                        show()
                    }
                } else {
                    scope.launch {
                        withContext(Dispatchers.IO) { db.categoryDao().deleteCategory(cat) }
                        refreshCategoryItems(layout, etName, spinner, btnAdd)
                        Toast.makeText(context, "Kategori berhasil dihapus!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            show()
        }
    }

    private fun injectDefaultCategories(layout: LinearLayout, etName: EditText, spinner: Spinner, btnAdd: Button) {
        scope.launch {
            val defaultCats = listOf(
                CategoryEntity(1, "Gaji & Pendapatan", "INCOME", "ic_income"),
                CategoryEntity(2, "Makanan & Minuman", "EXPENSE", "ic_food"),
                CategoryEntity(3, "Bahan Bakar & Transportasi", "EXPENSE", "ic_fuel"),
                CategoryEntity(4, "Tagihan & Utilitas", "EXPENSE", "ic_bill"),
                CategoryEntity(5, "Rokok & Hiburan Pribadi", "EXPENSE", "ic_smoke"),
                CategoryEntity(6, "Belanja Kebutuhan Rumah", "EXPENSE", "ic_home"),
                CategoryEntity(7, "Kesehatan & Medis", "EXPENSE", "ic_medical"),
                CategoryEntity(8, "Pendidikan & Buku", "EXPENSE", "ic_education"),
                CategoryEntity(9, "Pakaian & Gaya Lifestyle", "EXPENSE", "ic_fashion"),
                CategoryEntity(10, "Investasi & Tabungan", "EXPENSE", "ic_invest"),
                CategoryEntity(11, "Cicilan & Pinjaman", "EXPENSE", "ic_debt_pay"),
                CategoryEntity(12, "Hutang (Saya Meminjam)", "INCOME", "ic_debt_get"),
                CategoryEntity(13, "Piutang (Memberi Pinjaman)", "EXPENSE", "ic_receivable"),
                CategoryEntity(14, "Bonus & Hadiah", "INCOME", "ic_gift"),
                CategoryEntity(15, "Lain-lain / Umum", "EXPENSE", "ic_generic")
            )
            withContext(Dispatchers.IO) {
                defaultCats.forEach { db.categoryDao().insertCategory(it) }
            }
            refreshCategoryItems(layout, etName, spinner, btnAdd)
            Toast.makeText(context, "15 Kategori Master Berhasil Dimuat!", Toast.LENGTH_SHORT).show()
        }
    }
}

