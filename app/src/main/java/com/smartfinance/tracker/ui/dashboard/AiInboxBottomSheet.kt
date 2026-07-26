package com.smartfinance.tracker.ui.dashboard

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.AiNotification
import com.smartfinance.tracker.databinding.DialogAiInboxBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AiInboxBottomSheet : BottomSheetDialogFragment() {

    private var _binding: DialogAiInboxBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: AiNotificationViewModel
    private val sdf = SimpleDateFormat("dd MMM • HH:mm", Locale("id", "ID"))

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogAiInboxBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Memaksa tinggi maksimal agar nyaman dibaca
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED

        viewModel = ViewModelProvider(this)[AiNotificationViewModel::class.java]

        binding.btnClose.setOnClickListener { dismiss() }

        binding.btnMarkAllRead.setOnClickListener {
            viewModel.markAllAsRead()
        }

        // 🔥 Mulai mendengarkan aliran data asli dari ViewModel (Firestore)
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.notifications.collect { notifList ->
                renderMessages(notifList)
            }
        }
    }

    private fun renderMessages(notifications: List<AiNotification>) {
        binding.containerMessages.removeAllViews()

        if (notifications.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.btnMarkAllRead.visibility = View.GONE
            return
        }

        binding.layoutEmptyState.visibility = View.GONE
        binding.btnMarkAllRead.visibility = View.VISIBLE

        val density = requireContext().resources.displayMetrics.density

        notifications.forEach { notif ->
            val card = MaterialCardView(requireContext()).apply {
                radius = 12f * density
                cardElevation = 0f
                strokeWidth = (1f * density).toInt()
                strokeColor = ContextCompat.getColor(requireContext(), if (notif.isRead) R.color.divider_color else R.color.primary)
                setCardBackgroundColor(ContextCompat.getColor(requireContext(), if (notif.isRead) R.color.background_color else R.color.surface_white))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (12f * density).toInt()
                }
            }

            val layout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                val p = (16f * density).toInt()
                setPadding(p, p, p, p)
            }

            // Header (Tipe & Waktu)
            val headerRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            val iconStr = when (notif.type) {
                "BUDGET" -> "🚨"
                "RECURRING" -> "📅"
                "WEEKLY_REPORT" -> "📊"
                else -> "💡"
            }

            headerRow.addView(TextView(requireContext()).apply {
                text = "$iconStr  ${notif.type.replace("_", " ")}"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            headerRow.addView(TextView(requireContext()).apply {
                text = sdf.format(Date(notif.timestamp))
                textSize = 10f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
            })

            layout.addView(headerRow)

            // Judul
            layout.addView(TextView(requireContext()).apply {
                text = notif.title
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
                setPadding(0, (8f * density).toInt(), 0, (4f * density).toInt())
            })

            // Isi Nasihat AI
            layout.addView(TextView(requireContext()).apply {
                text = notif.message
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                setLineSpacing(4f, 1f)
            })

            card.addView(layout)
            binding.containerMessages.addView(card)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
