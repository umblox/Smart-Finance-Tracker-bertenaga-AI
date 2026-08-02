package com.smartfinance.tracker.utils

import com.smartfinance.tracker.R

data class FinanceIcon(val iconName: String, val resId: Int)

object IconProvider {

    // ========================================================
    // 6 KELOMPOK KHUSUS PEMASUKAN (INCOME) - Total 30 Ikon
    // ========================================================
    
    // 1. Gaji & Tunjangan Pribadi
    private val incomeSalaryIcons = listOf(
        FinanceIcon("ic_salary", R.drawable.ic_salary),
        FinanceIcon("ic_bonus", R.drawable.ic_bonus),
        FinanceIcon("ic_overtime", R.drawable.ic_overtime),
        FinanceIcon("ic_commission", R.drawable.ic_commission),
        FinanceIcon("ic_pension", R.drawable.ic_pension)
    )

    // 2. Usaha & Pekerjaan Lepas
    private val incomeBusinessIcons = listOf(
        FinanceIcon("ic_business", R.drawable.ic_business),
        FinanceIcon("ic_store", R.drawable.ic_store),
        FinanceIcon("ic_freelance", R.drawable.ic_freelance),
        FinanceIcon("ic_online_shop", R.drawable.ic_online_shop),
        FinanceIcon("ic_sell_used", R.drawable.ic_sell_used)
    )

    // 3. Investasi & Aset Lancar
    private val incomeInvestmentIcons = listOf(
        FinanceIcon("ic_investment", R.drawable.ic_investment),
        FinanceIcon("ic_crypto", R.drawable.ic_crypto),
        FinanceIcon("ic_real_estate", R.drawable.ic_real_estate),
        FinanceIcon("ic_dividend", R.drawable.ic_dividend),
        FinanceIcon("ic_passive_income", R.drawable.ic_passive_income)
    )

    // 4. Hadiah, Bantuan & Warisan
    private val incomeGiftIcons = listOf(
        FinanceIcon("ic_gift", R.drawable.ic_gift),
        FinanceIcon("ic_angpao", R.drawable.ic_angpao),
        FinanceIcon("ic_award", R.drawable.ic_award),
        FinanceIcon("ic_charity", R.drawable.ic_charity),
        FinanceIcon("ic_inheritance", R.drawable.ic_inheritance)
    )

    // 5. Klaim & Pengembalian Dana
    private val incomeRefundIcons = listOf(
        FinanceIcon("ic_refund", R.drawable.ic_refund),
        FinanceIcon("ic_cashback", R.drawable.ic_cashback),
        FinanceIcon("ic_insurance_claim", R.drawable.ic_insurance_claim),
        FinanceIcon("ic_tax_return", R.drawable.ic_tax_return),
        FinanceIcon("ic_debt_pay", R.drawable.ic_debt_pay) // Pembayaran piutang dari teman
    )

    // 6. Simpanan & Tunai Langsung
    private val incomeAssetIcons = listOf(
        FinanceIcon("ic_wallet", R.drawable.ic_wallet),
        FinanceIcon("ic_bank", R.drawable.ic_bank),
        FinanceIcon("ic_safe", R.drawable.ic_safe),
        FinanceIcon("ic_coins", R.drawable.ic_coins)
    )

    // ========================================================
    // KELOMPOK PENGELUARAN (EXPENSE) - Tidak Diubah Sesuai Instruksi
    // ========================================================
    private val foodIcons = listOf(
        FinanceIcon("ic_food", R.drawable.ic_food),
        FinanceIcon("ic_restaurant", R.drawable.ic_restaurant),
        FinanceIcon("ic_coffee", R.drawable.ic_coffee),
        FinanceIcon("ic_bar", R.drawable.ic_bar),
        FinanceIcon("ic_groceries", R.drawable.ic_groceries)
    )

    private val transportIcons = listOf(
        FinanceIcon("ic_car", R.drawable.ic_car),
        FinanceIcon("ic_motorcycle", R.drawable.ic_motorcycle),
        FinanceIcon("ic_transport", R.drawable.ic_transport),
        FinanceIcon("ic_gas", R.drawable.ic_gas),
        FinanceIcon("ic_tire", R.drawable.ic_tire),
        FinanceIcon("ic_service", R.drawable.ic_service),
        FinanceIcon("ic_flight", R.drawable.ic_flight),
        FinanceIcon("ic_train", R.drawable.ic_train)
    )

    private val homeIcons = listOf(
        FinanceIcon("ic_home", R.drawable.ic_home),
        FinanceIcon("ic_electricity", R.drawable.ic_electricity),
        FinanceIcon("ic_water", R.drawable.ic_water),
        FinanceIcon("ic_internet", R.drawable.ic_internet),
        FinanceIcon("ic_bills", R.drawable.ic_bills),
        FinanceIcon("ic_tax", R.drawable.ic_tax)
    )

    private val shoppingIcons = listOf(
        FinanceIcon("ic_shopping", R.drawable.ic_shopping),
        FinanceIcon("ic_shipping", R.drawable.ic_shipping),
        FinanceIcon("ic_clothing", R.drawable.ic_clothing),
        FinanceIcon("ic_laptop", R.drawable.ic_laptop),
        FinanceIcon("ic_phone", R.drawable.ic_phone)
    )

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

    private val eventIcons = listOf(
        FinanceIcon("ic_wedding", R.drawable.ic_wedding),
        FinanceIcon("ic_cake", R.drawable.ic_cake),
        FinanceIcon("ic_family", R.drawable.ic_family),
        FinanceIcon("ic_baby", R.drawable.ic_baby)
    )

    private val healthEduIcons = listOf(
        FinanceIcon("ic_hospital", R.drawable.ic_hospital),
        FinanceIcon("ic_pharmacy", R.drawable.ic_pharmacy),
        FinanceIcon("ic_education", R.drawable.ic_education),
        FinanceIcon("ic_book", R.drawable.ic_book)
    )

    private val travelIcons = listOf(
        FinanceIcon("ic_hotel", R.drawable.ic_hotel),
        FinanceIcon("ic_beach", R.drawable.ic_beach),
        FinanceIcon("ic_mountain", R.drawable.ic_mountain),
        FinanceIcon("ic_passport", R.drawable.ic_passport),
        FinanceIcon("ic_luggage", R.drawable.ic_luggage),
        FinanceIcon("ic_ticket", R.drawable.ic_ticket),
        FinanceIcon("ic_souvenir", R.drawable.ic_souvenir)
    )

    private val generalIcons = listOf(
        FinanceIcon("ic_custom", R.drawable.ic_wallet),
        FinanceIcon("ic_debt", R.drawable.ic_debt),
        FinanceIcon("ic_receivable", R.drawable.ic_receivable),
        FinanceIcon("ic_receivable_collect", R.drawable.ic_receivable_collect),
        FinanceIcon("ic_income", R.drawable.ic_income)
    )

    fun getAllIconGroups(): List<Pair<String, List<FinanceIcon>>> {
        return listOf(
            // Kelompok Pemasukan di Atas
            Pair("Gaji & Tunjangan", incomeSalaryIcons),
            Pair("Usaha & Jasa", incomeBusinessIcons),
            Pair("Investasi", incomeInvestmentIcons),
            Pair("Hadiah & Warisan", incomeGiftIcons),
            Pair("Klaim & Refund", incomeRefundIcons),
            Pair("Simpanan", incomeAssetIcons),
            
            // Kelompok Pengeluaran (Terjaga)
            Pair("Traveling", travelIcons),
            Pair("Makanan", foodIcons),
            Pair("Kendaraan", transportIcons),
            Pair("Rumah", homeIcons),
            Pair("Belanja", shoppingIcons),
            Pair("Gaya Hidup", lifestyleIcons),
            Pair("Acara", eventIcons),
            Pair("Kesehatan", healthEduIcons)
        )
    }

    fun getIconResource(iconName: String): Int {
        val allIcons = incomeSalaryIcons + incomeBusinessIcons + incomeInvestmentIcons + incomeGiftIcons + 
                       incomeRefundIcons + incomeAssetIcons + foodIcons + transportIcons + homeIcons + 
                       shoppingIcons + lifestyleIcons + eventIcons + healthEduIcons + travelIcons + generalIcons
        
        return allIcons.find { it.iconName == iconName }?.resId ?: R.drawable.ic_wallet
    }
}
