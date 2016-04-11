/*
 Copyright 2014 Georg Kohlweiss

 Licensed under the Apache License, Version 2.0 (the License);
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an AS IS BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
*/
package com.github.gekoh.yagen;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.NotFoundException;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author Georg Kohlweiss
 */
public class PatchTransformer implements ClassFileTransformer {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchTransformer.class);

    public static final List<String> PATCH_CLASS_LIST = Arrays.asList(
            "org.hibernate.cfg.Configuration$MappingsImpl",
            "org.hibernate.cfg.Configuration",
            "org.hibernate.mapping.Table",
            "org.hibernate.mapping.Constraint",
            "org.hibernate.mapping.Index",
            "org.hibernate.mapping.UniqueKey",
            "org.hibernate.id.SequenceGenerator",
            "org.hibernate.tool.hbm2ddl.SchemaExport",
            "org.hibernate.dialect.Dialect"
    );

    private ClassPool classPool;

    public PatchTransformer() {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        try {
            classPool.appendPathList(System.getProperty("java.class.path"));

            classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        if (PATCH_CLASS_LIST.contains(className.replace("/", "."))) {
            logInfo("patching class: " + className);

            try {
                classPool.appendClassPath(new LoaderClassPath(loader));
                CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(classfileBuffer));

                if (patchClass(ctClass)) {
                    logInfo("... done");
                    return ctClass.toBytecode();
                }
                else {
                    logInfo("... no change");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return classfileBuffer;
    }

    public static boolean patchClass(CtClass clazz) throws Exception {
        String className = clazz.getName().replace("/", ".");
        if("org.hibernate.cfg.Configuration".equals(className)) {
            patchConfiguration(clazz);
            return true;
        }
        if("org.hibernate.mapping.Table".equals(className)) {
            patchTable(clazz);
            return true;
        }
        if("org.hibernate.mapping.Index".equals(className)) {
            patchIndex(clazz);
            return true;
        }
        if("org.hibernate.mapping.UniqueKey".equals(className) || "org.hibernate.mapping.Constraint".equals(className)) {
            patchConstraint(clazz);
            return true;
        }
        if("org.hibernate.id.SequenceGenerator".equals(className)) {
            patchSequenceGenerator(clazz);
            return true;
        }
        if("org.hibernate.tool.hbm2ddl.SchemaExport".equals(className)) {
            patchSchemaExport(clazz);
            return true;
        }
        if("org.hibernate.dialect.Dialect".equals(className)) {
            patchDialect(clazz);
            return true;
        }
        return false;
    }

    private static void patchConfiguration(CtClass clazz) throws CannotCompileException, NotFoundException {

        CtClass serviceRegistryClass = clazz.getClassPool().get("java.lang.Object");
        clazz.addField(new CtField(serviceRegistryClass, "serviceRegistry", clazz));

        clazz.addMethod(CtMethod.make("public void setServiceRegistry(Object serviceRegistry) {\n" +
                "this.serviceRegistry = serviceRegistry;\n" +
                "}", clazz));

        String initDialectSrc = "com.github.gekoh.yagen.hibernate.PatchGlue.initDialect($1, getNamingStrategy(), getProperties(), serviceRegistry, classes.values());";

        clazz.getDeclaredMethod("generateDropSchemaScript").insertBefore(initDialectSrc);
        clazz.getDeclaredMethod("generateSchemaCreationScript").insertBefore(initDialectSrc);

        try {
            clazz.getDeclaredMethod("generateSchemaUpdateScriptList").insertBefore(initDialectSrc);
            clazz.getDeclaredMethod("generateSchemaUpdateScript").insertBefore(initDialectSrc);
        } catch (NotFoundException ignore) {
            // seems that used hibernate version does not have these methods
        }

        CtMethod method = clazz.getDeclaredMethod("generateSchemaCreationScript");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.addHeaderAndFooter($_, dialect);"
        );

        method = clazz.getDeclaredMethod("reset");

        method.insertAfter(
                "com.github.gekoh.yagen.hibernate.ReflectExecutor.newProfileIfNull();\n" +
                        "java.util.List interceptors = com.github.gekoh.yagen.hibernate.PatchGlue.getConfigurationInterceptors();\n" +
                        "if (interceptors != null) {\n" +
                        "    java.util.Iterator it = interceptors.iterator();\n" +
                        "    while(it.hasNext()) {\n" +
                        "        ((com.github.gekoh.yagen.hibernate.PatchGlue.ConfigurationInterceptor)it.next()).use(this);\n" +
                        "    }\n" +
                        "}"
        );
    }

    private static void patchTable(CtClass clazz) throws NotFoundException, CannotCompileException {
        CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterTableSqlCreateString($0, $1, $_);"
        );

        method = clazz.getDeclaredMethod("sqlDropString");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterTableSqlDropString($0, $1, $_);"
        );
    }

    private static void patchIndex(CtClass clazz) throws CannotCompileException, NotFoundException {
        CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterIndexSqlCreateString(getTable(), $1, $_, getName(), getColumnIterator());"
        );
    }

    private static void patchConstraint(CtClass clazz) throws CannotCompileException, NotFoundException {
        CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterConstraintSqlCreateString(getTable(), $1, $_, $0);"
        );
    }

    private static void patchSequenceGenerator(CtClass clazz) throws CannotCompileException, NotFoundException {
        CtMethod method = clazz.getDeclaredMethod("sqlCreateStrings");

        method.insertAfter(
                "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterSequenceSqlCreateStrings($1, $_, identifierType);"
        );
    }

    private static void patchSchemaExport(CtClass clazz) throws CannotCompileException, NotFoundException {
        ClassPool cp = clazz.getClassPool();

        CtClass ctClassServiceReg = null;
        try {
            ctClassServiceReg = cp.get("org.hibernate.service.ServiceRegistry");
            CtConstructor constructor = clazz.getDeclaredConstructor(new CtClass[]{ctClassServiceReg, cp.get("org.hibernate.cfg.Configuration")});
            constructor.insertBefore("$2.setServiceRegistry($1);");
        } catch (NotFoundException ignore) {
            // will not be able to set service registry since it is not available in hibernate prior ver 4
        }

        try {
            // Hibernate 4.3.5
            CtMethod method = clazz.getDeclaredMethod("perform");
            method.setName("performApi");
            method.setModifiers(Modifier.setPublic(method.getModifiers()));
            method.addParameter(cp.get("java.lang.String"));

            method.insertBefore("delimiter = $3;");

            CtMethod newMethod = CtMethod.make(
                    "private void perform(String[] sqlCommands, java.util.List exporters) {\n" +
                            "    com.github.gekoh.yagen.hibernate.PatchGlue.schemaExportPerform (sqlCommands, exporters, $0);\n" +
                            "}"
                    ,
                    clazz
            );
            clazz.addMethod(newMethod);
        } catch (NotFoundException e) {
            // Hibernate 3
            CtClass writerCl = cp.get("java.io.Writer");
            CtClass statementCl = cp.get("java.sql.Statement");
            CtClass stringCl = cp.get("java.lang.String");

            CtMethod method = clazz.getDeclaredMethod("execute", new CtClass[]{CtClass.booleanType, CtClass.booleanType, writerCl, statementCl, stringCl});
            method.setName("executeApi");
            method.setModifiers(Modifier.setPublic(method.getModifiers()));
            method.addParameter(cp.get("java.lang.String"));

            method.insertBefore("delimiter = $6;");

            CtMethod newMethod = CtMethod.make(
                    "private void execute(boolean script, boolean export, java.io.Writer fileOutput, java.sql.Statement statement, java.lang.String sql) {\n" +
                            "    com.github.gekoh.yagen.hibernate.PatchGlue.schemaExportExecute (script, export, fileOutput, statement, sql, $0);\n" +
                            "}"
                    ,
                    clazz
            );
            clazz.addMethod(newMethod);
        }
    }

    private static void patchDialect(CtClass clazz) throws CannotCompileException, NotFoundException {
        clazz.addField(CtField.make("private Object ddlEnhancer;", clazz));
        clazz.addField(CtField.make("private Object serviceRegistry;", clazz));

        clazz.addMethod(CtMethod.make(
                "public void initDDLEnhancer(Object profile, org.hibernate.dialect.Dialect dialect, Object serviceRegistry, java.util.Collection persistentClasses) {\n" +
                        "        this.serviceRegistry = serviceRegistry;\n" +
                        "        ddlEnhancer = com.github.gekoh.yagen.hibernate.PatchGlue.newDDLEnhancer(profile, dialect, persistentClasses);\n" +
                        "    }",
                clazz
        ));

        clazz.addMethod(CtMethod.make(
                "public Object getDDLEnhancer() {\n" +
                        "        return ddlEnhancer;\n" +
                        "    }",
                clazz
        ));

        clazz.addMethod(CtMethod.make(
                "public Object getServiceRegistry() {\n" +
                        "        return serviceRegistry;\n" +
                        "    }",
                clazz
        ));

        // when using hibernate 3 in runtime classpath we need to add a wrapper method since the signature changed slightly and
        // yagen is build against hibernate 4
        try {
            clazz.getDeclaredMethod("getTypeName", new CtClass[]{CtClass.intType, CtClass.longType, CtClass.intType, CtClass.intType});
        } catch (NotFoundException e) {
            clazz.addMethod(CtMethod.make(
                    "public java.lang.String getTypeName(int code, long length, int precision, int scale) throws org.hibernate.HibernateException {\n" +
                            "return getTypeName(code, (int) length, precision, scale);\n" +
                            "}",
                    clazz
            ));
        }

        clazz.addInterface(clazz.getClassPool().get("com.github.gekoh.yagen.hibernate.DDLEnhancer"));
    }

    private static void logInfo(String msg) {
        System.out.println(new Date().toString() + " " + PatchTransformer.class.getName() + " " + msg);
    }
}