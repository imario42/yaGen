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
import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import org.apache.commons.lang.StringUtils;
import org.hibernate.dialect.Dialect;
import org.joda.time.DateTime;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss 
 */
public class PatchHibernateMappingClasses {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchHibernateMappingClasses.class);

    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("\r?\n"+CreateDDL.STATEMENT_SEPARATOR.trim()+"\r?\n");
    private static final Pattern PLSQL_END_PATTERN = Pattern.compile("[\\s]+end[\\s]*([a-z_]+)?;([\\s]*(\\r?\\n)?/?)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "(((--).*((\\r?\\n)|$))+)|" + // single line comment(s)
            "(/\\*(?:.|(\\r?\\n))*?\\*/)", // block comment
            Pattern.CASE_INSENSITIVE|Pattern.MULTILINE);

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
            String initDialectSrc = "com.github.gekoh.yagen.hibernate.PatchGlue.initDialect($1, getNamingStrategy(), getProperties());";

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
                    "$_ = com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses.addHeaderAndFooter($_, dialect);"
            );

            method = clazz.getDeclaredMethod("reset");

            method.insertAfter(
                    "java.util.List interceptors = com.github.gekoh.yagen.hibernate.PatchGlue.getConfigurationInterceptors();\n" +
                            "if (interceptors != null) {\n" +
                            "    java.util.Iterator it = interceptors.iterator();\n" +
                            "    while(it.hasNext()) {\n" +
                            "        ((com.github.gekoh.yagen.hibernate.PatchGlue.ConfigurationInterceptor)it.next()).use(this);\n" +
                            "    }\n" +
                            "}"
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
            CtClass clazz = cp.get("org.hibernate.mapping.UniqueKey");
            CtMethod method = clazz.getDeclaredMethod("sqlCreateString");

            method.insertAfter(
                    "$_ = com.github.gekoh.yagen.hibernate.PatchGlue.afterConstraintSqlCreateString(getTable(), $1, $_, $0);"
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

            LOG.info("patched class {}", clazz.toClass().toString());
        }

        {
            CtClass clazz = cp.get("org.hibernate.dialect.Dialect");
            clazz.addField(CtField.make("private com.github.gekoh.yagen.ddl.CreateDDL ddlEnhancer;", clazz));

            clazz.addMethod(CtMethod.make(
                    "public void initDDLEnhancer(com.github.gekoh.yagen.ddl.DDLGenerator.Profile profile, org.hibernate.dialect.Dialect dialect) {\n" +
                            "        ddlEnhancer = new com.github.gekoh.yagen.ddl.CreateDDL(profile, dialect);\n" +
                            "    }",
                    clazz
            ));

            clazz.addMethod(CtMethod.make(
                    "public com.github.gekoh.yagen.ddl.CreateDDL getDDLEnhancer() {\n" +
                            "        return ddlEnhancer;\n" +
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

            clazz.addInterface(cp.get("com.github.gekoh.yagen.hibernate.DDLEnhancer"));

            LOG.info("patched class {}", clazz.toClass().toString());
        }

/*
        {
            CtClass clazz = cp.get("org.hibernate.tool.hbm2ddl.SchemaExport");
            CtClass writerCl = cp.get("java.io.Writer");
            CtClass statementCl = cp.get("java.sql.Statement");
            CtClass stringCl = cp.get("java.lang.String");

            CtMethod method = clazz.getDeclaredMethod("create", new CtClass[]{CtClass.booleanType, CtClass.booleanType});

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
*/
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

    public static Collection<String> splitSQL(String sql) {
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
            String singleSql = sql.substring(idx);
            if (StringUtils.isNotEmpty(singleSql.trim())) {
                statements.add(singleSql);
            }
        }

        for (int i=0; i<statements.size(); i++) {
            String stmt = statements.get(i);
            if (stmt == null || stmt.trim().length() < 1) {
                statements.remove(i);
                i--;
                continue;
            }
            matcher = COMMENT_PATTERN.matcher(stmt);
            if (matcher.find() && stmt.substring(0, matcher.start()).trim().length() < 1) {
                statements.remove(i);
                statements.add(i, stmt.substring(matcher.end()));
                if (stmt.substring(0, matcher.end()).trim().length() > 0) {
                    statements.add(i, stmt.substring(0, matcher.end()));
                }
            }
        }

        return statements;
    }

    public static boolean isEmptyStatement(String sqlStmt) {
        Matcher matcher = COMMENT_PATTERN.matcher(sqlStmt);
        int idx = 0;

        while (matcher.find(idx)) {
            if (sqlStmt.substring(idx, matcher.start()).trim().length() > 0) {
                return false;
            }
            idx = matcher.end();
        }
        return true;
    }

    public static SqlStatement prepareDDL(String sql){
        sql = sql.trim();
        String delimiter = "";

        Matcher matcher = PLSQL_END_PATTERN.matcher(sql);
        if (matcher.find()) {
            if (matcher.group(2) != null) {
                sql = sql.substring(0, matcher.start(2));
            }
            sql += "\n";
            delimiter = "/";
        }
        // remove trailing semicolon in case of non pl/sql type objects/statements
        else if (sql.endsWith(";")) {
            sql = sql.substring(0, sql.length()-1);
        }

        StringBuilder sqlWoComments = new StringBuilder(sql);
        while ((matcher = COMMENT_PATTERN.matcher(sqlWoComments.toString())).find()) {
            sqlWoComments.delete(matcher.start(), matcher.end());
        }

        if (delimiter.length() < 1 && sqlWoComments.toString().trim().length() > 0) {
            delimiter = ";";
        }

        return new SqlStatementImpl(sql, delimiter);
    }

    public static String[] addHeaderAndFooter(String[] createSQL, Dialect dialect){
        DDLGenerator.Profile profile;

        if (dialect instanceof DDLEnhancer) {
            DDLEnhancer ddlEnhancer = (DDLEnhancer) dialect;
            profile = ddlEnhancer.getDDLEnhancer().getProfile();
        }
        else {
            LOG.warn("{} was not patched, generator enhancements not working", Dialect.class.getName());
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

    public static class SqlStatementImpl implements SqlStatement {
        private String sql;
        private String delimiter;

        private SqlStatementImpl(String sql, String delimiter) {
            this.sql = sql;
            this.delimiter = delimiter;
        }

        public String getSql() {
            return sql;
        }

        public String getDelimiter() {
            return delimiter;
        }
    }
}
