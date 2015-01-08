/*
 * HSQLDialect
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.util;

import java.sql.Types;

/**
 * @author Georg Kohlweiss (F477448)
 */
public class HSQLFixLobDialect extends org.hibernate.dialect.HSQLDialect {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HSQLDialect.class);


    public HSQLFixLobDialect() {
        registerColumnType( Types.BLOB, "blob" );
        registerColumnType( Types.CLOB, "clob" );
    }
}