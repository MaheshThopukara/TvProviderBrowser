package com.mahesh.tvproviderbrowser.ui.components

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.mahesh.tvproviderbrowser.R
import kotlin.math.max

class LeanbackTableView(
    context: Context,
    private val cellWidthPx: Int,
    private val rowNumWidthPx: Int,
    private val onRowClick: (Int, List<String?>) -> Unit,
) : LinearLayout(context) {

    private var topFocusTarget: View? = null
    private var bottomFocusTargets: List<View> = emptyList()

    val currentColumns: List<String>
        get() = adapter.columns

    /** Try each bottom target in order; return the first one that accepts focus. */
    private fun focusFirstBottomTarget(): Boolean {
        for (target in bottomFocusTargets) {
            if (target.isShown && target.isFocusable && target.requestFocus()) return true
        }
        return false
    }

    /** Return the first visible+focusable bottom target (for focusSearch). */
    private fun firstAvailableBottomTarget(): View? =
        bottomFocusTargets.firstOrNull { it.isShown && it.isFocusable }

    /** Return the ID of the first available bottom target. */
    private fun firstBottomFocusId(): Int =
        firstAvailableBottomTarget()?.id ?: View.NO_ID

    private val horizontalScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = true
    }

    private val contentColumn = LinearLayout(context).apply {
        orientation = VERTICAL
    }

    private val headerRow = LinearLayout(context).apply {
        orientation = HORIZONTAL
        setBackgroundColor(color(R.color.surface_elevated))
    }

    private val divider = View(context).apply {
        setBackgroundColor(color(R.color.grid_line_header))
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(1))
    }

    private val listView = object : VerticalGridView(context) {
        override fun focusSearch(focused: View?, direction: Int): View? {
            if (focused != null) {
                when (direction) {
                    View.FOCUS_UP -> {
                        if (isFirstAdapterPosition(focused)) {
                            return topFocusTarget ?: super.focusSearch(focused, direction)
                        }
                    }
                    View.FOCUS_DOWN -> {
                        if (isLastAdapterPosition(focused)) {
                            return firstAvailableBottomTarget()
                                ?: super.focusSearch(focused, direction)
                        }
                    }
                }
            }
            return super.focusSearch(focused, direction)
        }
    }.apply {
        setNumColumns(1)
        isVerticalScrollBarEnabled = true
        overScrollMode = OVER_SCROLL_NEVER
        descendantFocusability = FOCUS_AFTER_DESCENDANTS
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, 0, 1f)
    }

    private val adapter = LeanbackTableAdapter(
        cellWidthPx = cellWidthPx,
        rowNumWidthPx = rowNumWidthPx,
        onRowClick = onRowClick,
        onMoveUpFromFirst = { topFocusTarget?.requestFocus() == true },
        onMoveDownFromLast = { focusFirstBottomTarget() },
        topFocusId = { topFocusTarget?.id ?: View.NO_ID },
        bottomFocusId = { firstBottomFocusId() },
    )

    private val horizontalStepPx = max(cellWidthPx, dp(140))

    init {
        orientation = VERTICAL

        horizontalScroll.addView(
            contentColumn,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT),
        )

        contentColumn.addView(headerRow)
        contentColumn.addView(divider)
        contentColumn.addView(listView)

        addView(
            horizontalScroll,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )

        listView.adapter = adapter
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isFocusInsideTable()) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        horizontalScroll.smoothScrollBy(horizontalStepPx, 0)
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (event.action == KeyEvent.ACTION_DOWN) {
                        horizontalScroll.smoothScrollBy(-horizontalStepPx, 0)
                    }
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun isFirstAdapterPosition(focused: View): Boolean {
        val itemView = listView.findContainingItemView(focused) ?: return false
        return listView.getChildAdapterPosition(itemView) == 0
    }

    private fun isLastAdapterPosition(focused: View): Boolean {
        val itemView = listView.findContainingItemView(focused) ?: return false
        val position = listView.getChildAdapterPosition(itemView)
        return position >= 0 && position == adapter.itemCount - 1
    }

    private fun isFocusInsideTable(): Boolean {
        val focused = findFocus() ?: return false
        var current: View? = focused
        while (current != null) {
            if (current === listView) return true
            current = (current.parent as? View)
        }
        return false
    }

    fun setEdgeFocusTargets(topTarget: View?, vararg bottomTargets: View) {
        topFocusTarget = topTarget
        bottomFocusTargets = bottomTargets.toList()
        listView.nextFocusUpId = topTarget?.id ?: View.NO_ID
        listView.nextFocusDownId = firstBottomFocusId()
        adapter.notifyDataSetChanged()
    }

    fun requestTableFocus(): Boolean = listView.requestFocus()

    fun bind(columns: List<String>, rows: List<List<String?>>, page: Int, pageSize: Int) {
        val contentWidth = rowNumWidthPx + (columns.size * cellWidthPx)
        headerRow.layoutParams = LayoutParams(contentWidth, LayoutParams.WRAP_CONTENT)
        (listView.layoutParams as LayoutParams).width = contentWidth

        bindHeader(columns)
        adapter.submit(columns = columns, rows = rows, page = page, pageSize = pageSize)

        // Subtle fade-in when table content refreshes
        listView.alpha = 0f
        listView.animate()
            .alpha(1f)
            .setDuration(250)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun bindHeader(columns: List<String>) {
        if (headerRow.childCount == columns.size + 1) {
            val first = headerRow.getChildAt(0) as? TextView
            if (first?.text == "#") return
        }

        headerRow.removeAllViews()
        headerRow.addView(
            makeCell(
                text = "#",
                widthPx = rowNumWidthPx,
                gravity = Gravity.CENTER,
                textColor = color(R.color.primary),
                bold = true,
            ),
        )
        columns.forEach { col ->
            headerRow.addView(
                makeCell(
                    text = col.uppercase(),
                    widthPx = cellWidthPx,
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL,
                    textColor = color(R.color.on_surface_variant),
                    bold = true,
                ),
            )
        }
    }

    private fun makeCell(
        text: String,
        widthPx: Int,
        gravity: Int,
        textColor: Int,
        bold: Boolean,
        italic: Boolean = false,
    ): TextView = TextView(context).apply {
        layoutParams = LayoutParams(widthPx, LayoutParams.WRAP_CONTENT)
        setPadding(dp(10), dp(14), dp(10), dp(14))
        this.text = text
        this.gravity = gravity
        setTextColor(textColor)
        textSize = 13f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        letterSpacing = 0.04f
        typeface = when {
            bold && italic -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC)
            bold -> Typeface.DEFAULT_BOLD
            italic -> Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            else -> Typeface.DEFAULT
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun color(id: Int): Int = ContextCompat.getColor(context, id)
}

private class LeanbackTableAdapter(
    private val cellWidthPx: Int,
    private val rowNumWidthPx: Int,
    private val onRowClick: (Int, List<String?>) -> Unit,
    private val onMoveUpFromFirst: () -> Boolean,
    private val onMoveDownFromLast: () -> Boolean,
    private val topFocusId: () -> Int,
    private val bottomFocusId: () -> Int,
) : RecyclerView.Adapter<LeanbackTableAdapter.RowViewHolder>() {

    var columns: List<String> = emptyList()
        private set

    private var data: List<Pair<Int, List<String?>>> = emptyList()

    fun submit(columns: List<String>, rows: List<List<String?>>, page: Int, pageSize: Int) {
        this.columns = columns
        this.data = rows.mapIndexed { index, row ->
            (page * pageSize + index + 1) to row
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowViewHolder {
        val context = parent.context
        val rowContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(dpPx(context, 4), dpPx(context, 2), dpPx(context, 4), dpPx(context, 2))
            layoutParams = RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.WRAP_CONTENT,
                RecyclerView.LayoutParams.WRAP_CONTENT,
            )
        }
        return RowViewHolder(rowContainer, cellWidthPx, rowNumWidthPx)
    }

    override fun onBindViewHolder(holder: RowViewHolder, position: Int) {
        val (rowNumber, rowValues) = data[position]
        val isEven = position % 2 == 0
        holder.bind(
            rowNumber = rowNumber,
            rowValues = rowValues,
            cellWidthPx = cellWidthPx,
            rowNumWidthPx = rowNumWidthPx,
            isEven = isEven,
            isFirst = position == 0,
            isLast = position == itemCount - 1,
            onClick = { onRowClick(rowNumber, rowValues) },
            onMoveUpFromFirst = onMoveUpFromFirst,
            onMoveDownFromLast = onMoveDownFromLast,
            topFocusId = topFocusId(),
            bottomFocusId = bottomFocusId(),
        )
    }

    override fun getItemCount(): Int = data.size

    class RowViewHolder(
        private val row: LinearLayout,
        private val cellWidthPx: Int,
        private val rowNumWidthPx: Int,
    ) : RecyclerView.ViewHolder(row) {

        /** Pre-allocated cell TextViews, reused across binds to avoid removeAllViews(). */
        private val cellPool = mutableListOf<TextView>()
        private var rowNumCell: TextView? = null

        fun bind(
            rowNumber: Int,
            rowValues: List<String?>,
            cellWidthPx: Int,
            rowNumWidthPx: Int,
            isEven: Boolean,
            isFirst: Boolean,
            isLast: Boolean,
            onClick: () -> Unit,
            onMoveUpFromFirst: () -> Boolean,
            onMoveDownFromLast: () -> Boolean,
            topFocusId: Int,
            bottomFocusId: Int,
        ) {
            val context = row.context
            val neededCells = rowValues.size
            val totalChildren = 1 + neededCells // row number + data cells

            // Ensure the row number cell exists
            if (rowNumCell == null) {
                rowNumCell = makeCell(
                    context = context,
                    text = "",
                    widthPx = rowNumWidthPx,
                    gravity = Gravity.CENTER,
                    textColor = color(context, R.color.primary),
                    bold = true,
                )
                row.addView(rowNumCell, 0)
            }
            rowNumCell!!.text = rowNumber.toString()

            // Grow or shrink the cell pool to match the column count
            while (cellPool.size < neededCells) {
                val cell = makeCell(
                    context = context,
                    text = "",
                    widthPx = cellWidthPx,
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL,
                    textColor = color(context, R.color.on_surface),
                )
                cellPool.add(cell)
                row.addView(cell)
            }
            // Hide excess cells if column count shrank
            for (i in 0 until cellPool.size) {
                cellPool[i].visibility = if (i < neededCells) View.VISIBLE else View.GONE
            }

            // Rebind cell contents — no view inflation, just text/color updates
            rowValues.forEachIndexed { i, value ->
                val cell = cellPool[i]
                val isNull = value == null
                val isBlob = value?.startsWith("BLOB(") == true
                cell.text = value ?: "null"
                cell.setTextColor(
                    when {
                        isNull -> color(context, R.color.on_surface_dim)
                        isBlob -> color(context, R.color.tertiary)
                        else -> color(context, R.color.on_surface)
                    },
                )
                cell.typeface = if (isNull) {
                    Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
                } else {
                    Typeface.DEFAULT
                }
            }

            row.setBackgroundColor(
                if (isEven) color(context, R.color.row_even) else color(context, R.color.row_odd),
            )

            row.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    row.setBackgroundResource(R.drawable.bg_table_row_focus)
                } else {
                    row.background = null
                    row.setBackgroundColor(
                        if (isEven) color(context, R.color.row_even) else color(context, R.color.row_odd),
                    )
                }
            }

            row.setOnClickListener { onClick() }

            row.nextFocusUpId = if (isFirst) topFocusId else View.NO_ID
            row.nextFocusDownId = if (isLast) bottomFocusId else View.NO_ID

            row.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> if (isFirst) onMoveUpFromFirst() else false
                    KeyEvent.KEYCODE_DPAD_DOWN -> if (isLast) onMoveDownFromLast() else false
                    else -> false
                }
            }
        }

        private fun makeCell(
            context: Context,
            text: String,
            widthPx: Int,
            gravity: Int,
            textColor: Int,
            bold: Boolean = false,
        ): TextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding(dpPx(context, 10), dpPx(context, 12), dpPx(context, 10), dpPx(context, 12))
            this.text = text
            this.gravity = gravity
            setTextColor(textColor)
            textSize = 14f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
    }
}

private fun color(context: Context, id: Int): Int = ContextCompat.getColor(context, id)

private fun dpPx(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
