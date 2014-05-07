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
package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import org.apache.commons.lang.StringUtils;
import org.hibernate.dialect.Dialect;
import org.joda.time.DateTime;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss 
 */
public class PatchHibernateMappingClasses {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchHibernateMappingClasses.class);

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\r?\n"+CreateDDL.STATEMENT_SEPARATOR.trim()+"\r?\n");
    private static final Pattern PLSQL_END_PATTERN = Pattern.compile("[\\s]+end[\\s]*([a-z_]+)?;$", Pattern.CASE_INSENSITIVE);

    static List CONFIGURATION_INTERCEPTOR_INSTANCES = new ArrayList();

    private static boolean alreadyLoaded(String className) throws Exception {
        java.lang.reflect.Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[] { String.class });
        m.setAccessible(true);
        ClassLoader cl = PatchHibernateMappingClasses.class.getClassLoader();
        Object test1 = m.invoke(cl, className);
        return test1 != null;
    }

    public static void applyPatch() throws Exception {
        if (alreadyLoaded("org.hibernate.cfg.Configuration$MappingsImpl")) {
            throw new HibernateClassesAlreadyLoadedException();
        }

        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.cfg.Configuration");
            CtMethod method = clazz.getDeclaredMethod("generateDropSchemaScript");

            method.insertBefore(
                    "com.github.gekoh.yagen.hibernate.PatchGlue.initDialect($1, getNamingStrategy(), getProperties());"
            );

            CtClass mappingsImpl = null;
            for (CtClass nestedClass : clazz.getNestedClasses()) {
                if (nestedClass.getSimpleName().equals("Configuration$MappingsImpl")) {
                    mappingsImpl = nestedClass;
                    method = mappingsImpl.getDeclaredMethod("addClass");

                    method.insertBefore(
                            "com.github.gekoh.yagen.hibernate.PatchGlue.addClass($1);"
                    );

                    break;
                }
            }
            LOG.info("patched class {}", clazz.toClass().toString());
            LOG.info("patched class {}", mappingsImpl.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.ejb.Ejb3Configuration");
            CtMethod method = clazz.getDeclaredMethod("configure", new CtClass[]{cp.get("java.util.Properties"), cp.get("java.util.Map")});

            method.insertAfter(
                    "java.util.List interceptors = com.github.gekoh.yagen.hibernate.PatchGlue.getConfigurationInterceptors();\n" +
                            "if (interceptors != null && $1 != null) {\n" +
                            "    java.util.Iterator it = interceptors.iterator();\n" +
                            "    while(it.hasNext()) {\n" +
                            "        ((com.github.gekoh.yagen.hibernate.PatchGlue.ConfigurationInterceptor)it.next()).use(this);\n" +
                            "    }\n" +
                            "}"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.mapping.Table");
            CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

            method.insertAfter(
                    "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterTableSqlCreateString($0, $1, $_);"
            );

            method = clazz.getDeclaredMethod("sqlDropString");

            method.insertAfter(
                    "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterTableSqlDropString($0, $1, $_);"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.mapping.Constraint");
            CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

            method.insertAfter(
                    "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterConstraintSqlCreateString(getTable(), $1, $_, $0);"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.mapping.Index");
            CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

            method.insertAfter(
                    "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterIndexSqlCreateString(getTable(), $1, $_, getName(), getColumnIterator());"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.id.SequenceGenerator");
            CtMethod method = clazz.getDeclaredMethod("sqlCreateStrings");

            method.insertAfter(
                    "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterSequenceSqlCreateStrings($1, $_, identifierType);"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.tool.hbm2ddl.SchemaExport");
            CtClass writerCl = cp.get("java.io.Writer");
            CtClass statementCl = cp.get("java.sql.Statement");
            CtClass stringCl = cp.get("java.lang.String");

            CtMethod method = clazz.getDeclaredMethod("create", new CtClass[]{CtClass.booleanType, CtClass.booleanType, writerCl, statementCl});

            method.insertBefore(
                    "createSQL = com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses.addHeaderAndFooter(createSQL, dialect);"
            );

            method = clazz.getDeclaredMethod("execute", new CtClass[]{CtClass.booleanType, CtClass.booleanType, writerCl, statementCl, stringCl});

            method.insertBefore(
                    "if (com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses.isScript($5)) {\n" +
                            "    java.util.Iterator it = com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses.splitSQL($5);\n" +
                            "    while(it.hasNext()) {\n" +
                            "        String sql = (String)it.next();\n" +
                            "        try {\n" +
                            "            execute($1,$2,$3,$4,sql);\n" +
                            "        } catch (java.sql.SQLException e) {\n" +
                            "            exceptions.add( e );\n" +
                            "            log.error( \"Unsuccessful: {}\", sql );\n" +
                            "            log.error( e.getMessage() );\n\n" +
                            "        }\n" +
                            "    }\n" +
                            "  return;\n" +
                            "}\n" +
                            "$5 = com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses.prepareDDL($5,$3,$1);\n" +
                            "if ($5.length()<1) {\n" +
                            "    $2 = false;\n" +
                            "    delimiter = \"\";\n" +
                            "}\n" +
                            "else {\n" +
                            "    int idx = $5.lastIndexOf(\"\\n/\");\n" +
                            "    if (idx>=0 && idx+2 == $5.length()) {\n" +
                            "        delimiter = \"\\n/\";\n" +
                            "        $5 = $5.substring(0,idx);\n" +
                            "    }\n" +
                            "    else {\n" +
                            "        delimiter = \";\";\n" +
                            "    }\n" +
                            "}\n"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }
    }

    public static void patchCollectionsAlwaysLazy() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.mapping.Collection");
            CtMethod method = clazz.getDeclaredMethod("setLazy");

            method.insertAfter(
                            "this.lazy = true;\n"
            );

            method = clazz.getDeclaredMethod("setFetchMode");

            method.insertAfter(
                            "this.fetchMode = org.hibernate.FetchMode.SELECT;\n"
            );

            method = clazz.getDeclaredMethod("setOrphanDelete");

            method.insertAfter(
                    "this.orphanDelete = false;\n"
            );

            LOG.info("patched class {}", clazz.toClass().toString());
        }

    }

    public static void patchIgnoreVersion() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.event.def.DefaultMergeEventListener");
            CtMethod method = clazz.getDeclaredMethod("isVersionChanged");

            method.insertBefore("return false;");

            LOG.info("patched class {}", clazz.toClass().toString());
        }
    }

    public static void patchNumericBooleanTypeForOracle() throws Exception {
        ClassPool cp = ClassPool.getDefault();
        cp.insertClassPath(new ClassClassPath(PatchHibernateMappingClasses.class));

        {
            CtClass clazz = cp.get("org.hibernate.type.NumericBooleanType");
            CtConstructor constructor = clazz.getConstructors()[0];

            constructor.setBody("super(org.hibernate.type.descriptor.sql.BitTypeDescriptor.INSTANCE, org.hibernate.type.descriptor.java.BooleanTypeDescriptor.INSTANCE);");

            LOG.info("patched class {}", clazz.toClass().toString());
        }
    }

    public static boolean isScript(String sql) {
        int idx = indexOfSeparator(sql, 0);

        return idx > 0 || indexOfSeparator(sql, Math.min(CreateDDL.STATEMENT_SEPARATOR.length(), sql.length())) > 0;
    }

    public static Iterator<String> splitSQL(String sql) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(sql);
        int endIdx, idx=0;
        ArrayList<String> statements = new ArrayList<String>();

        while(matcher.find(idx)) {
            endIdx=matcher.start();

            if (endIdx-idx > 0) {
                statements.add(sql.substring(idx, endIdx));
            }

            if (endIdx>=0) {
                idx = matcher.end();
            }
        }

        if (idx < sql.length()) {
            statements.add(sql.substring(idx));
        }

        return statements.iterator();
    }

    public static String prepareDDL(String sql, Writer fileOutput, boolean script){
        Iterator<String> it = splitSQL(sql);
        while(it.hasNext()) {
            sql = it.next();
        }
        sql = sql.trim();

        if (PLSQL_END_PATTERN.matcher(sql).find()) {
            sql += "\n/";
        }
        // remove trailing semicolon in case of non pl/sql type objects/statements
        else if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length()-1);
        }

        StringWriter wr = new StringWriter();
        int nlIdx=-1, prevIdx;
        String line;
        try {
            do {
                prevIdx = nlIdx+1;
                nlIdx = sql.indexOf('\n', prevIdx);
                if (nlIdx < 0) {
                    line = StringUtils.stripEnd(sql.substring(prevIdx), null);
                }
                else {
                    line = StringUtils.stripEnd(sql.substring(prevIdx, nlIdx), null) + "\n";
                }
                if (wr.getBuffer().length() < 1) {
                    if (line.trim().startsWith("/*")) {
                        nlIdx = sql.indexOf("*/", prevIdx);
                        line = StringUtils.stripEnd(sql.substring(prevIdx, nlIdx >= 0 ? nlIdx+2 : sql.length()), null) + "\n";
                        nlIdx+= line.trim().endsWith("*/") ? 2 : 1;
                    }
                    if (line.trim().startsWith("--") || line.trim().startsWith("/*"))
                    {
                        if (script) {
                            System.out.print(line);
                        }
                        if (fileOutput != null) {
                            fileOutput.write(line);
                        }
                        continue;
                    }
                }

                wr.write(line);
            } while (nlIdx >= 0);
        } catch (IOException e) {
            LOG.error("unable to prepare DDL", e);
        }
        return wr.toString().trim();
    }

    public static String[] addHeaderAndFooter(String[] createSQL, Dialect dialect){
        DDLGenerator.Profile profile;

        if (dialect instanceof DDLEnhancer) {
            DDLEnhancer ddlEnhancer = (DDLEnhancer) dialect;
            profile = ddlEnhancer.getDDLEnhancer().getProfile();
        }
        else {
            return createSQL;
        }

        int idx = 0;
        List<String> ddlList = new ArrayList(Arrays.asList(createSQL));

        StringWriter sw = new StringWriter();

        sw.write("-- auto generated by " + DDLGenerator.class.getName() + " at " + new DateTime()+"\n");
        sw.write("-- DO NOT EDIT MANUALLY!");

        ddlList.add(idx++, sw.toString());

        for (DDLGenerator.AddDDLEntry addDdlFile : profile.getHeaderDdls()) {
            sw = new StringWriter();

            sw.write("-- " + addDdlFile + "\n");
            sw.write("-- DO NOT EDIT!\n");
            sw.write(addDdlFile.getDdlText(dialect));
            sw.write("\n");

            ddlList.add(idx++, sw.toString());
        }

        for (DDLGenerator.AddDDLEntry addDdlFile : profile.getAddDdls()) {
            sw = new StringWriter();

            sw.write("-- " + addDdlFile + "\n");
            sw.write("-- DO NOT EDIT!\n");
            sw.write(addDdlFile.getDdlText(dialect));
            sw.write("\n");

            ddlList.add(sw.toString());
        }

        return ddlList.toArray(new String[ddlList.size()]);
    }

    private static int indexOfSeparator(String sql, int startIdx) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(sql);
        if (matcher.find(startIdx)) {
            return matcher.start();
        }
        return -1;
    }
}
