package dev.cyr1en.promptcore.logic.transform;

/** A compiled segment of a template, either a literal string or a transformed reference. */
public sealed interface TemplateSegment
    permits LiteralSegment, EscapedLiteralSegment, ReferenceSegment {}
