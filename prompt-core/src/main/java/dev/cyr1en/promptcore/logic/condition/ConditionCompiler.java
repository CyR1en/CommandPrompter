package dev.cyr1en.promptcore.logic.condition;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Compiler for condition expressions. Tokenizes, parses, and validates condition source into an
 * immutable {@link CompiledCondition} under strict bounds and fail-closed security contracts.
 */
public final class ConditionCompiler {

  public static final int MAX_SOURCE_LENGTH = 1024;
  public static final int MAX_AST_DEPTH = 10;
  public static final int MAX_AST_NODES = 128;

  private static final Pattern C0_CONTROLS = Pattern.compile("[\\u0000-\\u001F\\u007F]");

  private ConditionCompiler() {}

  /**
   * Compiles the given condition source with default inline options.
   *
   * @param source condition source expression
   * @return compiled condition
   */
  public static Condition compile(String source) {
    return compile(source, ConditionCompileOptions.defaultOptions());
  }

  /**
   * Compiles the given condition source with explicit options.
   *
   * @param source condition source expression
   * @param options compilation options
   * @return compiled condition
   * @throws ConditionParseException if source is invalid or violates bounds
   */
  public static Condition compile(String source, ConditionCompileOptions options) {
    Objects.requireNonNull(source, "Condition source cannot be null");
    Objects.requireNonNull(options, "ConditionCompileOptions cannot be null");

    if (source.isBlank()) {
      throw new ConditionParseException("Condition expression cannot be empty or blank");
    }

    if (source.length() > MAX_SOURCE_LENGTH) {
      throw new ConditionParseException(
          "Condition source length ("
              + source.length()
              + ") exceeds maximum limit of "
              + MAX_SOURCE_LENGTH);
    }

    if (C0_CONTROLS.matcher(source).find()) {
      throw new ConditionParseException(
          "Condition source contains forbidden raw C0 control characters");
    }

    List<Token> tokens = tokenize(source, options);
    if (tokens.isEmpty()) {
      throw new ConditionParseException("Condition expression contains no tokens");
    }

    Parser parser = new Parser(source, tokens);
    ConditionNode root = parser.parse();

    return new CompiledCondition(source, root);
  }

  private enum TokenType {
    LPAREN,
    RPAREN,
    AND,
    OR,
    NOT,
    NUMERIC_OP,
    STRING_OP,
    ANSWER_REF,
    PAPI_REF,
    NUMBER_LITERAL,
    STRING_LITERAL
  }

  private record Token(TokenType type, Object value, int position) {}

  private static List<Token> tokenize(String source, ConditionCompileOptions options) {
    List<Token> tokens = new ArrayList<>();
    int length = source.length();
    int i = 0;

    while (i < length) {
      char c = source.charAt(i);

      if (c == ' ') {
        i++;
        continue;
      }

      if (c == '(') {
        tokens.add(new Token(TokenType.LPAREN, "(", i));
        i++;
        continue;
      }

      if (c == ')') {
        tokens.add(new Token(TokenType.RPAREN, ")", i));
        i++;
        continue;
      }

      if (c == '&') {
        if (i + 1 < length && source.charAt(i + 1) == '&') {
          tokens.add(new Token(TokenType.AND, "&&", i));
          i += 2;
          continue;
        }
        throw new ConditionParseException("Invalid token '&' at position " + i + ", expected '&&'");
      }

      if (c == '|') {
        if (i + 1 < length && source.charAt(i + 1) == '|') {
          tokens.add(new Token(TokenType.OR, "||", i));
          i += 2;
          continue;
        }
        throw new ConditionParseException("Invalid token '|' at position " + i + ", expected '||'");
      }

      if (c == '!') {
        if (i + 1 < length && source.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenType.NUMERIC_OP, NumericOperator.NOT_EQUALS, i));
          i += 2;
          continue;
        }
        tokens.add(new Token(TokenType.NOT, "!", i));
        i++;
        continue;
      }

      if (c == '=') {
        if (i + 1 < length && source.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenType.NUMERIC_OP, NumericOperator.EQUALS, i));
          i += 2;
          continue;
        }
        throw new ConditionParseException(
            "Invalid assignment token '=' at position " + i + ", expected '=='");
      }

      if (c == '<') {
        if (i + 1 < length && source.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenType.NUMERIC_OP, NumericOperator.LESS_THAN_OR_EQUAL, i));
          i += 2;
          continue;
        }
        tokens.add(new Token(TokenType.NUMERIC_OP, NumericOperator.LESS_THAN, i));
        i++;
        continue;
      }

      if (c == '>') {
        if (i + 1 < length && source.charAt(i + 1) == '=') {
          tokens.add(new Token(TokenType.NUMERIC_OP, NumericOperator.GREATER_THAN_OR_EQUAL, i));
          i += 2;
          continue;
        }
        tokens.add(new Token(TokenType.NUMERIC_OP, NumericOperator.GREATER_THAN, i));
        i++;
        continue;
      }

      // Answer reference: {N}
      if (c == '{') {
        int start = i;
        int end = source.indexOf('}', start);
        if (end == -1) {
          throw new ConditionParseException(
              "Unterminated answer reference starting at position " + start);
        }
        String indexStr = source.substring(start + 1, end).trim();
        if (indexStr.isEmpty()) {
          throw new ConditionParseException("Empty answer reference '{}' at position " + start);
        }
        int index;
        try {
          index = Integer.parseInt(indexStr);
          if (index < 0) {
            throw new NumberFormatException();
          }
        } catch (NumberFormatException e) {
          throw new ConditionParseException(
              "Invalid answer index in {" + indexStr + "} at position " + start);
        }
        tokens.add(new Token(TokenType.ANSWER_REF, index, start));
        i = end + 1;
        continue;
      }

      // PlaceholderAPI reference: %papi%
      if (c == '%') {
        int start = i;
        int end = source.indexOf('%', start + 1);
        if (end == -1) {
          throw new ConditionParseException(
              "Unterminated PlaceholderAPI reference starting at position " + start);
        }
        String placeholder = source.substring(start + 1, end).trim();
        if (placeholder.isEmpty()) {
          throw new ConditionParseException(
              "Empty PlaceholderAPI reference '%%' at position " + start);
        }
        if (!options.allowPapiRefs()) {
          throw new ConditionParseException(
              "PlaceholderAPI reference '%"
                  + placeholder
                  + "%' is not permitted in untrusted/inline condition context");
        }
        tokens.add(new Token(TokenType.PAPI_REF, placeholder, start));
        i = end + 1;
        continue;
      }

      // Quoted string literal: "..."
      if (c == '"') {
        int start = i;
        StringBuilder sb = new StringBuilder();
        i++; // skip opening quote
        boolean closed = false;
        while (i < length) {
          char ch = source.charAt(i);
          if (ch == '\\') {
            if (i + 1 >= length) {
              throw new ConditionParseException(
                  "Trailing escape backslash in string literal at position " + i);
            }
            char next = source.charAt(i + 1);
            if (next == '"') {
              sb.append('"');
              i += 2;
            } else if (next == '\\') {
              sb.append('\\');
              i += 2;
            } else {
              throw new ConditionParseException(
                  "Invalid escape sequence '\\"
                      + next
                      + "' at position "
                      + i
                      + ". Only \\\" and \\\\ are permitted.");
            }
          } else if (ch == '"') {
            closed = true;
            i++; // skip closing quote
            break;
          } else {
            sb.append(ch);
            i++;
          }
        }
        if (!closed) {
          throw new ConditionParseException("Unclosed quoted string starting at position " + start);
        }
        tokens.add(new Token(TokenType.STRING_LITERAL, sb.toString(), start));
        continue;
      }

      // Number literal: signed decimal (e.g. +10, -5.5, 123)
      if (isDigit(c)
          || ((c == '+' || c == '-') && i + 1 < length && isDigit(source.charAt(i + 1)))) {
        int start = i;
        if (c == '+' || c == '-') {
          i++;
        }
        while (i < length && isDigit(source.charAt(i))) {
          i++;
        }
        if (i < length && source.charAt(i) == '.') {
          i++;
          if (i >= length || !isDigit(source.charAt(i))) {
            throw new ConditionParseException("Invalid decimal number format at position " + start);
          }
          while (i < length && isDigit(source.charAt(i))) {
            i++;
          }
        }
        String numStr = source.substring(start, i);
        BigDecimal num;
        try {
          num = new BigDecimal(numStr);
        } catch (NumberFormatException e) {
          throw new ConditionParseException(
              "Malformed decimal number '" + numStr + "' at position " + start);
        }
        tokens.add(
            new Token(TokenType.NUMBER_LITERAL, new NumberLiteralOperand(num, numStr), start));
        continue;
      }

      // String keywords / operators: equals, contains, startsWith, endsWith
      if (isAlpha(c)) {
        int start = i;
        while (i < length && (isAlpha(source.charAt(i)) || isDigit(source.charAt(i)))) {
          i++;
        }
        String word = source.substring(start, i);
        switch (word) {
          case "equals" -> tokens.add(new Token(TokenType.STRING_OP, StringOperator.EQUALS, start));
          case "contains" ->
              tokens.add(new Token(TokenType.STRING_OP, StringOperator.CONTAINS, start));
          case "startsWith" ->
              tokens.add(new Token(TokenType.STRING_OP, StringOperator.STARTS_WITH, start));
          case "endsWith" ->
              tokens.add(new Token(TokenType.STRING_OP, StringOperator.ENDS_WITH, start));
          default ->
              throw new ConditionParseException(
                  "Unexpected identifier '"
                      + word
                      + "' at position "
                      + start
                      + ". Identifiers, boolean literals, and functions are not permitted.");
        }
        continue;
      }

      throw new ConditionParseException("Unexpected character '" + c + "' at position " + i);
    }

    return tokens;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }

  private static final class Parser {
    private final String source;
    private final List<Token> tokens;
    private int cursor = 0;

    Parser(String source, List<Token> tokens) {
      this.source = source;
      this.tokens = tokens;
    }

    ConditionNode parse() {
      ConditionNode root = parseOr();
      if (!isAtEnd()) {
        Token trailing = peek();
        throw new ConditionParseException(
            "Unexpected trailing token '" + trailing.value + "' at position " + trailing.position);
      }
      enforceBounds(root);
      return root;
    }

    private ConditionNode parseOr() {
      ConditionNode node = parseAnd();
      while (match(TokenType.OR)) {
        ConditionNode right = parseAnd();
        node = new OrNode(node, right);
        enforceBounds(node);
      }
      return node;
    }

    private ConditionNode parseAnd() {
      ConditionNode node = parseUnary();
      while (match(TokenType.AND)) {
        ConditionNode right = parseUnary();
        node = new AndNode(node, right);
        enforceBounds(node);
      }
      return node;
    }

    private ConditionNode parseUnary() {
      if (match(TokenType.NOT)) {
        ConditionNode child = parseUnary();
        ConditionNode node = new NotNode(child);
        enforceBounds(node);
        return node;
      }
      return parsePrimary();
    }

    private ConditionNode parsePrimary() {
      if (match(TokenType.LPAREN)) {
        int pos = previous().position;
        if (match(TokenType.RPAREN)) {
          throw new ConditionParseException("Empty parentheses '()' at position " + pos);
        }
        ConditionNode expr = parseOr();
        consume(TokenType.RPAREN, "Expected closing ')' after parenthesized expression");
        return expr;
      }
      return parseComparison();
    }

    private ConditionNode parseComparison() {
      ValueOperand left = parseValueOperand();

      if (match(TokenType.NUMERIC_OP)) {
        NumericOperator op = (NumericOperator) previous().value;
        ValueOperand right = parseValueOperand();
        ConditionNode node = new NumericComparisonNode(left, op, right);
        enforceBounds(node);
        return node;
      }

      if (match(TokenType.STRING_OP)) {
        StringOperator op = (StringOperator) previous().value;
        ValueOperand right = parseValueOperand();
        ConditionNode node = new StringComparisonNode(left, op, right);
        enforceBounds(node);
        return node;
      }

      if (!isAtEnd()) {
        Token next = peek();
        throw new ConditionParseException(
            "Expected comparison operator after operand, found '"
                + next.value
                + "' at position "
                + next.position);
      }

      throw new ConditionParseException(
          "Expected comparison operator (==, !=, <, <=, >, >=, equals, contains, startsWith, endsWith)");
    }

    private ValueOperand parseValueOperand() {
      if (match(TokenType.ANSWER_REF)) {
        int index = (Integer) previous().value;
        return new AnswerRefOperand(index);
      }

      if (match(TokenType.PAPI_REF)) {
        String placeholder = (String) previous().value;
        return new PapiRefOperand(placeholder);
      }

      if (match(TokenType.NUMBER_LITERAL)) {
        return (NumberLiteralOperand) previous().value;
      }

      if (match(TokenType.STRING_LITERAL)) {
        String str = (String) previous().value;
        return new StringLiteralOperand(str);
      }

      if (isAtEnd()) {
        throw new ConditionParseException(
            "Expected value operand ({N}, %papi%, number, or quoted string) at end of input");
      }

      Token current = peek();
      throw new ConditionParseException(
          "Expected value operand ({N}, %papi%, number, or quoted string), found '"
              + current.value
              + "' at position "
              + current.position);
    }

    private void enforceBounds(ConditionNode node) {
      if (node.depth() > MAX_AST_DEPTH) {
        throw new ConditionDepthException(
            "Condition expression exceeds maximum AST depth of "
                + MAX_AST_DEPTH
                + " (actual depth: "
                + node.depth()
                + ")");
      }
      if (node.nodeCount() > MAX_AST_NODES) {
        throw new ConditionNodeLimitException(
            "Condition expression exceeds maximum AST node limit of "
                + MAX_AST_NODES
                + " (actual node count: "
                + node.nodeCount()
                + ")");
      }
    }

    private boolean match(TokenType type) {
      if (check(type)) {
        advance();
        return true;
      }
      return false;
    }

    private Token consume(TokenType type, String errorMessage) {
      if (check(type)) {
        return advance();
      }
      if (isAtEnd()) {
        throw new ConditionParseException(errorMessage + " at end of input");
      }
      Token current = peek();
      throw new ConditionParseException(
          errorMessage + " at position " + current.position + ", found '" + current.value + "'");
    }

    private boolean check(TokenType type) {
      if (isAtEnd()) return false;
      return peek().type == type;
    }

    private Token advance() {
      if (!isAtEnd()) cursor++;
      return previous();
    }

    private boolean isAtEnd() {
      return cursor >= tokens.size();
    }

    private Token peek() {
      return tokens.get(cursor);
    }

    private Token previous() {
      return tokens.get(cursor - 1);
    }
  }
}
