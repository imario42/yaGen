package com.github.gekoh.yagen.ddl;

import com.github.gekoh.yagen.api.AuditInfo;
import org.hibernate.dialect.Oracle10gDialect;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;

/**
 * @author Georg Kohlweiss
 */
public class CreateDDLTest {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CreateDDLTest.class);

    @Test
    public void testModifyDdl() {
        DDLGenerator.Profile profile = new DDLGenerator.Profile("default");
        Oracle10gDialect dialect = new Oracle10gDialect();
        CreateDDL createDDL = new CreateDDL(profile, dialect);

        TableConfig.add(createDDL, "AMP_JOB_CONFIGS")
                .withTableAnnotation("AMPJC")
                .withTemporalEntityAnnotation()
                .withAuditableAnnotation();

        Collection<String> sql = createDDL.enhanceCreateTableDdl(dialect, "CREATE TABLE AMP_JOB_CONFIGS ( ID varchar2(36) NOT NULL, PRIORITY number(10) NOT NULL, MEMORY_USAGE_MB number(10) NOT NULL, CPU_USAGE number(3,1) NOT NULL, RETRY_MAX_COUNT number(10) NOT NULL, RETRY_DELAY_SECONDS number(10) NOT NULL, TIMEOUT_SECONDS number(10) NOT NULL, AUTO_TRANSACTIONAL number(1) NOT NULL, JOBS_ID varchar2(36) NOT NULL, PRIMARY KEY (ID));");

        boolean historyTableCreated = false;
        boolean historyTriggerCreated = false;
        boolean auditTriggerCreated = false;
        boolean auditColumnsCreated = true;
        boolean primaryKeyNamed = false;
        boolean hstConstraintNamed = false;

        for (String s : sql) {

            String lowerCase = s.toLowerCase();
            if (lowerCase.contains("AMP_JOB_CONFIGS_HST".toLowerCase())) {
                historyTableCreated = true;
            }
            if (lowerCase.contains("amp_job_configs_htr".toLowerCase())) {
                historyTriggerCreated = true;
            }
            if (lowerCase.contains("amp_job_configs_ATR".toLowerCase())) {
                auditTriggerCreated = true;
            }
            if (lowerCase.contains("create table ")) {
                if (auditColumnsCreated &&
                        lowerCase.contains(AuditInfo.CREATED_AT.toLowerCase()) &&
                        lowerCase.contains(AuditInfo.CREATED_BY.toLowerCase()) &&
                        lowerCase.contains(AuditInfo.LAST_MODIFIED_AT.toLowerCase()) &&
                        lowerCase.contains(AuditInfo.LAST_MODIFIED_BY.toLowerCase())) {
                }
                else {
                    auditColumnsCreated = false;
                }
            }
            if (lowerCase.contains("AMPJC_ID_PK".toLowerCase())) {
                primaryKeyNamed = true;
            }
            if (lowerCase.contains("AMPJCH_operation_NN".toLowerCase())) {
                hstConstraintNamed = true;
            }
        }

        Assert.assertTrue(historyTableCreated);
        Assert.assertTrue(historyTriggerCreated);
        Assert.assertTrue(auditTriggerCreated);
        Assert.assertTrue(auditColumnsCreated);
        Assert.assertTrue(primaryKeyNamed);
        Assert.assertTrue(hstConstraintNamed);

    }
}