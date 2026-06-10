package com.smartfinance.tracker.ui.chat

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartfinance.tracker.data.model.ChatMessage

class ChatAdapter(
    private val messages: List<ChatMessage>,
    private val onReactionClick: (Int, String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_MESSAGE = 0
        private const val VIEW_TYPE_TYPING = 1
    }

    private var isTyping = false

    fun setTyping(show: Boolean) {
        isTyping = show
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (isTyping && position == messages.size) VIEW_TYPE_TYPING else VIEW_TYPE_MESSAGE
    }

    override fun getItemCount(): Int = messages.size + if (isTyping) 1 else 0

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_TYPING) {
            TypingViewHolder(createTypingView(parent.context))
        } else {
            MessageViewHolder(createMessageView(parent.context))
        }
    }

    private fun createMessageView(context: Context): LinearLayout {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, 8, 0, 8)
        }

        val bubbleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val bubble = TextView(context).apply {
            textSize = 15.5f
            setPadding(32, 24, 32, 24)
            lineSpacingMultiplier = 1.25f
            elevation = 4f
        }

        bubbleContainer.addView(bubble)
        container.addView(bubbleContainer)

        // Reactions
        val reactionsLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 6, 0, 0)
        }
        container.addView(reactionsLayout)

        return container
    }

    private fun createTypingView(context: Context): LinearLayout {
        return LinearLayout(context).apply {
            gravity = Gravity.START
            setPadding(24, 12, 24, 12)
            orientation = LinearLayout.HORIZONTAL

            val typingText = TextView(context).apply {
                text = "AI sedang mengetik"
                textSize = 13f
                setTextColor(Color.GRAY)
            }

            val dots = TextView(context).apply {
                text = "•••"
                textSize = 18f
                setTextColor(Color.GRAY)
            }

            addView(typingText)
            addView(dots)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is TypingViewHolder) {
            animateTypingDots((holder.itemView.getChildAt(1) as TextView))
            return
        }

        if (holder !is MessageViewHolder) return

        val message = messages[position]
        val container = holder.itemView as LinearLayout
        val bubbleContainer = container.getChildAt(0) as LinearLayout
        val bubble = bubbleContainer.getChildAt(0) as TextView
        val reactionsLayout = container.getChildAt(1) as LinearLayout

        val density = container.context.resources.displayMetrics.density
        val isNew = position == messages.size - 1

        // Text Content
        val rawText = message.text
        bubble.text = if (!message.isUser && (rawText.contains("**") || rawText.contains("\n"))) {
            val formatted = rawText.replace("\n", "<br/>")
                .replace(Regex("\\*\\*(.*?)\\*\\*"), "<b>$1</b>")
            Html.fromHtml(formatted, Html.FROM_HTML_MODE_LEGACY)
        } else rawText

        // Max width
        bubble.post {
            bubble.maxWidth = (bubble.rootView.width * 0.78).toInt()
        }

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (4 * density).toInt()
            bottomMargin = (8 * density).toInt()
        }

        if (message.isUser) {
            bubbleContainer.gravity = Gravity.END
            params.gravity = Gravity.END
            params.leftMargin = (64 * density).toInt()

            bubble.background = GradientDrawable().apply {
                setColors(intArrayOf(0xFF14B8A6.toInt(), 0xFF0F766E.toInt()))
                orientation = GradientDrawable.Orientation.TOP_BOTTOM
                cornerRadii = floatArrayOf(22f,22f,22f,22f,0f,0f,22f,22f).map { it * density }.toFloatArray()
            }
            bubble.setTextColor(Color.WHITE)
        } else {
            bubbleContainer.gravity = Gravity.START
            params.gravity = Gravity.START
            params.rightMargin = (64 * density).toInt()

            bubble.background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(22f,22f,22f,22f,22f,22f,0f,0f).map { it * density }.toFloatArray()
                setStroke((1.5f * density).toInt(), 0xFFE2E8F0.toInt())
            }
            bubble.setTextColor(Color.parseColor("#1F2937"))
        }

        bubble.layoutParams = params

        // Reactions
        setupReactions(reactionsLayout, position, message)

        if (isNew) applyEntryAnimation(container, message.isUser)
    }

    private fun setupReactions(reactionsLayout: LinearLayout, position: Int, message: ChatMessage) {
        reactionsLayout.removeAllViews()
        val emojis = listOf("❤️", "👍", "😂", "😮", "👎")
        
        emojis.forEach { emoji ->
            TextView(reactionsLayout.context).apply {
                text = emoji
                textSize = 18f
                setPadding(12, 4, 12, 4)
                setOnClickListener { onReactionClick(position, emoji) }
                reactionsLayout.addView(this)
            }
        }
    }

    private fun animateTypingDots(dots: TextView) {
        val anim = ObjectAnimator.ofFloat(dots, "alpha", 0.3f, 1f).apply {
            duration = 600
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
        }
        anim.start()
    }

    private fun applyEntryAnimation(view: LinearLayout, isUser: Boolean) {
        view.alpha = 0f
        view.translationY = 50f
        view.translationX = if (isUser) 60f else -60f

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, "alpha", 0f, 1f),
                ObjectAnimator.ofFloat(view, "translationY", 50f, 0f),
                ObjectAnimator.ofFloat(view, "translationX", if (isUser) 60f else -60f, 0f)
            )
            duration = 380
            start()
        }
    }

    class MessageViewHolder(itemView: LinearLayout) : RecyclerView.ViewHolder(itemView)
    class TypingViewHolder(itemView: LinearLayout) : RecyclerView.ViewHolder(itemView)
}
