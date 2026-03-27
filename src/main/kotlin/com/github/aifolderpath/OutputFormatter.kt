package com.github.aifolderpath

import com.github.aifolderpath.EditorSymbolContextResolver.EditorSymbolContext
import com.github.aifolderpath.settings.OutputFormatPreset
import com.github.aifolderpath.settings.OutputFormatPresetStore

object OutputFormatter {
    fun formatPath(path: String, preset: OutputFormatPreset = OutputFormatPresetStore.get()): String {
        return when (preset) {
            OutputFormatPreset.Compact, OutputFormatPreset.Anchor -> path
            OutputFormatPreset.Context -> "path: $path"
        }
    }

    fun formatContext(
        context: EditorSymbolContext,
        preset: OutputFormatPreset = OutputFormatPresetStore.get(),
    ): String {
        return when (preset) {
            OutputFormatPreset.Compact -> context.path
            OutputFormatPreset.Anchor -> formatAnchor(context)
            OutputFormatPreset.Context -> formatContextBlock(context)
        }
    }

    fun formatAnchor(context: EditorSymbolContext): String {
        return buildString {
            append(context.path)
            context.symbolText()?.let {
                append(' ')
                append(it)
            }
            append(' ')
            append(context.lineText())
        }
    }

    fun formatUsageAnchor(context: EditorSymbolContext): String {
        return buildString {
            append(context.path)
            context.symbolText()?.let {
                append(' ')
                append(it)
            }
            append(' ')
            append(context.currentLineText())
        }
    }

    fun formatContextBlock(context: EditorSymbolContext): String {
        return buildList {
            add("path: ${context.path}")
            context.className?.let { add("class: $it") }
            context.methodSignature?.let { add("method: $it") }
            add("lines: ${context.startLine}-${context.endLine}")
        }.joinToString("\n")
    }

    fun formatDefinitionAndUsages(
        definition: String,
        usages: List<String>,
        omittedCount: Int = 0,
    ): String {
        return buildList {
            add("definition: $definition")
            if (usages.isEmpty()) {
                add("usages: (none)")
            } else {
                usages.forEachIndexed { index, usage ->
                    add("usage[${index + 1}]: $usage")
                }
            }
            if (omittedCount > 0) {
                add("... +$omittedCount more call sites")
            }
        }.joinToString("\n")
    }
}
