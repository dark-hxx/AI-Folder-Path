package com.github.aifolderpath

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.vfs.VirtualFile
import java.awt.datatransfer.StringSelection

class CopyAITreeAction : AnAction() {
    private val log = Logger.getInstance(CopyAITreeAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val selectedFiles = getSelectedVirtualFiles(e)
        if (selectedFiles.isEmpty()) {
            return
        }

        val result = when {
            selectedFiles.size == 1 && selectedFiles.first().isDirectory -> {
                PathTreeFormatter.formatDirectorySummary(project, selectedFiles.first())
            }
            else -> PathTreeFormatter.formatSelection(project, selectedFiles)
        }

        log.info("AIFolderPath(Tree): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    private fun getSelectedVirtualFiles(e: AnActionEvent): List<VirtualFile> {
        val selectedFiles = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!selectedFiles.isNullOrEmpty()) {
            return selectedFiles.toList()
        }
        return e.getData(CommonDataKeys.VIRTUAL_FILE)?.let(::listOf).orEmpty()
    }

    private fun notify(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Tree Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Tree): notification failed", ex)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val selectedFiles = getSelectedVirtualFiles(e)
        e.presentation.isEnabledAndVisible = selectedFiles.isNotEmpty()
    }
}
