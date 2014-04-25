package com.github.gekoh.yagen.ddl;

import com.github.gekoh.yagen.api.Constants;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author Georg Kohlweiss 
 */
public class AuditTriggerDuplexer implements Duplexer {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AuditTriggerDuplexer.class);

    private Writer writer;
    private String delimiter;

    public AuditTriggerDuplexer(File writeToFile, String delimiter) throws IOException {
        this.writer = new FileWriter(writeToFile);
        this.delimiter = delimiter;
    }

    @Override
    public void handleDdl(ObjectType objectType, String objectName, String ddl) {
        if (objectType != ObjectType.TRIGGER || !objectName.endsWith(Constants._ATR)) {
            return;
        }

        try {
            writer.write(ddl);
            writer.write(delimiter);
            writer.flush();
        } catch (IOException e) {
            LOG.warn("unable to duplex DDL", e);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        writer.close();
    }
}