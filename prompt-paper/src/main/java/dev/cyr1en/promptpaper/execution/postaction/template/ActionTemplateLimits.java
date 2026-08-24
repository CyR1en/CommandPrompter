package dev.cyr1en.promptpaper.execution.postaction.template;

/**
 * Bounds and resource limits for post-action template compilation and rendering.
 */
public final class ActionTemplateLimits {

    public static final int MAX_SOURCE_LENGTH = 1024;
    public static final int MAX_INPUT_LENGTH = 1024;
    public static final int MAX_OUTPUT_LENGTH = 4096;
    public static final int MAX_SEGMENTS = 128;
    public static final int MAX_REFERENCES = 128;
    public static final int MAX_PAPI_TOKEN_LENGTH = 64;

    private ActionTemplateLimits() {}
}
