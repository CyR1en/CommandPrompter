package com.cyr1en.commandprompter.config;

import com.cyr1en.commandprompter.config.annotations.type.ConfigHeader;
import com.cyr1en.commandprompter.config.annotations.type.ConfigPath;
import com.cyr1en.commandprompter.config.annotations.type.Configuration;

@Configuration
@ConfigPath("prompt-config.yml")
@ConfigHeader({"Prompts", "Configuration"})
public record PromptConfig() {
}
