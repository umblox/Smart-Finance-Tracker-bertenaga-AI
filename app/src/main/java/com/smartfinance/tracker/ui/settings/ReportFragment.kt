package com.smartfinance.tracker.ui.settings

import android.graphics.Color
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

class ReportFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(44, 44, 44, 44)
            setBackgroundColor(Color.parseColor("#F7FAFC"))
        }

        val tvTitle = TextView(context).apply {
            text = "📊 LAPORAN TRANSAKSI MENDALAM"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#2D3748"))
        }
        root.addView(tvTitle)

        val tvContent = TextView(context).apply {
            text = "\nMemuat data dari SQLite local..."
            textSize = 15f
            setTextColor(Color.parseColor("#4A5568"))
        }
        root.addView(tvContent)

        // BACA DATABASE SECARA REAL-TIME
        lifecycleScope.launch {
            val db = AppDatabase.getDatabase(context)
            val transactions = db.transactionDao().getAllTransactions().first()
            val debts = db.debtDao().getAllDebts().first()

            var incomeTotal = 0.0
            var expenseTotal = 0.0
            var sisaPiutangBelumLunas = 0.0

            transactions.forEach { 
                if (it.type == "INCOME") incomeTotal += it.amount else expenseTotal += it.amount
            }

            debts.filter { !it.isPaid && it.type == "RECEIVABLE" }.forEach {
                sisaPiutangBelumLunas += it.remainingAmount
            }

            val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
            val sb = StringBuilder("\n")
            sb.append("▪️ Total Seluruh Pemasukan: ${formatRupiah.format(incomeTotal)}\n\n")
            sb.append("▪️ Total Seluruh Pengeluaran: ${formatRupiah.format(expenseTotal)}\n\n")
            sb.append("▪️ Sisa Bersih Dana Dompet: ${formatRupiah.format(incomeTotal - expenseTotal)}\n\n")
            sb.append("-------------------------------------------\n\n")
            sb.append("💰 STATUS HUKUM PIUTANG KELUAR:\n\n")
            if (sisaPiutangBelumLunas > 0.0) {
                sb.append("- Dana Anda di luar belum lunas: ${formatRupiah.format(sisaPiutangBelumLunas)}\n")
            } else {
                sb.append("- Bersih! Anda tidak mempunyai piutang aktif di luar.\n")
            }

            tvContent.text = sb.toString()
        }

        return root
    }
}
