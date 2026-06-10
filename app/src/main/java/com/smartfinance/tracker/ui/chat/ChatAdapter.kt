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
            textSize = 14.5f
            setPadding(36, 24, 36, 24) // Padding balon chat lebih empuk dan estetik
            setLineSpacing(0f, 1.15f) // Mengganti lineSpacingMultiplier yang memicu error val
        }
        
        linearParent.addView(textView)
        return ChatViewHolder(linearParent, textView)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val message = messages[position]
        val density = holder.itemView.context.resources.displayMetrics.density
        
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
            
            // ✨ BENTUK BALON USER: Dibangun langsung lewat kode, warna Deep Teal Premium
            val bubbleUser = GradientDrawable().apply {
                setColor(Color.parseColor("#0D9488"))
                val radius = 16 * density
                // Melengkung di sudut kiri atas, kanan atas, kiri bawah. Lancip di kanan bawah.
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, radius, radius)
            }
            holder.textView.background = bubbleUser
            holder.textView.setTextColor(Color.WHITE)
        } else {
            holder.container.gravity = Gravity.START
            params.gravity = Gravity.START
            params.rightMargin = 100
            params.leftMargin = 0
            
            // ✨ BENTUK BALON AI: Warna Putih Bersih dengan border tipis elegan Abu-abu Soft
            val bubbleAi = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke((1 * density).toInt(), Color.parseColor("#E2E8F0"))
                val radius = 16 * density
                // Lancip di kiri bawah, sudut lainnya melengkung halus modern
                cornerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, 0f, 0f)
            }
            holder.textView.background = bubbleAi
            holder.textView.setTextColor(Color.parseColor("#2D3748"))
        }
        holder.textView.layoutParams = params
    }

    override fun getItemCount(): Int = messages.size
}
