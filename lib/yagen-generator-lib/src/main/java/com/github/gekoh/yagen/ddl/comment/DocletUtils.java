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
package com.github.gekoh.yagen.ddl.comment;

import com.sun.javadoc.*;

public class DocletUtils {

    public static boolean hasAnnotation(final ProgramElementDoc programElementDoc, final Class<?>... soughtAnnotations) {
        return findAnnotation(programElementDoc, soughtAnnotations) != null;
    }

    public static AnnotationDesc findAnnotation(final ProgramElementDoc programElementDoc, final Class<?>... soughtAnnotations) {
        return findAnnotation(programElementDoc.annotations(), soughtAnnotations);
    }

    public static AnnotationDesc findAnnotation(final AnnotationDesc[] annotations, final Class<?>... soughtAnnotations) {
        for (final AnnotationDesc annotation : annotations) {
            final AnnotationTypeDoc annotationType = annotation.annotationType();
            for (final Class<?> soughtAnnotation : soughtAnnotations) {
                if (annotationType.qualifiedTypeName().equals(soughtAnnotation.getName())) {
                    return annotation;
                }
            }
        }
        return null;
    }

       public static ClassDoc findAnnotatedInterface(final ClassDoc klass, final Class<?>... soughtAnnotations) {
        // find it in the interfaces
        final Type[] interfaceTypes = klass.interfaceTypes();
        for (final Type interfaceType : interfaceTypes) {
            final ClassDoc interfaceClassDoc = interfaceType.asClassDoc();
            if (interfaceClassDoc != null) {
                if (hasAnnotation(interfaceClassDoc, soughtAnnotations)) {
                    return interfaceClassDoc;
                }
                final ClassDoc foundClassDoc = findAnnotatedInterface(interfaceClassDoc, soughtAnnotations);
                if (foundClassDoc != null) {
                    return foundClassDoc;
                }
            }
        }
        return null;
    }

    public static ClassDoc findAnnotatedClass(final ClassDoc klass, final Class<?>... soughtAnnotations) {
        if (!klass.isClass())
            return null;
        if (hasAnnotation(klass, soughtAnnotations)) {
            return klass;
        }
        // find it in the interfaces
        final ClassDoc foundClassDoc = findAnnotatedInterface(klass, soughtAnnotations);
        if (foundClassDoc != null) {
            return foundClassDoc;
        }

        final Type superclass = klass.superclassType();
        if (superclass != null && superclass.asClassDoc() != null) {
            return findAnnotatedClass(superclass.asClassDoc(), soughtAnnotations);
        }
        return null;
    }

}
