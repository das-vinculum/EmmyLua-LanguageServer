package com.tang.vscode.vscode

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.PsiFile
import com.intellij.util.Processor
import com.tang.vscode.api.IFolder
import com.tang.vscode.api.ILuaFile
import com.tang.vscode.api.IVirtualFile
import com.tang.vscode.api.IWorkspace
import com.tang.vscode.api.impl.Folder
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.services.WorkspaceService
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture

/**
 * tangzx
 * Created by Client on 2018/3/20.
 */
class LuaWorkspaceService : WorkspaceService, IWorkspace {
    private var _root: URI = URI("file:///")
    private val _rootList = mutableListOf<IFolder>()
    private val _rootWSFolders = mutableListOf<URI>()
    private val _baseFolders = mutableListOf<IFolder>()

    inner class WProject : UserDataHolderBase(), Project {
        override fun process(processor: Processor<PsiFile>) {
            for (ws in _rootList) {
                val continueRun = ws.walkFiles {
                    val psi = it.psi
                    if (psi != null)
                        return@walkFiles processor.process(psi)
                    true
                }
                if (!continueRun) break
            }
        }
    }

    val project: Project

    init {
        project = WProject()
        project.putUserData(IWorkspace.KEY, this)
    }

    override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams) {

    }

    override fun didChangeConfiguration(params: DidChangeConfigurationParams) {
    }

    override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<MutableList<out SymbolInformation>> {
        TODO()
    }

    var root: URI
        get() = _root
        set(value) {
            _root = value
            addWSRoot(value)
        }

    private fun eachWorkspace(processor: (ws: IFolder) -> Boolean) {
        for (ws in _rootList) {
            if (!processor(ws))
                break
        }
    }

    private fun getWSRoot(uri: URI): IFolder {
        var ws: IFolder? = null
        eachWorkspace {
            if (it.matchUri(uri)) {
                ws = it
                return@eachWorkspace false
            }
            true
        }
        return ws ?: addWSRoot(uri)
    }

    private fun findOrCreate(uri: URI, autoCreate: Boolean): Pair<IFolder?, Boolean> {
        val split = uri.path.split('/')
        var isCreated = false

        val driver = split[1]
        val base = _baseFolders.find { it.getName() == driver }
        var folder = base ?: if (autoCreate) {
            val u = URI("${uri.scheme}:///$driver/")
            val newBase = Folder(u, driver)
            _baseFolders.add(newBase)
            isCreated = true
            newBase
        } else null

        if (folder == null)
            return Pair(folder, false)

        for (i in 2 until split.size) {
            val name = split[i]
            folder = folder?.findFile(name) as? IFolder ?: folder?.createFolder(name)
        }

        return Pair(folder, isCreated)
    }

    private fun addWSRoot(uri: URI): IFolder {
        val pair = findOrCreate(uri, true)
        val folder = pair.first!!
        _rootList.add(folder)
        return folder
    }

    fun addRoot(uri: String) {
        addRoot(URI(uri))
    }

    private fun addRoot(uri: URI) {
        getWSRoot(uri)
        _rootWSFolders.add(uri)
    }

    private fun collectFiles(file: File, list: MutableList<File>) {
        if (file.isFile && file.extension == "lua") {
            list.add(file)
        } else if (file.isDirectory) {
            file.listFiles().forEach { collectFiles(it, list) }
        }
    }

    fun loadWorkspace(monitor: IProgressMonitor) {
        val allFiles = mutableListOf<File>()
        val arr = _rootWSFolders.toTypedArray()
        _rootWSFolders.clear()
        arr.forEach { uri->
            val folder = File(uri.path)
            collectFiles(folder, allFiles)
        }

        allFiles.forEachIndexed { index, file ->
            addFile(file)
            monitor.setProgress("Emmy load file: ${file.canonicalPath}", (index  + 1) / allFiles.size.toFloat())
        }
        monitor.done()
    }

    private fun getParentUri(uri: String): URI {
        val u = URI(uri)
        return u.resolve("..")
    }

    override fun findFile(uri: String): IVirtualFile? {
        val u = URI(uri)
        val pair = findOrCreate(getParentUri(uri), false)
        val root = pair.first
        return root?.findFile(File(u.path).name)
    }

    override fun addFile(file: File, text: String?): ILuaFile {
        val uri = URI("file:///${file.invariantSeparatorsPath}")
        val pair = findOrCreate(getParentUri(uri.toString()), true)
        val root = pair.first!!
        return root.addFile(file.name, text ?: file.readText())
    }
}