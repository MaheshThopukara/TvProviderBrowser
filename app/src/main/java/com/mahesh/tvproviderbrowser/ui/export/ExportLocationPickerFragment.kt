package com.mahesh.tvproviderbrowser.ui.export

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.os.storage.StorageManager
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import java.io.File

/**
 * D-pad-friendly storage location chooser using a simple [AlertDialog].
 *
 * Shows available storage destinations:
 * - **Downloads** (public, via MediaStore / direct write)
 * - **App Files** (app-specific external dir, no permission needed)
 * - **USB / SD** (detected via `getExternalFilesDirs`)
 *
 * The chosen location is delivered back through the **Fragment Result API**
 * on the activity's `supportFragmentManager` (key = [RESULT_KEY]).
 */
class ExportLocationPickerFragment : DialogFragment() {

    companion object {
        /** Fragment-result key shared with callers. */
        const val RESULT_KEY = "export_location_result"
        const val LOCATION_TYPE_KEY = "location_type"
        const val FILE_PATH_KEY = "file_path"
        const val DISPLAY_NAME_KEY = "display_name"

        /** Write via MediaStore (API 29+) or Environment.DIRECTORY_DOWNLOADS. */
        const val TYPE_DOWNLOADS = "downloads"
        /** Write directly to the [File] path in [FILE_PATH_KEY]. */
        const val TYPE_FILE = "file"
        /** Sent when the user cancels the picker without choosing. */
        const val TYPE_CANCELLED = "cancelled"

        private const val TAG = "ExportLocationPicker"

        /**
         * Shows the picker dialog on top of the current content.
         * Call from a fragment: `ExportLocationPickerFragment.show(requireActivity().supportFragmentManager)`
         */
        fun show(fragmentManager: FragmentManager) {
            ExportLocationPickerFragment().show(fragmentManager, TAG)
        }
    }

    /** Data class that holds one storage option. */
    private data class StorageOption(
        val label: String,
        val locationType: String,
        val filePath: String?,
        val displayName: String,
    )

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val options = buildStorageOptions(ctx)
        val labels = options.map { it.label }.toTypedArray()

        return AlertDialog.Builder(ctx)
            .setTitle("Save Export File")
            .setItems(labels) { _, which ->
                val chosen = options[which]
                val bundle = Bundle().apply {
                    putString(LOCATION_TYPE_KEY, chosen.locationType)
                    putString(FILE_PATH_KEY, chosen.filePath)
                    putString(DISPLAY_NAME_KEY, chosen.displayName)
                }
                parentFragmentManager.setFragmentResult(RESULT_KEY, bundle)
            }
            .setNegativeButton("Cancel", null)
            .create()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply { putString(LOCATION_TYPE_KEY, TYPE_CANCELLED) },
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun buildStorageOptions(ctx: Context): List<StorageOption> {
        val options = mutableListOf<StorageOption>()

        // 1. Public Downloads folder (always available)
        options.add(
            StorageOption(
                label = "📁  Downloads  —  Public Downloads folder",
                locationType = TYPE_DOWNLOADS,
                filePath = null,
                displayName = "Downloads",
            ),
        )

        // 2. App-specific external storage (always writable, no permission needed)
        val appDir = ctx.getExternalFilesDir(null)
        options.add(
            StorageOption(
                label = "📂  App Files  —  ${appDir?.absolutePath ?: "App-specific directory"}",
                locationType = TYPE_FILE,
                filePath = appDir?.absolutePath,
                displayName = "App Files",
            ),
        )

        // 3. External / USB / SD volumes (index 0 = internal, 1+ = external)
        val externalDirs = ctx.getExternalFilesDirs(null)
        externalDirs.forEachIndexed { index, dir ->
            if (index > 0 && dir != null && dir.canWrite()) {
                val label = getVolumeLabel(ctx, dir) ?: "External Storage $index"
                options.add(
                    StorageOption(
                        label = "🔌  $label  —  ${dir.absolutePath}",
                        locationType = TYPE_FILE,
                        filePath = dir.absolutePath,
                        displayName = label,
                    ),
                )
            }
        }

        return options
    }

    /**
     * Returns a human-readable label for the storage volume containing [dir],
     * e.g. "USB storage", "SD card", or the volume description from [StorageManager].
     */
    private fun getVolumeLabel(context: Context, dir: File): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
            return try {
                sm.getStorageVolume(dir)?.getDescription(context)
            } catch (_: Exception) {
                null
            }
        }
        return null
    }
}


