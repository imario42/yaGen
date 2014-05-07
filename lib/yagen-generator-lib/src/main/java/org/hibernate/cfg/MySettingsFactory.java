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