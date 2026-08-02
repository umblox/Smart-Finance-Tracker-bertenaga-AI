package com.smartfinance.tracker.utils

import com.smartfinance.tracker.R

data class FinanceIcon(
    val iconName: String,
    val resId: Int
)

object IconProvider {

    // 1. Kategori Baru: Traveling & Destinasi
    private val travelIcons = listOf(
        FinanceIcon("ic_flight", R.drawable.ic_flight),
        FinanceIcon("ic_train", R.drawable.ic_train),
        FinanceIcon("ic_hotel", R.drawable.ic_hotel),
        FinanceIcon("ic_beach", R.drawable.ic_beach),
        FinanceIcon("ic_mountain", R.drawable.ic_mountain),
        FinanceIcon("ic_passport", R.drawable.ic_passport),
        FinanceIcon("ic_luggage", R.drawable.ic_luggage),
        FinanceIcon("ic_ticket", R.drawable.ic_ticket),
        FinanceIcon("ic_souvenir", R.drawable.ic_souvenir)
    )

    // 2. Kategori: Makanan & Nongkrong
    private val foodIcons = listOf(
        FinanceIcon("ic_food", R.drawable.ic_food),
        FinanceIcon("ic_restaurant", R.drawable.ic_restaurant),
        FinanceIcon("ic_coffee", R.drawable.ic_coffee),
        FinanceIcon("ic_bar", R.drawable.ic_bar),
        FinanceIcon("ic_groceries", R.drawable.ic_groceries)
    )

    // 3. Kategori: Transportasi & Otomotif
    private val transportIcons = listOf(
        FinanceIcon("ic_car", R.drawable.ic_car),
        FinanceIcon("ic_motorcycle", R.drawable.ic_motorcycle),
        FinanceIcon("ic_transport", R.drawable.ic_transport),
        FinanceIcon("ic_gas", R.drawable.ic_gas),
        FinanceIcon("ic_tire", R.drawable.ic_tire),
        FinanceIcon("ic_service", R.drawable.ic_service)
    )

    // 4. Kategori: Rumah & Tagihan
    private val homeIcons = listOf(
        FinanceIcon("ic_home", R.drawable.ic_home),
        FinanceIcon("ic_electricity", R.drawable.ic_electricity),
        FinanceIcon("ic_water", R.drawable.ic_water),
        FinanceIcon("ic_internet", R.drawable.ic_internet),
        FinanceIcon("ic_bills", R.drawable.ic_bills),
        FinanceIcon("ic_tax", R.drawable.ic_tax)
    )

    // 5. Kategori: Belanja & Online
    private val shoppingIcons = listOf(
        FinanceIcon("ic_shopping", R.drawable.ic_shopping),
        FinanceIcon("ic_shipping", R.drawable.ic_shipping),
        FinanceIcon("ic_clothing", R.drawable.ic_clothing),
        FinanceIcon("ic_laptop", R.drawable.ic_laptop),
        FinanceIcon("ic_phone", R.drawable.ic_phone)
    )

    // 6. Kategori: Gaya Hidup & Hobi
    private val lifestyleIcons = listOf(
        FinanceIcon("ic_movie", R.drawable.ic_movie),
        FinanceIcon("ic_game", R.drawable.ic_game),
        FinanceIcon("ic_music", R.drawable.ic_music),
        FinanceIcon("ic_sports", R.drawable.ic_sports),
        FinanceIcon("ic_gym", R.drawable.ic_gym),
        FinanceIcon("ic_camera", R.drawable.ic_camera),
        FinanceIcon("ic_smoking", R.drawable.ic_smoking),
        FinanceIcon("ic_cosmetics", R.drawable.ic_cosmetics),
        FinanceIcon("ic_pet", R.drawable.ic_pet)
    )

    // 7. Kategori: Acara & Sosial
    private val eventIcons = listOf(
        FinanceIcon("ic_wedding", R.drawable.ic_wedding),
        FinanceIcon("ic_cake", R.drawable.ic_cake),
        FinanceIcon("ic_gift", R.drawable.ic_gift),
        FinanceIcon("ic_family", R.drawable.ic_family),
        FinanceIcon("ic_baby", R.drawable.ic_baby),
        FinanceIcon("ic_charity", R.drawable.ic_charity)
    )

    // 8. Kategori: Kesehatan & Edukasi
    private val healthEduIcons = listOf(
        FinanceIcon("ic_hospital", R.drawable.ic_hospital),
        FinanceIcon("ic_pharmacy", R.drawable.ic_pharmacy),
        FinanceIcon("ic_education", R.drawable.ic_education),
        FinanceIcon("ic_book", R.drawable.ic_book)
    )

    // 9. Kategori: Keuangan & Pendapatan
    private val financeIcons = listOf(
        FinanceIcon("ic_salary", R.drawable.ic_salary),
        FinanceIcon("ic_business", R.drawable.ic_business),
        FinanceIcon("ic_investment", R.drawable.ic_investment),
        FinanceIcon("ic_bank", R.drawable.ic_bank),
        FinanceIcon("ic_card", R.drawable.ic_card),
        FinanceIcon("ic_wallet", R.drawable.ic_wallet),
        FinanceIcon("ic_income", R.drawable.ic_income)
    )

    private val generalIcons = listOf(
        FinanceIcon("ic_custom", R.drawable.ic_wallet)
    )

    fun getAllIconGroups(): List<Pair<String, List<FinanceIcon>>> {
        return listOf(
            Pair("Traveling", travelIcons), // <-- TAB PENTING TERBARU
            Pair("Makanan", foodIcons),
            Pair("Transport", transportIcons),
            Pair("Rumah", homeIcons),
            Pair("Belanja", shoppingIcons),
            Pair("Gaya Hidup", lifestyleIcons),
            Pair("Acara", eventIcons),
            Pair("Kesehatan", healthEduIcons),
            Pair("Keuangan", financeIcons)
        )
    }

    fun getIconResource(iconName: String): Int {
        val allIcons = travelIcons + foodIcons + transportIcons + homeIcons + shoppingIcons + 
                       lifestyleIcons + eventIcons + healthEduIcons + financeIcons + generalIcons
        
        return allIcons.find { it.iconName == iconName }?.resId ?: R.drawable.ic_wallet
    }
}
