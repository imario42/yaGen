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
package com.github.gekoh.yagen.ddl;

import com.github.gekoh.yagen.api.AuditInfo;
import com.github.gekoh.yagen.api.Auditable;
import com.github.gekoh.yagen.api.CheckConstraint;
import com.github.gekoh.yagen.api.Constants;
import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.api.Deferrable;
import com.github.gekoh.yagen.api.Generated;
import com.github.gekoh.yagen.api.Index;
import com.github.gekoh.yagen.api.IntervalPartitioning;
import com.github.gekoh.yagen.api.NamingStrategy;
import com.github.gekoh.yagen.api.NoForeignKeyConstraint;
import com.github.gekoh.yagen.api.Profile;
import com.github.gekoh.yagen.api.Sequence;
import com.github.gekoh.yagen.api.TemporalEntity;
import com.github.gekoh.yagen.api.UniqueConstraint;
import com.github.gekoh.yagen.hst.CreateEntities;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.hibernate.dialect.Dialect;
import org.hibernate.mapping.Constraint;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.UniqueKey;
import org.hibernate.type.Type;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss
 */
@SuppressWarnings({"unchecked"})
public class CreateDDL {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CreateDDL.class);

    private static final int MAX_LEN_TABLE_NAME = 26;
    private static final int MAX_LEN_OBJECT_NAME = 30;

    public static final String STATIC_FIELD_TABLE_NAME_SHORT = "TABLE_NAME_SHORT";

    private static final String REGEX_COLNAME = "[\"'`]?[a-zA-Z]+[0-9a-zA-Z_]*[\"'`]?";

    private static final Pattern TBL_PATTERN = Pattern.compile(".*"
            + "create table[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)[\\s]*\\("
            + "(.*(,([\\s](primary key[\\s]*\\((" + REGEX_COLNAME +"([\\s]*,[\\s]*" + REGEX_COLNAME + ")*)\\))))"
            + ".*)\\)\\s*(partition\\s+by.*)?");
    private static final int TBL_PATTERN_IDX_TBLNAME = 1;
    private static final int TBL_PATTERN_IDX_TBL_DEF = 2;
    private static final int TBL_PATTERN_IDX_AFTER_COL_DEF = 3;
    private static final int TBL_PATTERN_IDX_PK_START = 4;
    private static final int TBL_PATTERN_IDX_PK_CLAUSE = 5;
    private static final int TBL_PATTERN_IDX_PK_COLLIST = 6;
    private static final int TBL_PATTERN_IDX_PART_CLAUSE = 8;

    private static final Pattern TBL_PATTERN_WO_PK = Pattern.compile(".*"
            + "create table[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)([\\s]*\\()"
            + ".*(\\))\\s*(partition\\s+by.*)?");
    private static final int TBL_PATTERN_WO_PK_IDX_TBLNAME = 1;
    private static final int TBL_PATTERN_WO_PK_IDX_AFTER_COL_DEF = 3;

    private static final Pattern TBL_ALTER_PATTERN = Pattern.compile("alter table[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)[\\s]");
    private static final Pattern IDX_CREATE_PATTERN = Pattern.compile("create( unique)? index[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)[\\s]+on[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)([\\s]*\\()");
    private static final Pattern SEQ_CREATE_PATTERN = Pattern.compile("create sequence[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)");
    private static final Pattern PKG_CREATE_PATTERN = Pattern.compile("create( or replace)?[\\s]+package[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)[\\s]", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final Pattern COL_PATTERN = Pattern.compile("([\\(|\\s]?)(" + REGEX_COLNAME + ")([\\s]((varchar(2)?\\([^\\)]+\\))|(number\\([^\\)]+\\))|(timestamp)|(date)|(clob)|(char\\([^\\)]+\\))|(int((eger)|[0-9]*))|(bigint)|(bit)|(bool(ean)?)|(double)))([\\s]+default[\\s]*([^\\s]*))?(([\\s]+not)?[\\s]+null)?([\\s]+unique)?[^\\(,]*(,|\\))");
    private static final int COL_PATTERN_IDX_COLNAME = 2;
    private static final int COL_PATTERN_IDX_TYPE    = 4;
    private static final int COL_PATTERN_IDX_DEFAULT = 20;
    private static final int COL_PATTERN_IDX_NOTNULL = 22;
    private static final int COL_PATTERN_IDX_NOT     = 23;
    private static final int COL_PATTERN_IDX_UNIQUE  = 24;

    private static final Pattern UNIQUE_PATTERN = Pattern.compile("(,([\\s]*unique[\\s]*\\((" + REGEX_COLNAME + "([\\s]*,[\\s]*" + REGEX_COLNAME + ")*)\\)))");
    private static final Pattern CONSTRAINT_PATTERN = Pattern.compile("constraint[\\s]*([a-zA-Z]+[0-9a-zA-Z_]*)");

    private static final Pattern VIEW_NAME_PATTERN = Pattern.compile("create( or replace)?[\\s]+view[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)[\\s]", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern DROP_TABLE_PATTERN = Pattern.compile("drop table( if exists)?[\\s]+([a-zA-Z]+[0-9a-zA-Z_]*)( if exists)?");

    public static final String LANGUAGE_VIEW_NAME = "I18N_LANGUAGE_V";
    public static final String I18N_LIVE_TABLE_SUFFIX = "_i18n";

    private static final String VERSION_COLUMN_NAME = "version";
    private static final String HIST_TABLE_PK_COLUMN_NAME = "hst_uuid";
    private static final String HIST_OPERATION_COLUMN_NAME = "operation";
    private static final String HIST_INVALID_TIMESTAMP_COLUMN_NAME = "invalidated_at";

    private static final String I18N_COLUMN_COMPOSITE_ID = "composite_id";
    private static final String I18N_COLUMN_IS_PERSISTENT = "is_persistent";

    public static final String STATEMENT_SEPARATOR = "\n------- " + CreateDDL.class.getSimpleName() + " statement separator -------\n";

    private static final List<String> AUDIT_COLUMNS = Arrays.asList(AuditInfo.CREATED_AT, AuditInfo.CREATED_BY, AuditInfo.LAST_MODIFIED_AT, AuditInfo.LAST_MODIFIED_BY);
    private static final Map<String, String> AUDIT_COLUMN_DEFINITION = new HashMap<String, String>();
    static {
        AUDIT_COLUMN_DEFINITION.put(AuditInfo.CREATED_AT, AuditInfo.CREATED_AT + " ${timestampType} not null");
        AUDIT_COLUMN_DEFINITION.put(AuditInfo.CREATED_BY, AuditInfo.CREATED_BY + " ${varcharType} not null");
        AUDIT_COLUMN_DEFINITION.put(AuditInfo.LAST_MODIFIED_AT, AuditInfo.LAST_MODIFIED_AT + " ${timestampType}");
        AUDIT_COLUMN_DEFINITION.put(AuditInfo.LAST_MODIFIED_BY, AuditInfo.LAST_MODIFIED_BY + " ${varcharType}");
    }

    private Map<String, TableConfig> tblNameToConfig = new LinkedHashMap<String, TableConfig>();

    private StringBuffer deferredDdl = new StringBuffer();
    private Set<String> objectNames = new HashSet<String>();
    private Set<String> externalViews = new HashSet<String>();
    private Set<String> views;
    private Set<String> tblColNameHasSingleColIndex = new HashSet<String>();
    private Map<String, List<String>> tblNameToDropObjectsSql = new HashMap<String, List<String>>();

    private DDLGenerator.Profile currentProfile;

    private List<String> dbObjects = new ArrayList<String>();

    private boolean historyInitSet = false;

    public CreateDDL(DDLGenerator.Profile profile, Dialect dialect) {
        init(profile);
        initViewsAndRegisterDDLs(dialect);
    }

    public void init(DDLGenerator.Profile profile) {
        currentProfile = profile;
        boolean selectiveRendering = false;

        for (Class entityClass : profile.getEntityClasses()) {

            Class baseClass = TableConfig.getClassOfTableAnnotation(entityClass);

            if (baseClass == null || baseClass != entityClass) {
                continue;
            }

            String nameLC = getProfile().getNamingStrategy().classToTableName(baseClass.getName()).toLowerCase();

            TableConfig tableConfig = tblNameToConfig.get(nameLC);
            if (tableConfig == null) {
                tblNameToConfig.put(nameLC, tableConfig = new TableConfig(this, baseClass, nameLC));
            }

            if (profile.getOnlyRenderEntities() != null && !profile.getOnlyRenderEntities().matcher(baseClass.getName()).matches()) {
                tableConfig.setTableToBeRendered(false);
            }
            else if (baseClass.isAnnotationPresent(Profile.class) &&
                    (Arrays.asList(((Profile) baseClass.getAnnotation(Profile.class)).value()).contains(profile.getName()))) {
                // at least one Profile annotation contains the name of current profile,
                // so only entities shall be rendered which have an appropriate Profile annotation
                selectiveRendering = true;
            }

            if (baseClass.isAnnotationPresent(Generated.class)) {
                if (((Generated) baseClass.getAnnotation(Generated.class)).isView()) {
                    externalViews.add(nameLC);
                }
                tableConfig.setTableToBeRendered(false);
            }
        }

        for (Class entityClass : profile.getEntityClasses()) {
            Class baseClass = TableConfig.getClassOfTableAnnotation(entityClass);

            if (baseClass == null) {
                continue;
            }

            String nameLC = getProfile().getNamingStrategy().classToTableName(baseClass.getName()).toLowerCase();

            TableConfig tableConfig = tblNameToConfig.get(nameLC);

            if (entityClass == baseClass && selectiveRendering &&
                    (!baseClass.isAnnotationPresent(Profile.class) ||
                    !(Arrays.asList(((Profile)baseClass.getAnnotation(Profile.class)).value()).contains(profile.getName())))) {
                tableConfig.setTableToBeRendered(false);
            }

            tableConfig.scanEntityClass(entityClass, selectiveRendering);
        }
    }

    public TableConfig getConfigForTableName (String tableName) {
        return tblNameToConfig.get(tableName);
    }

    public void addTableConfig (TableConfig tableConfig) {
        tblNameToConfig.put(tableConfig.getTableName(), tableConfig);
    }

    public void checkTableName(Dialect dialect, String name) {
        String nameLC = name.toLowerCase();

        if (objectNames.contains(name)) {
            throw new IllegalArgumentException("object name "+name+" already defined for another object");
        }

        if (isOracle(dialect)) {
            int maxlen = CreateDDL.MAX_LEN_TABLE_NAME;
            TableConfig config = tblNameToConfig.get(nameLC);

            if (views.contains(nameLC) || nameLC.endsWith("_mut") || nameLC.endsWith("_hst") || nameLC.endsWith("_pro") || nameLC.endsWith(I18N_LIVE_TABLE_SUFFIX) || (config != null && config.getI18nBaseEntityFkCol() != null)) {
                maxlen = CreateDDL.MAX_LEN_OBJECT_NAME;
            }

            if (name.length() > maxlen) {
                throw new IllegalArgumentException("table name '" + name + "' too long, counts " + name.length() + " chars. " +
                        "please specify a name with less or equal to " + maxlen + " chars.");
            }
        }

        objectNames.add(name);
    }
    
    public void checkObjectName(Dialect dialect, String name) {
        name = name.toLowerCase();

        if (objectNames.contains(name)) {
            throw new IllegalArgumentException("object name "+name+" already defined for another object");
        }

        if (isOracle(dialect)) {
            if (name.length() > CreateDDL.MAX_LEN_OBJECT_NAME) {
                throw new IllegalArgumentException("object name '" + name + "' too long, counts " + name.length() + " chars. " +
                        "please specify a name with less or equal to " + CreateDDL.MAX_LEN_OBJECT_NAME + " chars.");
            }
        }

        objectNames.add(name);
    }

    private boolean renderTable (String tableNameLC) {
        TableConfig tableConfig = tblNameToConfig.get(tableNameLC);
        return tableConfig == null || tableConfig.isTableToBeRendered();
    }

    private static String getIfExistsDropStatement(Dialect dialect, String sql, String name) {
        Matcher matcher = DROP_TABLE_PATTERN.matcher(sql);
        if (matcher.find()) {
            StringBuilder res = new StringBuilder();
            res.append(sql.substring(0, matcher.start(1) >= 0 ? matcher.start(1) : matcher.start(2)))
                    .append(getNameAndIfExistsWhenSupported(dialect, name == null ? matcher.group(2) : name))
                    .append(sql.substring(matcher.end(3) >= 0 ? matcher.end(3) : matcher.end(2)));
            sql = res.toString();
        }
        return sql;
    }

    public String updateDropTable(Dialect dialect, StringBuffer buf, String name) {
        StringBuilder sql = new StringBuilder();
        String nameLC = name.toLowerCase();

        if (!renderTable(nameLC)) {
            return "-- skipped dropping table '" + name + "' as the mapped entity was not chosen to be processed";
        }

        if (tblNameToDropObjectsSql.containsKey(nameLC)) {
            for (String dropSql : tblNameToDropObjectsSql.get(nameLC)) {
                sql.append(dropSql).append(STATEMENT_SEPARATOR);
            }
        }

        if (views.contains(nameLC)) {
            sql.append("drop view ");
            sql.append(getNameAndIfExistsWhenSupported(dialect, name));
        }
        else {
            sql.append(getIfExistsDropStatement(dialect, buf.toString(), null));
        }

        TableConfig tableConfig = tblNameToConfig.get(nameLC);
        if (tableConfig != null) {
            for (Sequence sequence : tableConfig.getSequences()) {
                sql.append(STATEMENT_SEPARATOR).append("drop sequence ");
                sql.append(getNameAndIfExistsWhenSupported(dialect, getProfile().getNamingStrategy().sequenceName(sequence.name())));
            }
        }

        return sql.toString();
    }

    public String updateCreateTable(Dialect dialect, StringBuffer buf, String tableName, Map columnMap) {
        LOG.info("modify DDL created by hibernate for table {}", tableName);
        boolean isOracle = isOracle(dialect);

        String nameLC = tableName.toLowerCase();
        String entityClassName = getEntityClassName(nameLC);

        Set<String> columns = new LinkedHashSet<String>(columnMap.keySet());

        if (!renderTable(nameLC)) {
            return "-- skipped creation statement for table '" + tableName + "' as the mapped entity was not chosen to be processed";
        }

        TableConfig tableConfig = tblNameToConfig.get(nameLC);

        checkTableName(dialect, tableName);

        if (externalViews.contains(nameLC)) {
            return "-- skipped creation statement for table '" + tableName + "' since there will be a view in place";
        }

        String sqlCreate = buf.toString();
        buf = new StringBuffer();
        Map<String, String> comments = getProfile().getComments() != null ? getProfile().getComments().get(nameLC) : null;
        String liveTableName = nameLC;
        Set<String> columnNames = columns;
        List<String> pkCols = getPkColumnNamesFrom(sqlCreate);

        if (comments != null && comments.size() < 1) {
            comments = null;
        }

        Auditable auditable = tableConfig.getTableAnnotationOfType(Auditable.class);
        if (auditable != null && auditable.createNonExistingColumns()) {
            sqlCreate = addAuditColumns(dialect, sqlCreate, columns);
        }

        sqlCreate = processCascadeNullable(dialect, buf, nameLC, sqlCreate, tableConfig.getColumnNamesIsCascadeNullable());

        String i18nFK = tableConfig.getI18nBaseEntityFkCol();

        if (i18nFK != null) {
            String baseEntityTableName = tableConfig.getI18nBaseEntityTblName();
            String i18nTblName = getProfile().getNamingStrategy().tableName(getI18NDetailTableName(nameLC));
            liveTableName = i18nTblName;
            columnNames = getI18NEntityColumns(columns);

            sqlCreate = getI18NDetailTableCreateString(dialect, sqlCreate, i18nTblName, i18nFK);

            addDropStatement(nameLC, getIfExistsDropStatement(dialect, "drop table " + i18nTblName, null));

            pkCols = getPkColumnNamesFrom(sqlCreate);

            if (dialect.supportsCommentOn()) {
                buf.append(STATEMENT_SEPARATOR).append("comment on table ").append(i18nTblName).append(" is 'Base table for I18N descriptions, for comments see view ")
                        .append(nameLC).append("'\n");
            }

            deferredDdl.append(STATEMENT_SEPARATOR).append(getI18NDetailViewCreateString(dialect, nameLC, baseEntityTableName, i18nTblName, i18nFK, columnNames));
            if (comments != null && isOracle) {
                addComments(deferredDdl, nameLC, comments);
                comments = null;
            }
            writeI18NDetailViewTriggerCreateString(dialect, deferredDdl, nameLC, i18nTblName, i18nFK, columnNames);
        }

        if (comments != null && isOracle) {
            addComments(buf, nameLC, comments);
        }

        addAuditTrigger(dialect, buf, liveTableName, columns);

        IntervalPartitioning partitioning = tableConfig.getTableAnnotationOfType(IntervalPartitioning.class);
        TemporalEntity temporalEntity = getProfile().isNoHistory() ? null : tableConfig.getTableAnnotationOfType(TemporalEntity.class);

        if (temporalEntity != null) {
            try {
                String histTableName;

                if (entityClassName != null) {
                    String hstEntityClassName = entityClassName + CreateEntities.HISTORY_ENTITY_SUFFIX;
                    histTableName = getProfile().getNamingStrategy().classToTableName(hstEntityClassName);

                    // this will throw an exception when the history entity class is not found
                    TableConfig hstConfig = new TableConfig(this, Class.forName(hstEntityClassName), histTableName);

                    hstConfig.setTableToBeRendered(false);

                    tblNameToConfig.put(hstConfig.getTableName(), hstConfig);
                }
                else {
//                  there is no entity for the live table, e.g. for ManyToMany relations
                    histTableName = getProfile().getNamingStrategy().tableName(temporalEntity.historyTableName());
                }

                Matcher tblMatcher = TBL_PATTERN_WO_PK.matcher(sqlCreate);

                if (!tblMatcher.find()) {
                    throw new IllegalStateException("cannot find create table statement in sql: " + sqlCreate);
                }

                String histColNameLC = temporalEntity.historyTimestampColumnName().toLowerCase();
                List<String> historyRelevantCols = getHistoryRelevantColumns(columnNames, temporalEntity.ignoreChangeOfColumns(), histColNameLC);

                if (StringUtils.isEmpty(histTableName)) {
                    histTableName = tblMatcher.group(TBL_PATTERN_WO_PK_IDX_TBLNAME) + Constants._HST;
                }

                buf.append(STATEMENT_SEPARATOR).append("-- adding history table due to annotation ")
                        .append(temporalEntity.annotationType().getName())
                        .append(" on entity of table ")
                        .append(tableName)
                        .append("\n")
                        .append(getHistTableSqlCreateString(dialect, sqlCreate, histTableName, histColNameLC, columnNames, pkCols, partitioning));

                if (isOracle) {
                    buf.append(STATEMENT_SEPARATOR);
                    buf.append("-- creating trigger for inserting history rows from table ").append(tableName).append("\n")
                            .append(getOracleHistTriggerSql(dialect, liveTableName, histTableName, histColNameLC, columnNames, pkCols, historyRelevantCols)).append("\n/");
                }
                else if (isPostgreSql(dialect)) {
                    buf.append(STATEMENT_SEPARATOR)
                            .append(getPostgreSQLHistTriggerFunction(dialect, liveTableName, histTableName, histColNameLC, columnNames, pkCols, historyRelevantCols)).append("\n/");

                    buf.append(STATEMENT_SEPARATOR)
                            .append("create trigger ").append(liveTableName).append("_htU\n")
                            .append("after update on ").append(liveTableName).append("\n")
                            .append("for each row\n")
                            .append("when (");
                    for (String historyRelevantCol : historyRelevantCols) {
                        buf.append("new.").append(historyRelevantCol).append(" is distinct from old.").append(historyRelevantCol).append(" or\n");
                    }
                    buf.delete(buf.length()-4, buf.length());
                    buf.append(")\nexecute procedure ").append(liveTableName).append("_htr_function()");

                    buf.append(STATEMENT_SEPARATOR)
                            .append("create trigger ").append(liveTableName).append("_htr\n")
                            .append("after insert or delete on ").append(liveTableName).append("\n")
                            .append("for each row\n")
                            .append("execute procedure ").append(liveTableName).append("_htr_function()");
                }
                else {
                    buf.append(getHsqlDBHistTriggerSql(dialect, liveTableName, histTableName, histColNameLC, columnNames, pkCols, historyRelevantCols));
                }

                if (!historyInitSet) {
                    getProfile().addHeaderDdl(new DDLGenerator.AddTemplateDDLEntry(CreateDDL.class.getResource("/com/github/gekoh/yagen/ddl/InitHistory.ddl.sql")));
                    historyInitSet = true;
                }
            } catch (ClassNotFoundException e) {
                LOG.info("not generating history table of live table {} since corresponding history entity class not found in classpath", nameLC);
            }
        }

        for (Sequence sequence : tableConfig.getSequences()) {
            String seqName = getProfile().getNamingStrategy().sequenceName(sequence.name());

            buf.append(STATEMENT_SEPARATOR);

            if (objectNames.contains(seqName.toLowerCase())) {
                buf.append("-- WARNING: duplicate definition of sequence or name already defined for another object!\n--");
            }
            else {
                checkObjectName(dialect, seqName.toLowerCase());
            }

            buf.append("create sequence ").append(seqName)
                    .append(" start with ").append(sequence.startWith())
                    .append(" increment by ").append(sequence.incrementBy());
            if (sequence.cache() > 1) {
                buf.append(" cache ").append(sequence.cache());
            }
        }

        if (supportsPartitioning(dialect) && partitioning != null) {
            sqlCreate = addPartitioning(buf, partitioning, nameLC, sqlCreate, columns, pkCols);
        }

        sqlCreate = addConstraintsAndNames(dialect, buf, sqlCreate, nameLC, tableConfig.getColumnNameToEnumCheckConstraints());
        sqlCreate = addDefaultValues(sqlCreate, nameLC);
        addIndexes(buf, dialect, tableConfig);

        if (buf.length() == 0) {
            return sqlCreate;
        }

        getProfile().duplex(ObjectType.TABLE, tableName, sqlCreate);

        buf.insert(0, sqlCreate);
        buf.insert(0, STATEMENT_SEPARATOR);

        return buf.toString();
    }

    private void addIndexes(StringBuffer buf, Dialect dialect, TableConfig tableConfig) {
        com.github.gekoh.yagen.api.Table annotation = tableConfig.getTableAnnotationOfType(com.github.gekoh.yagen.api.Table.class);
        if (annotation != null) {
            for (UniqueConstraint uniqueConstraint : annotation.uniqueConstraints()) {
                // only custom declarations of unique keys need to be created with separate unique index DDL
                // when specifying column names there will be an create table inline unique constraint
                if (StringUtils.isEmpty(uniqueConstraint.declaration())) {
                    continue;
                }
                String constraintName = getProfile().getNamingStrategy().constraintName(uniqueConstraint);
                if (StringUtils.isEmpty(constraintName)) {
                    throw new IllegalArgumentException("please specify an unique constraint name in annotation UniqueConstraint for table " + tableConfig.getTableName());
                }
                checkObjectName(dialect, constraintName);
                StringBuilder objDdl = new StringBuilder();
                objDdl.append("create unique index ").append(constraintName);
                objDdl.append(" on ").append(tableConfig.getTableName()).append(" (").append(uniqueConstraint.declaration()).append(")");

                if (uniqueConstraint.usingLocalIndex() && supportsPartitioning(dialect)) {
                    objDdl.append(" local");
                }

                getProfile().duplex(ObjectType.INDEX, constraintName, objDdl.toString());

                buf.append(STATEMENT_SEPARATOR).append(objDdl.toString());
            }
            for (Index index : annotation.indexes()) {
                if (StringUtils.isEmpty(index.declaration())) {
                    continue;
                }
                String indexName = getProfile().getNamingStrategy().indexName(index);
                if (StringUtils.isEmpty(indexName)) {
                    throw new IllegalArgumentException("please specify an index name in annotation Index for table " + tableConfig.getTableName());
                }
                checkObjectName(dialect, indexName);
                StringBuilder objDdl = new StringBuilder();
                objDdl.append("create index ").append(indexName);
                objDdl.append(" on ").append(tableConfig.getTableName()).append(" (").append(index.declaration()).append(")");

                if (index.usingLocalIndex() && supportsPartitioning(dialect)) {
                    objDdl.append(" local");
                }

                getProfile().duplex(ObjectType.INDEX, indexName, objDdl.toString());
                
                buf.append(STATEMENT_SEPARATOR).append(objDdl.toString());
            }
        }
    }

    public String updateCreateConstraint(Dialect dialect, StringBuffer buf, String name, Table table, Constraint constraint) {
        NamingStrategy namingStrategy = getProfile().getNamingStrategy();
        String newName = namingStrategy.constraintName(constraint, getEntityClassName(namingStrategy.tableName(table.getName())));

        if (!name.equals(newName)) {
            String sqlCreate = buf.toString();
            Matcher matcher = CONSTRAINT_PATTERN.matcher(sqlCreate);
            if (matcher.find()) {
                buf = new StringBuffer();
                buf.append(sqlCreate.substring(0, matcher.start(1)));
                buf.append(newName);
                buf.append(sqlCreate.substring(matcher.end(1)));
            }
            name = newName;
        }

        String tableNameLC = getProfile().getNamingStrategy().tableName(table.getName()).toLowerCase();

        if (!renderTable(tableNameLC) || externalViews.contains(tableNameLC)) {
            return "-- skipped creation of constraint '" + name + "' for table '" + table.getName() + "' as the mapped entity was not chosen to be processed or is a view";
        }

        TableConfig tableConfig = tblNameToConfig.get(tableNameLC);

        String refTblNameLC = null;
        if (constraint instanceof ForeignKey) {
            if (tableConfig.getColumnNamesIsNoFK().contains(constraint.getColumn(0).getName().toLowerCase())) {
                return "-- skipped creation of foreign key constraint '" + name + "' for table '" + table.getName() + "' according to annotation of type " + NoForeignKeyConstraint.class.getSimpleName();
            }
            refTblNameLC = getProfile().getNamingStrategy().tableName(((ForeignKey) constraint).getReferencedTable().getName()).toLowerCase();
        }

        checkObjectName(dialect, name);
        String i18nFK = tableConfig.getI18nBaseEntityFkCol();

        if (i18nFK != null) {
            StringBuilder sql = new StringBuilder();
            tableNameLC = getI18NDetailTableName(tableNameLC);
            Matcher matcher = TBL_ALTER_PATTERN.matcher(buf.toString());
            if (matcher.find()) {
                sql.append(buf.substring(0, matcher.start(1))).append(tableNameLC).append(buf.substring(matcher.end(1)));
            }
            buf = new StringBuffer(sql.toString());
        }

        if (constraint instanceof ForeignKey) {
            StringBuilder colList = new StringBuilder();
            org.hibernate.mapping.Column singleColumn = null;

            TableConfig refTableConfig = tblNameToConfig.get(refTblNameLC);
            IntervalPartitioning refTblPart = refTableConfig != null ? refTableConfig.getTableAnnotationOfType(IntervalPartitioning.class) : null;

            for (org.hibernate.mapping.Column column : (Iterable<? extends org.hibernate.mapping.Column>) constraint.getColumns()) {
                if (colList.length() > 0) {
                    colList.append(", ");
                }
                colList.append(column.getName().toLowerCase());
                singleColumn = singleColumn == null ? column : null;
            }

            if (externalViews.contains(refTblNameLC)) {
                buf = new StringBuffer("-- skipped creation of constraint '" + name + "' on table '" + tableNameLC + "' since a view will be referenced");
            }
            else if (refTblPart != null && refTblPart.useLocalPK() && supportsPartitioning(dialect)) {
                buf = new StringBuffer();
                buf.append("-- skipped creation of foreign key constraint '").append(name)
                        .append("' on table '").append(tableNameLC).append("' to table '").append(refTblNameLC)
                        .append("' as the partitioned target table has a local PK (see @IntervalPartitioning on ")
                        .append(((ForeignKey) constraint).getReferencedEntityName()).append(")");
            }
            else {
                if (singleColumn != null) {
                    if (tableConfig.getColumnNamesIsCascadeNullable().contains(singleColumn.getName().toLowerCase())) {
                        buf.append(" on delete set null");
                    }
                    else if (tableConfig.getColumnNamesIsCascadeDelete().contains(singleColumn.getName().toLowerCase()) &&
                            buf.indexOf("on delete") < 0) {
                        buf.append(" on delete cascade");
                    }
                }

                Map<String, Deferrable> col2Deferrable = tableConfig.getColumnNameToDeferrable();
                Deferrable deferrable;
                if (supportsDeferrable(dialect) && col2Deferrable != null && (deferrable = col2Deferrable.get(colList.toString())) != null) {
                    buf.append(" deferrable");
                    if (deferrable.initiallyDeferred()) {
                        buf.append(" initially deferred");
                    }
                }

                if (getProfile().isDisableFKs()) {
                    buf.insert(0, "-- creating FK constraint initially disabled since we do not need it for profile '" + getProfile() + "'\n");
                    buf.append(" disable");
                }
            }

            getProfile().duplex(ObjectType.CONSTRAINT, name, buf.toString());

            if (constraint.getColumnSpan() == 1 && hasIndex(table, tableNameLC, singleColumn)) {
                LOG.debug("not creating foreign key index as there is already an index on table " + tableNameLC + " and column " + colList.toString());
            }
            else {
                String fkIndexName = getProfile().getNamingStrategy().indexName(getEntityClassName(tableNameLC), tableNameLC, DefaultNamingStrategy.concatColumnNames(colList.toString()));
                StringBuilder objDdl = new StringBuilder();
                objDdl.append("create index ")
                        .append(fkIndexName)
                        .append(" on ").append(tableNameLC).append(" (").append(colList.toString()).append(")");

                if (constraint.getColumnSpan() == 1) {
                    tblColNameHasSingleColIndex.add(tableNameLC + "." + colList.toString());
                }

                buf.append(STATEMENT_SEPARATOR).append(objDdl);

                getProfile().duplex(ObjectType.INDEX, fkIndexName, objDdl.toString());
            }
        }

        return buf.toString();
    }

    public String updateCreateIndex(Dialect dialect, StringBuffer buf, String name, Table table, List<org.hibernate.mapping.Column> columns) {
        String newName = getProfile().getNamingStrategy().indexName(name);

        if (!name.equals(newName)) {
            Matcher matcher = IDX_CREATE_PATTERN.matcher(buf.toString());
            if (matcher.find()) {
                StringBuilder builder = new StringBuilder();
                builder.append(buf.substring(0, matcher.start(2)));
                builder.append(newName);
                builder.append(buf.substring(matcher.end(2)));
                buf = new StringBuffer(builder.toString());
            }
            name = newName;
        }

        String tableNameLC = getProfile().getNamingStrategy().tableName(table.getName()).toLowerCase();
        if (!renderTable(tableNameLC)) {
            return "-- skipped creation of index '" + name + "' for table '" + tableNameLC + "' as the mapped entity was not chosen to be processed";
        }

        if (externalViews.contains(tableNameLC)) {
            return "-- skipped creation of index '" + name + "' on table '" + tableNameLC + "' since there is a view in place";
        }
        TableConfig tableConfig = tblNameToConfig.get(tableNameLC);

        checkObjectName(dialect, name);

        IntervalPartitioning partitioning = tableConfig.getTableAnnotationOfType(IntervalPartitioning.class);
        if (partitioning != null && supportsPartitioning(dialect)) {
            Matcher matcher = IDX_CREATE_PATTERN.matcher(buf.toString());
            // find create index and define local not for unique indexes
            if (matcher.find() && matcher.group(1) == null) {
                buf.append(" local");
            }
        }

        String i18nFK = tableConfig.getI18nBaseEntityFkCol();

        if (i18nFK != null) {
            StringBuilder sql = new StringBuilder();
            String i18nTblName = getI18NDetailTableName(tableNameLC);

            if (columns.size() == 1) {
                if (hasIndex(table, i18nTblName, columns.get(0))) {
                    return "-- table " + i18nTblName + " already has an index on column " + columns.get(0).getName();
                }
                tblColNameHasSingleColIndex.add(i18nTblName + "." + columns.get(0).getName().toLowerCase());
            }

            Matcher matcher = IDX_CREATE_PATTERN.matcher(buf.toString());
            if (matcher.find()) {
                sql.append(buf.substring(0, matcher.start(3))).append(i18nTblName).append(buf.substring(matcher.end(3)));
            }

            getProfile().duplex(ObjectType.INDEX, name, sql.toString());
            
            return sql.toString();
        }

        if (columns.size() == 1) {
            if (hasIndex(table, tableNameLC, columns.get(0))) {
                return "-- table " + table.getName() + " already has an index on column " + columns.get(0).getName();
            }

            tblColNameHasSingleColIndex.add(tableNameLC + "." + columns.get(0).getName().toLowerCase());
        }

        getProfile().duplex(ObjectType.INDEX, name, buf.toString());

        return buf.toString();
    }

    public String updateCreateSequence(Dialect dialect, String sqlCreate, Type type) {
        Matcher matcher = SEQ_CREATE_PATTERN.matcher(sqlCreate);

        if (matcher.find()) {
            StringBuilder sql = new StringBuilder();
            sql.append(sqlCreate.substring(0, matcher.start(1)));
            sql.append(getProfile().getNamingStrategy().sequenceName(matcher.group(1)));
            sql.append(sqlCreate.substring(matcher.end(1)));
            sqlCreate = sql.toString();
        }

        return sqlCreate;
    }

    private String processCascadeNullable(Dialect dialect, StringBuffer buf, String tableName, String sqlCreate, Set<String> columns) {
        if (columns == null || columns.size() < 1) {
            return sqlCreate;
        }

        Matcher matcher = COL_PATTERN.matcher(sqlCreate);
        int idx = 0;

        StringBuilder sb = new StringBuilder();

        while (matcher.find(idx)) {
            String colName = TableConfig.getIdentifierForReference(matcher.group(COL_PATTERN_IDX_COLNAME));

            if (columns.contains(colName) && matcher.group(COL_PATTERN_IDX_NOT) != null) {
                sb.append(sqlCreate.substring(idx, matcher.start(COL_PATTERN_IDX_NOTNULL)));
                idx = matcher.end(COL_PATTERN_IDX_NOTNULL);
                createCascadeNullableTrigger(dialect, deferredDdl, tableName, colName);
            }
            else {
                sb.append(sqlCreate.substring(idx, matcher.end()));
                idx = matcher.end();
            }
        }

        sb.append(sqlCreate.substring(idx));

        return sb.toString();
    }

    private void createCascadeNullableTrigger(Dialect dialect, StringBuffer buf, String tableName, String colName) {
        if (isOracle(dialect)) {
            createCascadeNullableTrigger(dialect, buf, tableName, colName, "CascadeNullableTrigger.vm.pl.sql", null);
        }
        else if (isPostgreSql(dialect)) {
            String triggerName = createCascadeNullableTrigger(dialect, buf, tableName, colName, "CascadeNullableTrigger.vm.pl.sql", null);

            buf.append(STATEMENT_SEPARATOR)
                    .append("create trigger ").append(triggerName).append("_trg\n")
                    .append("before insert or update on ").append(tableName).append("\n")
                    .append("for each row\n")
                    .append("execute procedure ").append(triggerName).append("()");
        }
        else if (isHsqlDB(dialect)) {
            createCascadeNullableTrigger(dialect, buf, tableName, colName, "hsqldb/CascadeNullableTrigger.vm.pl.sql", "I");
            createCascadeNullableTrigger(dialect, buf, tableName, colName, "hsqldb/CascadeNullableTrigger.vm.pl.sql", "U");
        }
    }

    private String createCascadeNullableTrigger(Dialect dialect, StringBuffer buf, String tableName, String colName, String template, String operation) {
        String triggerName = getProfile().getNamingStrategy().triggerName(getEntityClassName(tableName), tableName, colName, operation == null ? Constants._NNTR : "_NNT" + operation);

        VelocityContext context = new VelocityContext();
        context.put("dialect", dialect);
        context.put("triggerName", triggerName);
        context.put("operation", operation);
        context.put("tableName", tableName);
        context.put("fkColumnName", colName);

        setNewOldVar(dialect, context);

        StringWriter wr = new StringWriter();
        mergeTemplateFromResource(template, wr, context);

        getProfile().duplex(ObjectType.TRIGGER, triggerName, wr.toString());

        buf.append(STATEMENT_SEPARATOR).append(wr.toString()).append("\n/");

        return triggerName;
    }

    private String addDefaultValues(String sqlCreate, String nameLC) {
        TableConfig tableConfig = tblNameToConfig.get(nameLC);

        if (tableConfig == null) {
            return sqlCreate;
        }

        StringBuilder b = new StringBuilder();
        Matcher matcher = COL_PATTERN.matcher(sqlCreate);
        int idx = 0;

        while (matcher.find(idx)) {
            String colName = TableConfig.getIdentifierForReference(matcher.group(COL_PATTERN_IDX_COLNAME));

            if (matcher.group(COL_PATTERN_IDX_DEFAULT) == null) {
                String defaultExpr = tableConfig.getColNameToDefault().get(colName);

                if (defaultExpr != null) {
                    b.append(sqlCreate.substring(idx, matcher.end(COL_PATTERN_IDX_TYPE)));
                    b.append(" default ").append(defaultExpr);
                    idx = matcher.end(COL_PATTERN_IDX_TYPE);
                }
            }

            b.append(sqlCreate.substring(idx, matcher.end()));
            idx = matcher.end();
        }

        b.append(sqlCreate.substring(idx));

        return b.toString();
    }

    private static String addAuditColumns(Dialect dialect, String sqlCreate, Set<String> columns) {
        Matcher matcher = TBL_PATTERN.matcher(sqlCreate);

        if (matcher.find()) {
            StringBuilder sb = new StringBuilder(sqlCreate.substring(0, matcher.start(TBL_PATTERN_IDX_AFTER_COL_DEF)));
            for (String auditColumn : AUDIT_COLUMNS) {
                if (!columns.contains(auditColumn)) {
                    sb.append(", ").append(formatColumn(dialect, AUDIT_COLUMN_DEFINITION.get(auditColumn), Constants.USER_NAME_LEN, null, null));
                    columns.add(auditColumn);
                }
            }
            sb.append(sqlCreate.substring(matcher.start(TBL_PATTERN_IDX_AFTER_COL_DEF)));
            sqlCreate = sb.toString();
        }

        return sqlCreate;
    }

    private static String formatColumn(Dialect dialect, String colTemplate, Integer length, Integer precision, Integer scale) {
        int intLen   = length != null ? length : 0;
        int intPrec  = precision != null ? precision : 0;
        int intScale = scale != null ? scale : 0;

        VelocityContext context = new VelocityContext();
        context.put("timestampType", dialect.getTypeName(Types.TIMESTAMP, intLen, intPrec, intScale));
        context.put("varcharType", dialect.getTypeName(Types.VARCHAR, intLen, intPrec, intScale));

        StringWriter wr = new StringWriter();
        Velocity.evaluate(context, wr, CreateDDL.class.getName() + "#formatColumn", colTemplate);
        return wr.toString();
    }

    private void addAuditTrigger(Dialect dialect, StringBuffer buf, String nameLC, Set<String> columns) {
        TableConfig tableConfig = tblNameToConfig.get(nameLC);

        String templateName = "AuditTrigger";

        if (!columns.containsAll(AUDIT_COLUMNS)) {
            if (tableConfig != null && tableConfig.getTableAnnotationOfType(Auditable.class) != null && columns.contains(AuditInfo.LAST_MODIFIED_AT)) {
                templateName += "SingleTimestamp";
            }
            else {
                return;
            }
        }

        if (isPostgreSql(dialect)) {
            writePostgreSqlAuditTrigger(dialect, buf, nameLC);
            return;
        }

        StringWriter wr = new StringWriter();

        VelocityContext context = new VelocityContext();
        context.put("liveTableName", nameLC);
        context.put("created_at", AuditInfo.CREATED_AT);
        context.put("created_by", AuditInfo.CREATED_BY);
        context.put("last_modified_at", AuditInfo.LAST_MODIFIED_AT);
        context.put("last_modified_by", AuditInfo.LAST_MODIFIED_BY);

        if (isOracle(dialect)) {
            writeOracleAuditTrigger(dialect, buf, context, nameLC, templateName + ".vm.pl.sql");
        }
        else {
            try {
                templateName += "SingleOperation.vm.pl.sql";
                wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, templateName, context, nameLC, "_at", "I"); wr.write("\n/\n");
                wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, templateName, context, nameLC, "_at", "U"); wr.write("\n/\n");

                buf.append(wr.toString());
            } catch (IOException e) {
                LOG.error("error writing audit triggers", e);
            }
        }
    }

    private void writePostgreSqlAuditTrigger(Dialect dialect, StringBuffer buf, String tableNameLC) {
        String triggerName = getProfile().getNamingStrategy().triggerName(getEntityClassName(tableNameLC), tableNameLC, null, Constants._ATR);

        if (triggerName.length() > CreateDDL.MAX_LEN_OBJECT_NAME) {
            triggerName = getShortName(tableNameLC) + Constants._ATR;
        }

        checkObjectName(dialect, triggerName);

        buf.append(STATEMENT_SEPARATOR)
                .append("create trigger ").append(triggerName).append("\n")
                .append("before insert or update on ").append(tableNameLC).append("\n")
                .append("for each row\n")
                .append("execute procedure audit_trigger_function()");
    }

    private void writeOracleAuditTrigger(Dialect dialect, StringBuffer buf, VelocityContext context, String tableNameLC, String templateName) {
        String triggerName = getProfile().getNamingStrategy().triggerName(getEntityClassName(tableNameLC), tableNameLC, null, Constants._ATR);

        if (triggerName.length() > CreateDDL.MAX_LEN_OBJECT_NAME) {
            triggerName = getShortName(tableNameLC) + Constants._ATR;
        }

        checkObjectName(dialect, triggerName);

        context.put("triggerName", triggerName);

        StringWriter wr = new StringWriter();
        mergeTemplateFromResource(templateName, wr, context);

        buf.append(STATEMENT_SEPARATOR).append("-- creating audit trigger\n");
        buf.append(wr.toString());
        buf.append("\n/");

        getProfile().duplex(ObjectType.TRIGGER, triggerName, wr.toString());
    }

    private String getEntityClassName(String tableName) {
        tableName = tableName.toLowerCase();
        String entityTableName = getLanguageDetailTableNameFromLiveTableName(tableName);
        if (entityTableName == null) {
            entityTableName = tableName;
        }

        TableConfig tableConfig = tblNameToConfig.get(entityTableName);
        if (tableConfig != null && tableConfig.getEntityBaseClass() != null) {
            return tableConfig.getEntityBaseClass().getName();
        }
        return null;
    }

    private static List<String> getPkColumnNamesFrom(String sqlCreate) {
        Matcher tblMatcher = TBL_PATTERN.matcher(sqlCreate);

        if (!tblMatcher.find()) {
            return null;
        }

        String pkColList = tblMatcher.group(TBL_PATTERN_IDX_PK_COLLIST);
        if (pkColList == null) {
            throw new IllegalStateException("unable to find primary key in sql: " + sqlCreate);
        }

        return Arrays.asList(pkColList.toLowerCase().split(",[ ]?"));
    }

    private static List<String> getHistoryRelevantColumns(Set<String> columnNames, String[] ignoreColumns, String hstColName) {
        List<String> cols = new ArrayList<String>(columnNames);

        cols.removeAll(AUDIT_COLUMNS);
        cols.remove(VERSION_COLUMN_NAME);
        cols.remove(hstColName);

        for (String ignoreColumn : ignoreColumns) {
            cols.remove(ignoreColumn.toLowerCase());
        }

        return cols;
    }

    private String getHistTableShortNameFromLiveTableName(String tableName) {
        return getHistTableShortNameFromLiveTableShortName(getShortName(tableName));
    }

    public static String getHistTableShortNameFromLiveTableShortName(String tableShortName) {
        return tableShortName + "H";
    }

    private boolean hasIndex(Table table, String tableNameLC, org.hibernate.mapping.Column column) {
        String columnName = column.getName().toLowerCase();
        if (tblColNameHasSingleColIndex.contains(tableNameLC+"."+columnName)) {
            return true;
        }

        TableConfig tableConfig = tblNameToConfig.get(tableNameLC);
        List<String> pkCols = tableConfig != null ? tableConfig.getPkColnames() : null;

        if (pkCols != null && pkCols.size() == 1 && pkCols.contains(columnName)) {
            return true;
        }

        PrimaryKey pk = table.getPrimaryKey();

        if (pk != null && pk.getColumnSpan() == 1 && pk.getColumns().get(0).equals(column))
        {
            return true;
        }

        Iterator<UniqueKey> uniqueKeyIterator = table.getUniqueKeyIterator();
        while (uniqueKeyIterator.hasNext()) {
            UniqueKey uk = uniqueKeyIterator.next();
            if (uk.getColumnSpan() == 1 && uk.containsColumn(column)) {
                return true;
            }
        }

        return column.isUnique();
    }

    private void addComments(StringBuffer buf, String tableName, Map<String, String> comments) {
        StringBuilder ddl = new StringBuilder();
        ddl.append("begin\n");
        for (Map.Entry<String, String> columnComment : comments.entrySet()) {
            String encoded = encodeComment(columnComment.getValue());
            ddl.append("execute immediate 'comment on ");
            if (columnComment.getKey() == null) {
                ddl.append("table ").append(tableName);
            }
            else {
                ddl.append("column ").append(tableName).append(".").append(columnComment.getKey());
            }
            ddl.append(" is ''").append(encoded).append("''';\n");
        }
        ddl.append("end;");

        getProfile().duplex(ObjectType.COMMENT, null, ddl.toString());

        buf.append(STATEMENT_SEPARATOR).append(ddl.toString()).append("\n/");
    }

    private static String encodeComment(String comment) {
        // double escape ' since we are using comment on inside an execute immediate expression
        return comment.replaceAll("'", "''''").replaceAll("\n", "'||chr(10)||\n'");
    }

    private String addConstraintsAndNames(Dialect dialect, StringBuffer additionalObjects, String sqlCreate, String nameLC, Map<String, String> column2EnumConstraint) {
        List<String> pkColumns = getPkColumnNamesFrom(sqlCreate);
        TableConfig tableConfig = tblNameToConfig.get(nameLC);

        StringBuilder b = new StringBuilder();
        StringBuilder enumConstraints = new StringBuilder();
        Matcher matcher = COL_PATTERN.matcher(sqlCreate);
        int idx = 0;

        while (matcher.find(idx)) {
            String defColName = matcher.group(COL_PATTERN_IDX_COLNAME);
            String colName = TableConfig.getIdentifierForReference(defColName);

            String constraintDef = column2EnumConstraint != null ? column2EnumConstraint.get(colName) : null;
            if (constraintDef != null) {
                String constraintName = getProfile().getNamingStrategy().constraintName(getEntityClassName(nameLC), nameLC, colName, Constants._CK);
                enumConstraints.append(", constraint ").append(constraintName);
                enumConstraints.append(" check (").append(defColName).append(" in (").append(constraintDef).append("))");
            }

            // name not null constraint
            idx = appendConstraint(b, sqlCreate, nameLC, colName, idx, matcher, COL_PATTERN_IDX_NOTNULL, Constants._NN);

            // name unique constraint
            idx = appendConstraint(b, sqlCreate, nameLC, colName, idx, matcher, COL_PATTERN_IDX_UNIQUE, Constants._UK);

            b.append(sqlCreate.substring(idx, matcher.end()));
            idx = matcher.end();
        }

        b.append(sqlCreate.substring(idx));

        sqlCreate = b.toString();
        b = new StringBuilder();

        matcher = UNIQUE_PATTERN.matcher(sqlCreate);
        idx = 0;

        while (matcher.find(idx)) {
            // name unique constraint
            idx = appendConstraint(b, sqlCreate, nameLC, DefaultNamingStrategy.concatColumnNames(matcher.group(3)), idx, matcher, 2, Constants._UK);

            b.append(sqlCreate.substring(idx, matcher.end()));
            idx = matcher.end();
        }

        b.append(sqlCreate.substring(idx));

        sqlCreate = b.toString();

        matcher = TBL_PATTERN.matcher(sqlCreate);

        if (matcher.find() && matcher.group(TBL_PATTERN_IDX_PK_CLAUSE) != null) {
            b = new StringBuilder();

            idx = matcher.start(TBL_PATTERN_IDX_PK_START);
            b.append(sqlCreate.substring(0, idx));

            // name primary key constraint
            idx = appendConstraint(b, sqlCreate, nameLC, DefaultNamingStrategy.concatColumnNames(matcher.group(TBL_PATTERN_IDX_PK_COLLIST)), idx, matcher, TBL_PATTERN_IDX_PK_START, Constants._PK);

            b.append(sqlCreate.substring(idx));

            sqlCreate = b.toString();
        }
        else {
            LOG.info("no primary key found for table {}", nameLC);
        }

        matcher = TBL_PATTERN_WO_PK.matcher(sqlCreate);
        if (matcher.matches()) {
            b = new StringBuilder(sqlCreate.substring(0, matcher.start(TBL_PATTERN_WO_PK_IDX_AFTER_COL_DEF)));

            if (enumConstraints.length() > 0) {
                b.append(enumConstraints);
            }

            com.github.gekoh.yagen.api.Table tblAnnotation = tableConfig != null ? tableConfig.getTableAnnotationOfType(com.github.gekoh.yagen.api.Table.class) : null;
            if (tblAnnotation != null) {
                for (CheckConstraint checkConstraint : tblAnnotation.checkConstraints()) {
                    String constraintName = getProfile().getNamingStrategy().constraintName(checkConstraint);
                    if (StringUtils.isEmpty(constraintName)) {
                        throw new IllegalArgumentException("please specify a check constraint name in annotation CheckConstraint for table " + nameLC);
                    }
                    checkObjectName(dialect, constraintName);
                    if (checkConstraint.initiallyDeferred() && isPostgreSql(dialect)) {
                        String objectName = constraintName + "_FCT";
                        additionalObjects.append(STATEMENT_SEPARATOR)
                                .append(getDeferredCheckConstraintFunction(dialect, objectName, constraintName, nameLC, String.format(checkConstraint.declaration(), "t."), pkColumns))
                                .append("\n/");
                        additionalObjects.append(STATEMENT_SEPARATOR)
                                .append("create constraint trigger ")
                                .append(getProfile().getNamingStrategy().triggerName(constraintName + "_TRG")).append("\n")
                                .append("after insert or update\n" +
                                        "on ").append(nameLC).append(" initially deferred for each row\n" +
                                "execute procedure ").append(objectName).append("();");
                    }
                    else {
                        b.append(", constraint ").append(constraintName);
                        b.append(" check (").append(String.format(checkConstraint.declaration(), "")).append(")");
                        if (supportsDeferrable(dialect) && checkConstraint.initiallyDeferred()) {
                            b.append(" deferrable initially deferred");
                        }
                    }
                }
                for (UniqueConstraint uniqueConstraint : tblAnnotation.uniqueConstraints()) {
                    // custom declarations of unique keys need to be created with separate unique index DDL
                    // when specifying column names we may use an inline unique constraint
                    if (uniqueConstraint.columnNames().length < 1) {
                        continue;
                    }
                    String constraintName = getProfile().getNamingStrategy().constraintName(uniqueConstraint);
                    if (StringUtils.isEmpty(constraintName)) {
                        throw new IllegalArgumentException("please specify a unique constraint name in annotation UniqueConstraint on table " + nameLC);
                    }
                    checkObjectName(dialect, constraintName);
                    if (StringUtils.isNotEmpty(uniqueConstraint.declaration()) && uniqueConstraint.columnNames().length>0) {
                        throw new IllegalArgumentException("please specify either a declaration or a set of column names for UniqueConstraint on table " + nameLC);
                    }

                    StringBuilder declaration = new StringBuilder();
                    for (String columnName : uniqueConstraint.columnNames()) {
                        if (declaration.length() > 0) {
                            declaration.append(", ");
                        }
                        declaration.append(columnName);
                    }

                    b.append(", constraint ").append(constraintName);
                    b.append(" unique (").append(declaration).append(")");

                    if (supportsDeferrable(dialect) && uniqueConstraint.initiallyDeferred()) {
                        b.append(" deferrable initially deferred");
                    }

                    if (uniqueConstraint.usingLocalIndex() && supportsPartitioning(dialect)) {
                        b.append(" using index (create unique index ").append(constraintName).append(" on ").append(nameLC);
                        b.append(" (").append(declaration).append(") local)");
                    }
                }
            }

            b.append(sqlCreate.substring(matcher.start(TBL_PATTERN_WO_PK_IDX_AFTER_COL_DEF)));
            sqlCreate = b.toString();
        }

        return sqlCreate;
    }

    private String getDeferredCheckConstraintFunction (Dialect dialect, String objectName, String constraintName, String tableName, String declaration, List<String> pkColumns) {
        VelocityContext context = new VelocityContext();

        context.put("dialect", dialect);
        context.put("objectName", objectName);
        context.put("tableName", tableName);
        context.put("constraintName", constraintName);
        context.put("pkColumns", pkColumns);
        context.put("declaration", declaration);

        StringWriter wr = new StringWriter();
        mergeTemplateFromResource("postgres/DeferredConstraintTriggerFunction.vm.pl.sql", wr, context);

        getProfile().duplex(ObjectType.VIEW, objectName, wr.toString());

        return wr.toString();
    }

    private String getShortName(String tableName) {
        String langDetailTblName = getLanguageDetailTableNameFromLiveTableName(tableName);
        if (langDetailTblName != null) {
            tableName = langDetailTblName;
        }
        NamingStrategy namingStrategy = getProfile().getNamingStrategy();
        TableConfig tableConfig = tblNameToConfig.get(tableName);
        Class entityBaseClass = tableConfig != null ? tableConfig.getEntityBaseClass() : null;
        String shortName;
        if (entityBaseClass == null) {
            shortName = namingStrategy.tableShortNameFromTableName(tableName);
        }
        else {
            shortName = namingStrategy.classToTableShortName(entityBaseClass.getName());
        }
        return namingStrategy.tableShortName(shortName);
    }

    private String getLanguageDetailTableNameFromLiveTableName(String liveTableName) {
        for (TableConfig tableConfig : tblNameToConfig.values()) {
            if (tableConfig.getI18nBaseEntityTblName() != null &&
                    liveTableName.equals(getI18NDetailTableName(tableConfig.getTableName()))) {
                return tableConfig.getTableName();
            }
        }
        return null;
    }

    private int appendConstraint(StringBuilder b,
                                        String sqlCreate,
                                        String tableName,
                                        String columnName,
                                        int currIdx,
                                        Matcher colMatcher,
                                        int groupId,
                                        String constraintSuffix) {
        if (colMatcher.group(groupId) != null) {
            String constraintName = getProfile().getNamingStrategy().constraintName(getEntityClassName(tableName), tableName, columnName, constraintSuffix);
            b.append(sqlCreate.substring(currIdx, colMatcher.start(groupId)));
            currIdx = colMatcher.start(groupId);
            b.append(" constraint ").append(constraintName);
            b.append(sqlCreate.substring(currIdx, colMatcher.end(groupId)));
            currIdx = colMatcher.end(groupId);
        }

        return currIdx;
    }

    private static final String FIRST_OF_NEXT_MONTH;
    static {
        GregorianCalendar calendar = new GregorianCalendar();
        calendar.add(Calendar.MONTH, 1);
        FIRST_OF_NEXT_MONTH = "01." + new SimpleDateFormat("MM.yyyy").format(calendar.getTime());
    }

    private String addPartitioning(StringBuffer addDdl, IntervalPartitioning partitioning, String nameLC, String sqlCreate, Set<String> columns, List<String> pkCols) {
        String shortName = getShortName(nameLC);

        if (StringUtils.isEmpty(shortName)) {
            String tblName = getProfile().getNamingStrategy().tableName(nameLC);
            shortName = (tblName.length() > 27 ? tblName.substring(0, 27) : tblName);
        }

        String partColName = partitioning.columnName().toLowerCase();
        if (!columns.contains(partColName)) {
            Matcher pkMatcher = TBL_PATTERN.matcher(sqlCreate);
            if (pkMatcher.find()) {
                StringBuilder sb = new StringBuilder();
                sb.append(sqlCreate.substring(0, pkMatcher.start(TBL_PATTERN_IDX_AFTER_COL_DEF)));
                sb.append(", ").append(partColName).append(" date default sysdate");
                sb.append(sqlCreate.substring(pkMatcher.start(TBL_PATTERN_IDX_AFTER_COL_DEF)));
                sqlCreate = sb.toString();
                columns.add(partColName);
            }
        }
        StringBuilder sb;

        if (partitioning.useLocalPK()) {
            sb = new StringBuilder();

            Matcher matcher = TBL_PATTERN.matcher(sqlCreate);

            if (!matcher.find()) {
                throw new IllegalArgumentException("cannot parse create table statement: "+sqlCreate);
            }

            if (matcher.group(TBL_PATTERN_IDX_PK_CLAUSE) != null) {
                String pkColList = matcher.group(TBL_PATTERN_IDX_PK_COLLIST);

                if (!pkCols.contains(partColName)) {
                    pkColList += ", " + partColName;
                }

                addDdl.append(STATEMENT_SEPARATOR).append("-- creating local unique index instead of global primary key\n");
                addDdl.append("create unique index ")
                        .append(getProfile().getNamingStrategy().indexName(getEntityClassName(nameLC), nameLC, DefaultNamingStrategy.concatColumnNames(pkColList)));
                addDdl.append(" on ").append(nameLC).append("(").append(pkColList).append(") local\n");
            }

            sb.append(sqlCreate.substring(0, matcher.start(TBL_PATTERN_IDX_AFTER_COL_DEF)));
            sb.append(sqlCreate.substring(matcher.end(TBL_PATTERN_IDX_AFTER_COL_DEF)));
        }
        else {
            sb = new StringBuilder(sqlCreate);
        }

        sb.append(" partition by range (").append(partitioning.columnName()).append(") ");
        sb.append("interval(").append(partitioning.interval()).append(") ");
        sb.append("( partition ").append(shortName).append("_P1 values less than (");

        if (StringUtils.isEmpty(partitioning.startPartitionLessThanValue())) {
            sb.append("to_date('").append(FIRST_OF_NEXT_MONTH).append("', 'dd.MM.yyyy')");
        }
        else {
            sb.append(partitioning.startPartitionLessThanValue());
        }

        sb.append(")) ");

        sb.append(partitioning.enableRowMovement() ? "ENABLE" : "DISABLE");
        sb.append(" ROW MOVEMENT");

        return sb.toString();
    }

    private String getI18NDetailTableName (String detailTableName) {
        String i18nBaseEntityTblName = tblNameToConfig.get(detailTableName).getI18nBaseEntityTblName();
        return i18nBaseEntityTblName != null ? i18nBaseEntityTblName + I18N_LIVE_TABLE_SUFFIX : null;
    }
    
    private String getI18NDetailTableCreateString(Dialect dialect, String sqlCreate, String i18nTblName, String i18nFKColName) {
        StringBuilder sql = new StringBuilder();
        Matcher matcher = TBL_PATTERN.matcher(sqlCreate);
        checkTableName(dialect, i18nTblName);
        
        if (matcher.matches()) {
            sql.append(sqlCreate.substring(0, matcher.start(TBL_PATTERN_IDX_TBLNAME))).append(i18nTblName);
            
            Matcher colMatcher = COL_PATTERN.matcher(sqlCreate);
            
            int idx = sqlCreate.indexOf('(', matcher.end(TBL_PATTERN_IDX_TBLNAME)) + 1;
            sql.append(sqlCreate.substring(matcher.end(TBL_PATTERN_IDX_TBLNAME), idx));

            StringBuilder colDef = new StringBuilder();
            while (colMatcher.find(idx)) {
                String colName = TableConfig.getIdentifierForReference(colMatcher.group(COL_PATTERN_IDX_COLNAME));
                if (!colName.toLowerCase().equals(I18N_COLUMN_IS_PERSISTENT) && !colName.toLowerCase().equals(I18N_COLUMN_COMPOSITE_ID)) {
                    colDef.append(" ").append(sqlCreate.substring(colMatcher.start(COL_PATTERN_IDX_COLNAME), colMatcher.end()));
                }
                idx = colMatcher.end();
            }
            sql.append(colDef.substring(1));
            sql.append(sqlCreate.substring(idx));

            String tmpSql = sql.toString();
            matcher = TBL_PATTERN.matcher(tmpSql);
            if (matcher.matches()) {
                sql = new StringBuilder(tmpSql.substring(0, matcher.start(TBL_PATTERN_IDX_PK_COLLIST)));
                sql.append(i18nFKColName).append(", language_cd")
                        .append(tmpSql.substring(matcher.end(TBL_PATTERN_IDX_PK_COLLIST)));
            }
        }

        getProfile().duplex(ObjectType.TABLE, i18nTblName, sql.toString());

        return sql.toString();
    }
    
    private String getI18NDetailViewCreateString (Dialect dialect, String i18nDetailTblName, String baseEntityTableName, String i18nTblName, String i18nFKColName, Set<String> columns) {
        VelocityContext context = new VelocityContext();

        context.put("dialect", dialect);
        context.put("i18nDetailTblName", i18nDetailTblName);
        context.put("i18nTblName", i18nTblName);
        context.put("baseEntityTableName", baseEntityTableName);
        context.put("i18nFKColName", i18nFKColName);
        context.put("columns", new ArrayList(columns));
        context.put("baseEntityPKColNames", tblNameToConfig.get(baseEntityTableName).getPkColnames());

        context.put("I18N_COLUMN_COMPOSITE_ID", I18N_COLUMN_COMPOSITE_ID);
        context.put("I18N_COLUMN_IS_PERSISTENT", I18N_COLUMN_IS_PERSISTENT);
        context.put("LANGUAGE_VIEW_NAME", LANGUAGE_VIEW_NAME);

        StringWriter wr = new StringWriter();
        mergeTemplateFromResource(isOracle(dialect) ? "I18NDetailView.vm.sql" : "I18NDetailView.hsqldb.vm.sql", wr, context);

        getProfile().duplex(ObjectType.VIEW, i18nDetailTblName, wr.toString());

        return wr.toString();
    }

    private void writeI18NDetailViewTriggerCreateString (Dialect dialect, StringBuffer buf, String i18nDetailTblName, String i18nTblName, String i18nFKColName, Set<String> columns) {
        VelocityContext context = new VelocityContext();
        String triggerBaseName = i18nDetailTblName.length() > MAX_LEN_OBJECT_NAME-4 ? i18nDetailTblName.substring(0, MAX_LEN_OBJECT_NAME-4) : i18nDetailTblName;

        context.put("dialect", dialect);
        context.put("i18nDetailTblName", i18nDetailTblName);
        context.put("i18nTblName", i18nTblName);
        context.put("i18nFKColName", i18nFKColName);
        context.put("columns", new ArrayList(columns));

        StringWriter wr = new StringWriter();
        if (isOracle(dialect)) {
            String objectName = triggerBaseName + "_TRG";
            context.put("objectName", objectName);

            setNewOldVar(dialect, context);

            mergeTemplateFromResource("I18NDetailViewTrigger.vm.pl.sql", wr, context);

            getProfile().duplex(ObjectType.TRIGGER, objectName, wr.toString());

            buf.append(STATEMENT_SEPARATOR).append(wr.toString()).append("\n/\n");
        }
        else if (isPostgreSql(dialect)) {
            String triggerName = triggerBaseName + "_TRG";
            String objectName = triggerName + "_function";
            context.put("objectName", objectName);

            setNewOldVar(dialect, context);

            mergeTemplateFromResource("I18NDetailViewTrigger.vm.pl.sql", wr, context);

            buf.append(STATEMENT_SEPARATOR).append(wr.toString()).append("\n/\n");

            buf.append(STATEMENT_SEPARATOR)
                    .append("create trigger ").append(triggerName).append("\n")
                    .append("instead of insert or update or delete on ").append(i18nDetailTblName).append("\n")
                    .append("for each row\n")
                    .append("execute procedure ").append(objectName).append("()");
        }
        else {
            try {
                wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, "I18NDetailViewTrigger.hsqldb.vm.pl.sql", context, triggerBaseName, "_it", "I"); wr.write("\n/\n");
                wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, "I18NDetailViewTrigger.hsqldb.vm.pl.sql", context, triggerBaseName, "_it", "U"); wr.write("\n/\n");
                wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, "I18NDetailViewTrigger.hsqldb.vm.pl.sql", context, triggerBaseName, "_it", "D"); wr.write("\n/\n");

                buf.append(wr.toString());
            } catch (IOException e) {
                LOG.error("error writing audit triggers", e);
            }
        }
    }

    private String getOracleHistTriggerSql (Dialect dialect,
                                            String tableName,
                                            String histTableName,
                                            String histColName,
                                            Set<String> columns,
                                            List<String> pkColumns,
                                            List<String> histRelevantCols) {
        String objectName = tableName + "_htr";

        String histTriggerSource = getHistTriggerSource(dialect, objectName, tableName, histTableName, histColName, columns, pkColumns, histRelevantCols);

        getProfile().duplex(ObjectType.TRIGGER, objectName, histTriggerSource);

        return histTriggerSource;
    }

    private String getPostgreSQLHistTriggerFunction (Dialect dialect,
                                                     String tableName,
                                                     String histTableName,
                                                     String histColName,
                                                     Set<String> columns,
                                                     List<String> pkColumns,
                                                     List<String> histRelevantCols) {
        return getHistTriggerSource(dialect, tableName + "_htr_function", tableName, histTableName, histColName, columns, pkColumns, histRelevantCols);
    }

    private String getHistTriggerSource (Dialect dialect,
                                         String objectName,
                                         String tableName,
                                         String histTableName,
                                         String histColName,
                                         Set<String> columns,
                                         List<String> pkColumns,
                                         List<String> histRelevantCols) {
        checkObjectName(dialect, objectName);

        VelocityContext context = new VelocityContext();

        Set<String> hstNoNullColumns = new HashSet<String>();
        TableConfig tableConfig = tblNameToConfig.get(tableName);

        IntervalPartitioning partitioning = tableConfig != null ? tableConfig.getTableAnnotationOfType(IntervalPartitioning.class) : null;
        if (partitioning != null) {
            hstNoNullColumns.add(partitioning.columnName().toLowerCase());
        }

        Set<String> nonPkColumns = new HashSet<String>(columns);
        nonPkColumns.removeAll(pkColumns);

        context.put("VERSION_COLUMN_NAME", VERSION_COLUMN_NAME);
        context.put("MODIFIER_COLUMN_NAME", AuditInfo.LAST_MODIFIED_BY);
        context.put("dialect", dialect);
        context.put("objectName", objectName);
        context.put("liveTableName", tableName);
        context.put("hstTableName", histTableName);
        context.put("columns", columns);
        context.put("histColName", histColName);
        context.put("pkColumns", pkColumns);
        context.put("nonPkColumns", nonPkColumns);
        context.put("noNullColumns", hstNoNullColumns);
        context.put("histRelevantCols", histRelevantCols);
        context.put("varcharType", dialect.getTypeName(Types.VARCHAR, 64, 0, 0));

        setNewOldVar(dialect, context);

        StringWriter wr = new StringWriter();
        mergeTemplateFromResource("HstTrigger.vm.pl.sql", wr, context);

        return wr.toString();
    }

    private String getHsqlDBHistTriggerSql (Dialect dialect,
                                            String tableName,
                                            String histTableName,
                                            String histColName,
                                            Set<String> columns,
                                            List<String> pkColumns,
                                            List<String> histRelevantCols) {
        VelocityContext context = new VelocityContext();

        Set<String> nonPkColumns = new HashSet<String>(columns);
        nonPkColumns.removeAll(pkColumns);

        context.put("VERSION_COLUMN_NAME", VERSION_COLUMN_NAME);
        context.put("MODIFIER_COLUMN_NAME", AuditInfo.LAST_MODIFIED_BY);
        context.put("liveTableName", tableName);
        context.put("hstTableName", histTableName);
        context.put("columns", columns);
        context.put("histColName", histColName);
        context.put("pkColumns", pkColumns);
        context.put("nonPkColumns", nonPkColumns);
        context.put("histRelevantCols", histRelevantCols);

        StringWriter wr = new StringWriter();

        try {
            wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, "HstTriggerSingleOperation.vm.pl.sql", context, tableName, "_ht", "I"); wr.write("\n/\n");
            wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, "HstTriggerSingleOperation.vm.pl.sql", context, tableName, "_ht", "U"); wr.write("\n/\n");
            wr.append(STATEMENT_SEPARATOR); writeTriggerSingleOperation(dialect, wr, "HstTriggerSingleOperation.vm.pl.sql", context, tableName, "_ht", "D"); wr.write("\n/\n");
        } catch (IOException e) {
            throw new IllegalStateException("cannot read history trigger template");
        }

        return wr.toString();
    }

    private void writeTriggerSingleOperation(Dialect dialect, Writer wr, String resourceName, VelocityContext context, String tableName, String suffix, String operation)
            throws IOException {
        StringWriter objWr = new StringWriter();

        String triggerName = getProfile().getNamingStrategy().triggerName(getEntityClassName(tableName), tableName, null, suffix + operation);
        checkObjectName(dialect, triggerName);

        context.put("triggerName", triggerName);
        context.put("operation", operation);

        mergeTemplateFromResource(resourceName, objWr, context);

        String object = objWr.toString();

        if (dbObjects != null) {
            dbObjects.add(object);
        }

        wr.write(object);

        getProfile().duplex(ObjectType.TRIGGER, triggerName, objWr.toString());
    }

    private String getHistTableSqlCreateString (Dialect dialect,
                                                String sqlCreateString,
                                                String histTableName,
                                                String histColName,
                                                Set<String> columns,
                                                List<String> pkCols,
                                                IntervalPartitioning livePartitioning) {
        checkTableName(dialect, histTableName);

        Matcher matcher = TBL_PATTERN.matcher(sqlCreateString);
        Matcher matchUnique = UNIQUE_PATTERN.matcher(sqlCreateString);

        StringBuilder sql = new StringBuilder();

        if (!matcher.matches()) {
            throw new IllegalStateException("cannot find create table with PK in sql: " + sqlCreateString);
        }

        sql.append(sqlCreateString.substring(0, matcher.start(TBL_PATTERN_IDX_TBLNAME))).append(histTableName);
        sql.append(sqlCreateString.substring(matcher.end(TBL_PATTERN_IDX_TBLNAME), matcher.start(TBL_PATTERN_IDX_TBL_DEF)));
        sql.append(formatColumn(dialect, HIST_TABLE_PK_COLUMN_NAME+" ${varcharType} not null", Constants.UUID_LEN, null, null)).append(", ");
        sql.append(formatColumn(dialect, HIST_OPERATION_COLUMN_NAME+" ${varcharType} not null", 1, null, null)).append(", ");

        sql.append(sqlCreateString.substring(matcher.start(TBL_PATTERN_IDX_TBL_DEF), matcher.start(TBL_PATTERN_IDX_PK_CLAUSE)));

        if (!columns.contains(histColName)) {
            sql.append(formatColumn(dialect, histColName+" ${timestampType} not null", null, null, null)).append(", ");
        }

        sql.append(sqlCreateString.substring(matcher.start(TBL_PATTERN_IDX_PK_CLAUSE), matcher.start(TBL_PATTERN_IDX_PK_COLLIST)));
        sql.append(HIST_TABLE_PK_COLUMN_NAME).append("), ");
        sql.append("unique (");
        sql.append(matcher.group(TBL_PATTERN_IDX_PK_COLLIST));
        sql.append(", ");
        sql.append(histColName);
        sql.append(")");

        int restIdx = matcher.end(TBL_PATTERN_IDX_PK_CLAUSE);
        while (matchUnique.find(restIdx)) {
            restIdx = matchUnique.end(1);
        }

        sql.append(sqlCreateString.substring(restIdx));

        Matcher uniqueColMatcher = COL_PATTERN.matcher(sql.toString());
        int colIdx = 0;
        while (uniqueColMatcher.find(colIdx)) {
            String colName = TableConfig.getIdentifierForReference(uniqueColMatcher.group(COL_PATTERN_IDX_COLNAME));
//                remove unique constraint from single column
            if (uniqueColMatcher.group(COL_PATTERN_IDX_UNIQUE) != null) {
                sql.delete(uniqueColMatcher.start(COL_PATTERN_IDX_UNIQUE), uniqueColMatcher.end(COL_PATTERN_IDX_UNIQUE));
                colIdx = uniqueColMatcher.start();
                uniqueColMatcher = COL_PATTERN.matcher(sql.toString());
            }
//                remove not null constraints
            else if (!colName.equals(HIST_OPERATION_COLUMN_NAME) &&
                    !colName.equals(histColName) &&
                    uniqueColMatcher.group(COL_PATTERN_IDX_NOT) != null) {
                sql.delete(uniqueColMatcher.start(COL_PATTERN_IDX_NOTNULL), uniqueColMatcher.end(COL_PATTERN_IDX_NOTNULL));
                colIdx = uniqueColMatcher.start();
                uniqueColMatcher = COL_PATTERN.matcher(sql.toString());
            }
            else if (colName.equals(histColName)) {
                String addCol = ", " + HIST_INVALID_TIMESTAMP_COLUMN_NAME + " " + uniqueColMatcher.group(COL_PATTERN_IDX_TYPE);
                sql.insert(uniqueColMatcher.end()-1, addCol);
                colIdx = uniqueColMatcher.end() + addCol.length();
                uniqueColMatcher = COL_PATTERN.matcher(sql.toString());
            }
            else {
                colIdx = uniqueColMatcher.end();
            }
        }

        StringBuffer additionalObjects = new StringBuffer();

        if (supportsPartitioning(dialect) && livePartitioning != null) {
            sqlCreateString = addPartitioning(additionalObjects, livePartitioning, histTableName, sql.toString(), columns, pkCols);
        }
        else {
            sqlCreateString = sql.toString();
        }

        sqlCreateString = addConstraintsAndNames(dialect, additionalObjects, sqlCreateString, histTableName.toLowerCase(), null);
        sqlCreateString = addDefaultValues(sqlCreateString, histTableName.toLowerCase());

        getProfile().duplex(ObjectType.TABLE, histTableName, sqlCreateString);

        return sqlCreateString + additionalObjects.toString();
    }
    
    private static void mergeTemplateFromResource(String resource, Writer wr, VelocityContext context) {
        try {
            Velocity.evaluate(context, wr, CreateDDL.class.getName()+"#"+resource,
                    new InputStreamReader(CreateDDL.class.getResourceAsStream(resource), "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    private static Set<String> getI18NEntityColumns (Set<String> columns) {
        Set<String> colSet = new HashSet<String>();
        for (String columnName : columns) {
            if (!(columnName instanceof String)) {
                throw new IllegalStateException("column name must be of type String");
            }
            if (I18N_COLUMN_COMPOSITE_ID.equals(columnName) || I18N_COLUMN_IS_PERSISTENT.equals(columnName)) {
                continue;
            }
            colSet.add(columnName);
        }
        return colSet;
    }
    
    private void addDropStatement(String tableName, String dropSql) {
        tableName = tableName.toLowerCase();
        List<String> dropSqls = tblNameToDropObjectsSql.get(tableName);
        if (dropSqls == null) {
            tblNameToDropObjectsSql.put(tableName, dropSqls = new ArrayList<String>());
        }
        dropSqls.add(dropSql);
    }

    public List<String> getDBObjects() {
        return dbObjects;
    }

    public void initViewsAndRegisterDDLs (Dialect dialect, DDLGenerator.AddDDLEntry... addDDLEntries) {
        List<DDLGenerator.AddDDLEntry> ddls = getProfile().getAllDdls();

        for (DDLGenerator.AddDDLEntry ddlFile : ddls) {
            if (ddlFile.isReader()) {
                continue;
            }
            Matcher matcher = VIEW_NAME_PATTERN.matcher(ddlFile.getDdlText(dialect));
            int idx = 0;
            while(matcher.find(idx)) {
                externalViews.add(matcher.group(2).toLowerCase());
                idx = matcher.end();
            }
        }

        views = new HashSet<String>(externalViews);
//        Add all i18n view names
        for (TableConfig tableConfig : tblNameToConfig.values()) {
            if (tableConfig.getI18nBaseEntityTblName() != null) {
                views.add(tableConfig.getI18nBaseEntityTblName());
            }
        }

        getProfile().addDdl(0, getAddDDL());

        if (addDDLEntries != null) {
            for (int i=addDDLEntries.length; i>0; i--) {
                getProfile().addDdl(1, addDDLEntries[i-1]);
            }
        }

    }

    private DDLGenerator.AddDDLEntry getAddDDL () {
        return new DDLGenerator.AddDDLEntry(new Reader() {

            StringReader reader;

            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                if (reader == null) {
                    StringBuilder ddl = new StringBuilder();
                    if (deferredDdl.length() > 0) {
                        ddl.append(STATEMENT_SEPARATOR);
                        ddl.append("-- deferred DDL executed after creation of entities\n")
                                .append("-- DO NOT EDIT MANUALLY!\n");

                        if (deferredDdl.indexOf(LANGUAGE_VIEW_NAME)>=0) {
                            ddl.append(STATEMENT_SEPARATOR);
                            ddl.append("create or replace view ").append(LANGUAGE_VIEW_NAME).append(" as\n")
                                    .append("select 'DE' language_cd from dual union all\n")
                                    .append("select 'EN' language_cd from dual;");
                        }
                        ddl.append(deferredDdl);
                    }

                    reader = new StringReader(ddl.toString());
                }
                return reader.read(cbuf, off, len);
            }

            @Override
            public void close() throws IOException {
            }
        });
    }

    public DDLGenerator.Profile getProfile() {
        return currentProfile;
    }

    private static void setNewOldVar(Dialect dialect, VelocityContext context) {
        if (isOracle(dialect)) {
            context.put("new", ":new");
            context.put("old", ":old");
        }
        else {
            context.put("new", "new");
            context.put("old", "old");
        }
    }

    private static String getNameAndIfExistsWhenSupported(Dialect dialect, String objectName) {
        if (supportsDropIfExists(dialect)) {
            if (isPostgreSql(dialect)) {
                return "if exists " + objectName;
            }
            return objectName + " if exists";
        }
        return objectName;
    }

    private static boolean isPostgreSql(Dialect dialect) {
        return dialect.getClass().getSimpleName().toLowerCase().contains("postgres");
    }

    private static boolean isHsqlDB(Dialect dialect) {
        return dialect.getClass().getName().toLowerCase().contains("hsql");
    }

    private static boolean isOracle(Dialect dialect) {
        return dialect.getClass().getSimpleName().toLowerCase().contains("oracle");
    }

    private static boolean isOracleXE(Dialect dialect) {
        return dialect.getClass().getSimpleName().toLowerCase().contains("oraclexe");
    }

    private static boolean supportsDeferrable(Dialect dialect) {
        return !isHsqlDB(dialect);
    }

    private static boolean supportsPartitioning(Dialect dialect) {
        return isOracle(dialect) && !isOracleXE(dialect);
    }

    private static boolean supportsDropIfExists(Dialect dialect) {
        return !isOracle(dialect);
    }
}