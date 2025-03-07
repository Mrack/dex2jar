/*
 * dex2jar - Tools to work with android .dex and java .class files
 * Copyright (c) 2009-2012 Panxiaobo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.googlecode.d2j.dex;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.googlecode.d2j.node.DexClassNode;
import com.googlecode.d2j.node.DexFieldNode;
import com.googlecode.d2j.node.DexMethodNode;
import com.googlecode.d2j.reader.BaseDexFileReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import com.googlecode.d2j.converter.IR2JConverter;
import com.googlecode.d2j.node.DexFileNode;
import com.googlecode.d2j.reader.DexFileReader;
import com.googlecode.d2j.reader.zip.ZipUtil;
import com.googlecode.dex2jar.ir.IrMethod;
import com.googlecode.dex2jar.ir.stmt.LabelStmt;
import com.googlecode.dex2jar.ir.stmt.Stmt;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;

public class Dex2jar {
    private static IDexAsm defaultAsm = new IDexAsm() {
        @Override
        public boolean convertField(Dex2jar dex2jar, DexClassNode classNode, DexFieldNode fieldNode, ClassVisitor cv) {
            return false;
        }

        @Override
        public boolean convertMethod(Dex2jar dex2jar, DexClassNode classNode, DexMethodNode methodNode, ClassVisitor cv, Dex2Asm.ClzCtx clzCtx) {
            return false;
        }

        @Override
        public boolean addMethod(Dex2jar dex2jar, DexClassNode classNode, ClassVisitor cv) {
            return false;
        }

        @Override
        public boolean convertClass(Dex2jar dex2jar, DexFileNode dfn, DexClassNode classNode, ClassVisitorFactory cvf, Map<String, Dex2Asm.Clz> classes) {
            return false;
        }

        @Override
        public boolean convertCode(Dex2jar dex2jar, DexMethodNode methodNode, MethodVisitor mv, Dex2Asm.ClzCtx clzCtx) {
            return false;
        }
    };

    public static void setAsm(IDexAsm asm) {
        Dex2jar.defaultAsm = asm;
    }

    public static Dex2jar from(byte[] in) throws IOException {
        return from(new DexFileReader(ZipUtil.readDex(in)));
    }

    public static Dex2jar from(ByteBuffer in) throws IOException {
        return from(new DexFileReader(in));
    }

    public static Dex2jar from(BaseDexFileReader reader) {
        return new Dex2jar(reader);
    }

    public static Dex2jar from(File in) throws IOException {
        return from(Files.readAllBytes(in.toPath()));
    }

    public static Dex2jar from(InputStream in) throws IOException {
        return from(new DexFileReader(in));
    }

    public static Dex2jar from(String in) throws IOException {
        return from(new File(in));
    }

    private DexExceptionHandler exceptionHandler;

    final private BaseDexFileReader reader;
    private int readerConfig;
    private int v3Config;

    public int getReaderConfig() {
        return readerConfig;
    }

    public int getV3Config() {
        return v3Config;
    }

    private Dex2jar(BaseDexFileReader reader) {
        super();
        this.reader = reader;
        readerConfig |= DexFileReader.SKIP_DEBUG;
    }

    private void doTranslate(final Path dist) throws IOException {

        DexFileNode fileNode = new DexFileNode();
        try {
            reader.accept(fileNode, readerConfig | DexFileReader.IGNORE_READ_EXCEPTION);
        } catch (Exception ex) {
            exceptionHandler.handleFileException(ex);
        }
        ClassVisitorFactory cvf = new ClassVisitorFactory() {
            @Override
            public ClassVisitor create(final String name) {
                final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                final LambadaNameSafeClassAdapter rca = new LambadaNameSafeClassAdapter(cw);
                return new ClassVisitor(Opcodes.ASM5, rca) {
                    @Override
                    public void visitEnd() {
                        super.visitEnd();
                        String className = rca.getClassName();
                        byte[] data;
                        try {
                            // FIXME handle 'java.lang.RuntimeException: Method code too large!'
                            data = cw.toByteArray();
                        } catch (Exception ex) {
                            System.err.println(String.format("ASM fail to generate .class file: %s", className));
                            exceptionHandler.handleFileException(ex);
                            return;
                        }
                        try {
                            Path dist1 = dist.resolve(className + ".class");
                            Path parent = dist1.getParent();
                            if (parent != null && !Files.exists(parent)) {
                                Files.createDirectories(parent);
                            }
                            Files.write(dist1, data);
                        } catch (IOException e) {
                            e.printStackTrace(System.err);
                        }
                    }
                };
            }
        };

        new ExDex2Asm(exceptionHandler) {
            @Override
            public void convertField(DexClassNode classNode, DexFieldNode fieldNode, ClassVisitor cv) {
                if (defaultAsm.convertField(Dex2jar.this, classNode, fieldNode, cv)) {
                    super.convertField(classNode, fieldNode, cv);
                }
            }

            @Override
            public void convertMethod(DexClassNode classNode, DexMethodNode methodNode, ClassVisitor cv, ClzCtx clzCtx) {
                if (defaultAsm.convertMethod(Dex2jar.this, classNode, methodNode, cv, clzCtx)) {
                    super.convertMethod(classNode, methodNode, cv, clzCtx);
                }
            }

            @Override
            public void addMethod(DexClassNode classNode, ClassVisitor cv) {
                if (defaultAsm.addMethod(Dex2jar.this, classNode, cv)) {
                    super.addMethod(classNode, cv);
                }
            }

            @Override
            public void convertClass(DexFileNode dfn, DexClassNode classNode, ClassVisitorFactory cvf, Map<String, Clz> classes) {
                if (defaultAsm.convertClass(Dex2jar.this, dfn, classNode, cvf, classes)) {
                    super.convertClass(dfn, classNode, cvf, classes);
                }
            }

            public void convertCode(DexMethodNode methodNode, MethodVisitor mv, ClzCtx clzCtx) {
                if ((readerConfig & DexFileReader.SKIP_CODE) != 0) {
                    // also skip clinit
                    return;
                }
                if (defaultAsm.convertCode(Dex2jar.this, methodNode, mv, clzCtx)) {
                    super.convertCode(methodNode, mv, clzCtx);
                }
            }

            @Override
            public void optimize(IrMethod irMethod) {
                T_cleanLabel.transform(irMethod);
                if (0 != (v3Config & V3.TOPOLOGICAL_SORT)) {
                    // T_topologicalSort.transform(irMethod);
                }
                T_deadCode.transform(irMethod);
                T_removeLocal.transform(irMethod);
                T_removeConst.transform(irMethod);
                T_zero.transform(irMethod);
                if (T_npe.transformReportChanged(irMethod)) {
                    T_deadCode.transform(irMethod);
                    T_removeLocal.transform(irMethod);
                    T_removeConst.transform(irMethod);
                }
                T_new.transform(irMethod);
                T_fillArray.transform(irMethod);
                T_agg.transform(irMethod);
                T_multiArray.transform(irMethod);
                T_voidInvoke.transform(irMethod);
                if (0 != (v3Config & V3.PRINT_IR)) {
                    int i = 0;
                    for (Stmt p : irMethod.stmts) {
                        if (p.st == Stmt.ST.LABEL) {
                            LabelStmt labelStmt = (LabelStmt) p;
                            labelStmt.displayName = "L" + i++;
                        }
                    }
                    System.out.println(irMethod);
                }
                {
                    // https://github.com/pxb1988/dex2jar/issues/477
                    // dead code found in unssa, clean up
                    T_deadCode.transform(irMethod);
                    T_removeLocal.transform(irMethod);
                    T_removeConst.transform(irMethod);
                }
                T_type.transform(irMethod);
                T_unssa.transform(irMethod);
                T_ir2jRegAssign.transform(irMethod);
                T_trimEx.transform(irMethod);
            }

            @Override
            public void ir2j(IrMethod irMethod, MethodVisitor mv, ClzCtx clzCtx) {
                Dex2jar.this.ir2j(irMethod, mv, clzCtx);
            }
        }.convertDex(fileNode, cvf);

    }

    public void ir2j(IrMethod irMethod, MethodVisitor mv, Dex2Asm.ClzCtx clzCtx) {
        new IR2JConverter()
                .optimizeSynchronized(0 != (V3.OPTIMIZE_SYNCHRONIZED & v3Config))
                .clzCtx(clzCtx)
                .ir(irMethod)
                .asm(mv)
                .convert();
    }


    public DexExceptionHandler getExceptionHandler() {
        return exceptionHandler;
    }

    public BaseDexFileReader getReader() {
        return reader;
    }

    public Dex2jar reUseReg(boolean b) {
        if (b) {
            this.v3Config |= V3.REUSE_REGISTER;
        } else {
            this.v3Config &= ~V3.REUSE_REGISTER;
        }
        return this;
    }

    public Dex2jar topoLogicalSort(boolean b) {
        if (b) {
            this.v3Config |= V3.TOPOLOGICAL_SORT;
        } else {
            this.v3Config &= ~V3.TOPOLOGICAL_SORT;
        }
        return this;
    }

    public Dex2jar noCode(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.SKIP_CODE | DexFileReader.KEEP_CLINIT;
        } else {
            this.readerConfig &= ~(DexFileReader.SKIP_CODE | DexFileReader.KEEP_CLINIT);
        }
        return this;
    }

    public Dex2jar optimizeSynchronized(boolean b) {
        if (b) {
            this.v3Config |= V3.OPTIMIZE_SYNCHRONIZED;
        } else {
            this.v3Config &= ~V3.OPTIMIZE_SYNCHRONIZED;
        }
        return this;
    }

    public Dex2jar printIR(boolean b) {
        if (b) {
            this.v3Config |= V3.PRINT_IR;
        } else {
            this.v3Config &= ~V3.PRINT_IR;
        }
        return this;
    }

    public Dex2jar reUseReg() {
        this.v3Config |= V3.REUSE_REGISTER;
        return this;
    }

    public Dex2jar optimizeSynchronized() {
        this.v3Config |= V3.OPTIMIZE_SYNCHRONIZED;
        return this;
    }

    public Dex2jar printIR() {
        this.v3Config |= V3.PRINT_IR;
        return this;
    }

    public Dex2jar topoLogicalSort() {
        this.v3Config |= V3.TOPOLOGICAL_SORT;
        return this;
    }

    public void setExceptionHandler(DexExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
    }

    public Dex2jar skipDebug(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.SKIP_DEBUG;
        } else {
            this.readerConfig &= ~DexFileReader.SKIP_DEBUG;
        }
        return this;
    }

    public Dex2jar skipDebug() {
        this.readerConfig |= DexFileReader.SKIP_DEBUG;
        return this;
    }

    public void to(Path file) throws IOException {
        if (Files.exists(file) && Files.isDirectory(file)) {
            doTranslate(file);
        } else {
            try (FileSystem fs = createZip(file)) {
                doTranslate(fs.getPath("/"));
            }
        }
    }

    private static FileSystem createZip(Path output) throws IOException {
        Map<String, Object> env = new HashMap<>();
        env.put("create", "true");
        Files.deleteIfExists(output);
        Path parent = output.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        for (FileSystemProvider p : FileSystemProvider.installedProviders()) {
            String s = p.getScheme();
            if ("jar".equals(s) || "zip".equalsIgnoreCase(s)) {
                return p.newFileSystem(output, env);
            }
        }
        throw new IOException("cant find zipfs support");
    }

    public Dex2jar withExceptionHandler(DexExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public Dex2jar skipExceptions(boolean b) {
        if (b) {
            this.readerConfig |= DexFileReader.SKIP_EXCEPTION;
        } else {
            this.readerConfig &= ~DexFileReader.SKIP_EXCEPTION;
        }
        return this;
    }

}
