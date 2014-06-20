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
import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.tool.hbm2ddl.SchemaExport;
import org.hibernate.type.Type;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * @author Georg Kohlweiss 
 */
public class PatchGlue {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(PatchGlue.class);

    private static DDLGenerator.Profile profile;

    private static Method schemaExportPerform;

    public PatchGlue() throws Exception {
        init();
    }

    public static void init() throws Exception {
        init(null);
    }

    public static void init(DDLGenerator.Profile profile) throws Exception {
        if (profile == null) {
            profile = new DDLGenerator.Profile("runtime");
        }
        if (PatchGlue.profile == null) {
            try {
                PatchHibernateMappingClasses.applyPatch();
            } catch (HibernateClassesAlreadyLoadedException e) {
                throw new IllegalStateException("hibernate classes already loaded, cannot patch", e);
            }
        }
        PatchGlue.profile = profile;
    }

    public static void patch4Transfer() {
        patch4Transfer(null);
    }

    public static void patch4Transfer(DDLGenerator.Profile profile) {
        try {
            init(profile);
            PatchHibernateMappingClasses.patchCollectionsAlwaysLazy();
            PatchHibernateMappingClasses.patchIgnoreVersion();
        } catch (Exception e) {
            LOG.error("unable to patch for transfer", e);
        }
    }

    public static void initDialect(Dialect dialect, org.hibernate.cfg.NamingStrategy namingStrategy, Properties cfgProperties) {
        if (dialect instanceof DDLEnhancer) {
            if (profile == null) {
                profile = new DDLGenerator.Profile("runtime");
            }
            DDLEnhancer ddlEnhancer = (DDLEnhancer) dialect;
            if (namingStrategy instanceof DefaultNamingStrategy) {
                profile.setNamingStrategy((DefaultNamingStrategy) namingStrategy);
            }
            if (ddlEnhancer.getDDLEnhancer() == null) {
                ddlEnhancer.initDDLEnhancer(profile, dialect);
            }
        }
        else {
            LOG.warn("dialect does not implement {}", DDLEnhancer.class.getName());
        }
    }

    public static void addClass (PersistentClass clazz) {
        if (profile != null) {
            profile.addPersistenceClass(clazz.getMappedClass());
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

        return getDDLEnhancerFromDialect(dialect).updateCreateTable(dialect, buf.append(dialect.getTableTypeString()), table.getName(), allColumns);
    }

    public static String afterTableSqlDropString(Table table, Dialect dialect, String returnValue) {
        StringBuffer buf = new StringBuffer(returnValue);

        return getDDLEnhancerFromDialect(dialect).updateDropTable(dialect, buf, table.getName());
    }

    public static String afterConstraintSqlCreateString(Table table, Dialect dialect, String returnValue, Constraint constraint) {
        if (returnValue == null) {
            return null;
        }

        StringBuffer buf = new StringBuffer(returnValue);

        return getDDLEnhancerFromDialect(dialect).updateCreateConstraint(dialect, buf, constraint.getName(), table, constraint);
    }

    public static String afterIndexSqlCreateString(Table table, Dialect dialect, String returnValue, String name, Iterator columns) {
        StringBuffer buf = new StringBuffer(returnValue);

        List<Column> columnList = new ArrayList<Column>();
        while ( columns.hasNext() ) {
            Column column = (Column) columns.next();
            columnList.add(column);
        }

        return getDDLEnhancerFromDialect(dialect).updateCreateIndex(dialect, buf, name, table, columnList);
    }

    public static String[] afterSequenceSqlCreateStrings(Dialect dialect, String[] ddl, Type type) {
        String returnValue = join(Arrays.asList(ddl), "\n", new StringValueExtractor<String>() {
            @Override
            public String getValue(String object) {
                return object != null ? object : "";
            }
        });

        return new String[]{getDDLEnhancerFromDialect(dialect).updateCreateSequence(dialect, returnValue, type)};
    }

    public static CreateDDL getDDLEnhancerFromDialect(Dialect dialect) {
        if (dialect instanceof DDLEnhancer) {
            DDLEnhancer ddlEnhancer = (DDLEnhancer) dialect;
            return ddlEnhancer.getDDLEnhancer();
        }
        throw new IllegalArgumentException("dialect must implement " + DDLEnhancer.class.getName());
    }

    public static void addConfigurationInterceptor(ConfigurationInterceptor interceptor) {
        PatchHibernateMappingClasses.CONFIGURATION_INTERCEPTOR_INSTANCES.add(interceptor);
    }

    public static List<ConfigurationInterceptor> getConfigurationInterceptors() {
        return PatchHibernateMappingClasses.CONFIGURATION_INTERCEPTOR_INSTANCES;
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
        for (String singleSql : PatchHibernateMappingClasses.splitSQL(sql)) {
            SqlStatement ddlStmt = PatchHibernateMappingClasses.prepareDDL(singleSql);
            try {
                schemaExportPerform.invoke(schemaExport, new Object[]{script, export, fileOutput, statement, ddlStmt.getSql(), ddlStmt.getDelimiter()});
            } catch (Exception e) {
                LOG.error("cannot call patched api method in SchemaExport", e);
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
            for (String singleSql : PatchHibernateMappingClasses.splitSQL(sqlCommand)) {
                SqlStatement ddlStmt = PatchHibernateMappingClasses.prepareDDL(singleSql);
                wrapArr[0] = ddlStmt.getSql();
                try {
                    schemaExportPerform.invoke(schemaExport, new Object[]{wrapArr, exporters, ddlStmt.getDelimiter()});
                } catch (Exception e) {
                    LOG.error("cannot call patched api method in SchemaExport", e);
                }
            }
        }
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

    public static interface StringValueExtractor<T> {
        String getValue(T object);
    }
}
