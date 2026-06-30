package io.crest.runtime;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Map;

public class CrestRuntimeRoleCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnCrestRuntimeRole.class.getName());
        if (attributes == null) {
            return true;
        }
        CrestRuntimeRole current = CrestRuntimeRole.from(context.getEnvironment());
        CrestRuntimeRole[] acceptedRoles = (CrestRuntimeRole[]) AnnotationAttributes.fromMap(attributes).get("value");
        if (acceptedRoles == null || acceptedRoles.length == 0) {
            return true;
        }
        for (CrestRuntimeRole acceptedRole : acceptedRoles) {
            if (acceptedRole == current) {
                return true;
            }
        }
        return false;
    }
}
