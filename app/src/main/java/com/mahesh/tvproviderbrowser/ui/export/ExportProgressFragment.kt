package com.mahesh.tvproviderbrowser.ui.export

import android.os.Bundle
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.mahesh.tvproviderbrowser.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Full-screen export progress screen.
 *
 * Flow:
 * 1. Shows the [ExportLocationPickerFragment] dialog automatically.
 * 2. Displays a progress indicator while data is being queried and written.
 * 3. Shows the exact file path on success, or an error message on failure.
 */
@AndroidEntryPoint
class ExportProgressFragment : Fragment(R.layout.fragment_export_progress) {

    private val viewModel: ExportProgressViewModel by viewModels()

    private lateinit var exportCard: View
    private lateinit var exportIcon: TextView
    private lateinit var exportTitle: TextView
    private lateinit var exportSubtitle: TextView
    private lateinit var exportStatus: TextView
    private lateinit var exportProgress: ProgressBar
    private lateinit var pathContainer: LinearLayout
    private lateinit var exportFilePath: TextView
    private lateinit var exportRowCount: TextView
    private lateinit var exportChooseButton: Button
    private lateinit var exportDoneButton: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindViews(view)
        setupSubtitle()

        exportDoneButton.setOnClickListener { findNavController().popBackStack() }
        exportChooseButton.setOnClickListener { showLocationPicker() }

        // Listen for location picker result
        requireActivity().supportFragmentManager.setFragmentResultListener(
            ExportLocationPickerFragment.RESULT_KEY,
            viewLifecycleOwner,
        ) { _, bundle ->
            val locationType = bundle.getString(ExportLocationPickerFragment.LOCATION_TYPE_KEY)
            if (locationType == ExportLocationPickerFragment.TYPE_CANCELLED) {
                // User cancelled the picker — go back
                findNavController().popBackStack()
                return@setFragmentResultListener
            }
            val filePath = bundle.getString(ExportLocationPickerFragment.FILE_PATH_KEY)
            viewModel.startExport(
                locationType ?: ExportLocationPickerFragment.TYPE_DOWNLOADS,
                filePath,
            )
        }

        observeState()
        animateEntrance()
    }

    private fun bindViews(view: View) {
        exportCard = view.findViewById(R.id.exportCard)
        exportIcon = view.findViewById(R.id.exportIcon)
        exportTitle = view.findViewById(R.id.exportTitle)
        exportSubtitle = view.findViewById(R.id.exportSubtitle)
        exportStatus = view.findViewById(R.id.exportStatus)
        exportProgress = view.findViewById(R.id.exportProgress)
        pathContainer = view.findViewById(R.id.pathContainer)
        exportFilePath = view.findViewById(R.id.exportFilePath)
        exportRowCount = view.findViewById(R.id.exportRowCount)
        exportChooseButton = view.findViewById(R.id.exportChooseButton)
        exportDoneButton = view.findViewById(R.id.exportDoneButton)
    }

    private fun setupSubtitle() {
        exportSubtitle.text = if (viewModel.exportMode == "single") {
            viewModel.table?.name ?: "Single Table"
        } else {
            "All Tables"
        }
    }

    private fun showLocationPicker() {
        ExportLocationPickerFragment.show(requireActivity().supportFragmentManager)
    }

    // ── State observation ────────────────────────────────────────────────

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    when (state) {
                        is ExportState.ChoosingLocation -> showChoosingState()
                        is ExportState.Exporting -> showExportingState(state.message)
                        is ExportState.Success -> showSuccessState(state.filePath, state.rowCount)
                        is ExportState.Error -> showErrorState(state.message)
                    }
                }
            }
        }
    }

    private fun showChoosingState() {
        exportIcon.text = "📤"
        exportTitle.text = "Export Data"
        exportStatus.text = "Choose where to save the file"
        exportProgress.visibility = View.GONE
        pathContainer.visibility = View.GONE
        exportDoneButton.visibility = View.GONE
        exportChooseButton.visibility = View.VISIBLE
        exportChooseButton.requestFocus()
    }

    private fun showExportingState(message: String) {
        exportIcon.text = "⏳"
        exportTitle.text = "Exporting…"
        exportStatus.text = message
        exportProgress.visibility = View.VISIBLE
        pathContainer.visibility = View.GONE
        exportDoneButton.visibility = View.GONE
        exportChooseButton.visibility = View.GONE
    }

    private fun showSuccessState(filePath: String, rowCount: Int) {
        exportIcon.text = "✅"
        exportTitle.text = "Export Complete!"
        exportStatus.text = "Your data has been exported successfully"
        exportProgress.visibility = View.GONE
        pathContainer.visibility = View.VISIBLE
        exportFilePath.text = filePath
        exportRowCount.text = "📊 $rowCount rows exported  •  📄 ${viewModel.fileName}"
        exportChooseButton.visibility = View.GONE
        exportDoneButton.visibility = View.VISIBLE
        exportDoneButton.text = "✅  Done"
        exportDoneButton.requestFocus()

        // Animate the path container in
        pathContainer.alpha = 0f
        pathContainer.translationY = 20f
        pathContainer.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    private fun showErrorState(message: String) {
        exportIcon.text = "❌"
        exportTitle.text = "Export Failed"
        exportStatus.text = message
        exportProgress.visibility = View.GONE
        pathContainer.visibility = View.GONE
        exportChooseButton.visibility = View.GONE
        exportDoneButton.visibility = View.VISIBLE
        exportDoneButton.text = "↩  Go Back"
        exportDoneButton.requestFocus()
    }

    // ── Animation ────────────────────────────────────────────────────────

    private fun animateEntrance() {
        exportCard.alpha = 0f
        exportCard.scaleX = 0.92f
        exportCard.scaleY = 0.92f
        exportCard.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(450)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }
}


