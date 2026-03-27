package com.github.aifolderpath

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

object PathTreeFormatter {
    private const val DEFAULT_MAX_DEPTH = 2
    private const val DEFAULT_MAX_NODES = 50

    private data class PathEntry(
        val normalizedPath: String,
        val segments: List<String>,
        val directory: Boolean,
    )

    private data class TreeNode(
        var directory: Boolean = true,
        val children: LinkedHashMap<String, TreeNode> = linkedMapOf(),
    )

    private class RenderState(
        private val maxNodes: Int,
    ) {
        var renderedNodes: Int = 0
        var truncated: Boolean = false

        fun tryConsumeNode(): Boolean {
            if (renderedNodes >= maxNodes) {
                truncated = true
                return false
            }
            renderedNodes++
            return true
        }
    }

    fun formatSelection(project: Project, selectedFiles: List<VirtualFile>): String {
        val entries = selectedFiles
            .distinctBy { it.path }
            .map { file ->
                val aiPath = if (file.isDirectory) {
                    PathResolver.resolveDirectory(project, file)
                } else {
                    PathResolver.resolve(project, file)
                }
                toPathEntry(aiPath, file.isDirectory)
            }
            .sortedWith(compareBy<PathEntry>({ it.segments.firstOrNull().orEmpty() }, { it.segments.size }, { it.normalizedPath }))
            .fold(mutableListOf<PathEntry>()) { kept, entry ->
                if (kept.none { it.directory && isAncestor(it.segments, entry.segments) }) {
                    kept += entry
                }
                kept
            }

        if (entries.isEmpty()) {
            return ""
        }

        return entries
            .groupBy { it.segments.firstOrNull().orEmpty() }
            .values
            .joinToString("\n\n") { renderSelectionGroup(it) }
    }

    fun formatDirectorySummary(
        project: Project,
        directory: VirtualFile,
        maxDepth: Int = DEFAULT_MAX_DEPTH,
        maxNodes: Int = DEFAULT_MAX_NODES,
    ): String {
        val header = PathResolver.resolveDirectory(project, directory).replace('\\', '/').trimEnd('/') + "/"
        val lines = mutableListOf(header)
        val state = RenderState(maxNodes)
        renderDirectoryChildren(directory, 1, maxDepth, "", lines, state)
        if (state.truncated) {
            lines += "... (+more omitted)"
        }
        return lines.joinToString("\n")
    }

    private fun renderSelectionGroup(entries: List<PathEntry>): String {
        val commonPrefix = commonDirectoryPrefix(entries)
        val header = commonPrefix.joinToString("/").ifEmpty { entries.first().segments.first() } + "/"
        val root = TreeNode(directory = true)

        entries.forEach { entry ->
            val relativeSegments = entry.segments.drop(commonPrefix.size)
            if (relativeSegments.isNotEmpty()) {
                insert(root, relativeSegments, entry.directory)
            }
        }

        val lines = mutableListOf(header)
        renderTree(root, "", lines)
        return lines.joinToString("\n")
    }

    private fun renderDirectoryChildren(
        directory: VirtualFile,
        depth: Int,
        maxDepth: Int,
        prefix: String,
        lines: MutableList<String>,
        state: RenderState,
    ) {
        if (depth > maxDepth) {
            if (directory.children.isNotEmpty()) {
                state.truncated = true
            }
            return
        }

        val children = directory.children
            .sortedWith(compareBy<VirtualFile>({ !it.isDirectory }, { it.name.lowercase() }))

        children.forEachIndexed { index, child ->
            if (!state.tryConsumeNode()) {
                return
            }
            val isLast = index == children.lastIndex
            val connector = if (isLast) "└─ " else "├─ "
            lines += prefix + connector + child.name + if (child.isDirectory) "/" else ""
            if (child.isDirectory) {
                renderDirectoryChildren(
                    directory = child,
                    depth = depth + 1,
                    maxDepth = maxDepth,
                    prefix = prefix + if (isLast) "   " else "│  ",
                    lines = lines,
                    state = state,
                )
            }
        }
    }

    private fun renderTree(node: TreeNode, prefix: String, lines: MutableList<String>) {
        val entries = node.children.entries
            .sortedWith(compareBy<Map.Entry<String, TreeNode>>({ !it.value.directory }, { it.key.lowercase() }))

        entries.forEachIndexed { index, entry ->
            val isLast = index == entries.lastIndex
            val connector = if (isLast) "└─ " else "├─ "
            lines += prefix + connector + entry.key + if (entry.value.directory) "/" else ""
            renderTree(entry.value, prefix + if (isLast) "   " else "│  ", lines)
        }
    }

    private fun insert(root: TreeNode, segments: List<String>, directory: Boolean) {
        var current = root
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.lastIndex
            current = current.children.getOrPut(segment) { TreeNode(directory = !isLast || directory) }
            if (isLast) {
                current.directory = directory
            }
        }
    }

    private fun commonDirectoryPrefix(entries: List<PathEntry>): List<String> {
        val candidates = entries.map { entry ->
            if (entry.directory) entry.segments else entry.segments.dropLast(1)
        }
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val first = candidates.first()
        var index = 0
        while (index < first.size && candidates.all { index < it.size && it[index] == first[index] }) {
            index++
        }
        return first.take(index)
    }

    private fun toPathEntry(aiPath: String, directory: Boolean): PathEntry {
        val normalized = aiPath.replace('\\', '/').trimEnd('/')
        return PathEntry(
            normalizedPath = normalized,
            segments = normalized.split('/').filter { it.isNotBlank() },
            directory = directory,
        )
    }

    private fun isAncestor(ancestor: List<String>, descendant: List<String>): Boolean {
        if (ancestor.size >= descendant.size) {
            return false
        }
        return ancestor.indices.all { ancestor[it] == descendant[it] }
    }
}
