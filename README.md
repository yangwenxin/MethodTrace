# 字节码插桩实现快速排查高耗时方法工具

Gradle plugin + Transform + Ams 字节码插桩实现快速排查高耗时方法工具

### 什么是插桩？

+ 插桩就是将一段代码通过某种策略插入到另一段代码，或替换另一段代码。这里的代码可以分为源码和字节码，而我们所说的插桩一般指字节码插桩。
+ 编译打包时，编写的源码（.java / .kt）通过 javac 编译成字节码（.class），然后通过dx/d8编译成dex文件，最后和resource一起打包到apk中。字节码插桩就是在.class转为.dex之前，修改.class文件从而达到修改或替换代码的目的。

### 插桩的应用场景有哪些？

+ 代码插入

1. 无痕埋点
2. ButterKnife
3. Dagger
4. ....

这些常用的框架，也是在编译期间生成了代码，简化了我们的操作。

+ 代码替换

根据业务场景不同，对特定代码进行代码替换

### 掌握插桩应该具备的基础知识有哪些？

+ 字节码相关知识。可参考 [一文让你深入了解 Java 字节码](https://www.bilibili.com/read/cv11620419/)

+ Gradle自定义插件，直接参考官网 [Developing Custom Gradle Plugins](https://docs.gradle.org/current/userguide/custom_plugins.html)


+ Asm字节码修改工具 [玩转Asm](https://my.oschina.net/ta8210?tab=newest&catalogId=388001)

+ groovy语言基础 简单看看即可

+ 了解[Transform Api](https://google.github.io/android-gradle-dsl/javadoc/3.4/)
这是android在将class转成dex之前给我们预留的一个接口，在该接口中我们可以通过插件形式来修改class文件。

+ gradle相关 
1. [Gradle Extension详解](https://www.jianshu.com/p/58d86b4c0ee5)
2. [字节连载 | 深入理解Gradle框架](https://mp.weixin.qq.com/s/mDCTtQZb6mhWOFAvLYKBSg)

### 插桩实践

1. ![image](https://github.com/yangwenxin/MethodTrace/blob/main/1.png)
2. methodtrace的gradle文件中配置依赖

```
apply plugin: 'groovy'
apply plugin: 'maven'
apply plugin: 'kotlin'
repositories {
    jcenter()
    mavenCentral()
}

dependencies {
    //gradle sdk
    compile gradleApi()
    //groovy sdk
    compile localGroovy()

    compile 'com.android.tools.build:gradle:3.3.3'
    compile 'org.ow2.asm:asm:7.0'
    compile 'org.ow2.asm:asm-commons:7.0'
    compile "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.3.41"
}

compileGroovy {
    dependsOn tasks.getByPath('compileKotlin')
    classpath += files(compileKotlin.destinationDir)
}
```
3.重写Transform API

```
package com.wx.methodtraceplugin

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

```

4.插入执行代码

```
package com.wx.methodtrace;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * @author ywx
 */
public class MethodTransformJ {

    private boolean isUnSupportAcc(int acc) {
        return (acc & (Opcodes.ACC_NATIVE | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ENUM | Opcodes.ACC_SUPER
                | Opcodes.ACC_ANNOTATION | Opcodes.ACC_BRIDGE | Opcodes.ACC_MANDATED | Opcodes.ACC_STRICT
                | Opcodes.ACC_TRANSIENT | Opcodes.ACC_VARARGS | Opcodes.ACC_SYNTHETIC)) != 0;
    }

    public byte[] transform(byte[] classBody, Config traceConfig) {
        ClassReader cr = new ClassReader(classBody);
        String clazz = cr.getClassName().replace("/", ".");
        if (!traceConfig.isConfigTraceClass(cr.getClassName()) || clazz.contains("MethodComputer")) {
            return classBody;
        }
        String beatClass = traceConfig.getBeatClass();
        if (Utils.INSTANCE.isEmpty(beatClass)) {
            beatClass = "com/wx/methodtrace/MethodComputer";
        }
        PluginLogger.info("transform  calssName = " + cr.getClassName());
        ClassWriter cw = new ClassWriter(cr, 0);
        String finalBeatClass = beatClass;
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM6, cw) {
            @Override
            public MethodVisitor visitMethod(int access,
                                             final String name, String desc, String signature, String[] exceptions) {
                final MethodVisitor visitor = super.visitMethod(access, name, desc, signature, exceptions);
                if (name.equals("<init>") || isUnSupportAcc(access)) {
                    // 构造函数和void返回类型函数不进行任何操作
                    return visitor;
                }
                return new MethodVisitor(Opcodes.ASM6, visitor) {

                    @Override
                    public void visitCode() {
                        onMethodEnter();
                        super.visitCode();
                    }

                    private void onMethodEnter() {
                        PluginLogger.info("====== 开始插入方法 = " + name);
                        mv.visitLdcInsn(clazz);
                        mv.visitLdcInsn(name);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, finalBeatClass, "onMethodEnter", "(Ljava/lang/String;Ljava/lang/String;)V", false);
                    }

                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ATHROW || opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.IRETURN
                                || opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN) {
                            mv.visitLdcInsn(clazz);
                            mv.visitLdcInsn(name);
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, finalBeatClass, "onMethodExit", "(Ljava/lang/String;Ljava/lang/String;)V", false);
                            PluginLogger.info("====== 插入方法结束 = " + name);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };
        cr.accept(cv, ClassReader.EXPAND_FRAMES);
        return cw.toByteArray();
    }
}

```

### 结果展现

![image](https://github.com/yangwenxin/MethodTrace/blob/main/2.png)

![image](https://github.com/yangwenxin/MethodTrace/blob/main/3.png)

编译后的文件在$project/build/intermediates/transforms/MethodTrace/下面有debug和release包，可以看到方法前后都插入了代码

```
public class App extends Application {
    public App() {
    }

    protected void attachBaseContext(Context base) {
        MethodComputer.onMethodEnter("com.wx.methodtracedemo.App", "attachBaseContext");
        super.attachBaseContext(base);

        try {
            Thread.sleep(2000L);
        } catch (InterruptedException var3) {
            var3.printStackTrace();
        }

        MethodComputer.onMethodExit("com.wx.methodtracedemo.App", "attachBaseContext");
    }
}
```
