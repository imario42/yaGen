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
import org.hibernate.Session;
import org.hibernate.dialect.Dialect;
import org.hibernate.jdbc.ReturningWork;

import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.github.gekoh.yagen.api.Constants.USER_NAME_LEN;

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

    public static boolean isBypassed(String objectName) {
        final String bypass = System.getProperty("yagen.bypass");
        if (bypass != null) {
            return true;
        }
        final String bypassPattern = System.getProperty("yagen.bypass.regex");
        if (bypassPattern != null) {
            return objectName.matches(bypassPattern);
        }
        return false;
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

    public static boolean regexpLike(String value, String regexp) {
        if (value == null) {
            return false;
        }
        return Pattern.compile(regexp).matcher(value).find();
    }

    public static boolean regexpLikeFlags(String value, String regexp, String flags) {
        if (value == null || flags == null) {
            return false;
        }
        String f = flags.toLowerCase();
        int opts = 0;
        if (f.contains("i")) {
            opts = opts | Pattern.CASE_INSENSITIVE;
        }
        if (f.contains("c")) {
            opts = opts | Pattern.CANON_EQ;
        }
        if (f.contains("n")) {
            opts = opts | Pattern.DOTALL;
        }
        if (f.contains("m")) {
            opts = opts | Pattern.MULTILINE;
        }
        return Pattern.compile(regexp, opts).matcher(value).find();
    }

    public static String injectSessionUser(String user, EntityManager em) {
        String prevUser = null;
        if (isHsqlDb(em)) {
            try {
                prevUser = (String) em.createNativeQuery("select VALUE from SESSION_VARIABLES where NAME='CLIENT_IDENTIFIER'").getSingleResult();
                em.createNativeQuery("update SESSION_VARIABLES set VALUE=:user where NAME='CLIENT_IDENTIFIER'").setParameter("user", user).executeUpdate();

            } catch (Exception ignore) {
                em.createNativeQuery("insert into SESSION_VARIABLES (NAME, VALUE) values ('CLIENT_IDENTIFIER', :user)").setParameter("user", user).executeUpdate();
            }
        }
        else {
            prevUser = em.unwrap(Session.class).doReturningWork(new SetUserWorkOracle(user));
        }

        return prevUser;
    }

    private static boolean isHsqlDb(EntityManager em) {
        return em.unwrap(Session.class).doReturningWork(new ReturningWork<Boolean>() {
            public Boolean execute(Connection connection) throws SQLException {
                String driverName = connection.getMetaData().getDriverName();

                return driverName != null && driverName.toLowerCase().contains("hsql");
            }
        });
    }

    public static String getDriverClassName(Dialect dialect) {
        return dialect instanceof DDLEnhancer ? getDriverClassName(((DDLEnhancer) dialect).getServiceRegistry()) : null;
    }

    public static String getDriverClassName(Object serviceRegistry) {
        String driverClassName = null;

        if (serviceRegistry == null) {
            return null;
        }

        Method getService = null;
        try {
            getService = Class.forName("org.hibernate.service.ServiceRegistry").getDeclaredMethod("getService", Class.class);
        } catch (Exception e) {
            LOG.warn("cannot detect jdbc driver name");
            return null;
        }
        try {

            Class providerClass = Class.forName("org.hibernate.service.jdbc.connections.internal.DatasourceConnectionProviderImpl");
            Field dataSourceField = providerClass.getDeclaredField("dataSource");
            dataSourceField.setAccessible(true);
            DataSource dataSource = (DataSource) dataSourceField.get(getService.invoke(serviceRegistry, Class.forName("org.hibernate.service.jdbc.connections.spi.ConnectionProvider")));
            Field driverClassNameField = dataSource.getClass().getDeclaredField("driverClassName");
            driverClassNameField.setAccessible(true);

            driverClassName = (String) driverClassNameField.get(dataSource);
        } catch (Exception e) {

            try {
                Field creatorField = Class.forName("org.hibernate.engine.jdbc.connections.internal.DriverManagerConnectionProviderImpl").getDeclaredField("connectionCreator");
                creatorField.setAccessible(true);
                Field driverField = Class.forName("org.hibernate.engine.jdbc.connections.internal.DriverConnectionCreator").getDeclaredField("driver");
                driverField.setAccessible(true);

                driverClassName = driverField.get(creatorField.get(getService.invoke(serviceRegistry, Class.forName("org.hibernate.engine.jdbc.connections.spi.ConnectionProvider")))).getClass().getName();
            } catch (Exception e1) {
                LOG.warn("cannot detect jdbc driver name");
            }
        }

        return driverClassName;
    }

    public static Timestamp getCurrentTimestamp() {
        return NanoAwareTimestampUtil.getCurrentTimestamp();
    }

    public static class SetUserWorkOracle implements ReturningWork<String> {
        private String userName;

        public SetUserWorkOracle(String userName) {
            this.userName = userName;
        }

        public String execute(Connection connection) throws SQLException {
            CallableStatement statement = connection.prepareCall(
                    "declare newUserValue varchar2(" + USER_NAME_LEN + ") := substr(?,1," + USER_NAME_LEN + "); " +
                            "begin ? := sys_context('USERENV','CLIENT_IDENTIFIER'); " +
                            "DBMS_SESSION.set_identifier(newUserValue); " +
                            "end;"
            );
            statement.setString(1, userName);
            statement.registerOutParameter(2, Types.VARCHAR);
            statement.execute();
            String result = statement.getString(2);
            statement.close();
            LOG.info("set client_identifier in oracle session from '{}' to '{}'", result == null ? "" : result, userName == null ? "" : "<db_user> (" + userName + ")");
            return result;
        }
    }

}