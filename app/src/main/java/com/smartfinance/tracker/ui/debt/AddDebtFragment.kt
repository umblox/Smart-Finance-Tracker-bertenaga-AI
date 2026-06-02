package com.smartfinance.tracker.ui.debt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.data.local.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

class AddDebtFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        val tvTitle = TextView(requireContext()).apply {
            text = "🤝 DAFTAR HISTORY HUTANG & PIUTANG"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        root.addView(tvTitle)

        val tvContent = TextView(requireContext()).apply {
            text = "Memuat data pinjaman..."
            textSize = 14f
        }
        root.addView(tvContent)

        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            val debts = db.debtDao().getAllDebts().first()
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            
            if (debts.isEmpty()) {
                tvContent.text = "\nTidak ada riwayat hutang atau piutang yang tercatat."
            } else {
                val builder = StringBuilder("\n")
                debts.forEach { debt ->
                    val jenis = if (debt.type == "DEBT") "⚠️ Hutang ke" else "💰 Piutang di"
                    val status = if (debt.isPaid) "[LUNAS]" else "[BELUM LUNAS]"
                    builder.append("$jenis ${debt.contactName}\nNominal: ${formatRupiah.format(debt.amount)}\nSisa: ${formatRupiah.format(debt.remainingAmount)} $status\nKeterangan: ${debt.note}\n---------------------------\n")
                }
                tvContent.text = builder.toString()
            }
        }

        return root
    }
}
