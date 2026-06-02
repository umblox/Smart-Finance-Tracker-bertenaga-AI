package com.smartfinance.tracker.ui.chat

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.data.model.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val context = parent.context
        val textView = TextView(context).apply {
            textSize = 15f
            setPadding(24, 16, 24, 16)
            maxWidth = (parent.width * 0.75).toInt()
        }
        return ChatViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.textView.text = message.text

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 8
            bottomMargin = 8
        }

        if (message.isUser) {
            // SISI KANAN - WARNA TEAL (USER)
            params.gravity = Gravity.END
            holder.textView.setBackgroundResource(android.R.drawable.toast_frame)
            holder.textView.background.setTint(Color.parseColor("#008080"))
            holder.textView.setTextColor(Color.WHITE)
        } else {
            // SISI KIRI - WARNA ABU-ABU (GROQ AI)
            params.gravity = Gravity.START
            holder.textView.setBackgroundResource(android.R.drawable.toast_frame)
            holder.textView.background.setTint(Color.parseColor("#E2E8F0"))
            holder.textView.setTextColor(Color.parseColor("#2D3748"))
        }
        holder.textView.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}
