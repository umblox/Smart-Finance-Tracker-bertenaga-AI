package com.smartfinance.tracker.ui.chat

import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.R
import com.smartfinance.tracker.data.model.ChatMessage

class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    // ✅ ViewHolder sekarang membaca ID dari XML
    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: LinearLayout = view.findViewById(R.id.chatContainer)
        val tvMessage: TextView = view.findViewById(R.id.tvChatMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        // ✅ UI di-inflate murni dari XML (Standar Best Practice)
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        val context = holder.itemView.context
        val dp = context.resources.displayMetrics.density
        
        // Render format teks (Bold / Enter)
        val rawText = message.text
        if (!message.isUser && (rawText.contains("**") || rawText.contains("\n"))) {
            val formattedHtml = rawText
                .replace("\n", "<br/>")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            holder.tvMessage.text = Html.fromHtml(formattedHtml, Html.FROM_HTML_MODE_LEGACY)
        } else {
            holder.tvMessage.text = rawText
        }

        // Batasi lebar maksimum gelembung chat (75% dari layar)
        holder.tvMessage.maxWidth = (context.resources.displayMetrics.widthPixels * 0.75).toInt()

        val params = holder.tvMessage.layoutParams as LinearLayout.LayoutParams

        // Logika Lempar Kiri (AI) / Kanan (User)
        if (message.isUser) {
            holder.container.gravity = Gravity.END
            params.gravity = Gravity.END
            params.leftMargin = (40 * dp).toInt()
            params.rightMargin = 0
            
            holder.tvMessage.setBackgroundResource(R.drawable.bubble_user_premium)
            holder.tvMessage.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        } else {
            holder.container.gravity = Gravity.START
            params.gravity = Gravity.START
            params.leftMargin = 0
            params.rightMargin = (40 * dp).toInt()
            
            holder.tvMessage.setBackgroundResource(R.drawable.bubble_ai_premium)
            holder.tvMessage.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
        }
        
        holder.tvMessage.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}
