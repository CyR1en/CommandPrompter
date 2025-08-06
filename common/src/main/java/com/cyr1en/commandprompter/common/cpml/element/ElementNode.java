package com.cyr1en.commandprompter.common.cpml.element;

/**
 * Define interface for elements that's going to be used in CPML.
 */
public sealed interface ElementNode permits PromptElement, PostCommandElement, PreConfigElement {}
