package com.github.aifolderpath

import com.github.aifolderpath.EditorSymbolContextResolver.EditorSymbolContext
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiModifier
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import java.awt.datatransfer.StringSelection

class CopyAIRefAction : AnAction() {

    private val log = Logger.getInstance(CopyAIRefAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val element = findTargetElement(editor, psiFile)
        if (element == null) {
            notify(project, "未找到可解析的符号", NotificationType.WARNING)
            return
        }

        val referenceTarget = resolveReferenceTarget(element)
        if (referenceTarget == null) {
            notify(project, "无法解析到定义或实现", NotificationType.WARNING)
            return
        }

        val definitionTarget = resolveToImplementation(referenceTarget)
        if (definitionTarget == null) {
            notify(project, "无法解析到定义或实现", NotificationType.WARNING)
            return
        }

        val targetFile = definitionTarget.containingFile?.virtualFile
        if (targetFile == null) {
            notify(project, "无法获取目标文件", NotificationType.WARNING)
            return
        }

        val definition = formatDefinitionAnchor(project, definitionTarget, targetFile)
        val usages = collectUsageAnchors(project, referenceTarget, definitionTarget, DEFAULT_USAGE_LIMIT)
        val result = OutputFormatter.formatDefinitionAndUsages(
            definition = definition,
            usages = usages.visibleUsages,
            omittedCount = usages.omittedCount,
        )

        log.info("AIFolderPath(Usages): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    private fun findTargetElement(
        editor: com.intellij.openapi.editor.Editor,
        psiFile: PsiFile,
    ): PsiElement? {
        val selectionModel = editor.selectionModel
        val offset = if (selectionModel.hasSelection()) selectionModel.selectionStart else editor.caretModel.offset
        return psiFile.findElementAt(offset)
    }

    private fun resolveReferenceTarget(element: PsiElement): PsiElement? {
        val reference = (element.parent as? PsiReference) ?: element.reference
        val resolved = reference?.resolve()
        if (resolved is PsiMethod || resolved is PsiClass) {
            return resolved
        }

        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (method != null && isOnMethodName(element, method)) {
            return method
        }

        val clazz = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)
        if (clazz != null && isOnClassName(element, clazz)) {
            return clazz
        }

        return null
    }

    private fun resolveToImplementation(target: PsiElement): PsiElement? {
        return when (target) {
            is PsiMethod -> findConcreteMethod(target)
            is PsiClass -> target
            else -> null
        }
    }

    private fun findConcreteMethod(method: PsiMethod): PsiMethod {
        val containingClass = method.containingClass ?: return method
        if (!containingClass.isInterface && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return method
        }

        val implementations = DefinitionsScopedSearch.search(method).findAll()
        val implMethods = implementations.filterIsInstance<PsiMethod>()
        return implMethods.firstOrNull() ?: method
    }

    private fun collectUsageAnchors(
        project: com.intellij.openapi.project.Project,
        referenceTarget: PsiElement,
        definitionTarget: PsiElement,
        limit: Int,
    ): UsageList {
        val allUsageAnchors = buildList {
            add(referenceTarget)
            if (definitionTarget != referenceTarget) {
                add(definitionTarget)
            }
        }
            .asSequence()
            .flatMap { ReferencesSearch.search(it).findAll().asSequence() }
            .mapNotNull { reference -> toUsageAnchor(project, reference) }
            .distinctBy { Triple(it.sortPath, it.lineNumber, it.displayText) }
            .sortedWith(compareBy(UsageAnchor::sortPath, UsageAnchor::lineNumber, UsageAnchor::sortOffset))
            .toList()

        val visibleUsages = allUsageAnchors.take(limit).map { it.displayText }
        return UsageList(
            visibleUsages = visibleUsages,
            omittedCount = (allUsageAnchors.size - visibleUsages.size).coerceAtLeast(0),
        )
    }

    private fun formatDefinitionAnchor(
        project: com.intellij.openapi.project.Project,
        target: PsiElement,
        targetFile: com.intellij.openapi.vfs.VirtualFile,
    ): String {
        return EditorSymbolContextResolver.resolve(project, target)?.let(OutputFormatter::formatAnchor)
            ?: PathResolver.resolve(project, targetFile)
    }

    private fun toUsageAnchor(
        project: com.intellij.openapi.project.Project,
        reference: PsiReference,
    ): UsageAnchor? {
        val element = reference.element
        val targetFile = element.containingFile?.virtualFile ?: return null
        val context = EditorSymbolContextResolver.resolve(project, element)
        val displayText = context?.let(OutputFormatter::formatUsageAnchor)
            ?: PathResolver.resolve(project, targetFile)
        return UsageAnchor(
            displayText = displayText,
            sortPath = targetFile.path,
            lineNumber = context?.currentLine ?: 1,
            sortOffset = element.textRange?.startOffset ?: 0,
        )
    }

    private fun isOnMethodName(element: PsiElement, method: PsiMethod): Boolean {
        val nameId = method.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    private fun isOnClassName(element: PsiElement, clazz: PsiClass): Boolean {
        val nameId = clazz.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    private fun notify(
        project: com.intellij.openapi.project.Project,
        content: String,
        type: NotificationType,
    ) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Usages Copied", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Usages): notification failed", ex)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }

    private data class UsageAnchor(
        val displayText: String,
        val sortPath: String,
        val lineNumber: Int,
        val sortOffset: Int,
    )

    private data class UsageList(
        val visibleUsages: List<String>,
        val omittedCount: Int,
    )

    companion object {
        private const val DEFAULT_USAGE_LIMIT = 10
    }
}
