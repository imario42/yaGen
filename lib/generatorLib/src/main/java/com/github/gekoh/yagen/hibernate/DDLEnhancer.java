package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import org.hibernate.dialect.Dialect;

/**
 * @author Georg Kohlweiss 
 */
public interface DDLEnhancer {
    void initDDLEnhancer(DDLGenerator.Profile profile, Dialect dialect);
    CreateDDL getDDLEnhancer();
}