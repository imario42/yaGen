package org.hibernate.cfg;

import org.hibernate.connection.ConnectionProviderFactory;
import org.hibernate.dialect.Dialect;
import org.hibernate.jdbc.util.SQLStatementLogger;
import org.hibernate.util.PropertiesHelper;

import java.util.Properties;

/**
 * @author Georg Kohlweiss 
 */
public class MySettingsFactory {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MySettingsFactory.class);

    public static Settings createSettings(Properties cfgProperties, Dialect dialect) {
        Settings settings = new Settings();
        settings.setConnectionProvider(ConnectionProviderFactory.newConnectionProvider(cfgProperties));
        settings.setDialect(dialect);
        boolean showSql = PropertiesHelper.getBoolean(Environment.SHOW_SQL, cfgProperties);
        boolean formatSql = PropertiesHelper.getBoolean(Environment.FORMAT_SQL, cfgProperties);
        settings.setSqlStatementLogger(new SQLStatementLogger( showSql, formatSql ));

        return settings;
    }
}