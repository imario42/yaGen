/*
 * SqlStatement
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.hibernate;

/**
 * @author Georg Kohlweiss (F477448)
 */
public interface SqlStatement {
    String getSql();
    String getDelimiter();
}