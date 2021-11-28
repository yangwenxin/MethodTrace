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
