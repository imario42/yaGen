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
package com.github.gekoh.yagen.hibernate;

import org.hibernate.dialect.Dialect;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.DiscriminatorType;
import org.hibernate.type.NumericBooleanType;
import org.hibernate.type.PrimitiveType;
import org.hibernate.type.descriptor.java.BooleanTypeDescriptor;
import org.hibernate.type.descriptor.sql.BitTypeDescriptor;

import java.io.Serializable;

/**
 * @author Georg Kohlweiss 
 */
public class OracleNumericBooleanType extends AbstractSingleColumnStandardBasicType<Boolean>
        implements PrimitiveType<Boolean>, DiscriminatorType<Boolean> {
    //private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(OracleNumericBooleanType.class);

    public OracleNumericBooleanType() {
        super(BitTypeDescriptor.INSTANCE, BooleanTypeDescriptor.INSTANCE);
    }

    @Override
    public Boolean stringToObject(String xml) throws Exception {
        return NumericBooleanType.INSTANCE.stringToObject(xml);
    }

    @Override
    public Class getPrimitiveClass() {
        return NumericBooleanType.INSTANCE.getPrimitiveClass();
    }

    @Override
    public Serializable getDefaultValue() {
        return NumericBooleanType.INSTANCE.getDefaultValue();
    }

    @Override
    public String objectToSQLString(Boolean value, Dialect dialect) throws Exception {
        return NumericBooleanType.INSTANCE.objectToSQLString(value, dialect);
    }

    @Override
    public String getName() {
        return NumericBooleanType.INSTANCE.getName();
    }
}