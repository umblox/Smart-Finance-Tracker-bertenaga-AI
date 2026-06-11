package com.smartfinance.tracker.ui.transaction

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.smartfinance.tracker.ui.transaction.TransactionEditorDialog
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

class HistoryTransactionFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private var historyListenerRegistration: ListenerRegistration? = null

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val currentCalendar = Calendar.getInstance()
    private lateinit var tvMonthLabel: TextView
    private lateinit var transactionListContainer: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = FrameLayout(context).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        val mainContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#F8FAFC")) }

        val tvTitle = TextView(context).apply {
            text = "Riwayat Transaksi"
            textSize = 20f; setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD)); setTextColor(Color.parseColor("#1E293B"))
            setPadding((24 * density).toInt(), (24 * density).toInt(), (24 * density).toInt(), (14 * density).toInt())
        }
        mainContent.addView(tvTitle)

        // Navigasi Bulan Elegan Gaya Premium
        val navMonth = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (14 * density).toInt()) }
        val btnPrev = MaterialButton(context).apply { text = "◀"; cornerRadius = 10; backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")); setTextColor(Color.parseColor("#475569")); setOnClickListener { currentCalendar.add(Calendar.MONTH, -1); observeCloudHistoryLive() }; insetTop = 0; insetBottom = 0 }
        tvMonthLabel = TextView(context).apply { textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")); gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        val btnNext = MaterialButton(context).apply { text = "▶"; cornerRadius = 10; backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0")); setTextColor(Color.parseColor("#475569")); setOnClickListener { currentCalendar.add(Calendar.MONTH, 1); observeCloudHistoryLive() }; insetTop = 0; insetBottom = 0 }
        
        navMonth.addView(btnPrev); navMonth.addView(tvMonthLabel); navMonth.addView(btnNext)
        mainContent.addView(navMonth)

        val sv = ScrollView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) }
        transactionListContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (24 * density).toInt()) }
        sv.addView(transactionListContainer)
        mainContent.addView(sv)
        root.addView(mainContent)

        val fab = FloatingActionButton(context).apply {
            setImageResource(android.R.drawable.ic_input_add)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, (24 * density).toInt(), (24 * density).toInt())
            }
            layoutParams = lp
            setOnClickListener {
                TransactionManualDialog { observeCloudHistoryLive() }.show(parentFragmentManager, "ManualDialog")
            }
        }
        root.addView(fab)

        observeCloudHistoryLive()
        return root
    }

    private fun observeCloudHistoryLive() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val sdfLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
        tvMonthLabel.text = sdfLabel.format(currentCalendar.time).uppercase()

        transactionListContainer.removeAllViews()
        historyListenerRegistration?.remove()

        historyListenerRegistration = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                transactionListContainer.removeAllViews()
                val monthlyCloudList = ArrayList<HashMap<String, Any>>()

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    
                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    val isMonthMatch = txCal.get(Calendar.MONTH) == currentCalendar.get(Calendar.MONTH) && 
                                       txCal.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR)

                    if (isMonthMatch) {
                        val itemMap = HashMap<String, Any>().apply {
                            put("id", doc.id)
                            put("amount", (data["amount"] as? Number)?.toDouble() ?: 0.0)
                            put("type", (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT))
                            put("timestamp", timestamp)
                            put("categoryName", data["categoryName"] as? String ?: "Umum")
                            put("categoryId", (data["categoryId"] as? Number)?.toLong() ?: 0L)
                            put("note", data["note"] as? String ?: "Transaksi AI")
                            // 🔥 AMAN TOTAL: Sekarang field debtId ditarik sempurna dari Firestore dan ikut dilempar!
                            put("debtId", data["debtId"] as? String ?: "")
                        }
                        monthlyCloudList.add(itemMap)
                    }
                }

                if (monthlyCloudList.isEmpty()) {
                    transactionListContainer.addView(TextView(context).apply { 
                        text = "\nBelum ada rekam jejak keuangan pada bulan ini."
                        gravity = Gravity.CENTER; setTextColor(Color.parseColor("#94A3B8")); textSize = 13.5f
                        setTypeface(null, Typeface.ITALIC)
                    })
                    return@addSnapshotListener
                }

                monthlyCloudList.sortByDescending { (it["timestamp"] as? Long) ?: 0L }

                val groupMapCloud = LinkedHashMap<String, ArrayList<HashMap<String, Any>>>()
                val sdfDay = SimpleDateFormat("EEEE, dd MMM yyyy", Locale("id", "ID"))

                monthlyCloudList.forEach { item ->
                    val timestamp = item["timestamp"] as Long
                    val dayHeaderString = sdfDay.format(Date(timestamp))
                    if (!groupMapCloud.containsKey(dayHeaderString)) {
                        groupMapCloud[dayHeaderString] = ArrayList()
                    }
                    groupMapCloud[dayHeaderString]?.add(item)
                }

                groupMapCloud.forEach { (dateHeader, transactionsList) ->
                    val dateCard = MaterialCardView(context).apply {
                        radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
                        setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (14 * density).toInt() }
                    }
                    val cardLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) }
                    
                    cardLayout.addView(TextView(context).apply { text = dateHeader.uppercase(); textSize = 11f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#64748B")) })
                    cardLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt()).apply { topMargin = (10 * density).toInt(); bottomMargin = (4 * density).toInt() }; setBackgroundColor(Color.parseColor("#F1F5F9")) })

                    transactionsList.forEach { item ->
                        val itemRow = LinearLayout(context).apply { 
                            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                            
                            setOnClickListener { 
                                TransactionEditorDialog(item) {
                                    observeCloudHistoryLive()
                                }.show(parentFragmentManager, "EditorDialog") 
                            }
                        }
                        
                        val note = item["note"] as String
                        val categoryName = item["categoryName"] as String
                        val amount = item["amount"] as Double
                        val typeUpper = item["type"] as String

                        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                        left.addView(TextView(context).apply { text = note; setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); setTextColor(Color.parseColor("#1E293B")); textSize = 14.5f })
                        left.addView(TextView(context).apply { text = categoryName; textSize = 11.5f; setTextColor(Color.parseColor("#94A3B8")); setPadding(0, (2 * density).toInt(), 0, 0) })
                        
                        val isIncomeFlow = typeUpper == "INCOME" || typeUpper == "DEBT"
                        val rightText = if (isIncomeFlow) "+" else "-"
                        val rightColor = if (isIncomeFlow) "#0284C7" else "#EF4444"
                        
                        val tvAmt = TextView(context).apply { 
                            text = "$rightText ${formatRupiah.format(amount)}"
                            setTextColor(Color.parseColor(rightColor))
                            setTypeface(null, Typeface.BOLD); textSize = 14.5f 
                        }
                        
                        itemRow.addView(left); itemRow.addView(tvAmt)
                        cardLayout.addView(itemRow)
                    }
                    dateCard.addView(cardLayout)
                    transactionListContainer.addView(dateCard)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        historyListenerRegistration?.remove()
    }
}
