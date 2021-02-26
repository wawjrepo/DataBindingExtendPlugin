package com.michael.databinding.plugin

import com.android.build.api.transform.*
import com.google.common.collect.ImmutableSet



import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.objectweb.asm.*
import org.objectweb.asm.commons.AdviceAdapter

/**
 * Created by hjy on 2018/10/23.
 */

class DataBindingExtendTransform extends Transform {

    Project project

    DataBindingExtendTransform(Project project) {
        this.project = project
    }

    @Override
    String getName() {
        return "DataBindingExtendTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return ImmutableSet.of(QualifiedContent.DefaultContentType.CLASSES)
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(QualifiedContent.Scope.PROJECT)
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)
        transformInvocation.getOutputProvider().deleteAll()
        boolean isFound = false
        transformInvocation.getInputs().each { TransformInput input ->
            input.directoryInputs.each { DirectoryInput directoryInput ->
                if (isFound) return
                if (directoryInput.file.isDirectory()) {
                    directoryInput.file.eachFileRecurse { File file ->
                        if (isFound) return
                        if (file.absolutePath.replace("\\", "/").contains("com/wawj/app/t/DataBinderMapperImpl.class")) {
                            ClassReader classReader = new ClassReader(new FileInputStream(file))
                            isFound = true
                            // 构建一个ClassWriter对象，并设置让系统自动计算栈和本地变量大小
                            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
                            ClassVisitor classVisitor = new AppLifecycleClassVisitor(classWriter)
                            //开始扫描class文件
                            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)

                            byte[] bytes = classWriter.toByteArray()

                            FileOutputStream fout = new FileOutputStream(file)
                            fout.write(bytes)
                            fout.close()
                            // 获取output目录
                            def dest = transformInvocation.getOutputProvider().getContentLocation(directoryInput.name,
                                    directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)

                            // 将input的目录复制到output指定目录
                            FileUtils.copyDirectory(directoryInput.file, dest)
                        }
                    }
                }

            }


            ////遍历jar文件 对jar不操作，但是要输出到out路径
            input.jarInputs.each { JarInput jarInput ->
                // 重命名输出文件（同目录copyFile会冲突）
                def jarName = jarInput.name
                def md5Name = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length() - 4)
                }
                def dest = transformInvocation.getOutputProvider().getContentLocation(jarName + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(jarInput.file, dest)
            }
        }

//
//        println " AppLifecycleTransform start to transform-------------->>>>>>>"
//
//        def appLifecycleCallbackList = []
//        transformInvocation.getInputs().each { TransformInput input ->
//
//            input.directoryInputs.each { DirectoryInput directoryInput ->
//
//                if (directoryInput.file.isDirectory()) {
//                    directoryInput.file.eachFileRecurse {File file ->
//                        //形如 AppLife$$****$$Proxy.class 的类，是我们要找的目标class
//                        if (file.absolutePath.contains("com/hm/iou/lifecycle/demo/MainActivity")) {
//                            ClassReader classReader = new ClassReader(inputStream)
//                            // 构建一个ClassWriter对象，并设置让系统自动计算栈和本地变量大小
//                            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS)
//                            ClassVisitor classVisitor = new AppLifecycleClassVisitor(classWriter)
//                            //开始扫描class文件
//                            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
//
//                            byte[] bytes = classWriter.toByteArray()
//                        }
//                    }
//                }
//
//                def dest = transformInvocation.getOutputProvider().getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
//                FileUtils.copyDirectory(directoryInput.file, dest)
//            }
//
//            input.jarInputs.each { JarInput jarInput ->
//                println "\njarInput = ${jarInput}"
//
//                def jarName = jarInput.name
//                def md5 = DigestUtils.md5Hex(jarInput.file.getAbsolutePath())
//                if (jarName.endsWith(".jar")) {
//                    jarName = jarName.substring(0, jarName.length() - 4)
//                }
//
//                def dest = transformInvocation.getOutputProvider().getContentLocation(jarName + md5, jarInput.contentTypes, jarInput.scopes, Format.JAR)
//                if (jarInput.file.getAbsolutePath().endsWith(".jar")) {
//                    //处理jar包里的代码
//                    File src = jarInput.file
//                    if (ScanUtil.shouldProcessPreDexJar(src.absolutePath)) {
//                        List<String> list = ScanUtil.scanJar(src, dest)
//                        if (list != null) {
//                            //appLifecycleCallbackList.addAll(list)
//                        }
//                    }
//                }
//                FileUtils.copyFile(jarInput.file, dest)
//            }
//        }
//
//        if (appLifecycleCallbackList.empty) {
//            println " LifeCycleTransform appLifecycleCallbackList empty"
//        } else {
//            new AppLifecycleCodeInjector(appLifecycleCallbackList).execute()
//        }
//
//        println "LifeCycleTransform transform finish----------------<<<<<<<\n"

    }

    class AppLifecycleClassVisitor extends ClassVisitor {

        private ClassVisitor mClassVisitor

        AppLifecycleClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM5, classVisitor)
            mClassVisitor = classVisitor
        }

        @Override
        MethodVisitor visitMethod(int access, String name,
                                  String desc, String signature,
                                  String[] exception) {
            MethodVisitor methodVisitor = mClassVisitor.visitMethod(access, name, desc, signature, exception)
            //找到collectDependencies()方法
            if ("collectDependencies" == name) {
                methodVisitor = new LoadAppLifecycleMethodAdapter(methodVisitor, access, name, desc)
            }
            return methodVisitor
        }
    }

    class LoadAppLifecycleMethodAdapter extends AdviceAdapter {

        LoadAppLifecycleMethodAdapter(MethodVisitor mv, int access, String name, String desc) {
            super(Opcodes.ASM5, mv, access, name, desc)
        }

        @Override
        protected void onMethodEnter() {
            super.onMethodEnter()
        }

        @Override
        protected void onMethodExit(int opcode) {
            super.onMethodExit(opcode)
            print "开始注入代码...  "
            def mainIncludes =project.extensions.extraProperties.get("mainIncludes") as ArrayList<String>
            mainIncludes.each { String moduleName ->
                mv.visitVarInsn(ALOAD, 1);
                mv.visitTypeInsn(NEW, "com/wawj/${moduleName}/DataBinderMapperImpl");
                mv.visitInsn(DUP);
                mv.visitMethodInsn(INVOKESPECIAL, "com/wawj/${moduleName}/DataBinderMapperImpl", "<init>", "()V", false);
                mv.visitMethodInsn(INVOKEVIRTUAL, "java/util/ArrayList", "add", "(Ljava/lang/Object;)Z", false);
                mv.visitInsn(POP);
            }
            println "结束注入代码"
        }
    }

}
