package com.smartfinance.tracker.ui.dashboard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.MainActivity
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentDashboardBinding
import com.smartfinance.tracker.ui.report.CategoryTrendReportFragment
import com.smartfinance.tracker.ui.report.QuadVerticalBarChartView
import com.smartfinance.tracker.ui.report.ReportFragment
import com.smartfinance.tracker.ui.transaction.HistoryTransactionFragment
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: DashboardViewModel
    private lateinit var aiViewModel: AiNotificationViewModel

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    private val sdfPremiumDateTime = SimpleDateFormat("dd-MM-yyyy • HH:mm 'WIB'", Locale("id", "ID"))

    private fun getThemeColor(resId: Int): Int {
        return ContextCompat.getColor(requireContext(), resId)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[DashboardViewModel::class.java]
        aiViewModel = ViewModelProvider(requireActivity())[AiNotificationViewModel::class.java]

        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
        val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())

        viewModel.updatePreferences(activeTimePrefs, "BULAN INI")
        updateTabUi("BULAN INI")

        binding.btnAiNotification.setOnClickListener {
            AiInboxBottomSheet().show(parentFragmentManager, "AiInboxBottomSheet")
        }

        binding.btnDetailLaporan.setOnClickListener {
            (activity as? MainActivity)?.navigateToSpecificFragment(ReportFragment())
        }
        
        binding.btnLihatAnalisis.setOnClickListener {
            val fragment = CategoryTrendReportFragment().apply {
                arguments = Bundle().apply {
                    putString("EXTRA_TARGET_MODE", "ALL_EXPENSE")
                    putString("EXTRA_TARGET_TYPE", "GLOBAL")
                    putLong("EXTRA_BASE_TIME", activeTimePrefs)
                    putBoolean("EXTRA_SHOW_DROPDOWN", false) 
                    putBoolean("EXTRA_FROM_REPORT_MENU", false)
                }
            }
            (activity as? MainActivity)?.navigateToSpecificFragment(fragment)
        }

        binding.btnLihatSemua.setOnClickListener {
            try {
                (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment(), R.id.menu_report)
            } catch (e: Exception) {
                (activity as? MainActivity)?.navigateToSpecificFragment(HistoryTransactionFragment())
            }
        }

        binding.btnTabWeek.setOnClickListener {
            viewModel.updatePreferences(activeTimePrefs, "PERMINGGU")
            updateTabUi("PERMINGGU")
        }
        binding.btnTabMonth.setOnClickListener {
            viewModel.updatePreferences(activeTimePrefs, "BULAN INI")
            updateTabUi("BULAN INI")
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderDashboardUi(state) }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            aiViewModel.notifications.collect { notifList ->
                val hasUnread = notifList.any { !it.isRead }
                binding.redDotBadge.visibility = if (hasUnread) View.VISIBLE else View.GONE
            }
        }
    }

    private fun renderDashboardUi(state: DashboardUiState) {
        val density = requireContext().resources.displayMetrics.density

        binding.tvTotalBalance.text = formatRupiah.format(state.totalBalance)
        binding.tvIncomeSummary.text = formatRupiah.format(state.incomeThisMonth)
        binding.tvExpenseSummary.text = formatRupiah.format(state.expenseThisMonth)

        binding.chartContainer.removeAllViews()
        val chartVerticalLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val barView = QuadVerticalBarChartView(
            requireContext(), state.incomeLastMonth.toFloat(), state.incomeThisMonth.toFloat(),
            state.expenseLastMonth.toFloat(), state.expenseThisMonth.toFloat()
        )
        chartVerticalLayout.addView(barView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (160 * density).toInt()))

        val summaryLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(0, (14 * density).toInt(), 0, 0) }
        val incDiffPercent = if (state.incomeLastMonth > 0) ((state.incomeThisMonth - state.incomeLastMonth) / state.incomeLastMonth * 100).toInt() else 0
        val expDiffPercent = if (state.expenseLastMonth > 0) ((state.expenseThisMonth - state.expenseLastMonth) / state.expenseLastMonth * 100).toInt() else 0

        // 🔥 FIX: Translasi dinamis untuk teks Chart Summary
        val filterLabelStr = if (state.activeTimeLabel == "PERMINGGU") getString(R.string.dashboard_filter_week) else getString(R.string.dashboard_filter_month)
        val incTextStr = if (incDiffPercent >= 0) getString(R.string.dashboard_trend_up, incDiffPercent) else getString(R.string.dashboard_trend_down, Math.abs(incDiffPercent))
        val expTextStr = if (expDiffPercent >= 0) getString(R.string.dashboard_trend_up, expDiffPercent) else getString(R.string.dashboard_trend_down, Math.abs(expDiffPercent))

        summaryLayout.addView(TextView(requireContext()).apply {
            text = getString(R.string.dashboard_summary_title, filterLabelStr)
            textSize = 11.5f; setTextColor(getThemeColor(R.color.text_secondary)); setPadding(0, 0, 0, (2 * density).toInt())
        })
        summaryLayout.addView(TextView(requireContext()).apply {
            text = getString(R.string.dashboard_summary_performance, incTextStr, expTextStr)
            textSize = 12f; setTextColor(getThemeColor(R.color.primary)); setTypeface(null, Typeface.BOLD)
        })
        
        chartVerticalLayout.addView(summaryLayout)
        binding.chartContainer.addView(chartVerticalLayout)

        binding.topExpenseContainer.removeAllViews()
        binding.cardTopExpense.setOnClickListener(null)
        
        if (state.topExpenses.isEmpty()) {
            for (i in 1..3) binding.topExpenseContainer.addView(createPlaceholderRow(
                getString(R.string.dashboard_empty_category_title, i), 
                getString(R.string.dashboard_empty_category_desc), 
                density
            ))
        } else {
            state.topExpenses.forEach { (categoryName, totalAmount) ->
                val percentage = if (state.topExpensesTotal > 0) ((totalAmount / state.topExpensesTotal) * 100).toInt() else 0
                val rowCard = MaterialCardView(requireContext()).apply {
                    radius = 12 * density; cardElevation = 1 * density; strokeWidth = 0
                    setCardBackgroundColor(getThemeColor(R.color.surface_white))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (8 * density).toInt() }
                }
                
                rowCard.setOnClickListener {
                    val prefs = requireContext().getSharedPreferences("smart_finance_prefs", Context.MODE_PRIVATE)
                    val activeTimePrefs = prefs.getLong("active_report_time", System.currentTimeMillis())
                    val fragment = CategoryTrendReportFragment().apply {
                        arguments = Bundle().apply {
                            putString("EXTRA_TARGET_MODE", categoryName)
                            putString("EXTRA_TARGET_TYPE", "CATEGORY")
                            putLong("EXTRA_BASE_TIME", activeTimePrefs)
                            putBoolean("EXTRA_SHOW_DROPDOWN", false)
                            putBoolean("EXTRA_FROM_REPORT_MENU", false)
                        }
                    }
                    (activity as? MainActivity)?.navigateToSpecificFragment(fragment)
                }

                val rowLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (10 * density).toInt()) }
                
                val iconCircle = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(getThemeColor(R.color.background_color)) }
                    addView(android.widget.ImageView(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams((20*density).toInt(), (20*density).toInt()).apply { gravity = Gravity.CENTER }
                        val iconName = state.categoryIconMap[categoryName] ?: "ic_custom"
                        setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(iconName))
                        imageTintList = android.content.res.ColorStateList.valueOf(getThemeColor(R.color.expense_red))
                    })
                }
                
                val centerInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                centerInfo.addView(TextView(requireContext()).apply { text = categoryName; setTextColor(getThemeColor(R.color.text_primary)); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14f })
                centerInfo.addView(TextView(requireContext()).apply { text = formatRupiah.format(totalAmount); setTextColor(getThemeColor(R.color.text_secondary)); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                
                rowLayout.addView(iconCircle); rowLayout.addView(centerInfo)
                rowLayout.addView(TextView(requireContext()).apply { text = "$percentage%"; setTextColor(getThemeColor(R.color.expense_red)); setTypeface(null, Typeface.BOLD); textSize = 14f })
                rowCard.addView(rowLayout)
                binding.topExpenseContainer.addView(rowCard)
            }
        }

        binding.recentTxContainer.removeAllViews()
        if (state.recentTransactions.isEmpty()) {
            for (i in 1..3) binding.recentTxContainer.addView(createPlaceholderRow(
                getString(R.string.dashboard_empty_tx_title, i), 
                getString(R.string.dashboard_empty_tx_desc), 
                density
            ))
        } else {
            state.recentTransactions.forEach { tx ->
                val mutasiCard = MaterialCardView(requireContext()).apply {
                    radius = 14 * density; cardElevation = 1.5f * density; strokeWidth = 0
                    setCardBackgroundColor(getThemeColor(R.color.surface_white))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = (10 * density).toInt() }
                }
                val rowLayout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding((14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt(), (14 * density).toInt()) }
                val isInc = tx.type == "INCOME" || tx.type == "DEBT"
                
                val iconCircle = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((38 * density).toInt(), (38 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(getThemeColor(R.color.background_color)) }
                    addView(android.widget.ImageView(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams((22*density).toInt(), (22*density).toInt()).apply { gravity = Gravity.CENTER }
                        val iconName = state.categoryIconMap[tx.categoryName] ?: "ic_custom"
                        setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(iconName))
                        imageTintList = android.content.res.ColorStateList.valueOf(getThemeColor(if (isInc) R.color.income_green else R.color.expense_red))
                    })
                }
                
                val centerInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
                centerInfo.addView(TextView(requireContext()).apply { text = tx.note; setTextColor(getThemeColor(R.color.text_primary)); setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); textSize = 14.5f })
                centerInfo.addView(TextView(requireContext()).apply { text = sdfPremiumDateTime.format(Date(tx.timestamp)); setTextColor(getThemeColor(R.color.text_secondary)); textSize = 11.5f; setPadding(0, 2, 0, 0) })
                
                rowLayout.addView(iconCircle); rowLayout.addView(centerInfo)
                rowLayout.addView(TextView(requireContext()).apply { 
                    text = (if (isInc) "+" else "-") + formatRupiah.format(tx.amount)
                    setTextColor(getThemeColor(if (isInc) R.color.income_green else R.color.expense_red))
                    setTypeface(null, Typeface.BOLD); textSize = 14.5f
                })
                mutasiCard.addView(rowLayout)
                binding.recentTxContainer.addView(mutasiCard)
            }
        }
    }

    private fun updateTabUi(activeFilter: String) {
        val density = requireContext().resources.displayMetrics.density
        if (activeFilter == "PERMINGGU") {
            binding.btnTabWeek.apply { 
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(getThemeColor(R.color.primary)) } 
            }
            binding.btnTabMonth.apply { setTextColor(getThemeColor(R.color.text_secondary)); setTypeface(null, Typeface.NORMAL); background = null }
        } else {
            binding.btnTabMonth.apply { 
                setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = 8 * density; setColor(getThemeColor(R.color.primary)) } 
            }
            binding.btnTabWeek.apply { setTextColor(getThemeColor(R.color.text_secondary)); setTypeface(null, Typeface.NORMAL); background = null }
        }
    }

    private fun createPlaceholderRow(mainTitle: String, subTitle: String, density: Float): View {
        val layout = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt()); alpha = 0.5f }
        val centerInfo = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) }
        centerInfo.addView(TextView(requireContext()).apply { text = mainTitle; textSize = 14f; setTextColor(getThemeColor(R.color.text_secondary)); setTypeface(null, Typeface.ITALIC) })
        centerInfo.addView(TextView(requireContext()).apply { text = subTitle; textSize = 11f; setTextColor(getThemeColor(R.color.divider_color)) })
        layout.addView(centerInfo)
        
        // 🔥 Angka placeholder kita tetap buat 0 agar terlihat konsisten
        layout.addView(TextView(requireContext()).apply { text = "Rp 0"; setTextColor(getThemeColor(R.color.divider_color)); textSize = 14f })
        return layout
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
