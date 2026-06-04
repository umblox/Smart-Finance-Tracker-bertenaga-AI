package com.smartfinance.tracker.ui.report

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class ReportFragment : Fragment() {

    private lateinit var db: AppDatabase
    private lateinit var listContainer: LinearLayout
    private lateinit var tvReportIncome: TextView
    private lateinit var tvReportExpense: TextView
    private lateinit var tvReportNet: TextView
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)
        val density = context.resources.displayMetrics.density

        val root = RelativeLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.parseColor("#F7FAFC"))
        }

        val nsv = NestedScrollView(context).apply {
            isFillViewport = true
            layoutParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT)
        }

        val mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        mainLayout.addView(TextView(context).apply {
            text = "Laporan Keuangan"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A202C"))
            setPadding(0, 0, 0, (16 * density).toInt())
        })

        val cardSummary = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (20 * density).toInt() }
        }

        val summaryInside = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }

        tvReportIncome = TextView(context).apply { text = "Pemasukan: Rp 0"; setTextColor(Color.parseColor("#2B6CB0")); textSize = 15f; setPadding(0, 0, 0, (4 * density).toInt()) }
        tvReportExpense = TextView(context).apply { text = "Pengeluaran: Rp 0"; setTextColor(Color.parseColor("#E53E3E")); textSize = 15f; setPadding(0, 0, 0, (8 * density).toInt()) }
        tvReportNet = TextView(context).apply { text = "Selisih Bersih: Rp 0"; setTextColor(Color.parseColor("#2D3748")); textSize = 16f; setTypeface(null, Typeface.BOLD) }

        summaryInside.addView(tvReportIncome)
        summaryInside.addView(tvReportExpense)
        summaryInside.addView(View(context).apply { setBackgroundColor(Color.parseColor("#E2E8F0")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { bottomMargin = (8 * density).toInt() } })
        summaryInside.addView(tvReportNet)
        cardSummary.addView(summaryInside)
        mainLayout.addView(cardSummary)

        mainLayout.addView(TextView(context).apply {
            text = "Semua Riwayat Transaksi"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#718096"))
            setPadding(0, 0, 0, (8 * density).toInt())
        })

        val cardList = MaterialCardView(context).apply {
            radius = 12 * density; cardElevation = 0f
            strokeWidth = (1 * density).toInt(); setStrokeColor(android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")))
            setCardBackgroundColor(Color.WHITE)
        }

        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt(), (8 * density).toInt())
        }
        cardList.addView(listContainer)
        mainLayout.addView(cardList)

        nsv.addView(mainLayout)
        root.addView(nsv)

        loadReportData()
        return root
    }

    private fun loadReportData() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        lifecycleScope.launch {
            val allTx = db.transactionDao().getAllTransactions().first()
            val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID"))

            var incomeSum = 0.0
            var expenseSum = 0.0

            allTx.forEach { tx ->
                if (tx.type.trim().uppercase() == "INCOME") incomeSum += tx.amount
                else if (tx.type.trim().uppercase() == "EXPENSE") expenseSum += tx.amount
            }

            tvReportIncome.text = "Pemasukan: ${formatRupiah.format(incomeSum)}"
            tvReportExpense.text = "Pengeluaran: ${formatRupiah.format(expenseSum)}"
            tvReportNet.text = "Selisih Bersih: ${formatRupiah.format(incomeSum - expenseSum)}"

            listContainer.removeAllViews()

            // PERBAIKAN MUTLAK LAPORAN: Urutkan data terbaru di paling atas agar tidak terbalik
            val sortedList = allTx.sortedByDescending { it.timestamp }

            if (sortedList.isEmpty()) {
                listContainer.addView(TextView(context).apply {
                    text = "Belum ada transaksi."
                    textSize = 14f
                    gravity = Gravity.CENTER
                    setPadding(0, (20 * density).toInt(), 0, (20 * density).toInt())
                })
            } else {
                sortedList.forEachIndexed { index, item ->
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        setPadding((8 * density).toInt(), (12 * density).toInt(), (8 * density).toInt(), (12 * density).toInt())
                    }

                    val iconCircle = FrameLayout(context).apply {
                        layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                        background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#EDF2F7")) }
                        val txt = TextView(context).apply { text = if (item.type == "INCOME") "📥" else "💸"; textSize = 16f; gravity = Gravity.CENTER }
                        addView(txt)
                    }
                    row.addView(iconCircle)

                    val center = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    center.addView(TextView(context).apply { text = item.note; setTextColor(Color.parseColor("#2D3748")); setTypeface(null, Typeface.BOLD); textSize = 14f })
                    center.addView(TextView(context).apply { text = "${item.categoryName} • ${sdf.format(Date(item.timestamp))}"; setTextColor(Color.parseColor("#A0AEC0")); textSize = 11f })
                    row.addView(center)

                    val isInc = item.type.trim().uppercase() == "INCOME"
                    row.addView(TextView(context).apply {
                        text = (if (isInc) "+" else "-") + formatRupiah.format(item.amount)
                        setTextColor(Color.parseColor(if (isInc) "#2B6CB0" else "#E53E3E"))
                        setTypeface(null, Typeface.BOLD)
                        textSize = 14f
                    })

                    listContainer.addView(row)

                    if (index < sortedList.size - 1) {
                        listContainer.addView(View(context).apply {
                            setBackgroundColor(Color.parseColor("#EDF2F7"))
                            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { leftMargin = (50 * density).toInt() }
                        })
                    }
                }
            }
        }
    }
}
