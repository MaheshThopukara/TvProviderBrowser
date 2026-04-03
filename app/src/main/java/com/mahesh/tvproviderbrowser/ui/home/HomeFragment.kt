package com.mahesh.tvproviderbrowser.ui.home

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mahesh.tvproviderbrowser.R
import com.mahesh.tvproviderbrowser.data.model.TvTable
import com.mahesh.tvproviderbrowser.data.model.TvTables
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private const val READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    private val viewModel: HomeViewModel by activityViewModels()

    private lateinit var permissionScreen: View
    private lateinit var homeScreen: View
    private lateinit var tableCardsRow: LinearLayout
    private lateinit var grantPermissionButton: Button
    private lateinit var statDbSizeValue: TextView
    private lateinit var exportAllButton: Button

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) showHomeScreen() else showPermissionScreen()
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        permissionScreen = view.findViewById(R.id.permissionScreen)
        homeScreen = view.findViewById(R.id.homeScreen)
        tableCardsRow = view.findViewById(R.id.tableCardsRow)
        grantPermissionButton = view.findViewById(R.id.grantPermissionButton)
        statDbSizeValue = view.findViewById(R.id.statDbSizeValue)
        exportAllButton = view.findViewById(R.id.exportAllButton)

        grantPermissionButton.setOnClickListener {
            permissionLauncher.launch(READ_TV_LISTINGS)
        }

        exportAllButton.setOnClickListener {
            val action = HomeFragmentDirections.actionHomeToExport(exportMode = "all")
            findNavController().navigate(action)
        }

        buildTableCards()
        updateStats(view)
        observeViewModel()

        if (hasPermission()) {
            showHomeScreen()
        } else {
            showPermissionScreen(requestNow = true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    statDbSizeValue.text = state.dbSizeText
                }
            }
        }
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), READ_TV_LISTINGS) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionScreen(requestNow: Boolean = false) {
        permissionScreen.visibility = View.VISIBLE
        homeScreen.visibility = View.GONE

        val card = (permissionScreen as? ViewGroup)?.getChildAt(0)
        card?.let {
            it.alpha = 0f
            it.scaleX = 0.9f
            it.scaleY = 0.9f
            it.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(500)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }

        if (requestNow) {
            permissionLauncher.launch(READ_TV_LISTINGS)
        }
    }

    private fun showHomeScreen() {
        permissionScreen.visibility = View.GONE
        homeScreen.visibility = View.VISIBLE

        if (viewModel.hasShownHomeBefore) {
            restoreCardFocus()
        } else {
            viewModel.hasShownHomeBefore = true
            animateHomeEntrance()
        }
        viewModel.loadTotalDbSize()
    }

    private fun animateHomeEntrance() {
        val topBar = (homeScreen as LinearLayout).getChildAt(0)
        val hero = (homeScreen as LinearLayout).getChildAt(1)
        val sectionHeader = (homeScreen as LinearLayout).getChildAt(2)
        val cardsScroll = (homeScreen as LinearLayout).getChildAt(3)
        val bottomHint = (homeScreen as LinearLayout).getChildAt(4)

        val views = listOfNotNull(topBar, hero, sectionHeader, cardsScroll, bottomHint)

        views.forEach { v ->
            v.alpha = 0f
            v.translationY = dp(30).toFloat()
        }

        views.forEachIndexed { index, v ->
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(450)
                .setStartDelay(index * 80L)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .start()
        }
    }

    private fun updateStats(view: View) {
        val state = viewModel.uiState.value
        view.findViewById<TextView>(R.id.statTablesCount).text = state.tableCount.toString()
        view.findViewById<TextView>(R.id.statColumnsCount).text = state.totalColumns.toString()
    }

    private fun buildTableCards() {
        tableCardsRow.removeAllViews()

        val bannerColors = mapOf(
            "channels" to intArrayOf(Color.parseColor("#1a3a5c"), Color.parseColor("#0d2747")),
            "programs" to intArrayOf(Color.parseColor("#1a4a3c"), Color.parseColor("#0d3a2c")),
            "preview_programs" to intArrayOf(Color.parseColor("#3a1a5c"), Color.parseColor("#270d47")),
            "recorded_programs" to intArrayOf(Color.parseColor("#5c3a1a"), Color.parseColor("#47270d")),
            "watch_next_programs" to intArrayOf(Color.parseColor("#1a5c5c"), Color.parseColor("#0d4747")),
            "watched_programs" to intArrayOf(Color.parseColor("#4a1a4a"), Color.parseColor("#360d36")),
        )

        TvTables.all.forEachIndexed { index, table ->
            val card = buildTableCard(table, bannerColors[table.id] ?: intArrayOf(Color.DKGRAY, Color.DKGRAY))
            val lp = LinearLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT)
            if (index > 0) lp.marginStart = dp(16)
            tableCardsRow.addView(card, lp)
        }
    }

    private fun buildTableCard(table: TvTable, gradientColors: IntArray): LinearLayout {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_table_card)
            foreground = ContextCompat.getDrawable(ctx, R.drawable.bg_table_card_focus_ring)
            isFocusable = true
            isClickable = true
            clipToOutline = true
        }

        // Banner
        val banner = FrameLayout(ctx).apply {
            val bannerBg = GradientDrawable(GradientDrawable.Orientation.TL_BR, gradientColors)
            bannerBg.cornerRadii = floatArrayOf(
                dp(16).toFloat(), dp(16).toFloat(),
                dp(16).toFloat(), dp(16).toFloat(),
                0f, 0f, 0f, 0f,
            )
            background = bannerBg
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(80))
        }
        val bannerIcon = TextView(ctx).apply {
            text = table.icon
            textSize = 40f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
        }
        banner.addView(bannerIcon)
        card.addView(banner)

        // Body
        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(4))
        }
        val name = TextView(ctx).apply {
            text = table.name
            textSize = 16f
            setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val desc = TextView(ctx).apply {
            text = table.description
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_dim))
            maxLines = 2
        }
        body.addView(name)
        body.addView(desc, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = dp(4) })
        card.addView(body, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
        ))

        // Footer
        val footer = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(14))
        }
        val colsBadge = TextView(ctx).apply {
            text = "${table.dbColumnOrder.size} columns"
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.on_surface_dim))
            setBackgroundResource(R.drawable.bg_cols_badge)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        val arrow = TextView(ctx).apply {
            text = "→"
            textSize = 18f
            setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            alpha = 0f
        }
        footer.addView(colsBadge)
        footer.addView(View(ctx), LinearLayout.LayoutParams(0, 0, 1f))
        footer.addView(arrow)
        card.addView(footer)

        // Focus handling
        card.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                name.setTextColor(ContextCompat.getColor(ctx, R.color.primary))
                arrow.animate().alpha(1f).translationX(0f).setDuration(200).start()
            } else {
                name.setTextColor(ContextCompat.getColor(ctx, R.color.on_surface))
                arrow.animate().alpha(0f).translationX(-dp(8).toFloat()).setDuration(200).start()
            }
        }

        card.setOnClickListener { openTable(table) }

        return card
    }

    private fun openTable(table: TvTable) {
        viewModel.lastOpenedTableIndex = TvTables.all.indexOf(table)
        val action = HomeFragmentDirections.actionHomeToTableData(tableId = table.id)
        findNavController().navigate(action)
    }

    private fun restoreCardFocus() {
        val targetIndex = if (viewModel.lastOpenedTableIndex in 0 until tableCardsRow.childCount) {
            viewModel.lastOpenedTableIndex
        } else {
            0
        }
        if (tableCardsRow.childCount > targetIndex) {
            tableCardsRow.getChildAt(targetIndex).post {
                tableCardsRow.getChildAt(targetIndex).requestFocus()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}

