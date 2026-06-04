package com.smartfinance.tracker.ui.transaction

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.smartfinance.tracker.data.local.AppDatabase
import com.smartfinance.tracker.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class HistoryTransactionFragment : Fragment() {

    private lateinit var db: AppDatabase
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("in", "ID"))
    private val currentCalendar = Calendar.getInstance()
    private lateinit var tvMonthLabel: TextView
    private lateinit var transactionListContainer: LinearLayout

    override fun onCreateView(
        inflater: android.view.LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        db = AppDatabase.getDatabase(context)

        // Gunakan FrameLayout sebagai root agar FAB bisa melayang di atas list
        val root = FrameLayout(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }

        val mainContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F7FAFC")) }

        val tvTitle = TextView(context).apply {
            text = "📜 RIWAYAT BUKU TRANSAKSI"
            textSize = 18f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748"))
            setPadding(48, 48, 48, 16)
        }
        mainContent.addView(tvTitle)

        val navMonth = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(48, 0, 48, 24) }
        val btnPrev = Button(context).apply { text = "◀"; backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0")); setTextColor(Color.parseColor("#4A5568")); setOnClickListener { currentCalendar.add(Calendar.MONTH, -1); refreshHistoryEngine() } }
        tvMonthLabel = TextView(context).apply { textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnNext = Button(context).apply { text = "▶"; backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E0")); setTextColor(Color.parseColor("#4A5568")); setOnClickListener { currentCalendar.add(Calendar.MONTH, 1); refreshHistoryEngine() } }
        
        navMonth.addView(btnPrev); navMonth.addView(tvMonthLabel); navMonth.addView(btnNext)
        mainContent.addView(navMonth)

        val sv = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        transactionListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 0, 48, 48) }
        sv.addView(transactionListContainer)
        mainContent.addView(sv)
        root.addView(mainContent)

        // Floating Action Button (+) di pojok kanan bawah
        val fab = FloatingActionButton(context).apply {
            setImageResource(android.R.drawable.ic_input_add)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#008080"))
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, 64, 64)
            }
            layoutParams = lp
            setOnClickListener {
                TransactionManualDialog { refreshHistoryEngine() }.show(parentFragmentManager, "ManualDialog")
            }
        }
        root.addView(fab)

        refreshHistoryEngine()
        return root
    }

    private fun refreshHistoryEngine() {
        val sdfLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        tvMonthLabel.text = sdfLabel.format(currentCalendar.time).uppercase()

        transactionListContainer.removeAllViews()
        val context = requireContext()

        lifecycleScope.launch {
            val allTx = db.transactionDao().getAllTransactions().first()
            val monthlyTx = allTx.filter { tx ->
                val c = Calendar.getInstance().apply { timeInMillis = tx.timestamp }
                c.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) && c.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)
            }.sortedByDescending { it.timestamp }

            if (monthlyTx.isEmpty()) {
                transactionListContainer.addView(TextView(context).apply { text = "\nBelum ada rekam jejak keuangan."; gravity = Gravity.CENTER; setTextColor(Color.GRAY) })
                return@launch
            }

            val groupMap = LinkedHashMap<String, ArrayList<TransactionEntity>>()
            val sdfDay = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))

            monthlyTx.forEach { tx ->
                val dayString = sdfDay.format(Date(tx.timestamp))
                if (!groupMap.containsKey(dayString)) groupMap[dayString] = ArrayList()
                groupMap[dayString]?.add(tx)
            }

            groupMap.forEach { (dateHeader, transactions) ->
                val dateCard = MaterialCardView(context).apply {
                    radius = 24f; cardElevation = 3f; setCardBackgroundColor(Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 24 }
                }
                val cardLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 24, 28, 24) }
                
                cardLayout.addView(TextView(context).apply { text = dateHeader.uppercase(); textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#4A5568")) })
                cardLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2).apply { topMargin = 12; bottomMargin = 12 }; setBackgroundColor(Color.parseColor("#EDF2F7")) })

                transactions.forEach { item ->
                    val itemRow = LinearLayout(context).apply { 
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, 12, 0, 12)
                        setOnClickListener { TransactionEditorDialog(item) { refreshHistoryEngine() }.show(parentFragmentManager, "EditorDialog") }
                    }
                    val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    left.addView(TextView(context).apply { text = item.note; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#2D3748")); textSize = 14f })
                    left.addView(TextView(context).apply { text = item.categoryName; textSize = 11f; setTextColor(Color.GRAY) })
                    
                    val rightText = if (item.type == "INCOME") "+" else "-"
                    val rightColor = if (item.type == "INCOME") "#2F855A" else "#C53030"
                    val tvAmt = TextView(context).apply { text = "$rightText " + formatRupiah.format(item.amount); setTextColor(Color.parseColor(rightColor)); setTypeface(null, Typeface.BOLD); textSize = 14f }
                    
                    itemRow.addView(left); itemRow.addView(tvAmt)
                    cardLayout.addView(itemRow)
                }
                dateCard.addView(cardLayout)
                transactionListContainer.addView(dateCard)
            }
        }
    }
}
