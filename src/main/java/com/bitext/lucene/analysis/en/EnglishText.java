package com.bitext.lucene.analysis.en;

import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Unicode helpers shared by lexical-resource validation and lookup. */
final class EnglishText {
  static final String NORMALIZED_LOWERCASE_ID = "lucene-10.4.0-lowercase-codepoint-v1";

  private EnglishText() {}

  static boolean wellFormedUnicode(String value) {
    if (value == null) return false;
    try {
      StandardCharsets.UTF_8
          .newEncoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .encode(java.nio.CharBuffer.wrap(value));
      return true;
    } catch (java.nio.charset.CharacterCodingException invalid) {
      return false;
    }
  }

  static String lower(String value) {
    StringBuilder result = new StringBuilder(value.length());
    value
        .codePoints()
        .forEach(codePoint -> result.appendCodePoint(Character.toLowerCase(codePoint)));
    return result.toString();
  }
}
