package com.smartfinance.tracker.utils

import com.smartfinance.tracker.R

data class FinanceIcon(
    val iconName: String,
    val resId: Int
)

object IconProvider {

    // 1. Kategori: Pendapatan & Keuangan
    private val financeIcons = listOf(
        FinanceIcon("ic_salary", R.drawable.ic_salary),
        FinanceIcon("ic_business", R.drawable.ic_business),
        FinanceIcon("ic_investment", R.drawable.ic_investment),
        FinanceIcon("ic_income", R.drawable.ic_income),
        FinanceIcon("ic_wallet", R.drawable.ic_wallet)
    )

    // 2. Kategori: Makanan & Minuman
    private val foodIcons = listOf(
        FinanceIcon("ic_food", R.drawable.ic_food),
        FinanceIcon("ic_groceries", R.drawable.ic_groceries)
    )

    // 3. Kategori: Kebutuhan Rumah & Transportasi
    private val homeTransportIcons = listOf(
        FinanceIcon("ic_home", R.drawable.ic_home),
        FinanceIcon("ic_utilities", R.drawable.ic_utilities),
        FinanceIcon("ic_transport", R.drawable.ic_transport)
    )

    // 4. Kategori: Belanja & Pribadi
    private val shoppingIcons = listOf(
        FinanceIcon("ic_shopping", R.drawable.ic_shopping),
        FinanceIcon("ic_clothing", R.drawable.ic_clothing),
        FinanceIcon("ic_charity", R.drawable.ic_charity) // Bisa untuk donasi & perawatan diri (Heart)
    )

    // 5. Kategori: Hiburan & Lain-lain
    private val leisureIcons = listOf(
        FinanceIcon("ic_movie", R.drawable.ic_movie),
        FinanceIcon("ic_pet", R.drawable.ic_pet),
        FinanceIcon("ic_education", R.drawable.ic_education),
        FinanceIcon("ic_bills", R.drawable.ic_bills)
    )

    // 6. Default / Sistem
    private val generalIcons = listOf(
        FinanceIcon("ic_custom", R.drawable.ic_wallet)
    )

    fun getAllIconGroups(): List<Pair<String, List<FinanceIcon>>> {
        return listOf(
            Pair("Keuangan", financeIcons),
            Pair("Makanan", foodIcons),
            Pair("Rumah & Jalan", homeTransportIcons),
            Pair("Belanja", shoppingIcons),
            Pair("Hiburan", leisureIcons)
        )
    }

    fun getIconResource(iconName: String): Int {
        val allIcons = financeIcons + foodIcons + homeTransportIcons + shoppingIcons + leisureIcons + generalIcons
        return allIcons.find { it.iconName == iconName }?.resId ?: R.drawable.ic_wallet
    }
}
