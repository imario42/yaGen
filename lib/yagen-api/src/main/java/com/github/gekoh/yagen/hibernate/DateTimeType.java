/*
 * DateTimeType
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.hibernate;

import org.hibernate.HibernateException;
import org.hibernate.type.ImmutableType;
import org.joda.time.DateTime;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Date;

/**
 * @author Thomas Spiegl
 * @author Georg Kohlweiss (G477448)
 */
@SuppressWarnings("deprecation")
public class DateTimeType extends ImmutableType {

    public static final String TYPE = "com.github.gekoh.yagen.hibernate.DateTimeType";

    @Override
    public Object get(ResultSet rs, String name) throws HibernateException, SQLException {
        Timestamp ts = rs.getTimestamp(name);
        if (ts == null) {
            return null;
        }
        long millis = ts.getTime();
        return new DateTime(millis) ;
    }

    @Override
    public void set(PreparedStatement st, Object value, int index) throws HibernateException, SQLException {
        long millis = ((DateTime) value).getMillis();
        st.setTimestamp(index, new Timestamp(millis));
    }

    @Override
    public int sqlType() {
        return Types.TIMESTAMP;
    }

    @Override
    public String toString(Object value) throws HibernateException {
        return value.toString();
    }

    public Class getReturnedClass() {
        return DateTime.class;
    }

    @Override
    public boolean isEqual(Object x, Object y) {
        return x == y || ( x != null && y != null && toDate(x).compareTo(toDate(y)) == 0 );
    }

    private java.util.Date toDate(Object dateTime) {
        return toDate(((DateTime) dateTime));
    }

    public String getName() {
        return "joda_date_time";
    }

    @Override
    public Object fromStringValue(String xml) {
        return new DateTime(xml);
    }

    private static Date toDate(DateTime dateTime){
        return dateTime != null ? dateTime.toDate(): null ;
    }
}