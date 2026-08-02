package com.smartfinance.tracker.ui.report

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.smartfinance.tracker.R
import com.smartfinance.tracker.databinding.FragmentCategoryAnalyticsBinding
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CategoryAnalyticsFragment : Fragment() {

    private var _binding: FragmentCategoryAnalyticsBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: CategoryAnalyticsViewModel

    private val formatRupiah = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    
    private val formatGroup = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val formatDayNum = SimpleDateFormat("dd", Locale.getDefault())
    private val formatDayName = SimpleDateFormat("EEEE", Locale("id", "ID"))
    private val formatMonthYear = SimpleDateFormat("MMMM yyyy", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCategoryAnalyticsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[CategoryAnalyticsViewModel::class.java]

        val categoryName = arguments?.getString("EXTRA_CATEGORY_NAME") ?: ""
        val noteFilter = arguments?.getString("EXTRA_NOTE_FILTER")
        val baseTimeMillis = arguments?.getLong("EXTRA_BASE_TIME") ?: System.currentTimeMillis()
        val dayRange = arguments?.getString("EXTRA_DAY_RANGE")
        
        val prefs = requireContext().getSharedPreferences("smart_finance_prefs", android.content.Context.MODE_PRIVATE)
        val activeFilterString = prefs.getString("active_time_filter", "MONTHLY") ?: "MONTHLY"

        viewModel.loadCategoryData(categoryName, activeFilterString, baseTimeMillis, dayRange, noteFilter)

        binding.btnBack.setOnClickListener { requireActivity().supportFragmentManager.popBackStack() }

        // 🔥 FIX 1: Memastikan judul selalu "Daftar Transaksi" sesuai desain
        binding.tvCategoryTitle.text = "Daftar transaksi"

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state -> renderUi(state) }
        }
    }

    private fun renderUi(state: CategoryAnalyticsUiState) {
        binding.tvResultCount.text = "${state.transactions.size} hasil"
        binding.tvTotalIncome.text = (if (state.totalIncome > 0) "+" else "") + formatRupiah.format(state.totalIncome)
        binding.tvTotalExpense.text = (if (state.totalExpense > 0) "-" else "") + formatRupiah.format(state.totalExpense)

        binding.transactionListContainer.removeAllViews()
        val density = requireContext().resources.displayMetrics.density

        if (state.isEmpty) {
            val emptyText = TextView(requireContext()).apply {
                text = "Tidak ada transaksi."; gravity = Gravity.CENTER
                setPadding(0, (50 * density).toInt(), 0, 0)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            }
            binding.transactionListContainer.addView(emptyText)
            return
        }

        val groupedTransactions = state.transactions.groupBy { formatGroup.format(Date(it.timestamp)) }

        groupedTransactions.forEach { (dateStr, txList) ->
            val dateObj = formatGroup.parse(dateStr) ?: Date()
            
            val headerLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * density).toInt(), (24 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            }

            headerLayout.addView(TextView(requireContext()).apply {
                text = formatDayNum.format(dateObj)
                textSize = 28f; setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                setPadding(0, 0, (12 * density).toInt(), 0)
            })

            val dateSubtitles = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            dateSubtitles.addView(TextView(requireContext()).apply {
                text = formatDayName.format(dateObj)
                textSize = 13f; setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            })
            dateSubtitles.addView(TextView(requireContext()).apply {
                text = formatMonthYear.format(dateObj)
                textSize = 12f; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })
            headerLayout.addView(dateSubtitles)

            val dayTotal = txList.sumOf { if (it.type == "EXPENSE" || it.type == "RECEIVABLE") -Math.abs(it.amount) else it.amount }
            headerLayout.addView(TextView(requireContext()).apply {
                text = (if (dayTotal > 0) "+" else if (dayTotal < 0) "-" else "") + formatRupiah.format(Math.abs(dayTotal))
                textSize = 14f; setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), if (dayTotal >= 0) R.color.text_primary else R.color.text_secondary))
            })

            binding.transactionListContainer.addView(headerLayout)
            
            binding.transactionListContainer.addView(View(requireContext()).apply { 
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
                setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.divider_color)) 
            })

            val typedValue = android.util.TypedValue()
            requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)

            txList.forEach { tx ->
                val rowLayout = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    setPadding((16 * density).toInt(), (14 * density).toInt(), (16 * density).toInt(), (14 * density).toInt())
                    setBackgroundResource(typedValue.resourceId); isClickable = true
                }

                val isInc = tx.type == "INCOME" || tx.type == "DEBT"

                // 🔥 INJEKSI IKON VEKTOR DINAMIS ANALYTICS
                val iconCircle = FrameLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt()).apply { rightMargin = (12 * density).toInt() }
                    background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(ContextCompat.getColor(requireContext(), R.color.background_color)) }
                    addView(android.widget.ImageView(requireContext()).apply {
                        layoutParams = FrameLayout.LayoutParams((24*density).toInt(), (24*density).toInt()).apply { gravity = Gravity.CENTER }
                        val iconName = state.categoryIconMap[tx.categoryName] ?: "ic_custom"
                        setImageResource(com.smartfinance.tracker.utils.IconProvider.getIconResource(iconName))
                        imageTintList = android.content.res.ColorStateList.valueOf(ContextCompat.getColor(requireContext(), if (isInc) R.color.income_green else R.color.expense_red))
                    })
                }
                rowLayout.addView(iconCircle)

                val infoBox = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                infoBox.addView(TextView(requireContext()).apply {
                    text = tx.categoryName; textSize = 14f; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                })
                infoBox.addView(TextView(requireContext()).apply {
                    text = tx.note.ifBlank { "Tanpa Catatan" }; textSize = 12f; setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                })
                rowLayout.addView(infoBox)

                val amtBox = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
                amtBox.addView(TextView(requireContext()).apply {
                    text = (if (isInc) "+" else "-") + formatRupiah.format(Math.abs(tx.amount))
                    textSize = 14f; setTypeface(null, Typeface.BOLD)
                    setTextColor(ContextCompat.getColor(requireContext(), if (isInc) R.color.income_green else R.color.expense_red))
                })
                rowLayout.addView(amtBox)

                binding.transactionListContainer.addView(rowLayout)
            }
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
