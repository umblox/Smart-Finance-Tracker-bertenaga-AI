package com.smartfinance.tracker.ui.chat

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.data.model.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) : 
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(val container: LinearLayout, val textView: TextView) : 
        RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val context = parent.context

        val linearParent = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, 6, 0, 6)
        }

        val textView = TextView(context).apply {
            textSize = 14.5f
            setPadding(36, 24, 36, 24)
            lineSpacingMultiplier = 1.15f
        }

        linearParent.addView(textView)
        return ChatViewHolder(linearParent, textView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        val density = holder.itemView.context.resources.displayMetrics.density

        // Text formatting
        val rawText = message.text
        if (!message.isUser && (rawText.contains("**") || rawText.contains("\n"))) {
            val formattedHtml = rawText
                .replace("\n", "<br/>")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            
            holder.textView.text = Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY)
        } else {
            holder.textView.text = rawText
        }

        // 🔥 FIX BARU: Selalu buat LayoutParams baru (paling aman, tidak ada reassignment)
        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        params.topMargin = (2 * density).toInt()
        params.bottomMargin = (2 * density).toInt()

        // Max width
        holder.textView.post {
            val maxChatWidth = (holder.itemView.rootView.width * 0.78).toInt()
            if (maxChatWidth > 0) {
                holder.textView.maxWidth = maxChatWidth
            }
        }

        if (message.isUser) {
            holder.container.gravity = Gravity.END
            params.gravity = Gravity.END
            params.leftMargin = (60 * density).toInt()
            params.rightMargin = 0

            holder.textView.background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D9488"))
                val r = 16 * density
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, r, r)
            }
            holder.textView.setTextColor(Color.WHITE)
        } else {
            holder.container.gravity = Gravity.START
            params.gravity = Gravity.START
            params.rightMargin = (60 * density).toInt()
            params.leftMargin = 0

            holder.textView.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                val r = 16 * density
                cornerRadii = floatArrayOf(r, r, r, r, r, r, 0f, 0f)
                setStroke((1 * density).toInt(), Color.parseColor("#E2E8F0"))
            }
            holder.textView.setTextColor(Color.parseColor("#1E293B"))
        }

        holder.textView.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}
