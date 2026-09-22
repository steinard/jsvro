package dev.jsvro.core.internal;

import tools.jackson.databind.introspect.AnnotatedMember;

@FunctionalInterface
interface PropertyAccessor {
    Object get(Object instance);

    static PropertyAccessor from(AnnotatedMember member) {
        return instance -> {
            try {
                return member.getValue(instance);
            }
            catch (RuntimeException ex) {
                throw ex;
            }
        };
    }
}
