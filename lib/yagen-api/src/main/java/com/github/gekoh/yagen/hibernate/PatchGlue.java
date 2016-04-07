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

import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import org.apache.commons.lang.StringUtils;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.type.Type;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss 
 */
public class PatchGlue {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchGlue.class);

    public static final String STATEMENT_SEPARATOR = "\n------- CreateDDL statement separator -------\n";
    public static final Pattern SEPARATOR_PATTERN = Pattern.compile("\r?\n" + STATEMENT_SEPARATOR.trim() + "\r?\n");
    public static final Pattern PLSQL_END_PATTERN = Pattern.compile("[\\s]+end[\\s]*([a-z_]+)?;([\\s]*(\\r?\\n)?/?)$", Pattern.CASE_INSENSITIVE);
    public static final Pattern COMMENT_PATTERN = Pattern.compile(
            "(((--)[^\\n]*((\\r?\\n)|$))+)|" + // single line comment(s)
                    "(/\\*+(.*?)\\*+/)", // block comment
            Pattern.DOTALL);

    private static Object profile;

    public static void setProfile(Object profile) {
        PatchGlue.profile = profile;
    }

    public static Object getProfile() {
        return profile;
    }

    private static Method schemaExportPerform;

    public static Object newDDLEnhancer(Object profile, Dialect dialect, Collection<PersistentClass> persistentClasses) {
        try {
            for (PersistentClass persistentClass : persistentClasses) {
                addClass(profile, persistentClass);
            }
            return ReflectExecutor.i_createDdl.get().newInstance(profile, dialect);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static void initDialect(Dialect dialect, org.hibernate.cfg.NamingStrategy namingStrategy, Properties cfgProperties, ServiceRegistry serviceRegistry, Collection persistentClasses) {
        if (dialect != null && ReflectExecutor.c_enhancer.get().isAssignableFrom(dialect.getClass())) {
            try {
                if (profile == null) {
                    profile = ReflectExecutor.i_profile.get().newInstance("runtime");
                }

                Object clonedProfile = ReflectExecutor.m_clone.get().invoke(profile);
                Object ddlEnhancer = dialect;

                if (namingStrategy instanceof DefaultNamingStrategy) {
                    ReflectExecutor.m_setNamingStrategy.get().invoke(clonedProfile, namingStrategy);
                }
                if (ReflectExecutor.m_getDDLEnhancer.get().invoke(ddlEnhancer) == null) {
                    ReflectExecutor.m_initDDLEnhancer.get().invoke(ddlEnhancer, clonedProfile, dialect, serviceRegistry, persistentClasses);
                }
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        else {
            LOG.warn("{} was not patched, generator enhancements not working", dialect != null ? dialect.getClass().getName() : "Dialect");
        }
    }

    public static void addClass (Object profile, PersistentClass clazz) {
        if (profile != null) {
            try {
                ReflectExecutor.m_addPersistenceClass.get().invoke(profile, clazz.getMappedClass());
            } catch (Exception e) {
                LOG.error("error adding persistence class", e);
            }
        }
    }

    public static String afterTableSqlCreateString(Table table, Dialect dialect, String returnValue) {
        StringBuffer buf = new StringBuffer(returnValue);

        Map<String, Column> allColumns = new LinkedHashMap<String, Column>();
        Iterator<Column> colIt = table.getColumnIterator();
        while (colIt.hasNext()) {
            Column column = colIt.next();
            allColumns.put(column.getName().toLowerCase(), column);
        }

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer == null) {
            return returnValue;
        }

        try {
            return (String) ReflectExecutor.m_updateCreateTable.get().invoke(ddlEnhancer, dialect, buf.append(dialect.getTableTypeString()), table.getName(), allColumns);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String afterTableSqlDropString(Table table, Dialect dialect, String returnValue) {
        StringBuffer buf = new StringBuffer(returnValue);

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer == null) {
            return returnValue;
        }

        try {
            return (String) ReflectExecutor.m_updateDropTable.get().invoke(ddlEnhancer, dialect, buf, table.getName());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String afterConstraintSqlCreateString(Table table, Dialect dialect, String returnValue, Constraint constraint) {
        if (returnValue == null) {
            return null;
        }

        StringBuffer buf = new StringBuffer(returnValue);

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer == null) {
            return returnValue;
        }

        try {
            return (String) ReflectExecutor.m_updateCreateConstraint.get().invoke(ddlEnhancer, dialect, buf, constraint.getName(), table, constraint);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String afterIndexSqlCreateString(Table table, Dialect dialect, String returnValue, String name, Iterator columns) {
        StringBuffer buf = new StringBuffer(returnValue);

        List<Column> columnList = new ArrayList<Column>();
        while ( columns.hasNext() ) {
            Column column = (Column) columns.next();
            columnList.add(column);
        }

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer == null) {
            return returnValue;
        }

        try {
            return (String) ReflectExecutor.m_updateCreateIndex.get().invoke(ddlEnhancer, dialect, buf, name, table, columnList);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String[] afterSequenceSqlCreateStrings(Dialect dialect, String[] ddl, Type type) {
        String returnValue = join(Arrays.asList(ddl), "\n", new StringValueExtractor<String>() {
            public String getValue(String object) {
                return object != null ? object : "";
            }
        });

        Object ddlEnhancer = getDDLEnhancerFromDialect(dialect);
        if (ddlEnhancer == null) {
            return ddl;
        }

        try {
            return new String[]{ (String) ReflectExecutor.m_updateCreateSequence.get().invoke(ddlEnhancer, dialect, returnValue, type) };
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Object getDDLEnhancerFromDialect(Dialect dialect) {
        if (dialect != null && ReflectExecutor.c_enhancer.get().isAssignableFrom(dialect.getClass())) {
            Object ddlEnhancer;
            try {
                ddlEnhancer = ReflectExecutor.m_getDDLEnhancer.get().invoke(dialect);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            if (ddlEnhancer == null) {
                LOG.warn("cannot enhance DDL since dialect was not initialized");
            }
            return ddlEnhancer;
        }
        throw new IllegalArgumentException((dialect != null ? dialect.getClass().getName() : "Dialect") + " was not patched, generator enhancements inoperable");
    }

    public static void addConfigurationInterceptor(ConfigurationInterceptor interceptor) {
        ReflectExecutor.CONFIGURATION_INTERCEPTOR_INSTANCES.add(interceptor);
    }

    public static List<ConfigurationInterceptor> getConfigurationInterceptors() {
        return ReflectExecutor.CONFIGURATION_INTERCEPTOR_INSTANCES;
    }

    public static interface ConfigurationInterceptor {
        void use(Configuration configuration);
    }

    // Hibernate 3
    public static void schemaExportExecute(boolean script, boolean export, Writer fileOutput, Statement statement, String sql, SchemaExport schemaExport)
            throws IOException, SQLException {
        if (schemaExportPerform == null) {
            try {
                schemaExportPerform = SchemaExport.class.getMethod("executeApi", boolean.class, boolean.class, Writer.class, Statement.class, String.class, String.class);
            } catch (NoSuchMethodException e) {
                LOG.error("cannot find api method inserted by patch", e);
            }
        }
        for (String singleSql : splitSQL(sql)) {
            SqlStatement ddlStmt = prepareDDL(singleSql);
            try {
                schemaExportPerform.invoke(schemaExport, new Object[]{script, export, fileOutput, statement, ddlStmt.getSql(), ddlStmt.getDelimiter()});
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof SQLException && !isEmptyStatement(singleSql)) {
                    LOG.warn("failed executing sql: {}", singleSql);
                    LOG.warn("failure: {}", e.getCause().getMessage());
                }
            } catch (Exception e) {
                LOG.error("error calling patched api method in SchemaExport", e);
            }
        }
    }

    // Hibernate 4.3.5
    public static void schemaExportPerform (String[] sqlCommands, List exporters, SchemaExport schemaExport) {
        if (schemaExportPerform == null) {
            try {
                schemaExportPerform = SchemaExport.class.getMethod("performApi", String[].class, List.class, String.class);
            } catch (NoSuchMethodException e) {
                LOG.error("cannot find api method inserted by patch", e);
            }
        }
        String[] wrapArr = new String[1];
        for (String sqlCommand : sqlCommands) {
            for (String singleSql : splitSQL(sqlCommand)) {
                SqlStatement ddlStmt = prepareDDL(singleSql);
                wrapArr[0] = ddlStmt.getSql();
                boolean emptyStatement = isEmptyStatement(singleSql);
                try {
                    List passedExporters = new ArrayList();
                    passedExporters.add(null);
                    for (Object exporter : exporters) {
                        passedExporters.set(0, exporter);
                        boolean databaseExporter = exporter.getClass().getSimpleName().equals("DatabaseExporter");
                        if (!databaseExporter || !emptyStatement) {
                            schemaExportPerform.invoke(schemaExport, new Object[]{wrapArr, passedExporters, databaseExporter ? null : ddlStmt.getDelimiter()});
                        }
                    }
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof SQLException && !emptyStatement) {
                        LOG.warn("failed executing sql: {}", singleSql);
                        LOG.warn("failure: {}", e.getCause().getMessage());
                    }
                } catch (Exception e) {
                    LOG.error("cannot call patched api method in SchemaExport", e);
                }
            }
        }
    }

    public static boolean isScript(String sql) {
        int idx = indexOfSeparator(sql, 0);

        return idx > 0 || indexOfSeparator(sql, Math.min(STATEMENT_SEPARATOR.length(), sql.length())) > 0;
    }

    private static int indexOfSeparator(String sql, int startIdx) {
        Matcher matcher = SEPARATOR_PATTERN.matcher(sql);
        if (matcher.find(startIdx)) {
            return matcher.start();
        }
        return -1;
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

        while (matcher.find()) {
            sqlStmt = sqlStmt.substring(0, matcher.start()) + sqlStmt.substring(matcher.end());
            matcher = COMMENT_PATTERN.matcher(sqlStmt);
        }

        return sqlStmt.trim().length()<1;
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

    public static <T> String join(List<T> list, String separator, StringValueExtractor<T> valueExtractor) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            T object = list.get(i);
            if (i > 0) {
                sb.append(separator);
            }
            sb.append(valueExtractor.getValue(object));
        }
        return sb.toString();
    }


    public static String[] addHeaderAndFooter(String[] createSQL, Dialect dialect){
        Object profile;

        if (dialect != null && ReflectExecutor.c_enhancer.get().isAssignableFrom(dialect.getClass())) {
            try {
                profile = ReflectExecutor.m_getProfile.get().invoke(ReflectExecutor.m_getDDLEnhancer.get().invoke(dialect));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
        else {
            LOG.warn("{} was not patched, generator enhancements not working", dialect != null ? dialect.getClass().getName() : "Dialect");
            return createSQL;
        }

        try {
            return (String[]) ReflectExecutor.m_addDdls.get().invoke(profile, createSQL, dialect);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static interface StringValueExtractor<T> {
        String getValue(T object);
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
