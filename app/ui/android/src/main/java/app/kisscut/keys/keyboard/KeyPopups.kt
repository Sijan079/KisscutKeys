package app.kisscut.keys.keyboard

import android.content.Context
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * The bubbles above the letters (docs/KEYBOARD.md, Key popups): a preview of the pressed key, and the row of
 * long-press alternates with the one under the finger in lime. Both are touch-through windows anchored to the
 * key, kept inside [container]'s width. Positions are in the key's own coordinates.
 */
internal class KeyPopups(
    private val context: Context,
    private val palette: KeyboardPalette,
    private val container: View,
) {
    private val preview = popup()
    private var previewOwner: View? = null

    private val alternatesWindow = popup()
    private var choices: List<String> = emptyList()
    private var cells: List<TextView> = emptyList()
    private var alternatesLeft = 0f
    private var cellWidth = 1f
    private var selected = 0

    /** Whether the alternates row is open; while it is, the key types the chosen alternate. */
    val alternatesShown: Boolean get() = alternatesWindow.isShowing

    /** The alternate under the finger, or null when the row is closed. */
    val chosen: String? get() = if (alternatesShown) choices.getOrNull(selected) else null

    fun showPreview(key: View, label: String) {
        val visual = keyVisual(key)
        val text = label(label, textSize = 28f)
        text.background = rounded(palette.raised, context.dp(10f))
        preview.contentView = text
        previewOwner = key
        place(preview, key, visual.width(), visual.height())
    }

    /** Hides the preview only if it still belongs to [key], so a fast second key keeps its own. */
    fun hidePreview(key: View) {
        if (previewOwner === key) {
            preview.dismiss()
            previewOwner = null
        }
    }

    fun showAlternates(key: View, options: List<String>) {
        if (options.isEmpty()) return
        hidePreview(key)
        val visual = keyVisual(key)
        cellWidth = maxOf(visual.width(), context.dp(34)).toFloat()
        choices = options
        selected = 0
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(palette.raised, context.dp(10f))
            elevation = context.dp(4f)
        }
        cells = options.map { option ->
            label(option, textSize = 22f).also {
                it.tag = KeyboardTags.alternate(option)
                row.addView(it, LinearLayout.LayoutParams(cellWidth.toInt(), visual.height()))
            }
        }
        alternatesWindow.contentView = row
        // The first (most common) choice sits right above the finger; the rest follow to its right.
        val width = (cellWidth * options.size).toInt()
        val shift = (width - cellWidth.toInt()) / 2
        alternatesLeft = place(alternatesWindow, key, width, visual.height(), shift).toFloat()
        highlight()
        key.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
    }

    /** [x] is the finger's position in the key's coordinates. */
    fun moveFinger(x: Float) {
        if (!alternatesShown) return
        val index = alternateIndex(x, alternatesLeft, cellWidth, choices.size)
        if (index != selected) {
            selected = index
            highlight()
        }
    }

    fun dismissAll() {
        preview.dismiss()
        previewOwner = null
        alternatesWindow.dismiss()
        choices = emptyList()
        cells = emptyList()
    }

    private fun highlight() {
        cells.forEachIndexed { i, cell ->
            val on = i == selected
            cell.background = if (on) rounded(palette.lime, context.dp(8f)) else null
            cell.setTextColor(if (on) palette.onLime else palette.text)
        }
    }

    /**
     * Shows [window] ([width] x [height]) just above [key], centered on it ([shift] pixels further right) but
     * inside the keyboard's width. Returns its left edge in the key's coordinates.
     */
    private fun place(window: PopupWindow, key: View, width: Int, height: Int, shift: Int = 0): Int {
        val inContainer = Rect(0, 0, key.width, key.height)
        (container as ViewGroup).offsetDescendantRectToMyCoords(key, inContainer)
        val left = popupLeft(inContainer.centerX() + shift, width, container.width) - inContainer.left
        // Drop-down offsets start at the key's bottom-left; this puts the popup's bottom near the key's top.
        val y = -key.height - height + context.dp(6)
        window.width = width
        window.height = height
        if (window.isShowing) {
            window.update(key, left, y, width, height)
        } else {
            window.showAsDropDown(key, left, y, Gravity.NO_GRAVITY)
        }
        return left
    }

    /** The drawn part of a key (cells have a small gap around their fill). */
    private fun keyVisual(key: View) = Rect(0, 0, key.width - context.dp(6), key.height - context.dp(8))

    private fun label(text: String, textSize: Float) = TextView(context).apply {
        this.text = text
        this.textSize = textSize
        setTextColor(palette.text)
        applyFigtree(600)
        gravity = Gravity.CENTER
        maxLines = 1
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun popup() = PopupWindow(context).apply {
        isTouchable = false
        isFocusable = false
        isClippingEnabled = false
        setBackgroundDrawable(null)
        animationStyle = 0
    }
}
