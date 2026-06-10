package com.smartfinance.tracker.ui.chat

import android.graphics.Color
import android.text.Html
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.data.model.ChatMessage
import com.smartfinance.tracker.R

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    class ChatViewHolder(val container: LinearLayout, val textView: TextView) : RecyclerView.ViewHolder(container)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val context = parent.context
        
        val linearParent = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding(0, 4, 0, 4)
        }

        // 🔥 FIX MUTLAK BARIS 32: Mengubah properti kaku menjadi fungsi setLineSpacing() agar lolos compiler
        val textView = TextView(context).apply {
            textSize = 14.5f
            setPadding(36, 24, 36, 24)
            setLineSpacing(0f, 1.15f)
        }
        
        linearParent.addView(textView)
        return ChatViewHolder(linearParent, textView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        
        val rawText = message.text
        if (!message.isUser && (rawText.contains("**") || rawText.contains("\n"))) {
            val formattedHtml = rawText
                .replace("\n", "<br/>")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            
            holder.textView.text = Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY)
        } else {
            holder.textView.text = rawText
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 4
            bottomMargin = 4
        }

        holder.textView.post {
            val maxChatWidth = (holder.itemView.rootView.width * 0.75).toInt()
            if (maxChatWidth > 0) {
                holder.textView.maxWidth = maxChatWidth
            }
        }

        if (message.isUser) {
            holder.container.gravity = Gravity.END
            params.gravity = Gravity.END
            params.leftMargin = 100
            params.rightMargin = 0
            holder.textView.setBackgroundResource(R.drawable.chat_bubble_user)
            holder.textView.setTextColor(Color.WHITE)
        } else {
            holder.container.gravity = Gravity.START
            params.gravity = Gravity.START
            params.rightMargin = 100
            params.leftMargin = 0
            holder.textView.setBackgroundResource(R.drawable.chat_bubble_ai)
            holder.textView.setTextColor(Color.parseColor("#2D3748"))
        }
        holder.textView.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}
