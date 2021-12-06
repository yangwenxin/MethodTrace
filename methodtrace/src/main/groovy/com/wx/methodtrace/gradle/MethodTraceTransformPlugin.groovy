package com.wx.methodtrace.gradle

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.wx.methodtrace.Config
import com.wx.methodtrace.MethodTransformJ
import com.wx.methodtrace.Utils
import org.apache.commons.io.FileUtils
import org.gradle.api.Plugin
import org.gradle.api.Project

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

class MethodTraceTransformPlugin extends Transform implements Plugin<Project> {

    private Project project

    void apply(Project project) {
        this.project = project
        //扩展，在app下build.gradle内可定义属性
        //methodTrace {
        //    是否开启
        //    open = true
        //    插桩配置文件
        //    traceConfigFile = "${project.projectDir}/traceconfig.txt"
        //}
        project.extensions.create("methodTrace", MethodTraceConfig)
        project.android.registerTransform(this)
    }

    @Override
    String getName() {
        return "MethodTrace"
    }

    //要转换的资源类型（字节码还是资源文件等）
    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    //适用的范围
    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    //具体转换过程
    @Override
    void transform(Context context, Collection<TransformInput> inputs,
                   Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider,
                   boolean isIncremental) throws IOException, TransformException, InterruptedException {
        def methodTraceConfig = project.methodTrace
        if (methodTraceConfig.open) {
            Config traceConfig = new Config()
            traceConfig.traceConfigFile = methodTraceConfig.traceConfigFile
            traceConfig.parseTraceConfigFile()

            outputProvider.deleteAll()
            final MethodTransformJ methodTransform = new MethodTransformJ()
            inputs.each { TransformInput input ->
                //derectoryInputs本地project编译成的多个class⽂件存放的⽬录
                input.directoryInputs.each { DirectoryInput directoryInput ->
                    traceSrcFiles(directoryInput, methodTransform, outputProvider, traceConfig)
                }

                //jarInputs各个依赖所编译成的jar⽂件
                input.jarInputs.findAll { it.file.getAbsolutePath().endsWith(".jar") }.each { JarInput jarInput ->
                    traceJarFiles(jarInput, outputProvider, methodTransform, traceConfig)
                }
            }
        }
    }

    private void traceJarFiles(JarInput jarInput, TransformOutputProvider outputProvider
                               , MethodTransformJ methodTransform
                               , Config traceConfig) {
        def jarName = jarInput.name.substring(0, jarInput.name.length() - 4)
        File dest = outputProvider.getContentLocation(jarName + "_dest", jarInput.contentTypes,
                jarInput.scopes, Format.JAR)
        FileUtils.copyFile(jarInput.file, dest)
        JarOutputStream jos = new JarOutputStream(new FileOutputStream(dest))

        JarFile jarFile = new JarFile(jarInput.file)
        Enumeration<JarEntry> jarEntryEnumeration = jarFile.entries()
        while (null != jarEntryEnumeration && jarEntryEnumeration.hasMoreElements()) {
            JarEntry entry = jarEntryEnumeration.nextElement()
            if (null == entry) {
                continue
            }
            JarEntry newEntry = new JarEntry(entry.getName())
            jos.putNextEntry(newEntry)
            if (entry.isDirectory()) {
                continue
            }
            InputStream inputStream = jarFile.getInputStream(entry)
            byte[] body = Utils.inputStreamToBytes(inputStream)
            if (traceConfig.isNeedTraceClass(entry.getName())) {
                body = methodTransform.transform(body, traceConfig)
            }
            jos.write(body, 0, body.length)
        }
        jos.finish()
        jos.close()
        jarFile.close()
    }

    private void traceSrcFiles(DirectoryInput directoryInput, methodTransform
                               , TransformOutputProvider outputProvider
                               , Config traceConfig) {
        if (directoryInput.file.isDirectory()) {
            directoryInput.file.eachFileRecurse { File file ->
                String name = file.name
                if (traceConfig.isNeedTraceClass(name)) {
                    //不统计R.class和R$drawable.class这类的资源的映射
                    byte[] bytes = methodTransform.transform(file.bytes, traceConfig)
                    File destFile = new File(file.parentFile.absoluteFile, name)
                    FileOutputStream fileOutputStream = new FileOutputStream(destFile)
                    fileOutputStream.write(bytes)
                    fileOutputStream.close()
                }
            }
        }

        //坐等遍历class并被ASM操作
        File dest = outputProvider.getContentLocation(directoryInput.name,
                directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
        FileUtils.copyDirectory(directoryInput.file, dest)
    }

}

