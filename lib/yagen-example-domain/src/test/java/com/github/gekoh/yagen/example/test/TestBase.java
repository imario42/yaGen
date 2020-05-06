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
 * @author Georg Kohlweiss
 */
public abstract class TestBase {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TestBase.class);

    private EntityManagerFactory emf;

    static {
        try {
            YagenInit.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected EntityManager em;

    public EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            setupDatabase();
            emf = Persistence.createEntityManagerFactory(getPersistenceUnitName(), null);
        }
        return emf;
    }

    protected abstract String getPersistenceUnitName();

    @Before
    public void setup() {
        em = getEntityManagerFactory().createEntityManager();
    }

    @After
    public void shutdown() {
        if (em != null) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            em.close();
        }
        shutdownDatabase();
    }

    protected void setupDatabase() { }
    protected void shutdownDatabase() {}
    protected abstract String getDbUserName();
}
