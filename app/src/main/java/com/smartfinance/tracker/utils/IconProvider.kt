package com.smartfinance.tracker.utils

import com.smartfinance.tracker.R

/**
 * Model data sederhana untuk memegang nama ikon dan resource ID-nya.
 */
data class FinanceIcon(
    val iconName: String,
    val resId: Int
)

/**
 * Object Singleton untuk manajemen ikon secara terpusat.
 * Engine ini memetakan String dari Database ke Resource Drawable XML.
 */
object IconProvider {

    // 1. Kategori Ikon: Makanan & Minuman
    private val foodIcons = listOf(
        FinanceIcon("ic_food", R.drawable.ic_food)
        // Tambahkan ikon makanan lain di sini nanti (misal: ic_coffee, ic_restaurant)
    )

    // 2. Kategori Ikon: Transportasi
    private val transportIcons = listOf(
        FinanceIcon("ic_transport", R.drawable.ic_transport)
        // Tambahkan ikon transportasi lain di sini (misal: ic_car, ic_flight)
    )

    // 3. Kategori Ikon: Keuangan & Pendapatan
    private val financeIcons = listOf(
        FinanceIcon("ic_income", R.drawable.ic_income)
        // Tambahkan ikon keuangan lain (misal: ic_wallet, ic_investment)
    )

    // 4. Kategori Ikon: Umum / Default
    private val generalIcons = listOf(
        FinanceIcon("ic_custom", R.drawable.ic_income) // Fallback menggunakan ic_income sementara
    )

    /**
     * Mengembalikan daftar semua grup ikon untuk ditampilkan di IconPickerDialog (ViewPager / RecyclerView)
     * Format: Pair<Nama Grup, Daftar Ikon>
     */
    fun getAllIconGroups(): List<Pair<String, List<FinanceIcon>>> {
        return listOf(
            Pair("Keuangan", financeIcons),
            Pair("Makanan", foodIcons),
            Pair("Transportasi", transportIcons),
            Pair("Umum", generalIcons)
        )
    }

    /**
     * Mengubah string iconName dari Database menjadi Resource ID Drawable.
     * Jika tidak ditemukan, akan mengembalikan fallback icon.
     */
    fun getIconResource(iconName: String): Int {
        val allIcons = foodIcons + transportIcons + financeIcons + generalIcons
        
        // Cari ikon yang cocok, jika tidak ada, gunakan default
        return allIcons.find { it.iconName == iconName }?.resId ?: R.drawable.ic_income
    }
}
