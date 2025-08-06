package com.cyr1en.commandprompter.common.cpml;

import com.cyr1en.commandprompter.common.cpml.element.ElementNode;
import com.cyr1en.commandprompter.common.cpml.element.PostCommandElement;
import com.cyr1en.commandprompter.common.cpml.element.PreConfigElement;
import com.cyr1en.commandprompter.common.cpml.element.PromptElement;

import java.util.ArrayList;

public class CPMLParser {
    private final String input;
    private int pos = 0;

    public CPMLParser(String input) {
        this.input = input;
    }

    public ParseResult parse() {
        skipWhitespace();
        if (peek("<cpml>")) {
            consume("<cpml>");
            var elements = new ArrayList<ElementNode>();
            while (!peek("</cpml>")) {
                elements.add(parseElement(true));
                skipWhitespace();
            }
            consume("</cpml>");
            return new ParseResult(new CPMLDocument(elements), null);
        } else {
            var elem = parseElement(false);
            return new ParseResult(null, elem);
        }
    }

    private ElementNode parseElement(boolean insideCpml) {
        skipWhitespace();
        if (peek("<prompt")) {
            return parsePrompt(insideCpml);
        } else if (peek("<postcommand")) {
            return parsePostcommand(insideCpml);
        } else if (peek("<preconfig")) {
            return parsePreconfig(insideCpml);
        }
        throw error("Unknown element");
    }

    private PromptElement parsePrompt(boolean insideCpml) {
        consume("<prompt");
        skipWhitespace();
        var id = insideCpml ? parseRequiredAttribute("id") : null;
        var type = parseRequiredAttribute("type");
        var args = parseOptionalAttribute("args");
        skipWhitespace();
        consume(">");
        var content = parseUntil("</prompt>");
        consume("</prompt>");
        return new PromptElement(id, type, args != null ? args : "", content);
    }

    private PostCommandElement parsePostcommand(boolean insideCpml) {
        consume("<postcommand");
        skipWhitespace();
        var id = insideCpml ? parseRequiredAttribute("id") : null;
        var type = parseRequiredAttribute("type");
        var args = parseOptionalAttribute("args");
        skipWhitespace();
        consume(">");
        var content = parseUntil("</postcommand>");
        consume("</postcommand>");
        return new PostCommandElement(id, type, args != null ? args : "", content);
    }

    private PreConfigElement parsePreconfig(boolean insideCpml) {
        consume("<preconfig");
        skipWhitespace();
        var id = parseRequiredAttribute("id");
        skipWhitespace();
        consume("/>");
        return new PreConfigElement(id);
    }

    private String parseRequiredAttribute(String name) {
        skipWhitespace();
        if (!peek(name + "=")) throw error("Expected attribute: " + name);
        consume(name + "=");
        return parseQuotedString();
    }

    private String parseOptionalAttribute(String name) {
        skipWhitespace();
        if (peek(name + "=")) {
            consume(name + "=");
            return parseQuotedString();
        }
        return null;
    }

    private String parseQuotedString() {
        consume("\"");
        var start = pos;
        while (pos < input.length() && input.charAt(pos) != '"') pos++;
        var result = input.substring(start, pos);
        consume("\"");
        return result;
    }

    private String parseUntil(String endToken) {
        var start = pos;
        var idx = input.indexOf(endToken, pos);
        if (idx == -1) throw error("Expected " + endToken);
        var result = input.substring(start, idx);
        pos = idx;
        return result;
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) pos++;
    }

    private boolean peek(String s) {
        return input.startsWith(s, pos);
    }

    private void consume(String s) {
        if (!peek(s)) throw error("Expected '" + s + "'");
        pos += s.length();
    }

    private RuntimeException error(String msg) {
        return new RuntimeException(msg + " at position " + pos);
    }
}
