package com.googlecode.d2j.dex;

import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexFieldNode;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.node.DexMethodNode;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import java.util.Map;

public interface IDexAsm {
    boolean convertField(Dex2jar dex2jar,DexClassNode classNode, DexFieldNode fieldNode, ClassVisitor cv);

    boolean convertMethod(Dex2jar dex2jar,DexClassNode classNode, DexMethodNode methodNode, ClassVisitor cv, Dex2Asm.ClzCtx clzCtx);

    boolean addMethod(Dex2jar dex2jar,DexClassNode classNode, ClassVisitor cv);

    boolean convertClass(Dex2jar dex2jar,DexFileNode dfn, DexClassNode classNode, ClassVisitorFactory cvf, Map<String, Dex2Asm.Clz> classes);

    boolean convertCode(Dex2jar dex2jar,DexMethodNode methodNode, MethodVisitor mv, Dex2Asm.ClzCtx clzCtx);
}
