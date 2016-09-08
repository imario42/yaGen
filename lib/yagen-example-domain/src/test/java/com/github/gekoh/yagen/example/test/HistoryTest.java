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
package com.github.gekoh.yagen.example.test;

import com.github.gekoh.yagen.example.Aircraft;
import com.github.gekoh.yagen.example.AircraftHst;
import com.github.gekoh.yagen.example.EngineType;
import com.github.gekoh.yagen.hibernate.YagenInit;
import com.github.gekoh.yagen.hst.Operation;
import org.junit.Assert;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

/**
 * @author Georg Kohlweiss
 */
public class HistoryTest {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(HistoryTest.class);

    private static final EntityManagerFactory emf;
    static {
        try {
            YagenInit.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        emf = Persistence.createEntityManagerFactory("example-domain-test", null);
    }

    @Test
    public void testHistory() {
        EntityManager em = null;
        try {
            em = emf.createEntityManager();
            em.getTransaction().begin();

            Aircraft ac = new Aircraft(EngineType.piston, "C172", "OE-DVK", 10.92f, 8.2f);
            em.persist(ac);

            em.flush();
            em.getTransaction().commit();

            AircraftHst ach = (AircraftHst) em.createQuery("from AircraftHst a where a.liveUuid=:uuid")
                    .setParameter("uuid", ac.getUuid()).getSingleResult();

            Assert.assertEquals("wrong operation,", Operation.I, ach.getOperation());
            Assert.assertEquals("wrong engine type,", EngineType.piston, ach.getEngineType());
            Assert.assertEquals("wrong type,", "C172", ach.getType());
            Assert.assertEquals("wrong callsign,", "OE-DVK", ach.getCallSign());
        } catch (Exception e) {
            e.printStackTrace();
            if (em != null) {
                em.close();
            }
            Assert.fail(e.getMessage());
        }
    }
}