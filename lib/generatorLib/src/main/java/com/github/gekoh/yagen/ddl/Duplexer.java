package com.github.gekoh.yagen.ddl;

/**
 * @author Georg Kohlweiss 
 */
public interface Duplexer {

    void handleDdl(ObjectType objectType, String objectName, String ddl);
}