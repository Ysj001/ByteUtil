package com.ysj.lib.bytecodeutil.plugin

import com.ysj.lib.bytecodeutil.plugin.api.logger.YLogger
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileType
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.ChangeType
import org.gradle.work.ChangeType.ADDED
import org.gradle.work.ChangeType.MODIFIED
import org.gradle.work.ChangeType.REMOVED
import org.gradle.work.Incremental
import org.gradle.work.InputChanges
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject
import kotlin.jvm.internal.Ref

/**
 * 用于处理 [TransformTask.notNeedOutput]。
 *
 * @author Ysj
 * Create time：2023/9/7
 */
abstract class AppendTask : DefaultTask() {

    @get:Incremental
    @get:InputDirectory
    abstract val input: DirectoryProperty

    @get:OutputDirectory
    abstract val output: DirectoryProperty

    @get:Inject
    abstract val worker: WorkerExecutor

    private val logger = YLogger.getLogger(javaClass)

    @TaskAction
    fun execute(inputChanges: InputChanges) {
        val inputDir = input.get().asFile
        val outputDir = output.get().asFile
        logger.quiet(">>> BCU append input ---> $inputDir")
        logger.quiet(">>> BCU append output ---> $outputDir")
        logger.quiet(">>> BCU append isIncremental ---> ${inputChanges.isIncremental}")
        val startTime = System.currentTimeMillis()
        val removedCount = Ref.IntRef()
        val removedGroup = ArrayList<ArrayList<File>>(50)
        val modifiedCount = Ref.IntRef()
        val modifiedGroup = ArrayList<ArrayList<File>>(10)
        val addedCount = Ref.IntRef()
        val addedGroup = ArrayList<ArrayList<File>>(100)
        inputChanges
            .getFileChanges(input)
            .asSequence()
            .filter { it.fileType == FileType.FILE }
            .forEach { change ->
                when (change.changeType) {
                    REMOVED -> groupTo(100, removedCount, removedGroup, change.file)
                    MODIFIED -> groupTo(50, modifiedCount, modifiedGroup, change.file)
                    else -> groupTo(100, addedCount, addedGroup, change.file)
                }
            }
        logger.quiet(">>> BCU append removed group:${removedGroup.size} , size:${removedCount}")
        logger.quiet(">>> BCU append modified group:${modifiedGroup.size} , size:${modifiedCount}")
        logger.quiet(">>> BCU append added group:${addedGroup.size} , size:${addedCount}")
        val workQueue = worker.noIsolation()
        removedGroup.forEach { files ->
            workQueue.submit(Action::class.java) {
                it.changeType.set(REMOVED)
                it.files.set(files)
                it.inputDir.set(inputDir)
                it.outputDir.set(outputDir)
            }
        }
        modifiedGroup.forEach { files ->
            workQueue.submit(Action::class.java) {
                it.changeType.set(MODIFIED)
                it.files.set(files)
                it.inputDir.set(inputDir)
                it.outputDir.set(outputDir)
            }
        }
        // 等移除和修改执行完才能添加，否则可能产生文件冲突
        workQueue.await()
        addedGroup.forEach { files ->
            workQueue.submit(Action::class.java) {
                it.changeType.set(ADDED)
                it.files.set(files)
                it.inputDir.set(inputDir)
                it.outputDir.set(outputDir)
            }
        }
        workQueue.await()
        val endTime = System.currentTimeMillis()
        logger.quiet(">>> BCU append time: ${endTime - startTime} ms")
    }

    private fun <T> groupTo(num: Int, total: Ref.IntRef, dest: ArrayList<ArrayList<T>>, t: T) {
        val groupIndex = total.element / num
        if (groupIndex == dest.size) {
            dest.add(ArrayList(num))
        }
        dest[groupIndex] += t
        total.element++
    }

    abstract class Action : WorkAction<Action.Param> {

        override fun execute() {
            val files = parameters.files.get()
            val outputDir = parameters.outputDir.get()
            val inputDir = parameters.inputDir.get()
            files.forEach { file ->
                val path = inputDir.toURI()
                    .relativize(file.toURI())
                    .path
                    .substringBeforeLast("-crc")
                when (parameters.changeType.get()) {
                    REMOVED -> File(outputDir, path).delete()
                    MODIFIED -> file.copyTo(File(outputDir, path), true)
                    else -> file.copyTo(File(outputDir, path))
                }
            }
        }

        abstract class Param : WorkParameters {
            abstract val files: ListProperty<File>
            abstract val inputDir: Property<File>
            abstract val outputDir: Property<File>
            abstract val changeType: Property<ChangeType>
        }
    }
}