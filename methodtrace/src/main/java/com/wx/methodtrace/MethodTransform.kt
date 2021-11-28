package com.wx.methodtrace

import com.wx.methodtrace.PluginLogger.info
import org.objectweb.asm.*

/**
 * @author ywx
 * @date 2021-11-21
 */
class MethodTransform {

    private fun isUnSupportAcc(acc: Int): Boolean {
        return acc and (Opcodes.ACC_NATIVE or Opcodes.ACC_ABSTRACT or Opcodes.ACC_ENUM or Opcodes.ACC_SUPER
                or Opcodes.ACC_ANNOTATION or Opcodes.ACC_BRIDGE or Opcodes.ACC_MANDATED or Opcodes.ACC_STRICT
                or Opcodes.ACC_TRANSIENT or Opcodes.ACC_VARARGS or Opcodes.ACC_SYNTHETIC) != 0
    }

    fun transform(classBody: ByteArray, traceConfig: Config): ByteArray {
        val cr = ClassReader(classBody)
        val clazz = cr.className.replace("/", ".")
        if (clazz.contains("MethodComputer")
            || !traceConfig.isConfigTraceClass(cr.className)) {
            return classBody
        }
        var beatClass = traceConfig.beatClass
        info("transform  beatClass = $beatClass")
        if (Utils.isEmpty(beatClass)) {
            beatClass = "com/wx/methodtrace/MethodComputer"
        }
        info("transform  calssName = $clazz")
        val cw = ClassWriter(cr, 0)
        val cv: ClassVisitor = object : ClassVisitor(Opcodes.ASM6, cw) {
            override fun visitMethod(
                access: Int,
                name: String, desc: String, signature: String, exceptions: Array<String>
            ): MethodVisitor {
                val visitor = super.visitMethod(access, name, desc, signature, exceptions)
                return if (name == "<init>" || isUnSupportAcc(access)) {
                    // 构造函数和void返回类型函数不进行任何操作
                    visitor
                } else object : MethodVisitor(Opcodes.ASM6, visitor) {
                    override fun visitCode() {
                        onMethodEnter()
                        super.visitCode()
                    }

                    private fun onMethodEnter() {
                        info("====== 开始插入方法 = $name ======")
                        mv.visitLdcInsn(clazz)
                        mv.visitLdcInsn(name)
                        mv.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            beatClass,
                            "onMethodEnter",
                            "(Ljava/lang/String;Ljava/lang/String;)V",
                            false
                        )
                    }

                    override fun visitInsn(opcode: Int) {
                        if (opcode == Opcodes.ATHROW || opcode == Opcodes.RETURN || opcode == Opcodes.ARETURN || opcode == Opcodes.IRETURN || opcode == Opcodes.LRETURN || opcode == Opcodes.FRETURN || opcode == Opcodes.DRETURN) {
                            mv.visitLdcInsn(clazz)
                            mv.visitLdcInsn(name)
                            mv.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                beatClass,
                                "onMethodExit",
                                "(Ljava/lang/String;Ljava/lang/String;)V",
                                false
                            )
                            info("====== 插入方法结束 = $name ====== opcode = $opcode")
                        }
                        super.visitInsn(opcode)
                    }
                }
            }
        }
        cr.accept(cv, ClassReader.EXPAND_FRAMES)
        return cw.toByteArray()
    }
}