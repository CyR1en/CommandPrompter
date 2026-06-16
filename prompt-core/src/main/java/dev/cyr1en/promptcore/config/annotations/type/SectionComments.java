package dev.cyr1en.promptcore.config.annotations.type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Container for multiple {@link SectionComment} annotations on a configuration class. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface SectionComments {
  SectionComment[] value();
}
