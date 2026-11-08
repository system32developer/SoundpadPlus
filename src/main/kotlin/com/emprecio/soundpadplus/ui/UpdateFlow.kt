package com.emprecio.soundpadplus.ui

import com.emprecio.soundpadplus.api.AudioSource
import com.emprecio.soundpadplus.i18n.I18n
import com.emprecio.soundpadplus.tools.ToolInstaller
import com.emprecio.soundpadplus.update.BuildInfo
import com.emprecio.soundpadplus.update.Updater
import com.emprecio.soundpadplus.util.async
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SwingUtilities

/** Asks GitHub for a newer release; [silent] hides "up to date" and network errors (used at startup). */
fun checkForUpdates(frame: JFrame, silent: Boolean) {
    async({ Updater.latest() }) { result ->
        result.onFailure { if (!silent) JOptionPane.showMessageDialog(frame, I18n.t("update.failed", it.message ?: ""), I18n.t("update.title"), JOptionPane.ERROR_MESSAGE) }
        result.onSuccess { release ->
            if (release == null) {
                if (!silent) JOptionPane.showMessageDialog(frame, I18n.t("update.latest", BuildInfo.version), I18n.t("update.title"), JOptionPane.INFORMATION_MESSAGE)
                return@onSuccess
            }
            val yes = JOptionPane.showConfirmDialog(frame, I18n.t("update.available", release.version, BuildInfo.version), I18n.t("update.title"), JOptionPane.YES_NO_OPTION)
            if (yes != JOptionPane.YES_OPTION) return@onSuccess
            val dialog = ProgressDialog(frame, I18n.t("update.title")).apply { update(I18n.t("update.downloading", release.version), -1); isVisible = true }
            async({ Updater.download(release) { p -> SwingUtilities.invokeLater { dialog.update(I18n.t("update.downloading", release.version), p) } } }) { done ->
                dialog.dispose()
                done.onSuccess { Updater.launch(it); System.exit(0) }
                done.onFailure { JOptionPane.showMessageDialog(frame, I18n.t("update.failed", it.message ?: ""), I18n.t("update.title"), JOptionPane.ERROR_MESSAGE) }
            }
        }
    }
}

/** Downloads the dependencies the sources declare and are still missing, with a progress window. */
fun installToolsIfMissing(frame: JFrame, sources: List<AudioSource>) {
    val deps = sources.flatMap { runCatching { it.dependencies }.getOrDefault(emptyList()) }
    if (ToolInstaller.missing(deps).isEmpty()) return
    val dialog = ProgressDialog(frame, I18n.t("tools.title")).apply { update(I18n.t("tools.downloading", ""), -1); isVisible = true }
    async({ ToolInstaller.ensure(deps) { tool, p -> SwingUtilities.invokeLater { dialog.update(I18n.t("tools.downloading", tool), p) } } }) { r ->
        dialog.dispose()
        r.onFailure { JOptionPane.showMessageDialog(frame, I18n.t("tools.failed", it.message ?: ""), I18n.t("tools.title"), JOptionPane.ERROR_MESSAGE) }
    }
}
