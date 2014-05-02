package com.github.gekoh.yagen.api;

import org.hibernate.mapping.Constraint;

/**
 * @author Georg Kohlweiss 
 */
public interface NamingStrategy extends org.hibernate.cfg.NamingStrategy {

    /**
     * Return a table short name for an entity class
     * @param className the fully-qualified class name
     * @return a table short name
     */
    String classToTableShortName(String className);
    /**
     * Alter the table short name given by annotation {@link Table} or static String field named TABLE_NAME_SHORT
     * @param tableShortName a table short name
     * @return a table short name
     */
    String tableShortName(String tableShortName);
    /**
     * basically only generates a short name for a given table name if there is no other way to go
     * @param tableName a table name
     * @return a table short name
     */
    String tableShortNameFromTableName(String tableName);

    String constraintName(String constraintName);

    String constraintName(Constraint constraint, String entityClass);

    String constraintName(UniqueConstraint constraint);

    String constraintName(CheckConstraint constraint);

    String constraintName(String entityClass, String tableName, String colName, String suffix);

    String indexName(String indexName);

    String indexName(Index index);

    String indexName(String entityClass, String tableName, String colName);

    String triggerName(String triggerName);

    String triggerName(String entityClass, String tableName, String colName, String suffix);

    String sequenceName(String sequenceName);
}