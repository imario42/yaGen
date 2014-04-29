package com.github.gekoh.yagen.ddl;

import com.github.gekoh.yagen.ddl.comment.CommentsDDLGenerator;
import com.github.gekoh.yagen.hibernate.PatchGlue;
import com.sun.javadoc.RootDoc;
import org.hibernate.ejb.Ejb3Configuration;

/**
 * @author Georg Kohlweiss
 */
public class CoreDDLGenerator extends CommentsDDLGenerator {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CoreDDLGenerator.class);

    private static final String PARAM_PROFILE_NAME = "--profile-name";
    private static final String PARAM_PROFILE_PROVIDER_CLASS = "--profile-provider-class";
    private static final String PARAM_PERSISTENCE_UNIT_NAME = "--persistence-unit-name";
    private static final String PARAM_PERSISTENCE_XML_LIST = "--persistence-xml-list";
    private static final String PARAM_OUTPUT_FILENAME = "--output-file";
    private static final String PARAM_HBMXML_OUTPUT_FILENAME = "--hbm-xml-output-file";
    private static final String PARAM_HEADER_DDLS_LIST = "--header-ddl-list";
    private static final String PARAM_ADDITIONAL_DDLS_LIST = "--additional-ddl-list";
    private static final String PARAM_DISABLE_FKS = "--disable-foreign-keys";
    private static final String PARAM_REGEX_RENDER_ONLY_ENTITIES = "--only-entities-regex";
    private static final String PARAM_NO_HISTORY_GENERATION = "--no-history";

    private static DDLGenerator.Profile PROFILE;

    private static String profileName;
    private static ProfileProvider profileProvider;

    @SuppressWarnings("UnusedDeclaration")
    public static boolean start(final RootDoc root) {

        parseOptions(root.options());

        try {
            PatchGlue.init(PROFILE);
        } catch (Exception e) {
            throw new IllegalStateException("cannot init patches for ddl generator", e);
        }

/*
        try {
            PROFILE.addDuplexer(new com.github.gekoh.yagen.ddl.AuditTriggerDuplexer(new java.io.File("auditTriggers_"+ PROFILE.getName()+".ddl.sql"), "\n/\n"));
        } catch (IOException e) {
            e.printStackTrace();
        }
*/

        CommentsDDLGenerator.start(root);
        PROFILE.setComments(getComments());

        DDLGenerator generator = new DDLGenerator();

        generator.writeDDL(PROFILE);

        return true;
    }

    @SuppressWarnings("UnusedDeclaration")
    public static int optionLength(String option) {
        if (option.equals(PARAM_OUTPUT_FILENAME) ||
                option.equals(PARAM_HBMXML_OUTPUT_FILENAME) ||
                option.equals(PARAM_PERSISTENCE_UNIT_NAME) ||
                option.equals(PARAM_PERSISTENCE_XML_LIST) ||
                option.equals(PARAM_HEADER_DDLS_LIST) ||
                option.equals(PARAM_ADDITIONAL_DDLS_LIST) ||
                option.equals(PARAM_PROFILE_NAME) ||
                option.equals(PARAM_PROFILE_PROVIDER_CLASS) ||
                option.equals(PARAM_REGEX_RENDER_ONLY_ENTITIES)) {
            return 2;
        } else if (option.equals(PARAM_DISABLE_FKS) ||
                option.equals(PARAM_NO_HISTORY_GENERATION)) {
            return 1;
        }
        return 0;
    }

    private static void parseOptions(String[][] options) {
        for (String[] opt : options) {
            try {
                if (opt[0].equals(PARAM_PROFILE_NAME)) {
                    profileName = opt[1];
                } else if (opt[0].equals(PARAM_PROFILE_PROVIDER_CLASS)) {
                    try {
                        profileProvider = (ProfileProvider) Class.forName(opt[1]).newInstance();
                    } catch (Exception e) {
                        LOG.error("cannot instantiate profile provider class {}", opt[1]);
                    }
                } else if (opt[0].equals(PARAM_OUTPUT_FILENAME)) {
                    getProfile().setOutputFile(opt[1]);
                } else if (opt[0].equals(PARAM_PERSISTENCE_UNIT_NAME)) {
                    getProfile().setPersistenceUnitName(opt[1]);
                } else if (opt[0].equals(PARAM_PERSISTENCE_XML_LIST)) {
                    getProfile().addPersistenceFile(opt[1].split(";[\\s]*"));
                } else if (opt[0].equals(PARAM_HEADER_DDLS_LIST)) {
                    getProfile().addHeaderDdlFile(opt[1].split(";[\\s]*"));
                } else if (opt[0].equals(PARAM_ADDITIONAL_DDLS_LIST)) {
                    getProfile().addDdlFile(opt[1].split(";[\\s]*"));
                } else if (opt[0].equals(PARAM_DISABLE_FKS)) {
                    getProfile().setDisableFKs(true);
                } else if (opt[0].equals(PARAM_NO_HISTORY_GENERATION)) {
                    getProfile().setNoHistory(true);
                } else if (opt[0].equals(PARAM_REGEX_RENDER_ONLY_ENTITIES)) {
                    getProfile().setOnlyRenderEntitiesRegex(opt[1]);
                }
            } catch (NullPointerException e) {
                throw new IllegalArgumentException("profile name must be the first argument");
            }
        }
    }

    private static DDLGenerator.Profile getProfile() {
        if (PROFILE == null) {
            if (profileProvider != null) {
                PROFILE = profileProvider.getProfile(profileName);
            }
            if (PROFILE == null) {
                PROFILE = new DDLGenerator.Profile(profileName);
            }
        }
        return PROFILE;
    }

}
