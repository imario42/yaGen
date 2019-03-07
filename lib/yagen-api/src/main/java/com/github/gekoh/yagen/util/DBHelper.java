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

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.regex.Pattern;

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
        long nanoTime = new NanoSecondsTimestamp().currentNanoSecondsTimestamp();

        Timestamp timestamp = new Timestamp(nanoTime/NanoSecondsTimestamp.MICRO_NANO_FACTOR);
        timestamp.setNanos(timestamp.getNanos() + (int)(nanoTime-(nanoTime/NanoSecondsTimestamp.MICRO_NANO_FACTOR)*NanoSecondsTimestamp.MICRO_NANO_FACTOR));
        return timestamp;
    }

    /**
     * code from <a href="https://michael.hoennig.de/2009/08/21/absoluter-nanosekunden-zeitstempel-in-java/">here</a>
     */
    private static class NanoSecondsTimestamp {

        private static final long MICRO_NANO_FACTOR = 1000000L;

        private long nanoSecondsOffset, nanoSecondsError;

        public NanoSecondsTimestamp() {
            long curMilliSecs0, curMilliSecs1,
                    curNanoSecs, startNanoSecs, endNanoSecs;
            do {
                startNanoSecs = System.nanoTime();
                curMilliSecs0 = System.currentTimeMillis();
                curNanoSecs = System.nanoTime();
                curMilliSecs1 = System.currentTimeMillis();
                endNanoSecs = System.nanoTime();
            } while ( curMilliSecs0 == curMilliSecs1 );

            nanoSecondsOffset = MICRO_NANO_FACTOR*curMilliSecs1 - curNanoSecs;
            nanoSecondsError = endNanoSecs - startNanoSecs;
        }

        public long getNanoSecondsDeviation() {
            return nanoSecondsError;
        }

        public long currentNanoSecondsTimestamp() {
            return System.nanoTime() + nanoSecondsOffset;
        }
    }
}