package com.github.aifolderpath.settings

import com.intellij.ide.util.PropertiesComponent

enum class OutputFormatPreset(
    val id: String,
    val label: String,
    val description: String,
) {
    Compact(
        id = "compact",
        label = "Compact",
        description = "只输出路径",
    ),
    Anchor(
        id = "anchor",
        label = "Anchor",
        description = "输出路径、符号和行范围",
    ),
    Context(
        id = "context",
        label = "Context",
        description = "输出多行上下文头",
    );

    override fun toString(): String = "$label - $description"

    companion object {
        fun fromId(value: String?): OutputFormatPreset {
            return entries.firstOrNull { it.id == value } ?: Compact
        }
    }
}

object OutputFormatPresetStore {
    private const val KEY = "com.github.aifolderpath.outputFormatPreset"

    fun get(): OutputFormatPreset {
        return OutputFormatPreset.fromId(PropertiesComponent.getInstance().getValue(KEY))
    }

    fun set(value: OutputFormatPreset) {
        PropertiesComponent.getInstance().setValue(KEY, value.id, OutputFormatPreset.Compact.id)
    }
}
