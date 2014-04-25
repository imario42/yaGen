package com.github.gekoh.yagen.hibernate;

import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import org.hibernate.dialect.Dialect;

/**
 * @author Georg Kohlweiss 
 */
public class Oracle10gDialect extends org.hibernate.dialect.Oracle10gDialect implements DDLEnhancer {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Oracle10gDialect.class);

    private CreateDDL ddlEnhancer;

    public void initDDLEnhancer(DDLGenerator.Profile profile, Dialect dialect) {
        ddlEnhancer = new CreateDDL(profile, dialect);
    }

    public CreateDDL getDDLEnhancer() {
        return ddlEnhancer;
    }
}