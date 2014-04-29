package com.github.gekoh.yagen.ddl.comment;

import com.thoughtworks.xstream.XStream;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;

/**
 * @author Georg Kohlweiss
 */
public class MetadataSerializationSupport {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MetadataSerializationSupport.class);

    private static final String REFERENCE_ATTRIBUTE_NAME = "reference";
    private static final String IDREF_ATTRIBUTE_NAME = "idref";

    private static final XStream XSTREAM;
    static {
        XSTREAM = new XStream();
        XSTREAM.aliasType("date", LocalDate.class);
        XSTREAM.aliasType("ts", DateTime.class);
        XSTREAM.aliasPackage("comment", TableMetadata.class.getPackage().getName());
        XSTREAM.setMode(XStream.XPATH_ABSOLUTE_REFERENCES);
        XSTREAM.aliasSystemAttribute(IDREF_ATTRIBUTE_NAME, REFERENCE_ATTRIBUTE_NAME); //replace all reference attributes with idref
        XSTREAM.autodetectAnnotations(false); // donot change to true -> xstream will not be thread safe!
    }

    public static String toXML (Metadata metadata) {
        return XSTREAM.toXML(metadata);
    }
}
