package com.smartfinance.tracker.ui.chat

import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.data.model.ChatMessage

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

        val textView = TextView(context).apply {
            textSize = 15f
            setPadding(28, 20, 28, 20)
        }
        
        linearParent.addView(textView)
        return ChatViewHolder(linearParent, textView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        holder.textView.text = message.text

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = 4
            bottomMargin = 4
        }

        // PERBAIKAN MUTLAK: Atur max width langsung ke TextView secara aman setelah layout dirender
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
            holder.textView.setBackgroundResource(android.R.drawable.toast_frame)
            holder.textView.background.setTint(Color.parseColor("#008080"))
            holder.textView.setTextColor(Color.WHITE)
        } else {
            holder.container.gravity = Gravity.START
            params.gravity = Gravity.START
            params.rightMargin = 100
            params.leftMargin = 0
            holder.textView.setBackgroundResource(android.R.drawable.toast_frame)
            holder.textView.background.setTint(Color.parseColor("#E2E8F0"))
            holder.textView.setTextColor(Color.parseColor("#2D3748"))
        }
        holder.textView.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}
