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
package com.github.gekoh.yagen.util;

import com.github.gekoh.yagen.hibernate.DDLEnhancer;
import org.hibernate.dialect.Dialect;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.Service;
import org.hibernate.service.ServiceRegistry;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.UUID;

/**
 * @author Georg Kohlweiss
 */
public class DBHelper {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DBHelper.class);

    public static String createUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "").toUpperCase(); // replace "-" 36 -> 32 char
    }

    public static String getOsUser() {
//        that's not necessarily the user logged in but one can change this value with env var USERNAME
//        which is absolutely sufficient in this case
        return System.getProperty("user.name");
    }

    public static String getSysContext(String namespace, String parameter) {
        if ("USERENV".equals(namespace)) {
            if ("DB_NAME".equals(parameter)) {
                return "HSQLDB";
            }
            else if ("OS_USER".equals(parameter)) {
                return getOsUser();
            }
            else if ("CLIENT_IDENTIFIER".equals(parameter)) {
                return null;
            }
        }

        return null;
    }

    public static String getDriverClassName(Dialect dialect) {
        return dialect instanceof DDLEnhancer ? getDriverClassName(((DDLEnhancer) dialect).getServiceRegistry()) : null;
    }

    public static String getDriverClassName(ServiceRegistry serviceRegistry) {
        String driverClassName = null;

        try {
            Class providerClass = Class.forName("org.hibernate.service.jdbc.connections.internal.DatasourceConnectionProviderImpl");
            Field dataSourceField = providerClass.getDeclaredField("dataSource");
            dataSourceField.setAccessible(true);
            DataSource dataSource = (DataSource) dataSourceField.get(serviceRegistry.getService((Class<? extends Service>) Class.forName("org.hibernate.service.jdbc.connections.spi.ConnectionProvider")));
            Field driverClassNameField = dataSource.getClass().getDeclaredField("driverClassName");
            driverClassNameField.setAccessible(true);

            driverClassName = (String) driverClassNameField.get(dataSource);
        } catch (Exception e) {

            try {
                Field creatorField = Class.forName("org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl").getDeclaredField("connectionCreator");
                creatorField.setAccessible(true);
                Field driverField = Class.forName("org.hibernate.engine.jdbc.connections.internal.DriverConnectionCreator").getDeclaredField("driver");
                driverField.setAccessible(true);

                driverClassName = driverField.get(creatorField.get(serviceRegistry.getService(ConnectionProvider.class))).getClass().getName();
            } catch (Exception e1) {
                LOG.warn("cannot detect jdbc driver name");
            }
        }

        return driverClassName;
    }
}