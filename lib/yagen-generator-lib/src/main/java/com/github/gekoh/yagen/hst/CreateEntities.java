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
package com.github.gekoh.yagen.hst;

import com.github.gekoh.yagen.api.Constants;
import com.github.gekoh.yagen.api.DefaultNamingStrategy;
import com.github.gekoh.yagen.api.NamingStrategy;
import com.github.gekoh.yagen.api.TemporalEntity;
import com.github.gekoh.yagen.ddl.CreateDDL;
import com.github.gekoh.yagen.ddl.DDLGenerator;
import com.github.gekoh.yagen.util.MappingUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;
import org.hibernate.annotations.Type;

import javax.persistence.AttributeOverride;
import javax.persistence.AttributeOverrides;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Embedded;
import javax.persistence.Entity;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Georg Kohlweiss
 */
public class CreateEntities {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(CreateEntities.class);

    public static final String HISTORY_ENTITY_SUFFIX = "Hst";

    public static void main (String[] args) {
        if (args == null || args.length<1) {
            LOG.error("parameters: <java-src-output-dir> <base-classes-package-name> <persistence-xml-file-path> <orm2.0-file-out-path> [<orm1.0-file-out-path>]");
            return;
        }
        CreateEntities createEntities = new CreateEntities(new File(args[0]));

        createEntities.writeBaseClasses(args[1]);

        String[] persistenceXmlFiles = args[2].split(";[\\s]*");
        createEntities.processBaseEntityClasses(
                args[1],
                scanEntityClasses(persistenceXmlFiles));

        File orm20OutFile = new File(args[3]);
        createEntities.writeOrmFile(orm20OutFile, args[1], "2.0");

        if (args.length > 4) {
            File orm10OutFile = new File(args[4]);
            createEntities.writeOrmFile(orm10OutFile, args[1], "1.0");
        }
    }

    private File outputDirectory;

    private String template = readClasspathResource("HstTemplate.java.vm");

    private List<String> createdMappedSuperClasses = new ArrayList<String>();
    private List<String> createdEntityClasses = new ArrayList<String>();

    private NamingStrategy namingStrategy = new DefaultNamingStrategy();

    public CreateEntities(File outputDirectory) {
        this.outputDirectory = outputDirectory;
        if (!outputDirectory.exists() && !outputDirectory.mkdirs()) {
            throw new IllegalArgumentException("cannot create directory '" + outputDirectory.getAbsolutePath() + "'");
        }
        Velocity.init();
    }

    public void processBaseEntityClasses (String baseClassPackageName, Collection<Class> baseEntities) {
        Map<Class, List<AccessibleObject>> inverseFKs = getInverseFKs (baseEntities);

        for (Class baseEntity : baseEntities) {
            Class tableEntity = getTableEntityClass(baseEntity);
            if (baseEntity.isAnnotationPresent(MappedSuperclass.class) ||
                    (tableEntity != null && tableEntity.isAnnotationPresent(TemporalEntity.class))) {

                String className = createHistoryEntity(baseClassPackageName, baseEntity, new StringReader(template), inverseFKs.get(baseEntity));

                if (baseEntity.isAnnotationPresent(MappedSuperclass.class)) {
                    createdMappedSuperClasses.add(className);
                }
                else {
                    createdEntityClasses.add(className);
                }
            }

            for (AccessibleObject accessibleObject : getFieldsAndMethods(baseEntity)) {
                if (accessibleObject.isAnnotationPresent(TemporalEntity.class) && accessibleObject.isAnnotationPresent(JoinTable.class)) {
                    String className = createHistoryEntity(baseClassPackageName, accessibleObject, new StringReader(template));
                    createdEntityClasses.add(className);
                }
            }
        }
    }

    private Class getTableEntityClass(Class baseClass) {
        while (baseClass != null && !baseClass.isAnnotationPresent(Table.class)) {
            baseClass = baseClass.getSuperclass();
        }
        return baseClass;
    }

    private Map<Class, List<AccessibleObject>> getInverseFKs(Collection<Class> baseEntities) {
        Map<Class, List<AccessibleObject>> inverseFKs = new HashMap<Class, List<AccessibleObject>>();

        for (Class baseEntity : baseEntities) {
            Set<AccessibleObject> fieldOrMethods = getFieldsAndMethods(baseEntity);

            for (AccessibleObject fieldOrMethod : fieldOrMethods) {
                if (fieldOrMethod.isAnnotationPresent(OneToMany.class)) {
                    OneToMany o2m = fieldOrMethod.getAnnotation(OneToMany.class);
                    Class<?> mappedClass = MappingUtils.determineTargetEntity(fieldOrMethod, o2m.targetEntity());

                    if (fieldOrMethod.isAnnotationPresent(JoinColumn.class) &&
                            !hasColumnDeclared(mappedClass, fieldOrMethod.getAnnotation(JoinColumn.class).name())) {
                        List<AccessibleObject> fks = inverseFKs.get(mappedClass);

                        if (fks == null) {
                            inverseFKs.put(mappedClass, fks = new ArrayList<AccessibleObject>());
                        }

                        fks.add(fieldOrMethod);
                    }
                }
            }
        }

        return inverseFKs;
    }

    private Set<AccessibleObject> getFieldsAndMethods(Class clazz) {
        Set<AccessibleObject> fieldOrMethods = new HashSet<AccessibleObject>(Arrays.asList(clazz.getDeclaredFields()));
        fieldOrMethods.addAll(Arrays.asList(clazz.getDeclaredMethods()));

        return fieldOrMethods;
    }

    private boolean hasColumnDeclared(Class clazz, String columnName) {
        for (AccessibleObject fieldOrMethod : getFieldsAndMethods(clazz)) {
            if (fieldOrMethod.isAnnotationPresent(Column.class) &&
                    fieldOrMethod.getAnnotation(Column.class).name().compareToIgnoreCase(columnName) == 0) {
                return true;
            }
            if (fieldOrMethod.isAnnotationPresent(JoinColumn.class) &&
                    fieldOrMethod.getAnnotation(JoinColumn.class).name().compareToIgnoreCase(columnName) == 0) {
                return true;
            }
        }
        return false;
    }

    private String createHistoryEntity(String baseClassPackageName,
                                       AccessibleObject fieldOrMethod,
                                       Reader template) {
        TemporalEntity temporalEntity = fieldOrMethod.getAnnotation(TemporalEntity.class);
        JoinTable joinTable = fieldOrMethod.getAnnotation(JoinTable.class);
        Class targetEntity = null;
        Class declaringClass;
        String packageName;
        String hstEntityClassSimpleName = toCamelCase(joinTable.name()) + HISTORY_ENTITY_SUFFIX;
        hstEntityClassSimpleName = hstEntityClassSimpleName.substring(0, 1).toUpperCase() + hstEntityClassSimpleName.substring(1);

        if (fieldOrMethod.isAnnotationPresent(ManyToMany.class)) {
            targetEntity = MappingUtils.determineTargetEntity(fieldOrMethod, fieldOrMethod.getAnnotation(ManyToMany.class).targetEntity());
        }
        else if (fieldOrMethod.isAnnotationPresent(ManyToOne.class)) {
            targetEntity = MappingUtils.determineTargetEntity(fieldOrMethod, fieldOrMethod.getAnnotation(ManyToOne.class).targetEntity());
        }
        else {
            throw new UnsupportedOperationException("when generating history entity for relation on " + fieldOrMethod);
        }

        if (fieldOrMethod instanceof Field) {
            declaringClass = ((Field) fieldOrMethod).getDeclaringClass();
        }
        else {
            declaringClass = ((Method) fieldOrMethod).getDeclaringClass();
        }
        packageName = declaringClass.getPackage().getName();

        List<FieldInfo> fieldInfos = new ArrayList<FieldInfo>();

//      add join columns to both sides of the relation (assumes we have only one each)
        fieldInfos.add(getIdFieldInfo(declaringClass, getFieldNameFromReferencingClassName(declaringClass.getSimpleName()), joinTable.joinColumns()[0].name()));
        fieldInfos.add(getIdFieldInfo(targetEntity, getFieldNameFromReferencingClassName(targetEntity.getSimpleName()), joinTable.inverseJoinColumns()[0].name()));

        return createHistoryEntity(
                baseClassPackageName,
                packageName,
                hstEntityClassSimpleName,
                temporalEntity.historyTableName(),
                null,
                null,
                template,
                fieldInfos);
    }

    private String getFieldNameFromReferencingClassName(String classSimpleName) {
        return classSimpleName.substring(0, 1).toLowerCase() + classSimpleName.substring(1);
    }

    private String createHistoryEntity (String baseClassPackageName,
                                        Class baseEntity,
                                        Reader template,
                                        List<AccessibleObject> inverseFKs) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>(convertFields(baseEntity));
        if (inverseFKs != null) {
            fields.addAll(convertInverseFKs(inverseFKs));
        }

        TemporalEntity temporalEntity = (TemporalEntity) baseEntity.getAnnotation(TemporalEntity.class);

        return createHistoryEntity(
                baseClassPackageName,
                baseEntity.getPackage().getName(),
                baseEntity.getSimpleName() + HISTORY_ENTITY_SUFFIX,
                temporalEntity != null ? temporalEntity.historyTableName() : null,
                temporalEntity != null ? namingStrategy.classToTableShortName(baseEntity.getName()) : null,
                baseEntity,
                template,
                fields);
    }

    private String createHistoryEntity (String baseClassPackageName,
                                        String hstEntityClassPackageName,
                                        String hstEntityClassSimpleName,
                                        String historyTableName,
                                        String historyTableShortName,
                                        Class baseEntity,
                                        Reader template,
                                        List<FieldInfo> fields) {
        String hstEntityClassName = hstEntityClassPackageName + "." + hstEntityClassSimpleName;
        Class baseEntitySuperClass = baseEntity != null && !baseEntity.getSuperclass().equals(Object.class) ? baseEntity.getSuperclass() : null;
        String classAnnotations = "";

        VelocityContext context = new VelocityContext();
        context.put("baseClassPackageName", baseClassPackageName);
        context.put("entityClassPackageName", hstEntityClassPackageName);
        context.put("entityClassSimpleName", hstEntityClassSimpleName);

        if (historyTableName != null) {
            context.put("tableName", historyTableName);
            if (historyTableShortName != null) {
                context.put("tableShortName", CreateDDL.getHistTableShortNameFromLiveTableShortName(historyTableShortName));
                classAnnotations = String.format("@com.github.gekoh.yagen.api.Table(shortName=%s.%s)\n", hstEntityClassSimpleName, CreateDDL.STATIC_FIELD_TABLE_NAME_SHORT);
            }
        }

        context.put("fieldInfoList", fields);

        if (baseEntity != null && baseEntity.isAnnotationPresent(MappedSuperclass.class)) {
            context.put("classAnnotation", classAnnotations + "@javax.persistence.MappedSuperclass");
        }
        else if (baseEntity != null && baseEntity.isAnnotationPresent(Entity.class)) {
            String value = classAnnotations + "@javax.persistence.Entity";
            String entityName = ((Entity) baseEntity.getAnnotation(Entity.class)).name();
            if (StringUtils.isNotEmpty(entityName)) {
                value += "(name = \"" + entityName + HISTORY_ENTITY_SUFFIX + "\")";
            }
            context.put("classAnnotation", value);
        }

//        Inheritance used, baseEntity is superclass
        if (baseEntity != null && baseEntity.isAnnotationPresent(Inheritance.class)) {
            DiscriminatorColumn discriminatorColumn = (DiscriminatorColumn) baseEntity.getAnnotation(DiscriminatorColumn.class);
            String classAnnotation = context.get("classAnnotation") + "\n" +
                    "@javax.persistence.Inheritance(strategy=javax.persistence.InheritanceType." + ((Inheritance) baseEntity.getAnnotation(Inheritance.class)).strategy().name() + ")";
            if (discriminatorColumn != null) {
                classAnnotation += "\n@javax.persistence.DiscriminatorColumn(name=\"" + discriminatorColumn.name() + "\", length=" + discriminatorColumn.length() + ")";
            }
            context.put("classAnnotation", classAnnotation);
        }
//        Inheritance used, baseEntity is subclass
        else if (baseEntity != null && baseEntity.isAnnotationPresent(DiscriminatorValue.class)) {
            context.put("classAnnotation", context.get("classAnnotation") + "\n" +
                    "@javax.persistence.DiscriminatorValue(\""+((DiscriminatorValue) baseEntity.getAnnotation(DiscriminatorValue.class)).value()+"\")");
        }

        if (baseEntitySuperClass != null) {
            context.put("entitySuperClassName", baseEntitySuperClass.getName() + "Hst");
        }
        else if (baseEntity != null) {
            String name;
            int length;
            PrimaryKeyJoinColumn pkJC = (PrimaryKeyJoinColumn) baseEntity.getAnnotation(PrimaryKeyJoinColumn.class);
            if (pkJC != null) {
                name = pkJC.name();
                length = Constants.UUID_LEN;
            }
            else {
                Column column = getUuidColumn(baseEntity);
                name = column.name();
                length = column.length();
            }
            context.put("baseEntityUuidColumnName", name.toLowerCase());
            context.put("baseEntityUuidColumnLength", length);
        }

        evaluate2JavaFile(hstEntityClassName, template, context);

        return hstEntityClassName;
    }

    private void writeBaseClasses(String baseClassPackageName) {
        VelocityContext context = new VelocityContext();
        context.put("baseClassPackageName", baseClassPackageName);

        evaluate2JavaFile(baseClassPackageName+".BaseEntity", new StringReader(readClasspathResource("BaseEntity.java.vm")), context);
        evaluate2JavaFile(baseClassPackageName+".Operation", new StringReader(readClasspathResource("Operation.java.vm")), context);
    }

    private void evaluate2JavaFile(String entityClassName, Reader template, VelocityContext context) {
        try {
            File outFile = new File(outputDirectory, entityClassName.replace('.', File.separatorChar) + ".java");
            if (!outFile.getParentFile().exists() && !outFile.getParentFile().mkdirs()) {
                throw new IllegalArgumentException("error creating output directory for generated sources");
            }
            FileWriter wr = new FileWriter(outFile);
            Velocity.evaluate(context, wr, CreateEntities.class.getSimpleName()+"#createHistoryEntity", template);
            wr.close();
        } catch (IOException e) {
            throw new IllegalStateException("cannot create output file", e);
        }
    }

    private List<FieldInfo> convertFields(Class baseEntity) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>();

        for (Field field : baseEntity.getDeclaredFields()) {
            FieldInfo fi;
            Class type = field.getType();
            String name = field.getName();
            Column column = field.getAnnotation(Column.class);
            if (field.isAnnotationPresent(Embedded.class)) {
                if (field.isAnnotationPresent(AttributeOverride.class)) {
                    fi = new FieldInfo(type, name, field.getAnnotation(AttributeOverride.class));
                }
                else {
                    fi = new FieldInfo(type, name, field.getAnnotation(AttributeOverrides.class));
                }
            }
            else if (field.isAnnotationPresent(Enumerated.class)) {
                fi = new FieldInfo(type, name, true, column);
            }
            else if (column != null) {
                if (type.isPrimitive()) {
                    if (type.equals(Boolean.TYPE)) {
                        type = Boolean.class;
                    }
                    else if (type.equals(Long.TYPE)) {
                        type = Long.class;
                    }
                    else if (type.equals(Integer.TYPE)) {
                        type = Integer.class;
                    }
                    else if (type.equals(Short.TYPE)) {
                        type = Short.class;
                    }
                    else if (type.equals(Byte.TYPE)) {
                        type = Byte.class;
                    }
                    else if (type.equals(Double.TYPE)) {
                        type = Double.class;
                    }
                    else if (type.equals(Float.TYPE)) {
                        type = Float.class;
                    }
                    else if (type.equals(Character.TYPE)) {
                        type = Character.class;
                    }
                }
                fi = new FieldInfo(type, name, false, column);
            }
            else if ((field.isAnnotationPresent(ManyToOne.class) && !field.isAnnotationPresent(JoinTable.class)) ||
                    (field.isAnnotationPresent(OneToOne.class) && StringUtils.isEmpty(field.getAnnotation(OneToOne.class).mappedBy()))) {
                String columnName = field.isAnnotationPresent(JoinColumn.class) ? field.getAnnotation(JoinColumn.class).name() : field.getName();
                fi = getIdFieldInfo(type, name, columnName);
            }
            else {
                continue;
            }
            if (field.isAnnotationPresent(Type.class)) {
                fi.addAnnotation(field.getAnnotation(Type.class));
            }
            fields.add(fi);
        }

        return fields;
    }

    private FieldInfo getIdFieldInfo (Class type, String namePrefix, String columnName) {
        AccessibleObject id = getIdFieldOrMethod(type);
        Column column = id.getAnnotation(Column.class);
        String suffix;
        if (id instanceof Field) {
            type = ((Field) id).getType();
            suffix = ((Field) id).getName().substring(0, 1).toUpperCase() + ((Field) id).getName().substring(1);
        }
        else {
            type = ((Method) id).getReturnType();
            suffix = ((Method) id).getName().replace("get", "").replace("set", "");
        }
        String name = namePrefix + suffix;
        return new FieldInfo(type, name, columnName, column.length());
    }

    private List<FieldInfo> convertInverseFKs (List<AccessibleObject> inverseFKfieldsOrMethods) {
        List<FieldInfo> fields = new ArrayList<FieldInfo>();
        for (AccessibleObject inverseFK : inverseFKfieldsOrMethods) {
            String joinColName = inverseFK.getAnnotation(JoinColumn.class).name();
            fields.add(new FieldInfo(String.class, toCamelCase("INV_FK_"+joinColName), joinColName, Constants.UUID_LEN));
        }
        return fields;
    }

    private static String toCamelCase(String columnName) {
        StringBuilder s = new StringBuilder();
        int idx=-1, lastIdx=0;

        while ((idx = columnName.indexOf('_', idx+1)) >= 0) {
            if (lastIdx > 0) {
                s.append(columnName.substring(lastIdx+1, lastIdx+2).toUpperCase());
                s.append(columnName.substring(lastIdx+2, idx).toLowerCase());
            }
            else {
                s.append(columnName.substring(lastIdx, idx).toLowerCase());
            }
            lastIdx = idx;
        }

        if (lastIdx > 0) {
            s.append(columnName.substring(lastIdx+1, lastIdx+2).toUpperCase());
            s.append(columnName.substring(lastIdx+2).toLowerCase());
        }
        else {
            s.append(columnName.substring(lastIdx).toLowerCase());
        }

        return s.toString();
    }

    private Column getUuidColumn(Class entityClass) {
        AccessibleObject idFieldOrMethod = getIdFieldOrMethod(entityClass);

        if (idFieldOrMethod == null) {
            throw new IllegalStateException("cannot find field or method with @Id for entity class " + entityClass.getName());
        }

        return idFieldOrMethod.getAnnotation(Column.class);
    }

    private AccessibleObject getIdFieldOrMethod(Class entityClass) {
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field;
            }
        }
        for (Method method : entityClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Id.class)) {
                return method;
            }
        }
        return entityClass.getSuperclass() != null ? getIdFieldOrMethod(entityClass.getSuperclass()) : null;
    }

    public void writeOrmFile (File ormOutFile, String baseClassPackageName, String ormVersion) {
        if (!ormOutFile.getParentFile().exists() && !ormOutFile.getParentFile().mkdirs()) {
            throw new IllegalArgumentException(
                    "Could not create the output directory for the ORM file '" + ormOutFile.getAbsolutePath() + "'.");
        }

        VelocityContext context = new VelocityContext();
        context.put("baseClassPackageName", baseClassPackageName);
        context.put("mappedSuperClassNames", createdMappedSuperClasses);
        context.put("entityClassNames", createdEntityClasses);
        context.put("orm-version", ormVersion);

        try {
            FileWriter wr = new FileWriter(ormOutFile);
            Velocity.evaluate(context, wr,
                    CreateEntities.class + "#writeOrmFile", readClasspathResource("entities.orm.xml.vm"));
            wr.close();
        } catch (IOException e) {
            LOG.error("error writing to orm file", e);
        }
    }

    /**
     * <p>Extracts all entity classes in the given set of persistence.xml files an returns a collection thereof.</p>
     * @param persistenceXmlFiles set of persistence.xml files that you want to scan
     * @return extracted entity classes
     */
    private static Collection<Class> scanEntityClasses(String... persistenceXmlFiles) {
        DDLGenerator.Profile profile = new DDLGenerator.Profile(null);
        profile.addPersistenceFile(persistenceXmlFiles);
        return profile.getEntityClasses();
    }

    /**
     * <p>Reads an entire file from the classpath using UTF-8 encoding.</p>
     * @param filename the name of the file in the classpath
     * @return the contents of the file
     */
    private static String readClasspathResource(String filename) {
        InputStream is = CreateEntities.class.getResourceAsStream(filename);
        if (is == null) {
            throw new IllegalArgumentException(
                    "Could not find the given file '" + filename + "' in the classpath.");
        }
        
        return readContents(is);
    }

    private static String readContents(InputStream is) {
        StringWriter wr = new StringWriter();
        
        try {
            Reader rd = new InputStreamReader(is, "UTF-8");
            
            char[] buf = new char[1024];
            int read;
            while ((read = rd.read(buf)) > 0) {
                wr.write(buf, 0, read);
            }
            
        } catch (Exception e) {
            LOG.error("An error occurred while reading the template file.", e);
        }
        
        return wr.toString();
    }

    public static class FieldInfo {

        private static Pattern NULLABLE_PATTERN = Pattern.compile("nullable=((true)|(false))");
        private static Pattern UNIQUE_PATTERN = Pattern.compile("unique=((true)|(false))");
        private static Pattern STRING_ATTR_PATTERN = Pattern.compile("[\\(|\\s*](name|columnDefinition|type)=([^)\\s*]*)[,\\)]");

        private Class type;
        private String name;
        private String columnName;
        private String columnAnnotation;

        private boolean isEnum;
        private boolean isEmbedded;

        public FieldInfo(Class type, String name, AttributeOverrides overrides) {
            this.type = type;
            this.name = name;
            this.columnName = null;
            isEnum = false;
            isEmbedded = true;

            StringBuilder columnAnnotation = new StringBuilder();
            if (overrides != null) {
                columnAnnotation.append(formatAnnotation(overrides));
            }

            this.columnAnnotation = concatOverrides(columnAnnotation.toString(), getAttributeOverrides(type).values());
        }

        public FieldInfo(Class type, String name, AttributeOverride override) {
            this(type, name, (AttributeOverrides)null);

            Map<String, String> overrides = new LinkedHashMap<String, String>();
            if (override != null) {
                addAttributeOverride(overrides, formatAnnotation(override));
            }
            addAttributeOverrides(overrides, "", type);

            this.columnAnnotation = concatOverrides("", overrides.values());
        }

        public FieldInfo(Class type, String name, boolean anEnum, Column column) {
            this.type = type;
            this.name = name;
            this.columnName = column.name().toLowerCase();
            isEnum = anEnum;
            isEmbedded = false;
            this.columnAnnotation = formatAnnotation(column);
        }

        public FieldInfo(Class type, String name, String columnName, int columnLength) {
            this.type = type;
            this.name = name;
            this.columnName = columnName.toLowerCase();
            columnAnnotation = "@" + Column.class.getName() + "(name = \"" + escapeAttributeValue(columnName) + "\", length = " + columnLength + ")";
            isEnum = false;
            isEmbedded = false;
        }

        public FieldInfo(Class type, String name, String columnName, boolean nullable, String typeAnnotation) {
            this.type = type;
            this.name = name;
            this.columnName = columnName.toLowerCase();
            columnAnnotation =
                    "@" + Column.class.getName() + "(name = \"" + escapeAttributeValue(columnName) + "\", nullable = " + nullable + ")" + (typeAnnotation != null ? " @" + Type.class.getName() + "(type = \"" + typeAnnotation + "\")" : "");
            isEnum = false;
            isEmbedded = false;
        }

        public Class getType() {
            return type;
        }

        public String getName() {
            return name;
        }

        public String getColumnName() {
            return columnName;
        }

        public String getColumnAnnotation() {
            return columnAnnotation;
        }

        public boolean isEnum() {
            return isEnum;
        }

        public boolean isEmbedded() {
            return isEmbedded;
        }

        public boolean isBooleanType() {
            return type == Boolean.class || type == boolean.class;
        }

        public void addAnnotation(Annotation annotation) {
            columnAnnotation = (columnAnnotation != null ? columnAnnotation+"\n    " : "" ) + formatAnnotation(annotation);
        }

        private static String formatAnnotation (Annotation annotation) {
            String a = annotation.toString();
            StringBuilder result = new StringBuilder();

            // wrap string value of attribute "name" into double quotes as needed for java code
            Matcher m = STRING_ATTR_PATTERN.matcher(a);
            int idx = 0;
            while (m.find(idx)) {
                result.append(a.substring(idx, m.start(2)));
                result.append("\"").append(escapeAttributeValue(m.group(2))).append("\"");
                result.append(a.substring(m.end(2), m.end()));
                idx = m.end();
            }
            result.append(a.substring(idx));

            a = result.toString();
            result = new StringBuilder();

            // remove empty attributes like (columnDefinition=)
            m = Pattern.compile("\\(?(,?\\s*[A-Za-z]*=)[,|\\)]").matcher(a);
            idx = 0;
            while (m.find(idx)) {
                result.append(a.substring(idx, m.start(1)));
                idx = m.end(1);
            }
            result.append(a.substring(idx));

            // set nullable=true
            m = NULLABLE_PATTERN.matcher(result);
            idx = 0;
            while (m.find(idx)) {
                if (m.group(1).equals("false")) {
                    result.replace(m.start(1), m.end(1), "true");
                }
                idx = m.start(1)+1;
                m = NULLABLE_PATTERN.matcher(result);
            }

            // set unique=false
            m = UNIQUE_PATTERN.matcher(result);
            idx = 0;
            while (m.find(idx)) {
                if (m.group(1).equals("true")) {
                    result.replace(m.start(1), m.end(1), "false");
                }
                idx = m.start(1)+1;
                m = UNIQUE_PATTERN.matcher(result);
            }

            return result.toString().replaceAll("=\\[([^\\]]*)\\]", "={$1}");
        }

        private static String escapeAttributeValue(String value) {
            return value.replace("\"", "\\\"");
        }

        private static final Pattern ATTR_OVERR_NAME = Pattern.compile("AttributeOverride\\s*\\(\\s*name\\s*=\\s*\"([^\"]+)\"");

        private static String addNamePrefixToAttributeOverride (String annotation, String prefix) {
            Matcher matcher = ATTR_OVERR_NAME.matcher(annotation);
            if (matcher.find()) {
                return annotation.substring(0, matcher.start(1)) + prefix + annotation.substring(matcher.start(1));
            }
            throw new IllegalArgumentException("no AttributeOverride found in '"+annotation+"'");
        }

        private static String getNameFromAttributeOverride (String attributeOverride) {
            Matcher matcher = ATTR_OVERR_NAME.matcher(attributeOverride);
            if (matcher.find()) {
                return matcher.group(1);
            }
            throw new IllegalArgumentException("no name of AttributeOverride found in '"+attributeOverride+"'");
        }

        /**
         * generates a collection of javax.persistence.AttributeOverride annotations needed to override nullable=false
         * columns since this is not allowed in *Hst-Entities
         *
         * @param type embeddable class with non-nullable fields
         * @return collection of javax.persistence.AttributeOverride annotations needed to set columns to nullable=true
         */
        private static Map<String, String> getAttributeOverrides(Class type) {
            Map<String, String> overrides = new LinkedHashMap<String, String>();
            addAttributeOverrides(overrides, "", type);
            return overrides;
        }

        private static void addAttributeOverrides(Map<String, String> overrides, String path, Class type) {
            for (Field field : type.getDeclaredFields()) {
                String curPath = path + field.getName() + ".";
                Column column;
                if (field.isAnnotationPresent(AttributeOverride.class)) {
                    addAttributeOverride(overrides, addNamePrefixToAttributeOverride(
                            formatAnnotation(field.getAnnotation(AttributeOverride.class)),
                            curPath));
                }
                else if (field.isAnnotationPresent(AttributeOverrides.class)) {
                    for (AttributeOverride attributeOverride : field.getAnnotation(AttributeOverrides.class).value()) {
                        addAttributeOverride(overrides, addNamePrefixToAttributeOverride(
                                formatAnnotation(attributeOverride),
                                curPath));
                    }
                }
                else if (((column = field.getAnnotation(Column.class)) != null && (!column.nullable() || column.unique())) ||
                        (field.isAnnotationPresent(Basic.class) && !field.getAnnotation(Basic.class).optional())) {
                    String columnName = column != null ? column.name() : field.getName();
                    int length = column != null ? column.length() : 255;

                    String override = "@javax.persistence.AttributeOverride(name=\"" + path + field.getName() + "\", column=" +
                            "@javax.persistence.Column(name=\"" + columnName + "\", length=" + length + ", nullable=true, unique=false))";

                    addAttributeOverride(overrides, override);
                }

                if (field.isAnnotationPresent(Embedded.class)) {
                    addAttributeOverrides(overrides, curPath, field.getType());
                }
            }
        }

        private static void addAttributeOverride(Map<String, String> overrides, String attributeOverride) {
            String name = getNameFromAttributeOverride(attributeOverride);
            if (!overrides.containsKey(name)) {
                overrides.put(name, attributeOverride);
            }
        }

        /**
         * merges given collection of javax.persistence.AttributeOverride elements into an optionally existing
         * javax.persistence.AttributeOverrides annotation with optionally pre-existing javax.persistence.AttributeOverride elements.
         *
         * @param annotation existing javax.persistence.AttributeOverrides annotation, if any, otherwise it will be created
         * @param attributeOverrides collection of javax.persistence.AttributeOverride annotation to be appended
         * @return merged AttributeOverrides annotation
         */
        private static String concatOverrides(String annotation, Collection<String> attributeOverrides) {
            StringBuilder columnAnnotation = new StringBuilder(annotation != null ? annotation : "");

            for (String addOverride : attributeOverrides) {
                if (columnAnnotation.length() < 1) {
                    columnAnnotation.append("@javax.persistence.AttributeOverrides({").append(addOverride).append("})");
                    continue;
                }

                columnAnnotation.insert(columnAnnotation.length()-2, ", ");
                columnAnnotation.insert(columnAnnotation.length() - 2, addOverride);
            }

            return columnAnnotation.toString();
        }
    }
}
