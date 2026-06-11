package com.smartfinance.tracker.ui.debt

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import com.smartfinance.tracker.R
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class AddDebtFragment : Fragment() {

    private val firestore = FirebaseFirestore.getInstance()
    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    
    // ✅ FORMAT PREMIUM TERPADU: Format penanggalan luxury untuk tampilan Buku Utang
    private val sdfDisplayPremium = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))
    private val sdfMonthLabel = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))
    
    private var currentTab = "DEBT"
    private var debtListenerRegistration: ListenerRegistration? = null
    private val currentCalendar = Calendar.getInstance()

    private lateinit var tvMonthLabel: TextView
    private lateinit var listContainer: LinearLayout
    private lateinit var cardDebt: MaterialCardView
    private lateinit var cardReceivable: MaterialCardView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        val root = FrameLayout(context).apply { 
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) 
        }
        
        val mainContent = LinearLayout(context).apply { 
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F8FAFC")) 
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // 1. HEADER BAR VISUAL LUXURY
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((20 * density).toInt(), (24 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(context).apply {
            text = "🤝 Buku Utang Piutang"
            textSize = 20f
            setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD))
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(tvTitle)
        mainContent.addView(headerLayout)

        // 2. TIMELINE NAVIGASI BULANAN PREMIUM
        val navMonthRow = LinearLayout(context).apply { 
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (14 * density).toInt()) 
        }
        val btnPrev = MaterialButton(context).apply { 
            text = "◀"; cornerRadius = 10
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            setTextColor(Color.parseColor("#475569"))
            insetTop = 0; insetBottom = 0
            setOnClickListener { 
                currentCalendar.add(Calendar.MONTH, -1)
                refreshDebtListLive() 
            }
        }
        tvMonthLabel = TextView(context).apply { 
            textSize = 14f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B"))
            gravity = Gravity.CENTER; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) 
        }
        val btnNext = MaterialButton(context).apply { 
            text = "▶"; cornerRadius = 10
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#E2E8F0"))
            setTextColor(Color.parseColor("#475569"))
            insetTop = 0; insetBottom = 0
            setOnClickListener { 
                currentCalendar.add(Calendar.MONTH, 1)
                refreshDebtListLive() 
            }
        }
        navMonthRow.addView(btnPrev)
        navMonthRow.addView(tvMonthLabel)
        navMonthRow.addView(btnNext)
        mainContent.addView(navMonthRow)

        // 3. CARDS RINGKASAN SALDO BERJALAN
        val summaryLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (16 * density).toInt())
            weightSum = 2f
        }
        cardDebt = createPremiumSummaryCard(context, "Hutang Aktif Saya", "#D97706")
        cardReceivable = createPremiumSummaryCard(context, "Piutang Aktif (Di Orang)", "#0284C7")
        summaryLayout.addView(cardDebt)
        summaryLayout.addView(View(context).apply { layoutParams = LinearLayout.LayoutParams((12 * density).toInt(), 1) })
        summaryLayout.addView(cardReceivable)
        mainContent.addView(summaryLayout)

        // 4. KONTROL TAB LAYOUT
        val tabOuterContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * density).toInt()).apply {
                leftMargin = (16 * density).toInt(); rightMargin = (16 * density).toInt(); bottomMargin = (16 * density).toInt()
            }
            background = GradientDrawable().apply { cornerRadius = 12 * density; setColor(Color.parseColor("#E2E8F0")) }
        }
        val btnTabDebt = MaterialButton(context).apply { text = "Hutang Saya"; textSize = 13f; cornerRadius = (10 * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        val btnTabReceivable = MaterialButton(context).apply { text = "Piutang / Tagihan"; textSize = 13f; cornerRadius = (10 * density).toInt(); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f); insetTop = 0; insetBottom = 0 }
        tabOuterContainer.addView(btnTabDebt)
        tabOuterContainer.addView(btnTabReceivable)
        mainContent.addView(tabOuterContainer)

        // 5. SCROLL VIEW DATA MUTASI
        val scrollView = ScrollView(context).apply { isFillViewport = true }
        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * density).toInt(), 0, (16 * density).toInt(), (24 * density).toInt())
        }
        scrollView.addView(listContainer)
        mainContent.addView(scrollView)
        root.addView(mainContent)

        // FAB UNTUK MEMICU DIALOG INPUT MANUAL BARU
        val fab = FloatingActionButton(context).apply {
            setImageResource(android.R.drawable.ic_input_add)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#0D9488"))
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(0, 0, (24 * density).toInt(), (24 * density).toInt())
            }
            setOnClickListener {
                DebtManualDialog(currentTab) { refreshDebtListLive() }.show(parentFragmentManager, "DebtManualDialog")
            }
        }
        root.addView(fab)

        btnTabDebt.setOnClickListener {
            currentTab = "DEBT"
            setPremiumTabStyles(btnTabDebt, btnTabReceivable)
            refreshDebtListLive()
        }
        btnTabReceivable.setOnClickListener {
            currentTab = "RECEIVABLE"
            setPremiumTabStyles(btnTabReceivable, btnTabDebt)
            refreshDebtListLive()
        }

        setPremiumTabStyles(btnTabDebt, btnTabReceivable)
        refreshDebtListLive()
        return root
    }

    private fun createPremiumSummaryCard(ctx: Context, title: String, valueColorHex: String): MaterialCardView {
        val density = ctx.resources.displayMetrics.density
        return MaterialCardView(ctx).apply {
            radius = 14 * density; cardElevation = 2 * density; strokeWidth = 0
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

            val layout = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt())
            }
            layout.addView(TextView(ctx).apply { text = title; setTextColor(Color.parseColor("#64748B")); textSize = 11.5f })
            layout.addView(TextView(ctx).apply { text = "Rp 0"; setTextColor(Color.parseColor(valueColorHex)); textSize = 15.5f; setTypeface(null, Typeface.BOLD); setPadding(0, (4 * density).toInt(), 0, 0) })
            addView(layout)
        }
    }

    private fun setPremiumTabStyles(active: MaterialButton, inactive: MaterialButton) {
        active.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
        active.setTextColor(Color.WHITE); active.setTypeface(null, Typeface.BOLD)
        inactive.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        inactive.setTextColor(Color.parseColor("#64748B")); inactive.setTypeface(null, Typeface.NORMAL)
    }

    private fun refreshDebtListLive() {
        if (!isAdded) return
        val context = requireContext()
        val density = context.resources.displayMetrics.density

        tvMonthLabel.text = sdfMonthLabel.format(currentCalendar.time).uppercase(Locale.ROOT)
        debtListenerRegistration?.remove()

        debtListenerRegistration = firestore.collection("debts")
            .addSnapshotListener { snapshots, e ->
                if (e != null || snapshots == null) return@addSnapshotListener

                listContainer.removeAllViews()
                var totalDebtSum = 0.0
                var totalReceivableSum = 0.0

                val targetMonth = currentCalendar.get(Calendar.MONTH)
                val targetYear = currentCalendar.get(Calendar.YEAR)
                val monthlyFilteredDebts = ArrayList<HashMap<String, Any>>()

                for (doc in snapshots.documents) {
                    val data = doc.data ?: continue
                    val isPaid = data["isPaid"] as? Boolean ?: false
                    val type = data["type"] as? String ?: "DEBT"
                    val remainingAmount = (data["remainingAmount"] as? Number)?.toDouble() ?: 0.0
                    val timestamp = (data["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis()

                    if (!isPaid) {
                        if (type == "DEBT") totalDebtSum += remainingAmount else totalReceivableSum += remainingAmount
                    }
                    
                    val txCal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    if (txCal.get(Calendar.MONTH) == targetMonth && txCal.get(Calendar.YEAR) == targetYear) {
                        val itemMap = HashMap(data)
                        itemMap["id"] = doc.id
                        itemMap["timestamp"] = timestamp
                        monthlyFilteredDebts.add(itemMap)
                    }
                }

                // Inject data live ke Summary Card Atas
                (((cardDebt.getChildAt(0) as LinearLayout).getChildAt(1)) as TextView).text = formatRupiah.format(totalDebtSum)
                (((cardReceivable.getChildAt(0) as LinearLayout).getChildAt(1)) as TextView).text = formatRupiah.format(totalReceivableSum)

                val activeTabFiltered = monthlyFilteredDebts.filter { (it["type"] as? String) == currentTab }

                if (activeTabFiltered.isEmpty()) {
                    listContainer.addView(TextView(context).apply {
                        text = "\nTidak ada catatan pinjaman pada periode bulan ini."
                        textSize = 13.5f; setTextColor(Color.parseColor("#94A3B8")); gravity = Gravity.CENTER
                        setTypeface(null, Typeface.ITALIC)
                    })
                    return@addSnapshotListener
                }

                activeTabFiltered.sortByDescending { (it["timestamp"] as? Long) ?: 0L }

                activeTabFiltered.forEach { debtItem ->
                    val contactName = (debtItem["contactName"] as? String) ?: "TEMAN"
                    val remainingAmount = (debtItem["remainingAmount"] as? Number)?.toDouble() ?: 0.0
                    val isPaid = (debtItem["isPaid"] as? Boolean) ?: false
                    val docId = (debtItem["id"] as? String) ?: ""
                    val debtType = (debtItem["type"] as? String) ?: "DEBT"
                    val debtTime = (debtItem["timestamp"] as? Long) ?: System.currentTimeMillis()

                    val itemCard = MaterialCardView(context).apply {
                        radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
                        setCardBackgroundColor(Color.WHITE)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = (10 * density).toInt() }
                        
                        setOnClickListener { 
                            // Melempar data komplit reaktif ke komponen Editor Dialog khusus kita
                            val passMap = HashMap(debtItem)
                            DebtEditorDialog(passMap) { refreshDebtListLive() }.show(parentFragmentManager, "DebtEditorDialog")
                        }
                    }

                    val rowLayout = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        setPadding((16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt(), (16 * density).toInt())
                    }

                    val leftLayout = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                    leftLayout.addView(TextView(context).apply { text = contactName; textSize = 15f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor("#1E293B")) })
                    
                    val dateAndStatusLabel = "📅 ${sdfDisplayPremium.format(Date(debtTime))} \nSisa Tagihan: ${formatRupiah.format(remainingAmount)}"
                    val statusLabel = if (isPaid) "LUNAS SEPENUHNYA ✅" else dateAndStatusLabel
                    
                    leftLayout.addView(TextView(context).apply { 
                        text = statusLabel; textSize = 11.5f
                        setTextColor(if (isPaid) Color.parseColor("#10B981") else Color.parseColor("#64748B"))
                        setPadding(0, (4 * density).toInt(), 0, 0) 
                    })
                    rowLayout.addView(leftLayout)

                    val tvOriginalAmount = TextView(context).apply {
                        text = formatRupiah.format((debtItem["amount"] as? Number)?.toDouble() ?: 0.0)
                        textSize = 14.5f; setTypeface(null, Typeface.BOLD)
                        setTextColor(if (currentTab == "DEBT") Color.parseColor("#D97706") else Color.parseColor("#0284C7"))
                        gravity = Gravity.END
                    }
                    rowLayout.addView(tvOriginalAmount)
                    
                    itemCard.addView(rowLayout)
                    listContainer.addView(itemCard)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        debtListenerRegistration?.remove()
    }
}
