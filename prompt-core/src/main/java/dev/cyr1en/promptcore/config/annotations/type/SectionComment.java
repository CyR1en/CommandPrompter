package dev.cyr1en.promptcore.config.annotations.type;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches a comment block to a specific parent section path in the YAML hierarchy. Used inside
 * {@link SectionComments}.
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface SectionComment {
  /** The YAML path of the section (e.g., "Permission-Attachment"). */
  String path();

  /** The comment lines to attach above the section. */
  String[] comments();
}
