package com.github.gekoh.yagen.ddl;

import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.api.NamingStrategy;
import com.github.gekoh.yagen.hibernate.PatchHibernateMappingClasses;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.io.DOMWriter;
import org.dom4j.io.SAXReader;
import org.hibernate.cfg.Configuration;
import org.hibernate.dialect.Dialect;
import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.tool.hbm2ddl.SchemaExport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss
 */
public class DDLGenerator {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(DDLGenerator.class);

    public void writeDDL (Profile profile) {

        SchemaExport export = new SchemaExportFactory().createSchemaExport(profile);
        export.setDelimiter(";");
        export.setFormat(true);
        export.setOutputFile(profile.getOutputFile());
        export.execute(true, false, false, true);

        LOG.info("schema script written to file {}", profile.getOutputFile());
    }

    public static class SchemaExportFactory {
        public SchemaExport createSchemaExport (Profile profile) {
            Configuration cfg;
            if (StringUtils.isNotEmpty(profile.getPersistenceUnitName())) {
//            need to patch the class NumericBooleanType only for oracle until all AURA applications have
//            upgraded hibernate to 3.6.10-Final and the type specifications @Type(type = "org.hibernate.type.NumericBooleanType")
//            are replaced back to @Type(type = "numeric_boolean")
                if (profile.getPersistenceUnitName().contains("oracle")) {
                    try {
                        PatchHibernateMappingClasses.patchNumericBooleanTypeForOracle();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Ejb3Configuration ejb3Configuration = new Ejb3Configuration().configure(profile.getPersistenceUnitName(), null);
                if (ejb3Configuration == null) {
                    throw new IllegalArgumentException("cannot find persistence unit " + profile.getPersistenceUnitName());
                }
                cfg = ejb3Configuration.getHibernateConfiguration();
            }
            else {
                cfg = new Configuration();
            }

            for (Class entityClass : profile.getEntityClasses()) {
                if (cfg.getClassMapping(entityClass.getName()) == null) {
                    cfg.addAnnotatedClass(entityClass);
                }
            }

            try {
                DOMWriter wr = new DOMWriter();
                for (Document persistenceFile : profile.persistenceFiles) {
                    cfg.addDocument(wr.write(persistenceFile));
                    cfg.addProperties(extractProperties(persistenceFile));
                }
            } catch (DocumentException e) {
                LOG.error("cannot set persistence xml file", e);
            }

            return new SchemaExport(cfg);
        }

        private Properties extractProperties(Document persistenceFile) {
            Properties props = new Properties();
            Element elm;

            if ((elm = persistenceFile.getRootElement().element("persistence-unit")) != null &&
                    (elm = elm.element("properties")) != null) {
                for(Element pElm: (Iterable<? extends Element>) elm.elements("property")) {
                    props.put(pElm.attribute("name").getValue(), pElm.attribute("value").getValue());
                }
            }
            return props;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class Profile implements Cloneable {
        private static final List<Profile> PROFILES = new ArrayList<Profile>();

        private String name;
        private String outputFile;
        private String persistenceUnitName;
        private List<Document> persistenceFiles = new ArrayList<Document>();
        private Set<Class> entityClasses = new LinkedHashSet<Class>();
        private List<AddDDLEntry> headerDdls = new ArrayList<AddDDLEntry>();
        private List<AddDDLEntry> addDdls = new ArrayList<AddDDLEntry>();
        private boolean disableFKs = false;
        private boolean noHistory = false;
        private Pattern onlyRenderEntities;
        private Map<String, Map<String, String>> comments;
        private List<Duplexer> duplexers = new ArrayList<Duplexer>();
        private NamingStrategy namingStrategy;

        public static List<Profile> getAllProfiles() {
            return Collections.unmodifiableList(PROFILES);
        }

        public Profile(String name) {
            this.name = name;

            PROFILES.add(this);
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setOutputFile(String outputFile) {
            this.outputFile = outputFile;
        }

        public String getOutputFile() {
            return outputFile;
        }

        public String getPersistenceUnitName() {
            return persistenceUnitName;
        }

        public void setPersistenceUnitName(String persistenceUnitName) {
            this.persistenceUnitName = persistenceUnitName;
        }

        public boolean isDisableFKs() {
            return disableFKs;
        }

        public void setDisableFKs(boolean disableFKs) {
            this.disableFKs = disableFKs;
        }

        public boolean isNoHistory() {
            return noHistory;
        }

        public void setNoHistory(boolean noHistory) {
            this.noHistory = noHistory;
        }

        public Pattern getOnlyRenderEntities() {
            return onlyRenderEntities;
        }

        public void setOnlyRenderEntitiesRegex(String onlyRenderEntitiesRegex) {
            this.onlyRenderEntities = Pattern.compile(onlyRenderEntitiesRegex);
        }

        public Map<String, Map<String, String>> getComments() {
            return comments;
        }

        public void setComments(Map<String, Map<String, String>> comments) {
            this.comments = comments;
        }

        public void addDdl (AddDDLEntry ddlEntry) {
            addDdls.add(ddlEntry);
        }

        public void addDdl (int index, AddDDLEntry ddlEntry) {
            addDdls.add(index, ddlEntry);
        }

        public void addDdlFile (String... ddl) {
            for (String fileName : ddl) {
                addDdls.add(new AddDDLEntry(getUrl(fileName)));
            }
        }

        public void addHeaderDdl (AddDDLEntry... entries) {
            for (AddDDLEntry entry: entries) {
                headerDdls.add(entry);
            }
        }

        public void addHeaderDdlFile (String... ddl) {
            for (String fileName : ddl) {
                headerDdls.add(new AddDDLEntry(getUrl(fileName)));
            }
        }

        private URL getUrl(String resourceOrFileName) {
            URL url = DDLGenerator.class.getResource(resourceOrFileName);
            try {
                if (url == null) {
                    url = new File(resourceOrFileName).toURI().toURL();
                }
            } catch (MalformedURLException e) {
                LOG.error("error finding ddl resource/file named '{}', skipping", resourceOrFileName);
            }
            return url;
        }

        public void addPersistenceClass(Class clazz) {
            entityClasses.add(clazz);
        }

        public void addPersistenceFile (String... persistenceXmlFile) {
            for (String file : persistenceXmlFile) {
                addPersistenceFile(getPersistenceDocument(file));
            }
        }

        public void addPersistenceFile (Document persistenceXml) {
            if (persistenceXml==null) {
                return;
            }
            try {
                Element pu = persistenceXml.getRootElement().element("persistence-unit");
                if(pu != null) {
                    persistenceFiles.add(persistenceXml);

                    for (Object classNode: pu.elements("class")) {
                        entityClasses.add(Class.forName(((Node) classNode).getText()));
                    }
                    for (Object fileNode: pu.elements("mapping-file")) {
                        addPersistenceFile(getPersistenceDocument(((Node) fileNode).getText()));
                    }
                } else {
                    for(Object entityNode : persistenceXml.getRootElement().elements("mapped-superclass")) {
                        entityClasses.add(Class.forName((String) ((Element)entityNode).attribute("class").getData()));
                    }
                    for(Object entityNode : persistenceXml.getRootElement().elements("entity")) {
                        entityClasses.add(Class.forName((String) ((Element)entityNode).attribute("class").getData()));
                    }
                }
            } catch (ClassNotFoundException e) {
                throw new IllegalArgumentException(e);
            }
        }

        public List<AddDDLEntry> getHeaderDdls() {
            return Collections.unmodifiableList(headerDdls);
        }

        public List<AddDDLEntry> getAddDdls() {
            return Collections.unmodifiableList(addDdls);
        }

        public List<AddDDLEntry> getAllDdls() {
            List<AddDDLEntry> allDdls = new ArrayList<AddDDLEntry>(getHeaderDdls());
            allDdls.addAll(getAddDdls());
            return allDdls;
        }

        public Set<Class> getEntityClasses() {
            return entityClasses;
        }

        public void addDuplexer(Duplexer duplexer) {
            duplexers.add(duplexer);
        }

        public void duplex(ObjectType objectType, String objectName, String ddl) {
            for (Duplexer duplexer : duplexers) {
                duplexer.handleDdl(objectType, objectName, ddl);
            }
        }

        public NamingStrategy getNamingStrategy() {
            return namingStrategy != null ? namingStrategy : (namingStrategy = new DefaultNamingStrategy());
        }

        public void setNamingStrategy(NamingStrategy namingStrategy) {
            this.namingStrategy = namingStrategy;
        }

        @Override
        public String toString() {
            return getName();
        }

        @Override
        public Profile clone() throws CloneNotSupportedException {
            Profile profile = (Profile) super.clone();

            profile.name = getName();
            profile.outputFile = getOutputFile();
            profile.persistenceUnitName = getPersistenceUnitName();
            profile.persistenceFiles = new ArrayList<Document>(this.persistenceFiles);
            profile.entityClasses = new LinkedHashSet<Class>(this.entityClasses);
            profile.headerDdls = new ArrayList<AddDDLEntry>(this.headerDdls);
            profile.addDdls = new ArrayList<AddDDLEntry>(this.addDdls);
            profile.disableFKs = isDisableFKs();
            profile.noHistory = isNoHistory();
            profile.onlyRenderEntities = getOnlyRenderEntities();
            profile.comments = this.comments != null ? new HashMap<String, Map<String, String>>(this.comments) : null;
            profile.duplexers = new ArrayList<Duplexer>(this.duplexers);

            return profile;
        }
    }

    private static Document getPersistenceDocument (String persistenceXml) {
        if (StringUtils.isEmpty(persistenceXml)) {
            LOG.warn("empty persistence.xml or orm file specified");
            return null;
        }
        try {
            InputStream resource = DDLGenerator.class.getResourceAsStream("/" + persistenceXml);
            if (resource == null) {
                resource = new FileInputStream(persistenceXml);
            }
            return new SAXReader().read(resource);
        } catch (Exception e) {
            throw new IllegalArgumentException("unable to find resource "+persistenceXml+" in classpath or filesystem", e);
        }
    }

    public static String read(Reader reader) {
        StringWriter wr = new StringWriter();
        char[] buf = new char[1024];
        int read;
        try {

            while ((read=reader.read(buf)) > -1) {
                wr.write(buf, 0, read);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return wr.toString();
    }

    @SuppressWarnings("UnusedDeclaration")
    public static class AddDDLEntry {
        protected URL url;
        protected String ddlText;
        protected Reader reader;

        public AddDDLEntry(Reader reader) {
            this.reader = reader;
        }

        public AddDDLEntry(URL url) {
            this.url = url;
        }

        public AddDDLEntry(String ddlText) {
            this.ddlText = ddlText;
        }

        public boolean isReader() {
            return reader != null;
        }

        public String getDdlText(Dialect dialect) {
            if (ddlText != null) {
                return ddlText;
            }

            Reader rd = reader;

            if (rd == null) {
                try {
                    rd = new InputStreamReader(url.openStream());
                } catch (FileNotFoundException e) {
                    return "";
                } catch (IOException e) {
                    throw new IllegalArgumentException(e);
                }
            }

            return read(rd);
        }

        @Override
        public String toString() {
            if (url != null) {
                return url.toString();
            }
            return "dynamic content";
        }
    }

    public static class AddTemplateDDLEntry extends AddDDLEntry {
        private String text;

        public AddTemplateDDLEntry(URL url) {
            super(url);
        }

        @Override
        public String getDdlText(Dialect dialect) {
            if (text == null) {
                String template = super.getDdlText(dialect);
                VelocityContext ctx = new VelocityContext();
                ctx.put("dialect", dialect);

                StringWriter wr = new StringWriter();
                Velocity.evaluate(ctx, wr, url.toString(), template);
                text = wr.toString();
            }
            return text;
        }
    }
}
