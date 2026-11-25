package app.kisscut.keys.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.kisscut.keys.R
import app.kisscut.keys.shared.core.KeyboardTab
import app.kisscut.keys.shared.core.StickerKind
import app.kisscut.keys.shared.db.Sticker
import java.io.File
import kotlin.math.max

/**
 * The sticker panel (docs/KEYBOARD.md, Keyboard board): tabs, subcollection chips, a sticker grid, and a
 * bottom row with the ABC key, the All/Static/GIF switch and Backspace. Plain Views, built in code so it
 * can read the theme tokens. Visuals follow the board; every touch area is at least 48dp.
 */
@SuppressLint("ViewConstructor")
class StickerPanelView(
    context: Context,
    private val palette: KeyboardPalette,
    thumbnailOf: (Sticker) -> File?,
    private val actions: Actions,
    feedback: KeyFeedback,
    /** Height setting (Settings, Keyboard); the letters take the same height. */
    private val heightScale: Float = 1f,
    private val numberRow: Boolean = false,
) : LinearLayout(context) {

    interface Actions {
        fun onTab(tab: KeyboardTab)
        fun onChip(id: Long?)
        fun onKind(kind: StickerKind)
        fun onSticker(sticker: Sticker)
        fun onEmojiCategory(category: EmojiCategory)
        fun onEmoji(emoji: String)
        fun onLetters()
        fun onSwitchKeyboard()
        fun onBackspace()
        fun onOpenApp()
    }

    private val tabRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val chipRow = LinearLayout(context).apply { orientation = HORIZONTAL }
    private val chipScroll = scroller(chipRow)
    private val adapter = StickerAdapter(palette, thumbnailOf, actions::onSticker)
    private val grid = RecyclerView(context).apply {
        tag = KeyboardTags.GRID
        layoutManager = GridLayoutManager(context, spanCount())
        adapter = this@StickerPanelView.adapter
        clipToPadding = false
        setPadding(dp(7), dp(7), dp(7), dp(7))
        addItemDecoration(Gap(dp(3)))
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
    }
    private val emojiAdapter = EmojiAdapter(palette, feedback, actions::onEmoji)
    private val emojiGrid = RecyclerView(context).apply {
        tag = KeyboardTags.GRID_EMOJI
        layoutManager = GridLayoutManager(context, emojiSpanCount())
        adapter = emojiAdapter
        clipToPadding = false
        setPadding(dp(7), dp(4), dp(7), dp(7))
        itemAnimator = null
        overScrollMode = OVER_SCROLL_NEVER
        visibility = GONE
    }
    private val messageTitle = text(17f, 700, palette.text).apply { gravity = Gravity.CENTER }
    private val messageBody = text(14f, 600, palette.muted).apply { gravity = Gravity.CENTER }
    private val openApp = TextView(context).apply {
        tag = KeyboardTags.OPEN_APP
        text = context.getString(R.string.kb_open_app)
        setTextColor(palette.onLime)
        textSize = 15f
        applyFigtree(800)
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(22), 0, dp(22), 0)
        background = rounded(palette.lime, dp(24f))
        isClickable = true
        isFocusable = true
        setOnClickListener { actions.onOpenApp() }
    }
    private val message = LinearLayout(context).apply {
        tag = KeyboardTags.MESSAGE
        orientation = VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(24), dp(8), dp(24), dp(8))
        addView(messageTitle)
        addView(messageBody, lp(WRAP, WRAP).apply { topMargin = dp(4) })
        addView(openApp, lp(WRAP, WRAP).apply { topMargin = dp(14) })
    }
    private val hint = text(13f, 700, palette.text).apply {
        tag = KeyboardTags.HINT
        background = rounded(palette.raised, dp(16f), dp(1), palette.line)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        visibility = GONE
        // Announced by TalkBack when it appears.
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val middle = LinearLayout(context).apply { orientation = VERTICAL }
    private val kindButtons = mutableMapOf<StickerKind, TextView>()
    private var kindGroup: View? = null
    private val hideHint = Runnable { hint.visibility = GONE }

    private var shownTabs: List<KeyboardTab>? = null
    private var shownTab: KeyboardTab? = null
    private var shownChips: Any? = null
    private var shownView: Triple<KeyboardTab, Long?, StickerKind>? = null
    private var shownEmoji: EmojiCategory? = null

    init {
        orientation = VERTICAL
        background = panelBackground(context, palette)
        setPadding(0, dp(8), 0, dp(10))

        tabRow.setPadding(dp(12), 0, dp(12), 0)
        chipRow.setPadding(dp(12), 0, dp(12), 0)
        addView(scroller(tabRow), lp(MATCH, WRAP))

        val content = FrameLayout(context).apply {
            addView(grid, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(emojiGrid, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(message, FrameLayout.LayoutParams(MATCH, MATCH))
            addView(
                hint,
                FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                    .apply { bottomMargin = dp(8) },
            )
        }
        middle.addView(chipScroll, lp(MATCH, WRAP))
        middle.addView(content, lp(MATCH, 0).apply { weight = 1f })
        addView(middle, lp(MATCH, middleHeight()))
        addView(bottomRow(), lp(MATCH, WRAP).apply { topMargin = dp(2) })

        ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(0, dp(8), 0, dp(10) + nav)
            insets
        }
    }

    /** Draws [state]. [password] disables sending and says why. */
    fun render(state: KeyboardUiState, password: Boolean) {
        if (state.tabs != shownTabs || state.tab != shownTab) {
            buildTabs(state.tabs, state.tab)
            shownTabs = state.tabs
            shownTab = state.tab
        }
        val emoji = state.tab == KeyboardTab.Emoji
        val stickerChips = state.chips.map { it.id to it.name }
        val chips: Any = if (emoji) state.emojiCategory else stickerChips to state.chip
        if (chips != shownChips) {
            if (emoji) buildEmojiChips(state.emojiCategory) else buildChips(stickerChips, state.chip)
            shownChips = chips
        }
        chipScroll.visibility = if (emoji || state.chips.isNotEmpty()) VISIBLE else GONE
        kindButtons.forEach { (kind, button) -> styleSegment(button, kind == state.kind) }
        // Static and GIF mean nothing for emoji; the space stays so the row does not jump.
        kindGroup?.visibility = if (emoji) INVISIBLE else VISIBLE
        if (emoji) {
            renderEmoji(state.emojiCategory, password)
            return
        }
        emojiGrid.visibility = GONE

        adapter.collectionNames = state.collectionNames
        adapter.enabled = !password
        val view = Triple(state.tab, state.chip, state.kind)
        val switched = view != shownView
        shownView = view
        adapter.submitList(state.stickers) { if (switched) grid.scrollToPosition(0) }

        val (title, body, showOpen) = when {
            !state.loaded -> Triple(null, null, false)
            state.libraryEmpty -> Triple(
                context.getString(R.string.kb_empty_library_title),
                context.getString(R.string.kb_empty_library_body),
                true,
            )
            password -> Triple(null, context.getString(R.string.kb_password), false)
            state.stickers.isEmpty() -> Triple(
                context.getString(R.string.kb_empty_tab),
                if (state.tab == KeyboardTab.Recents) context.getString(R.string.kb_empty_recents) else null,
                false,
            )
            else -> Triple(null, null, false)
        }
        val showMessage = title != null || body != null
        message.visibility = if (showMessage) VISIBLE else GONE
        grid.visibility = if (showMessage) GONE else VISIBLE
        messageTitle.text = title
        messageTitle.visibility = if (title != null) VISIBLE else GONE
        messageBody.text = body
        messageBody.visibility = if (body != null) VISIBLE else GONE
        openApp.visibility = if (showOpen) VISIBLE else GONE
    }

    private fun renderEmoji(category: EmojiCategory, password: Boolean) {
        val switched = shownView?.first != KeyboardTab.Emoji || shownEmoji != category
        shownView = Triple(KeyboardTab.Emoji, null, StickerKind.ALL)
        shownEmoji = category
        emojiAdapter.submitList(EmojiAdapter.shown(category)) { if (switched) emojiGrid.scrollToPosition(0) }
        grid.visibility = GONE
        emojiGrid.visibility = if (password) GONE else VISIBLE
        message.visibility = if (password) VISIBLE else GONE
        messageTitle.visibility = GONE
        messageBody.text = context.getString(R.string.kb_password)
        messageBody.visibility = if (password) VISIBLE else GONE
        openApp.visibility = GONE
    }

    /** A short message over the grid ("Copied. Paste to send."). */
    fun showHint(text: String) {
        hint.text = text
        hint.visibility = VISIBLE
        removeCallbacks(hideHint)
        postDelayed(hideHint, HINT_MS)
    }

    fun clearHint() {
        removeCallbacks(hideHint)
        hint.visibility = GONE
    }

    /** The text of the hint on screen, or null (for tests). */
    val hintText: CharSequence? get() = hint.text.takeIf { hint.visibility == VISIBLE }

    // ---------------------------------------------------------------------------------------------

    private fun buildTabs(tabs: List<KeyboardTab>, selected: KeyboardTab) {
        tabRow.removeAllViews()
        tabs.forEachIndexed { i, tab ->
            val on = tab == selected
            val view = when (tab) {
                KeyboardTab.Emoji -> iconTab(R.drawable.ic_kb_emoji, R.string.kb_tab_emoji, on)
                KeyboardTab.Recents -> iconTab(R.drawable.ic_kb_recents, R.string.kb_tab_recents, on)
                KeyboardTab.Favorites -> iconTab(R.drawable.ic_kb_favorites, R.string.kb_tab_favorites, on)
                KeyboardTab.Unsorted -> textTab(context.getString(R.string.kb_tab_unsorted), on)
                is KeyboardTab.Collection -> textTab(tab.name, on)
            }
            view.tag = when (tab) {
                KeyboardTab.Emoji -> KeyboardTags.EMOJI
                KeyboardTab.Recents -> KeyboardTags.RECENTS
                KeyboardTab.Favorites -> KeyboardTags.FAVORITES
                KeyboardTab.Unsorted -> KeyboardTags.UNSORTED
                is KeyboardTab.Collection -> KeyboardTags.collection(tab.id)
            }
            view.isSelected = on
            view.setOnClickListener { actions.onTab(tab) }
            tabRow.addView(view, lp(WRAP, dp(48)).apply { if (i > 0) marginStart = dp(6) })
            if (on) tabRow.post { view.requestRectangleOnScreen(android.graphics.Rect(0, 0, view.width, view.height)) }
        }
    }

    private fun iconTab(icon: Int, label: Int, on: Boolean) = ImageButton(context).apply {
        setImageResource(icon)
        contentDescription = context.getString(label)
        imageTintList = ColorStateList.valueOf(if (on) palette.onLime else palette.muted)
        minimumWidth = dp(48)
        setBackgroundKeepPadding(pill(if (on) palette.lime else Color.TRANSPARENT, visualHeight = 40), dp(12))
        scaleType = android.widget.ImageView.ScaleType.CENTER
    }

    private fun textTab(name: String, on: Boolean) = text(14f, if (on) 800 else 700, if (on) palette.onLime else palette.muted)
        .apply {
            text = name
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(180)
            gravity = Gravity.CENTER
            minWidth = dp(48)
            setBackgroundKeepPadding(pill(if (on) palette.lime else Color.TRANSPARENT, visualHeight = 40), dp(16))
            isClickable = true
            isFocusable = true
        }

    private fun buildChips(chips: List<Pair<Long, String>>, selected: Long?) {
        chipRow.removeAllViews()
        val all = listOf<Pair<Long?, String>>(null to context.getString(R.string.kb_chip_all)) + chips
        all.forEachIndexed { i, (id, name) ->
            val tag = if (id == null) KeyboardTags.CHIP_ALL else KeyboardTags.chip(id)
            addChip(i, name, tag, on = id == selected) { actions.onChip(id) }
        }
    }

    private fun buildEmojiChips(selected: EmojiCategory) {
        chipRow.removeAllViews()
        EmojiCategory.entries.forEachIndexed { i, category ->
            val tag = KeyboardTags.emojiCategory(category.name)
            addChip(i, context.getString(category.label), tag, on = category == selected) {
                actions.onEmojiCategory(category)
            }
        }
    }

    private fun addChip(i: Int, name: String, testTag: String, on: Boolean, onClick: () -> Unit) {
        val chip = text(13f, 700, if (on) palette.onInverse else palette.muted).apply {
            tag = testTag
            text = name
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = dp(160)
            gravity = Gravity.CENTER
            minWidth = dp(48)
            setBackgroundKeepPadding(
                pill(
                    fill = if (on) palette.inverse else Color.TRANSPARENT,
                    visualHeight = 32,
                    stroke = if (on) palette.inverse else palette.line,
                ),
                dp(14),
            )
            isSelected = on
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        chipRow.addView(chip, lp(WRAP, dp(48)).apply { if (i > 0) marginStart = dp(8) })
        if (on) chipRow.post { chip.requestRectangleOnScreen(android.graphics.Rect(0, 0, chip.width, chip.height)) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun bottomRow(): View {
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), 0, dp(10), 0)
        }
        // ABC returns to the letters; holding it opens the system keyboard list (on the letters, hold Space).
        val letters = TextView(context).apply {
            tag = KeyboardTags.LETTERS
            text = context.getString(R.string.kb_page_letters)
            contentDescription = context.getString(R.string.kb_key_letters)
            setTextColor(palette.text)
            textSize = 15f
            applyFigtree(700)
            gravity = Gravity.CENTER
            background = InsetDrawable(rounded(palette.raised, dp(14f)), 0, dp(2), 0, dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener { actions.onLetters() }
            setOnLongClickListener { actions.onSwitchKeyboard(); true }
        }
        val backspace = key(R.drawable.ic_kb_backspace, R.string.kb_backspace, KeyboardTags.BACKSPACE).apply {
            setOnClickListener { actions.onBackspace() }
        }
        val group = LinearLayout(context).apply {
            orientation = HORIZONTAL
            contentDescription = context.getString(R.string.kb_kind_group)
            background = rounded(palette.raised, dp(14f))
            setPadding(dp(3), dp(3), dp(3), dp(3))
        }
        listOf(
            StickerKind.ALL to R.string.kb_kind_all,
            StickerKind.STATIC to R.string.kb_kind_static,
            StickerKind.ANIMATED to R.string.kb_kind_gif,
        ).forEachIndexed { i, (kind, label) ->
            val b = text(13f, 700, palette.muted).apply {
                tag = KeyboardTags.kind(kind.name)
                text = context.getString(label)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                minWidth = dp(48)
                setPadding(dp(14), 0, dp(14), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener { actions.onKind(kind) }
            }
            kindButtons[kind] = b
            group.addView(b, lp(WRAP, dp(42)).apply { if (i > 0) marginStart = dp(2) })
        }
        kindGroup = group
        val center = FrameLayout(context).apply {
            addView(group, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }
        row.addView(letters, lp(dp(64), dp(48)))
        row.addView(center, lp(0, WRAP).apply { weight = 1f; marginStart = dp(6); marginEnd = dp(6) })
        row.addView(backspace, lp(dp(64), dp(48)))
        return row
    }

    private fun key(icon: Int, label: Int, testTag: String) = ImageButton(context).apply {
        tag = testTag
        setImageResource(icon)
        contentDescription = context.getString(label)
        imageTintList = ColorStateList.valueOf(palette.text)
        background = InsetDrawable(rounded(palette.raised, dp(14f)), 0, dp(2), 0, dp(2))
        scaleType = android.widget.ImageView.ScaleType.CENTER
    }

    private fun styleSegment(button: TextView, on: Boolean) {
        button.isSelected = on
        button.setTextColor(if (on) palette.onInverse else palette.muted)
        button.setBackgroundKeepPadding(
            if (on) InsetDrawable(rounded(palette.inverse, dp(11f)), 0, dp(2), 0, dp(2)) else null,
            dp(14),
        )
    }

    /** Setting a background replaces padding with the drawable's insets; put the side padding back. */
    private fun View.setBackgroundKeepPadding(drawable: Drawable?, side: Int) {
        background = drawable
        setPadding(side, 0, side, 0)
    }

    /** A pill drawn [visualHeight] dp tall, centered in a 48dp touch area. */
    private fun pill(fill: Int, visualHeight: Int, stroke: Int? = null): Drawable {
        val inset = dp((48 - visualHeight) / 2)
        val shape = rounded(fill, dp(visualHeight / 2f), if (stroke != null) dp(1) else 0, stroke ?: 0)
        return InsetDrawable(shape, 0, inset, 0, inset)
    }

    private fun text(size: Float, weight: Int, color: Int) = TextView(context).apply {
        textSize = size
        setTextColor(color)
        applyFigtree(weight)
    }

    private fun scroller(child: View) = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(child)
    }

    /** About 40% of the screen in total (UI_SPEC) at Normal height, never less than one row of stickers. */
    private fun middleHeight(): Int = panelMiddleHeight(
        screenPx = resources.displayMetrics.heightPixels,
        chromePx = dp(8 + 48 + 2 + 48 + 10),
        minPx = dp(48 + 96),
        scale = heightScale,
        numberRow = numberRow,
        share = PANEL_SHARE,
    )

    private fun spanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return max(MIN_COLUMNS, ((widthDp - 20) / CELL_DP).toInt())
    }

    private fun emojiSpanCount(): Int {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        return max(MIN_EMOJI_COLUMNS, ((widthDp - 14) / EMOJI_CELL_DP).toInt())
    }

    private fun dp(v: Int) = context.dp(v)
    private fun dp(v: Float) = context.dp(v)

    private class Gap(private val half: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) = outRect.set(half, half, half, half)
    }

    private companion object {
        const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
        const val PANEL_SHARE = 0.40f
        const val MIN_COLUMNS = 4

        /** 5 columns on a 390dp phone, as on the board; more on wide screens. */
        const val CELL_DP = 70f
        const val MIN_EMOJI_COLUMNS = 7
        const val EMOJI_CELL_DP = 48f
        const val HINT_MS = 2500L

        fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
    }
}
