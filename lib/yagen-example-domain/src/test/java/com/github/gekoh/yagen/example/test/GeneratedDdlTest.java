package com.github.gekoh.yagen.example.test;

import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.ddl.Duplexer;
import com.github.gekoh.yagen.ddl.ObjectType;
import com.github.gekoh.yagen.hibernate.YagenInit;
import junit.framework.Assert;
import org.junit.After;
import org.junit.Test;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hanspeter Duennenberger
 */
public class GeneratedDdlTest {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(GeneratedDdlTest.class);

    private EntityManagerFactory emf;

    @After
    public void cleanup() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @Test
    public void testGeneratedDdl() throws Exception {
        final Map<ObjectType, Map<String, String>> ddlMap = new HashMap<ObjectType, Map<String, String>>();

        DDLGenerator.Profile profile = new DDLGenerator.Profile("default");
        profile.addDuplexer(new Duplexer() {
            public void handleDdl(ObjectType objectType, String objectName, String ddl) {
                Map<String, String> ddlSubMap = ddlMap.get(objectType);
                if (ddlSubMap == null) {
                    ddlSubMap = new HashMap<String, String>();
                    ddlMap.put(objectType, ddlSubMap);
                }
                ddlSubMap.put(objectName, ddl);
            }
        });
        YagenInit.init(profile);

        // create EMF to let Yagen do it's work
        this.emf = Persistence.createEntityManagerFactory("example-domain-test", null);

        // assert operating_resources_htU contains "is null" conditions to update invalidated_at
        assertTriggerContains(ddlMap, "operating_resources_htU", ".*set invalidated_at([^;]+);.*",
         "board_book_uuid=old.board_book_uuid and",
         "(added_operating_resources is null and old.added_operating_resources is null or added_operating_resources=old.added_operating_resources) and");

        // assert board_book_entry_htU does not contain "is null" conditions to update invalidated_at
        assertTriggerContains(ddlMap, "board_book_entry_htU", ".*(set invalidated_at[^;]+;).*",
         "operation <> 'd' and",
          "uuid=old.uuid and");

    }

    private void assertTriggerContains(Map<ObjectType, Map<String, String>> ddlMap, String name, String patternMatchOneGroup, String... contents) {
        String ddlLc = ddlMap.get(ObjectType.TRIGGER).get(name).toLowerCase();
        Matcher m = Pattern.compile(patternMatchOneGroup, Pattern.DOTALL).matcher(ddlLc);
        Assert.assertTrue(m.matches());
        String group1 = m.group(1);
        for (String content : contents) {
            Assert.assertTrue(group1 + "\n==> should contain: " + content, group1.contains(content));
        }
    }

}