package com.mahesh.tvproviderbrowser.ui.tabledata

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mahesh.tvproviderbrowser.R
import com.mahesh.tvproviderbrowser.data.repository.TvProviderRepository
import com.mahesh.tvproviderbrowser.ui.components.LeanbackTableView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TableDataFragment : Fragment(R.layout.fragment_table_data) {

    companion object {
        private const val ALPHA_ENABLED = 1.0f
        private const val ALPHA_DISABLED = 0.35f
        /** Must match the longest duration in nav_enter.xml / nav_exit.xml */
        private const val NAV_ANIM_DURATION_MS = 300L
    }

    private val viewModel: TableDataViewModel by viewModels()

    private lateinit var titleView: TextView
    private lateinit var breadcrumbCurrent: TextView
    private lateinit var titleIconEmoji: TextView
    private lateinit var metaRows: TextView
    private lateinit var metaColumns: TextView
    private lateinit var metaPage: TextView
    private lateinit var metaSize: TextView
    private lateinit var statusView: TextView
    private lateinit var prevButton: Button
    private lateinit var nextButton: Button
    private lateinit var tableContainer: FrameLayout
    private lateinit var tableView: LeanbackTableView
    private lateinit var pageDots: LinearLayout
    private lateinit var loadingOverlay: LinearLayout
    private lateinit var emptyOverlay: LinearLayout
    private lateinit var emptySubtext: TextView
    // Detail overlay
    private lateinit var detailOverlay: FrameLayout
    private lateinit var detailPanel: LinearLayout
    private lateinit var detailRowBadge: TextView
    private lateinit var detailBody: LinearLayout
    private lateinit var detailCloseBtn: Button
    private lateinit var detailScrollView: ScrollView

    private var loadingPulse: ObjectAnimator? = null

    private val backPressedCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            hideDetail()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupScreen(view)
        observeViewModel()

        // Wait for the navigation enter animation to finish before loading data.
        view.postDelayed({ if (isAdded) viewModel.loadPage(0) }, NAV_ANIM_DURATION_MS)

        // Intercept key events on the root view for detail overlay dismiss
        view.isFocusableInTouchMode = true
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_BACK)
            ) {
                if (detailOverlay.visibility == View.VISIBLE) {
                    hideDetail()
                    return@setOnKeyListener true
                }
            }
            false
        }
    }

    // ── Observe ViewModel state ─────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.pageState.collect { state ->
                    when (state) {
                        is PageState.Loading -> showLoadingState()
                        is PageState.Success -> showSuccessState(state)
                        is PageState.Error -> showErrorState(state.message)
                    }
                }
            }
        }
    }

    private fun showLoadingState() {
        prevButton.alpha = if (viewModel.currentPage > 0) ALPHA_ENABLED else ALPHA_DISABLED
        nextButton.alpha = ALPHA_DISABLED
        statusView.text = "Loading..."

        loadingOverlay.visibility = View.VISIBLE
        emptyOverlay.visibility = View.GONE
        loadingOverlay.alpha = 1f

        loadingPulse?.cancel()
        loadingPulse = ObjectAnimator.ofFloat(statusView, "alpha", 1f, 0.3f).apply {
            duration = 600
            repeatMode = ObjectAnimator.REVERSE
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun showSuccessState(state: PageState.Success) {
        loadingPulse?.cancel()
        loadingPulse = null
        statusView.alpha = 1f

        // Hide loading overlay with a fade-out
        loadingOverlay.animate()
            .alpha(0f)
            .setDuration(200)
            .withEndAction { loadingOverlay.visibility = View.GONE }
            .start()

        val result = state.result
        val total = state.totalCount
        val isEmpty = result.rows.isEmpty()

        if (isEmpty) {
            emptyOverlay.visibility = View.VISIBLE
            emptySubtext.text = "This table has no rows yet"
        } else {
            emptyOverlay.visibility = View.GONE
        }

        tableView.bind(
            columns = result.columns,
            rows = result.rows,
            page = state.page,
            pageSize = TvProviderRepository.PAGE_SIZE,
        )

        val totalText = total?.toString() ?: "?"
        val pageSize = TvProviderRepository.PAGE_SIZE
        val rangeStart = state.page * pageSize + 1
        val rangeEnd = state.page * pageSize + result.rows.size

        metaRows.text = "📊 $totalText rows"
        metaColumns.text = "📐 ${result.columns.size} columns"
        val totalPages = if (total != null && total > 0) ((total + pageSize - 1) / pageSize) else 1
        metaPage.text = "📄 Page ${state.page + 1} of $totalPages"
        metaSize.text = "💾 ≈ ${TableDataViewModel.formatStorageSize(state.sizeBytes)}"

        if (isEmpty) {
            statusView.text = "No data in this table"
        } else {
            statusView.text = "Showing $rangeStart–$rangeEnd of $totalText rows"
        }

        nextButton.alpha = if (state.hasNextPage) ALPHA_ENABLED else ALPHA_DISABLED
        prevButton.alpha = if (state.page > 0) ALPHA_ENABLED else ALPHA_DISABLED

        updatePageDots(totalPages)

        if (!state.isInitialLoad) animatePageTransition(state.isForward)
    }

    private fun showErrorState(message: String) {
        loadingPulse?.cancel()
        loadingPulse = null
        statusView.alpha = 1f
        statusView.text = "Error: $message"
        tableView.bind(emptyList(), emptyList(), viewModel.currentPage, TvProviderRepository.PAGE_SIZE)
        nextButton.alpha = ALPHA_DISABLED
        loadingOverlay.visibility = View.GONE
        emptyOverlay.visibility = View.VISIBLE
        emptySubtext.text = "Error loading data: $message"
    }

    // ── View binding & setup ────────────────────────────────────────────

    private fun bindViews(view: View) {
        view.findViewById<ImageButton>(R.id.backButton).setOnClickListener {
            findNavController().popBackStack()
        }
        titleView = view.findViewById(R.id.tableTitle)
        breadcrumbCurrent = view.findViewById(R.id.breadcrumbCurrent)
        titleIconEmoji = view.findViewById(R.id.titleIconEmoji)
        metaRows = view.findViewById(R.id.metaRows)
        metaColumns = view.findViewById(R.id.metaColumns)
        metaPage = view.findViewById(R.id.metaPage)
        metaSize = view.findViewById(R.id.metaSize)
        statusView = view.findViewById(R.id.statusText)
        prevButton = view.findViewById(R.id.previousButton)
        nextButton = view.findViewById(R.id.nextButton)
        tableContainer = view.findViewById(R.id.tableContainer)
        pageDots = view.findViewById(R.id.pageDots)
        loadingOverlay = view.findViewById(R.id.loadingOverlay)
        emptyOverlay = view.findViewById(R.id.emptyOverlay)
        emptySubtext = view.findViewById(R.id.emptySubtext)

        // Detail overlay
        detailOverlay = view.findViewById(R.id.detailOverlay)
        detailPanel = view.findViewById(R.id.detailPanel)
        detailRowBadge = view.findViewById(R.id.detailRowBadge)
        detailBody = view.findViewById(R.id.detailBody)
        detailCloseBtn = view.findViewById(R.id.detailCloseBtn)
        detailScrollView = view.findViewById(R.id.detailScrollView)
    }

    private fun setupScreen(view: View) {
        val table = viewModel.table
        titleView.text = table.name
        breadcrumbCurrent.text = table.name
        titleIconEmoji.text = table.icon

        val bannerColors = mapOf(
            "channels" to intArrayOf(Color.parseColor("#1a3a5c"), Color.parseColor("#0d2747")),
            "programs" to intArrayOf(Color.parseColor("#1a4a3c"), Color.parseColor("#0d3a2c")),
            "preview_programs" to intArrayOf(Color.parseColor("#3a1a5c"), Color.parseColor("#270d47")),
            "recorded_programs" to intArrayOf(Color.parseColor("#5c3a1a"), Color.parseColor("#47270d")),
            "watch_next_programs" to intArrayOf(Color.parseColor("#1a5c5c"), Color.parseColor("#0d4747")),
            "watched_programs" to intArrayOf(Color.parseColor("#4a1a4a"), Color.parseColor("#360d36")),
        )
        bannerColors[table.id]?.let { colors ->
            val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
            bg.cornerRadius = dp(16).toFloat()
            view.findViewById<FrameLayout>(R.id.titleIcon).background = bg
        }

        val backButton = view.findViewById<ImageButton>(R.id.backButton)

        tableView = LeanbackTableView(
            context = requireContext(),
            cellWidthPx = dp(180),
            rowNumWidthPx = dp(64),
            onRowClick = { rowNumber, rowValues -> showRowDetail(rowNumber, rowValues) },
        )
        tableContainer.addView(
            tableView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        tableView.setEdgeFocusTargets(topTarget = backButton, prevButton, nextButton)

        prevButton.setOnClickListener { viewModel.previousPage() }
        nextButton.setOnClickListener { viewModel.nextPage() }

        backButtonFocusBridge(backButton)
        previousButtonFocusBridge(prevButton)
        nextButtonFocusBridge(nextButton)

        // Detail close handlers
        detailCloseBtn.setOnClickListener { hideDetail() }
        detailOverlay.setOnClickListener { hideDetail() }
        detailPanel.setOnClickListener { /* consume click to prevent close */ }

        // Trap focus inside detail panel when overlay is visible
        setupDetailFocusTrapping()

        backButton.post { backButton.requestFocus() }
    }

    // ── UI helpers ──────────────────────────────────────────────────────

    private fun backButtonFocusBridge(backButton: View) {
        backButton.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                tableView.requestTableFocus()
            } else {
                false
            }
        }
    }

    private fun previousButtonFocusBridge(button: Button) {
        button.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> tableView.requestTableFocus()
                KeyEvent.KEYCODE_DPAD_RIGHT -> nextButton.requestFocus()
                else -> false
            }
        }
    }

    private fun nextButtonFocusBridge(button: Button) {
        button.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> tableView.requestTableFocus()
                KeyEvent.KEYCODE_DPAD_LEFT -> prevButton.requestFocus()
                else -> false
            }
        }
    }

    private fun animatePageTransition(forward: Boolean) {
        val direction = if (forward) 1f else -1f
        tableContainer.alpha = 0f
        tableContainer.translationX = direction * dp(40).toFloat()
        tableContainer.animate()
            .alpha(1f)
            .translationX(0f)
            .setDuration(300)
            .setInterpolator(DecelerateInterpolator(1.3f))
            .start()
    }

    private fun updatePageDots(totalPages: Int) {
        pageDots.removeAllViews()
        val ctx = requireContext()
        val dotCount = minOf(totalPages, 7)
        for (i in 0 until dotCount) {
            val dot = View(ctx)
            val isActive = i == viewModel.currentPage
            if (isActive) {
                dot.setBackgroundResource(R.drawable.bg_page_dot_active)
                dot.layoutParams = LinearLayout.LayoutParams(dp(24), dp(8)).apply {
                    marginStart = if (i > 0) dp(6) else 0
                }
            } else {
                dot.setBackgroundResource(R.drawable.bg_page_dot)
                dot.layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    marginStart = if (i > 0) dp(6) else 0
                }
            }
            pageDots.addView(dot)

            dot.alpha = 0f
            dot.animate()
                .alpha(1f)
                .setDuration(200)
                .setStartDelay(i * 40L)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    // ── Detail panel ────────────────────────────────────────────────────

    private fun setupDetailFocusTrapping() {
        detailOverlay.setOnKeyListener { _, keyCode, event ->
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT -> true
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE -> {
                    hideDetail()
                    true
                }
                else -> false
            }
        }

        detailCloseBtn.nextFocusUpId = R.id.detailCloseBtn
        detailCloseBtn.nextFocusLeftId = R.id.detailCloseBtn
        detailCloseBtn.nextFocusRightId = R.id.detailCloseBtn
        detailCloseBtn.nextFocusDownId = R.id.detailScrollView

        detailScrollView.isFocusable = true
        detailScrollView.isFocusableInTouchMode = true
        detailScrollView.nextFocusUpId = R.id.detailCloseBtn
        detailScrollView.nextFocusDownId = R.id.detailCloseBtn
        detailScrollView.nextFocusLeftId = R.id.detailScrollView
        detailScrollView.nextFocusRightId = R.id.detailScrollView
    }

    private fun showRowDetail(rowNumber: Int, rowValues: List<String?>) {
        val ctx = requireContext()
        detailRowBadge.text = "#$rowNumber"
        detailBody.removeAllViews()

        val columns = tableView.currentColumns
        columns.forEachIndexed { index, col ->
            val value = rowValues.getOrNull(index)

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, dp(12))
                gravity = Gravity.TOP
            }

            val keyView = TextView(ctx).apply {
                text = col
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(ContextCompat.getColor(ctx, R.color.secondary))
                layoutParams = LinearLayout.LayoutParams(dp(180), ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(0, dp(2), dp(16), 0)
            }

            val valueView = TextView(ctx).apply {
                textSize = 14f
                maxLines = 4
                setLineSpacing(dp(3).toFloat(), 1f)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                when {
                    value == null -> {
                        text = "null"
                        setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_dim))
                        setTypeface(typeface, Typeface.ITALIC)
                    }
                    value.startsWith("BLOB(") -> {
                        text = value
                        setTextColor(ContextCompat.getColor(ctx, R.color.tertiary))
                    }
                    else -> {
                        text = value
                        setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
                    }
                }
            }

            row.addView(keyView)
            row.addView(valueView)
            detailBody.addView(row)

            if (index < columns.size - 1) {
                val divider = View(ctx).apply {
                    setBackgroundColor(Color.parseColor("#08FFFFFF"))
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 1,
                    )
                }
                detailBody.addView(divider)
            }
        }

        detailOverlay.visibility = View.VISIBLE
        detailOverlay.alpha = 0f

        detailPanel.translationX = dp(560).toFloat()
        detailPanel.scaleX = 0.95f
        detailPanel.scaleY = 0.95f

        ObjectAnimator.ofFloat(detailOverlay, "alpha", 0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            start()
        }

        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(detailPanel, "translationX", dp(560).toFloat(), 0f),
                ObjectAnimator.ofFloat(detailPanel, "scaleX", 0.95f, 1f),
                ObjectAnimator.ofFloat(detailPanel, "scaleY", 0.95f, 1f),
            )
            duration = 380
            interpolator = DecelerateInterpolator(1.5f)
            start()
        }

        backPressedCallback.isEnabled = true
        detailCloseBtn.requestFocus()
    }

    private fun hideDetail() {
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(detailPanel, "translationX", 0f, dp(560).toFloat()),
                ObjectAnimator.ofFloat(detailPanel, "scaleX", 1f, 0.95f),
                ObjectAnimator.ofFloat(detailPanel, "scaleY", 1f, 0.95f),
            )
            duration = 280
            interpolator = AccelerateInterpolator(1.2f)
            start()
        }
        detailOverlay.animate()
            .alpha(0f)
            .setDuration(220)
            .withEndAction {
                detailOverlay.visibility = View.GONE
                backPressedCallback.isEnabled = false
                tableView.requestTableFocus()
            }
            .start()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

