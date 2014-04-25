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
