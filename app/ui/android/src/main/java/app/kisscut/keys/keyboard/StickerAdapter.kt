package app.kisscut.keys.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.load
import coil3.request.crossfade
import coil3.dispose
import app.kisscut.keys.R
import app.kisscut.keys.shared.db.Sticker
import java.io.File

/** Test tags shared by the panel and its tests. */
object KeyboardTags {
    fun sticker(id: Long) = "sticker:$id"
    const val RECENTS = "tab:recents"
    const val FAVORITES = "tab:favorites"
    const val UNSORTED = "tab:unsorted"
    fun collection(id: Long) = "tab:collection:$id"
    const val CHIP_ALL = "chip:all"
    fun chip(id: Long) = "chip:$id"
    fun kind(name: String) = "kind:$name"
    const val GRID = "kb:grid"
    const val GRID_EMOJI = "kb:grid-emoji"
    const val MESSAGE = "kb:message"
    const val OPEN_APP = "kb:open-app"
    const val HINT = "kb:hint"
    const val BACKSPACE = "kb:backspace"
    /** ABC key on the sticker panel. */
    const val LETTERS = "kb:letters"

    // Typing keyboard
    fun key(text: String) = "key:$text"
    /** One choice in the long-press alternates row. */
    fun alternate(text: String) = "alt:$text"

    // Emoji tab
    const val EMOJI = "tab:emoji"
    fun emojiCategory(name: String) = "emoji-cat:$name"
    fun emoji(text: String) = "emoji:$text"
    fun page(name: String) = "key:page:$name"
    const val SPACE = "key:space"
    const val SHIFT = "key:shift"
    const val KEY_BACKSPACE = "key:backspace"
    const val ENTER = "key:enter"
    const val STICKERS = "key:stickers"

    // Toolbar above the letters (Settings, Toolbar)
    const val TOOLBAR = "toolbar"
    const val TOOLBAR_STICKERS = "toolbar:stickers"
    const val TOOLBAR_EMOJI = "toolbar:emoji"
    const val TOOLBAR_SETTINGS = "toolbar:settings"
    const val TOOLBAR_SWITCH = "toolbar:switch"
}

/**
 * Sticker tiles for the panel grid. Thumbnails only (docs/KEYBOARD.md, Performance); animated stickers show
 * their first frame, like the Library, until playback-while-visible lands in M9.
 */
class StickerAdapter(
    private val palette: KeyboardPalette,
    private val thumbnailOf: (Sticker) -> File?,
    private val onTap: (Sticker) -> Unit,
) : ListAdapter<Sticker, StickerAdapter.Holder>(Diff) {

    /** Collection names for content descriptions. */
    var collectionNames: Map<Long, String> = emptyMap()

    /** False in password fields: tiles stay visible but do nothing. */
    var enabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                notifyItemRangeChanged(0, itemCount)
            }
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Holder(Tile(parent.context, palette))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val sticker = getItem(position)
        val tile = holder.tile
        val ctx = tile.context
        tile.tag = KeyboardTags.sticker(sticker.id)
        tile.background = rounded(palette.tint(sticker.id), ctx.dp(16f))
        tile.isEnabled = enabled
        tile.alpha = if (enabled) 1f else 0.4f
        tile.setOnClickListener { if (enabled) onTap(sticker) }

        val name = sticker.collectionId?.let { collectionNames[it] }
        tile.contentDescription = buildList {
            add(if (name != null) ctx.getString(R.string.cd_sticker_in, name) else ctx.getString(R.string.cd_sticker))
            if (sticker.isAnimated) add(ctx.getString(R.string.cd_animated))
        }.joinToString(", ")

        val file = thumbnailOf(sticker)
        val cutStill = sticker.hasAlpha && !sticker.isAnimated
        tile.bind(file, mode = if (!sticker.hasAlpha) Tile.Mode.FILL else Tile.Mode.FIT, shadow = cutStill)
        tile.badge.visibility = if (sticker.isAnimated) View.VISIBLE else View.GONE
    }

    override fun onViewRecycled(holder: Holder) {
        holder.tile.image.dispose()
        holder.tile.shadow.dispose()
    }

    class Holder(val tile: Tile) : RecyclerView.ViewHolder(tile)

    /** A square tile: tinted rounded fill, the sticker, an optional shadow and a GIF badge. */
    class Tile(context: Context, palette: KeyboardPalette) : FrameLayout(context) {
        enum class Mode { FIT, FILL }

        val shadow = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            colorFilter = PorterDuffColorFilter(
                Color.argb((palette.shadowAlpha * 255).toInt(), 0, 0, 0),
                PorterDuff.Mode.SRC_IN,
            )
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        val image = ImageView(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
        val badge = TextView(context).apply {
            text = context.getString(R.string.save_badge_gif)
            setTextColor(palette.badgeText)
            textSize = 10f
            letterSpacing = 0.05f
            applyFigtree(800)
            background = rounded(palette.badgeFill, context.dp(8f))
            setPadding(context.dp(7), context.dp(2), context.dp(7), context.dp(2))
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        init {
            clipToOutline = true
            isClickable = true
            isFocusable = true
            minimumHeight = context.dp(48)
            addView(shadow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(image, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            addView(
                badge,
                LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.START)
                    .apply { setMargins(context.dp(5), 0, 0, context.dp(5)) },
            )
        }

        fun bind(file: File?, mode: Mode, shadow: Boolean) {
            val inset = if (mode == Mode.FIT) INSET else 0f
            image.scaleType = if (mode == Mode.FIT) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            image.tag = inset
            this.shadow.tag = inset
            this.shadow.visibility = if (shadow) View.VISIBLE else View.GONE
            image.load(file) { crossfade(false) }
            if (shadow) this.shadow.load(file) { crossfade(false) } else this.shadow.dispose()
            requestLayout()
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Square: height follows width.
            super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            val size = right - left
            for (v in listOf(shadow, image)) {
                val pad = ((v.tag as? Float ?: 0f) * size).toInt()
                val dy = if (v === shadow) context.dp(2) else 0
                v.layout(pad, pad + dy, size - pad, size - pad + dy)
            }
        }

        private companion object {
            const val INSET = 0.12f
        }
    }

    private object Diff : DiffUtil.ItemCallback<Sticker>() {
        override fun areItemsTheSame(a: Sticker, b: Sticker) = a.id == b.id
        override fun areContentsTheSame(a: Sticker, b: Sticker) = a == b
    }
}

internal fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
internal fun Context.dp(v: Float): Float = v * resources.displayMetrics.density

/** Panel fill with rounded top corners and a 1dp top line (Keyboard board). Shared by both views. */
internal fun panelBackground(context: Context, palette: KeyboardPalette): android.graphics.drawable.Drawable {
    val r = context.dp(28f)
    fun top(color: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
    }
    return android.graphics.drawable.LayerDrawable(
        arrayOf(top(palette.line), android.graphics.drawable.InsetDrawable(top(palette.panel), 0, context.dp(1), 0, 0)),
    )
}

internal fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

/** Figtree (bundled variable font) at [weight]. */
internal fun TextView.applyFigtree(weight: Int) {
    typeface = KeyboardFonts.figtree(context)
    fontVariationSettings = "'wght' $weight"
}

internal object KeyboardFonts {
    private var cached: android.graphics.Typeface? = null
    fun figtree(context: Context): android.graphics.Typeface? =
        cached ?: androidx.core.content.res.ResourcesCompat.getFont(context, R.font.figtree).also { cached = it }
}
