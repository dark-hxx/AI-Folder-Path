package com.github.aifolderpath

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

object PathResolver {

    /**
     * 生成 AI 友好的路径格式: @模块名/模块内相对路径
     */
    fun resolve(project: Project, file: VirtualFile): String {
        return buildPath(project, file, appendDirectorySeparator = false)
    }

    fun resolveDirectory(project: Project, directory: VirtualFile): String {
        return buildPath(project, directory, appendDirectorySeparator = true)
    }

    private fun buildPath(project: Project, file: VirtualFile, appendDirectorySeparator: Boolean): String {
        val module = ModuleUtilCore.findModuleForFile(file, project)
        val projectBasePath = project.basePath ?: return finalizePath(file.path, appendDirectorySeparator)

        if (module != null) {
            val moduleRoot = ModuleRootManager.getInstance(module).contentRoots
                .filter { VfsUtilCore.isAncestor(it, file, false) || it == file }
                .maxByOrNull { it.path.length }

            if (moduleRoot != null) {
                val relPath = VfsUtilCore.getRelativePath(file, moduleRoot, '/')
                val path = if (relPath.isNullOrEmpty()) "@${module.name}" else "@${module.name}/$relPath"
                return finalizePath(path, appendDirectorySeparator)
            }

            val modulePath = findModuleRoot(file, projectBasePath)
            if (modulePath != null) {
                val relPath = file.path.removePrefix(modulePath).trimStart('/', '\\').replace('\\', '/')
                val path = if (relPath.isEmpty()) "@${module.name}" else "@${module.name}/$relPath"
                return finalizePath(path, appendDirectorySeparator)
            }
        }

        // 单模块回退: @项目名/相对路径
        val projectName = project.name
        val relPath = file.path.removePrefix(projectBasePath).trimStart('/', '\\').replace('\\', '/')
        val path = if (relPath.isEmpty()) "@$projectName" else "@$projectName/$relPath"
        return finalizePath(path, appendDirectorySeparator)
    }

    /**
     * 从文件向上查找最近的包含 pom.xml 或 build.gradle 的模块根目录
     * 但不超过项目根目录
     */
    private fun findModuleRoot(file: VirtualFile, projectBasePath: String): String? {
        var dir = if (file.isDirectory) file else file.parent
        val normalizedBase = projectBasePath.replace('\\', '/')
        while (dir != null) {
            val dirPath = dir.path.replace('\\', '/')
            if (dirPath.length < normalizedBase.length) break

            if (hasModuleMarker(dir)) {
                return dir.path
            }
            if (dirPath == normalizedBase) break
            dir = dir.parent
        }
        return null
    }

    private fun finalizePath(path: String, appendDirectorySeparator: Boolean): String {
        val normalizedPath = path.replace('\\', '/')
        return if (appendDirectorySeparator) {
            "${normalizedPath.trimEnd('/', '\\')}\\"
        } else {
            normalizedPath
        }
    }

    private fun hasModuleMarker(dir: VirtualFile): Boolean {
        return dir.findChild("pom.xml") != null
                || dir.findChild("build.gradle") != null
                || dir.findChild("build.gradle.kts") != null
    }
}
