package com.smartfinance.tracker.ui.report

import android.content.Context
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
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import com.smartfinance.tracker.utils.FirebaseManager

class DetailCategoryReportFragment : Fragment() {

    private val firestore = FirebaseManager.getFirestore()
    private var txListener: ListenerRegistration? = null
    
    private lateinit var tvMonthLabel: TextView
    private lateinit var containerHierarchy: LinearLayout
    
    private val currentCalendar = Calendar.getInstance()
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfMonth = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    private val sdfDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private var allTransactions = ArrayList<HashMap<String, Any>>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC"))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // TOP BAR NAVIGASI BULANAN PREMIUM (◀ MMMM yyyy ▶)
        val topNavContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            setBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val btnBack = TextView(context).apply {
            text = "✕"; textSize = 18f; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, (24 * density).toInt(), 0)
            setOnClickListener { parentFragmentManager.popBackStack() }
        }
        topNavContainer.addView(btnBack)

        val btnPrev = TextView(context).apply {
            text = "◀"; textSize = 16f; setTextColor(Color.parseColor("#0D9488"))
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            setOnClickListener { 
                currentCalendar.add(Calendar.MONTH, -1)
                updatePeriodAndLoadData()
            }
        }
        topNavContainer.addView(btnPrev)

        tvMonthLabel = TextView(context).apply {
            text = sdfMonth.format(currentCalendar.time).uppercase(Locale.ROOT)
            textSize = 14f; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topNavContainer.addView(tvMonthLabel)

        val btnNext = TextView(context).apply {
            text = "▶"; textSize = 16f; setTextColor(Color.parseColor("#0D9488"))
            setPadding((12 * density).toInt(), (6 * density).toInt(), (12 * density).toInt(), (6 * density).toInt())
            setOnClickListener { 
                currentCalendar.add(Calendar.MONTH, 1)
                updatePeriodAndLoadData()
            }
        }
        topNavContainer.addView(btnNext)
        root.addView(topNavContainer)

        // SCROLL VIEW CONTAINER DATA
        val nsv = NestedScrollView(context).apply { isFillViewport = true }
        containerHierarchy = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
        }
        nsv.addView(containerHierarchy)
        root.addView(nsv)

        updatePeriodAndLoadData()
        return root
    }

    private fun updatePeriodAndLoadData() {
        tvMonthLabel.text = sdfMonth.format(currentCalendar.time).uppercase(Locale.ROOT)
        
        // Simpan preferensi bulan aktif ke global SharedPreferences agar Dashboard ikut sinkron bergeser
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("active_report_time", currentCalendar.timeInMillis).apply()

        observeTransactionsFromCloud()
    }

    private fun observeTransactionsFromCloud() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        txListener?.remove()
        txListener = firestore.collection("transactions")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                allTransactions.clear()
                val targetMonth = currentCalendar.get(Calendar.MONTH)
                val targetYear = currentCalendar.get(Calendar.YEAR)

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val amount = (data["amount"] as? Number)?.toDouble() ?: 0.0
                    val type = (data["type"] as? String ?: "EXPENSE").trim().uppercase(Locale.ROOT)
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()
                    val categoryName = data["categoryName"] as? String ?: "Lain-lain / Umum"
                    val note = data["note"] as? String ?: ""

                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    if (cal.get(Calendar.MONTH) == targetMonth && cal.get(Calendar.YEAR) == targetYear) {
                        if (type == "EXPENSE" || type == "RECEIVABLE") {
                            val map = HashMap<String, Any>().apply {
                                put("amount", amount)
                                put("categoryName", categoryName)
                                put("note", note)
                                put("timestamp", timestamp)
                            }
                            allTransactions.add(map)
                        }
                    }
                }
                renderExpandableCategoryHierarchy()
            }
    }

    private fun renderExpandableCategoryHierarchy() {
        containerHierarchy.removeAllViews()
        val context = context ?: return
        val density = context.resources.displayMetrics.density

        if (allTransactions.isEmpty()) {
            containerHierarchy.addView(TextView(context).apply {
                text = "Tidak ada catatan pengeluaran pada bulan ini."
                textSize = 14f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                setPadding(0, (40 * density).toInt(), 0, 0)
            })
            return
        }

        val totalMonthExpense = allTransactions.sumOf { (it["amount"] as? Double) ?: 0.0 }
        val grouped = allTransactions.groupBy { (it["categoryName"] as? String) ?: "Lain-lain / Umum" }

        grouped.forEach { (categoryName, txList) ->
            val totalCategoryAmount = txList.sumOf { (it["amount"] as? Double) ?: 0.0 }
            val percentage = if (totalMonthExpense > 0) ((totalCategoryAmount / totalMonthExpense) * 100).toInt() else 0

            val card = MaterialCardView(context).apply {
                radius = 14 * density; cardElevation = 1f * density; strokeWidth = 0
                setCardBackgroundColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (12 * density).toInt() }
            }

            val masterLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            
            // BARIS INDUK KATEGORI
            val rowHeader = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
                setBackgroundColor(Color.parseColor("#FFFFFF"))
            }

            val iconFrame = FrameLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Color.parseColor("#F1F5F9")) }
                addView(TextView(context).apply { text = "📁"; textSize = 15f; gravity = Gravity.CENTER })
            }
            rowHeader.addView(iconFrame)

            val infoLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
            infoLayout.addView(TextView(context).apply { text = categoryName; setTextColor(Color.parseColor("#1E293B")); setTypeface(null, Typeface.BOLD); textSize = 14.5f })
            infoLayout.addView(TextView(context).apply { text = "${txList.size} Transaksi • ${formatRupiah.format(totalCategoryAmount)}"; setTextColor(Color.parseColor("#64748B")); textSize = 11.5f; setPadding(0, 2, 0, 0) })
            rowHeader.addView(infoLayout)

            val tvPercent = TextView(context).apply { text = "$percentage%"; setTextColor(Color.parseColor("#F43F5E")); setTypeface(null, Typeface.BOLD); textSize = 14.5f }
            rowHeader.addView(tvPercent)
            masterLayout.addView(rowHeader)

            // CONTAINER ANAK (LIST TRANSAKSI TERSEMBUNYI)
            val childContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (12 * density).toInt())
                setBackgroundColor(Color.parseColor("#FAFAFA"))
            }

            // Urutkan transaksi dari menit/jam paling baru
            val sortedTxList = txList.sortedByDescending { (it["timestamp"] as? Long) ?: 0L }
            sortedTxList.forEach { tx ->
                val txRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, (10 * density).toInt(), 0, (10 * density).toInt())
                }

                val txInfo = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                txInfo.addView(TextView(context).apply { text = (tx["note"] as? String ?: "Transaksi AI").ifEmpty { "Tanpa Catatan" }; setTextColor(Color.parseColor("#334155")); textSize = 13.5f })
                txInfo.addView(TextView(context).apply { text = sdfDateTime.format(Date(tx["timestamp"] as Long)); setTextColor(Color.parseColor("#94A3B8")); textSize = 10.5f; setPadding(0, 2, 0, 0) })
                txRow.addView(txInfo)

                val tvAmt = TextView(context).apply { text = "-" + formatRupiah.format(tx["amount"] as Double); setTextColor(Color.parseColor("#64748B")); textSize = 13.5f }
                txRow.addView(tvAmt)

                childContainer.addView(txRow)
                childContainer.addView(View(context).apply { setBackgroundColor(Color.parseColor("#F1F5F9")); layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (0.5f * density).toInt()) })
            }
            masterLayout.addView(childContainer)

            // LOGIKA ANIMASI EXPAND DROPDOWN KETIKA DI-KLIK
            rowHeader.setOnClickListener {
                if (childContainer.visibility == View.GONE) {
                    childContainer.visibility = View.VISIBLE
                    iconFrame.addView(View(context).apply { tag = "expanded" }) // Tanda bendera perluasan
                } else {
                    childContainer.visibility = View.GONE
                }
            }

            card.addView(masterLayout)
            containerHierarchy.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        txListener?.remove()
    }
}
