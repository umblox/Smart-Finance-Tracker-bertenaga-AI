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
        // Membuat layout container dinamis via kode agar tidak memicu error XML "not found"
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            padding = 16
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val tvTitle = TextView(requireContext()).apply {
            text = "🤝 DAFTAR HISTORY HUTANG & PIUTANG"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 20)
        }
        root.addView(tvTitle)

        val tvContent = TextView(requireContext()).apply {
            text = "Memuat data pinjaman..."
            textSize = 14f
        }
        root.addView(tvContent)

        // Muat Riwayat Data Hutang Langsung dari SQLite Room Database
        val db = AppDatabase.getDatabase(requireContext())
        lifecycleScope.launch {
            val debts = db.debtDao().getAllDebts().first()
            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            
            if (debts.isEmpty()) {
                tvContent.text = "Tidak ada riwayat hutang atau piutang yang tercatat saat ini."
            } else {
                val builder = StringBuilder()
                debts.forEach { debt ->
                    val jenis = if (debt.type == "DEBT") "⚠️ Hutang ke" else "💰 Piutang di"
                    val status = if (debt.isPaid) "[LUNAS]" else "[BELUM LUNAS]"
                    builder.append("$jenis ${debt.contactName}\n Nominal: ${formatRupiah.format(debt.amount)}\n Sisa: ${formatRupiah.format(debt.remainingAmount)} $status\n Keterangan: ${debt.note}\n-----------------------------------\n")
                }
                tvContent.text = builder.toString()
            }
        }

        return root
    }
}
        _binding = null
    }
}

