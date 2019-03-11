/*
 * TestBase
 * Copyright (c) 2012 CREDIT SUISSE Technology and Operations. All Rights Reserved.
 * This software is the proprietary information of CREDIT SUISSE Technology and Operations.
 * Use is subject to license and non-disclosure terms.
 */
package com.github.gekoh.yagen.example.test;

import com.github.gekoh.yagen.hibernate.YagenInit;
import org.junit.After;
import org.junit.Before;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Georg Kohlweiss (G477448)
 */
public class TestBase {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TestBase.class);

    protected static final EntityManagerFactory emf;

    static {
        try {
            YagenInit.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        emf = Persistence.createEntityManagerFactory("example-domain-test", null);
    }

    protected EntityManager em;

    @Before
    public void setup() {
        em = emf.createEntityManager();
    }

    @After
    public void shutdown() {
        if (em.getTransaction().isActive()) {
            em.getTransaction().rollback();
        }
        em.close();
    }
}