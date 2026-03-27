package com.github.aifolderpath

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil

object EditorSymbolContextResolver {
    data class EditorSymbolContext(
        val path: String,
        val currentLine: Int,
        val startLine: Int,
        val endLine: Int,
        val className: String?,
        val methodSignature: String?,
    ) {
        fun lineText(): String {
            return if (startLine == endLine) "L$startLine" else "L$startLine-L$endLine"
        }

        fun currentLineText(): String = "L$currentLine"

        fun symbolText(): String? {
            return when {
                methodSignature != null && className != null -> "$className.$methodSignature"
                methodSignature != null -> methodSignature
                className != null -> className
                else -> null
            }
        }
    }

    fun resolve(project: Project, editor: Editor, psiFile: PsiFile): EditorSymbolContext? {
        val selectionModel = editor.selectionModel
        val startOffset = if (selectionModel.hasSelection()) {
            selectionModel.selectionStart
        } else {
            editor.caretModel.offset
        }
        val endOffset = if (selectionModel.hasSelection()) {
            normalizeEndOffset(selectionModel.selectionStart, selectionModel.selectionEnd)
        } else {
            startOffset
        }
        return resolve(
            project = project,
            psiFile = psiFile,
            element = psiFile.findElementAt(startOffset),
            currentOffset = startOffset,
            rangeStartOffset = startOffset,
            rangeEndOffset = endOffset,
            useExplicitRange = selectionModel.hasSelection(),
        )
    }

    fun resolve(project: Project, element: PsiElement): EditorSymbolContext? {
        val psiFile = element.containingFile ?: return null
        val offset = element.textRange?.startOffset ?: 0
        return resolve(
            project = project,
            psiFile = psiFile,
            element = element,
            currentOffset = offset,
            rangeStartOffset = offset,
            rangeEndOffset = offset,
            useExplicitRange = false,
        )
    }

    private fun resolve(
        project: Project,
        psiFile: PsiFile,
        element: PsiElement?,
        currentOffset: Int,
        rangeStartOffset: Int,
        rangeEndOffset: Int,
        useExplicitRange: Boolean,
    ): EditorSymbolContext? {
        val virtualFile = psiFile.virtualFile ?: return null
        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
            ?: FileDocumentManager.getInstance().getDocument(virtualFile)
        val currentLine = toLineNumber(document, currentOffset)
        val method = element?.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java, false) }
        val clazz = element?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java, false) }
        val target = method ?: clazz
        val path = PathResolver.resolve(project, virtualFile)

        val startLine = if (useExplicitRange) {
            toLineNumber(document, rangeStartOffset)
        } else {
            target?.textRange?.startOffset?.let { toLineNumber(document, it) } ?: currentLine
        }
        val endLine = if (useExplicitRange) {
            toLineNumber(document, rangeEndOffset)
        } else {
            target?.textRange?.let { range ->
                val endOffset = normalizeEndOffset(range.startOffset, range.endOffset)
                toLineNumber(document, endOffset)
            } ?: currentLine
        }

        return EditorSymbolContext(
            path = path,
            currentLine = currentLine,
            startLine = startLine,
            endLine = endLine,
            className = method?.containingClass?.name ?: clazz?.name,
            methodSignature = method?.let(::buildMethodSignature),
        )
    }

    private fun normalizeEndOffset(startOffset: Int, endOffset: Int): Int {
        return if (endOffset > startOffset) endOffset - 1 else endOffset
    }

    private fun toLineNumber(document: Document?, offset: Int): Int {
        if (document == null || document.textLength == 0) {
            return 1
        }
        val boundedOffset = offset.coerceIn(0, document.textLength - 1)
        return document.getLineNumber(boundedOffset) + 1
    }

    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { param ->
            param.type.presentableText
        }
        return "${method.name}($params)"
    }
}
