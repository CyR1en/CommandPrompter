package dev.cyr1en.promptcore.logic.condition;

import java.util.Objects;

/**
 * Case-sensitive string comparison operators supported in condition expressions. Evaluated in
 * linear time without regular expressions.
 */
public enum StringOperator {
  EQUALS("equals"),
  CONTAINS("contains"),
  STARTS_WITH("startsWith"),
  ENDS_WITH("endsWith");

  private final String keyword;

  StringOperator(String keyword) {
    this.keyword = keyword;
  }

  public String keyword() {
    return keyword;
  }

  public static StringOperator fromKeyword(String keyword) {
    Objects.requireNonNull(keyword, "keyword cannot be null");
    for (StringOperator op : values()) {
      if (op.keyword.equals(keyword)) {
        return op;
      }
    }
    throw new IllegalArgumentException("Unknown string operator keyword: " + keyword);
  }
}
