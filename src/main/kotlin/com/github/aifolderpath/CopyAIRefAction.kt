package com.github.aifolderpath

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.psi.*
import com.intellij.psi.search.searches.DefinitionsScopedSearch
import com.intellij.psi.util.PsiTreeUtil
import java.awt.datatransfer.StringSelection

/**
 * 复制选中方法/标识符的实现处路径（而非当前文件路径）。
 * 快捷键: Ctrl+Alt+P
 */
class CopyAIRefAction : AnAction() {

    private val log = Logger.getInstance(CopyAIRefAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val psiFile = e.getData(CommonDataKeys.PSI_FILE) ?: return

        val element = findTargetElement(editor, psiFile)
        if (element == null) {
            notify(project, "未找到可解析的标识符", NotificationType.WARNING)
            return
        }

        val resolved = resolveToImplementation(element)
        if (resolved == null) {
            notify(project, "无法解析到定义或实现", NotificationType.WARNING)
            return
        }

        val targetFile = resolved.containingFile?.virtualFile
        if (targetFile == null) {
            notify(project, "无法获取目标文件", NotificationType.WARNING)
            return
        }

        val basePath = PathResolver.resolve(project, targetFile)
        val result = if (resolved is PsiMethod) "$basePath ${resolved.name}" else basePath

        log.info("AIFolderPath(Ref): copying result=$result")
        CopyPasteManager.getInstance().setContents(StringSelection(result))
        notify(project, result, NotificationType.INFORMATION)
    }

    /**
     * 从编辑器获取光标/选区处的 PSI 元素
     */
    private fun findTargetElement(
        editor: com.intellij.openapi.editor.Editor,
        psiFile: PsiFile
    ): PsiElement? {
        val selectionModel = editor.selectionModel
        val offset = if (selectionModel.hasSelection()) selectionModel.selectionStart else editor.caretModel.offset
        return psiFile.findElementAt(offset)
    }

    /**
     * 解析标识符到实现类方法。
     * 优先级：引用解析 -> 如果解析到接口/抽象方法则查找实现 -> 当前上下文方法
     */
    private fun resolveToImplementation(element: PsiElement): PsiElement? {
        // 1. 尝试引用解析（方法调用 -> 方法声明）
        val parent = element.parent
        if (parent is PsiReference) {
            val resolved = parent.resolve()
            if (resolved is PsiMethod) {
                return findConcreteMethod(resolved)
            }
            if (resolved is PsiClass) return resolved
        }

        // 2. 光标在方法名上 -> 当前方法声明本身，尝试查找实现
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false)
        if (method != null && isOnMethodName(element, method)) {
            return findConcreteMethod(method)
        }

        // 3. 光标在类名上
        val clazz = PsiTreeUtil.getParentOfType(element, PsiClass::class.java, false)
        if (clazz != null && isOnClassName(element, clazz)) {
            return clazz
        }

        return null
    }

    /**
     * 如果方法在接口或抽象类中，查找具体实现。
     * 只有一个实现直接返回；多个实现返回第一个。
     * 如果本身就是具体实现则直接返回。
     */
    private fun findConcreteMethod(method: PsiMethod): PsiMethod {
        val containingClass = method.containingClass ?: return method
        if (!containingClass.isInterface && !containingClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
            return method
        }

        val implementations = DefinitionsScopedSearch.search(method).findAll()
        val implMethods = implementations.filterIsInstance<PsiMethod>()
        return implMethods.firstOrNull() ?: method
    }

    private fun isOnMethodName(element: PsiElement, method: PsiMethod): Boolean {
        val nameId = method.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    private fun isOnClassName(element: PsiElement, clazz: PsiClass): Boolean {
        val nameId = clazz.nameIdentifier ?: return false
        return element.textRange.intersects(nameId.textRange)
    }

    private fun notify(project: com.intellij.openapi.project.Project, content: String, type: NotificationType) {
        try {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("AIFolderPath.Notification")
                .createNotification("AI Ref Path", content, type)
                .notify(project)
        } catch (ex: Exception) {
            log.warn("AIFolderPath(Ref): notification failed", ex)
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val psiFile = e.getData(CommonDataKeys.PSI_FILE)
        e.presentation.isEnabledAndVisible = editor != null && psiFile != null
    }
}
