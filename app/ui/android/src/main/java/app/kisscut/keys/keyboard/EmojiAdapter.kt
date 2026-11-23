package app.kisscut.keys.keyboard

import android.graphics.Paint
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

/** Emoji cells for the Emoji tab. A tap types the emoji; TalkBack reads its name from the text. */
internal class EmojiAdapter(
    private val palette: KeyboardPalette,
    private val feedback: KeyFeedback,
    private val onEmoji: (String) -> Unit,
) : ListAdapter<String, EmojiAdapter.Cell>(Same) {

    class Cell(val view: TextView) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Cell {
        val context = parent.context
        val view = TextView(context).apply {
            textSize = 28f
            gravity = Gravity.CENTER
            minHeight = context.dp(48)
            setTextColor(palette.text)
            background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), rounded(palette.raised, context.dp(10f)))
            }
            isClickable = true
            isFocusable = true
        }
        return Cell(view)
    }

    override fun onBindViewHolder(holder: Cell, position: Int) {
        val emoji = getItem(position)
        holder.view.text = emoji
        // TalkBack reads an emoji's name from the emoji itself.
        holder.view.contentDescription = emoji
        holder.view.tag = KeyboardTags.emoji(emoji)
        holder.view.setOnClickListener {
            feedback.tap(it)
            onEmoji(emoji)
        }
    }

    private object Same : DiffUtil.ItemCallback<String>() {
        override fun areItemsTheSame(a: String, b: String) = a == b
        override fun areContentsTheSame(a: String, b: String) = a == b
    }

    companion object {
        private val paint = Paint()
        private val drawable = mutableMapOf<EmojiCategory, List<String>>()

        /** [category]'s emoji that this phone's font can draw (older phones lack newer emoji). */
        fun shown(category: EmojiCategory): List<String> =
            drawable.getOrPut(category) { category.emoji.filter(paint::hasGlyph) }
    }
}
