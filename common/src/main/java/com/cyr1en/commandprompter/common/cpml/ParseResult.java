package com.cyr1en.commandprompter.common.cpml;

import com.cyr1en.commandprompter.common.cpml.element.ElementNode;

public class ParseResult {
    public final CPMLDocument document;
    public final ElementNode singleElement;

    public ParseResult(CPMLDocument document, ElementNode singleElement) {
        this.document = document;
        this.singleElement = singleElement;
    }
}

