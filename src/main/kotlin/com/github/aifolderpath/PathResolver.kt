package com.github.aifolderpath

import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

object PathResolver {

    /**
     * 生成 AI 友好的路径格式: @模块名/模块内相对路径
     */
    fun resolve(project: Project, file: VirtualFile): String {
        val module = ModuleUtilCore.findModuleForFile(file, project)
        val projectBasePath = project.basePath ?: return file.path

        if (module != null) {
            val modulePath = findModuleRoot(file, projectBasePath)
            if (modulePath != null) {
                val moduleRelPath = modulePath.removePrefix(projectBasePath).trimStart('/', '\\')
                val fileRelPath = file.path.removePrefix(modulePath).trimStart('/', '\\')
                val moduleName = moduleRelPath.replace('\\', '/')
                return "@$moduleName/$fileRelPath"
            }
        }

        // 单模块回退: @项目名/相对路径
        val projectName = project.name
        val relPath = file.path.removePrefix(projectBasePath).trimStart('/', '\\')
        return "@$projectName/${relPath.replace('\\', '/')}"
    }

    /**
     * 从文件向上查找最近的包含 pom.xml 或 build.gradle 的模块根目录
     * 但不超过项目根目录
     */
    private fun findModuleRoot(file: VirtualFile, projectBasePath: String): String? {
        var dir = file.parent
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

    private fun hasModuleMarker(dir: VirtualFile): Boolean {
        return dir.findChild("pom.xml") != null
                || dir.findChild("build.gradle") != null
                || dir.findChild("build.gradle.kts") != null
    }
}
