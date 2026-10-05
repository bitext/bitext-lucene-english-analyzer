package com.bitext.lucene.analysis.en.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** An immutable lexical analysis; order is never a ranking. */
public record CandidateAnalysis(
    String lemma,
    String tagSetId,
    String partOfSpeech,
    Map<String, String> features,
    Map<String, String> lexicalProperties)
    implements Comparable<CandidateAnalysis> {
  public CandidateAnalysis {
    if (lemma == null || lemma.isEmpty())
      throw new IllegalArgumentException("lemma must be nonempty");
    features = ordered(features);
    lexicalProperties = ordered(lexicalProperties);
  }

  @Override
  public int compareTo(CandidateAnalysis other) {
    int c = lemma.compareTo(other.lemma);
    if (c != 0) return c;
    c = compareNullable(tagSetId, other.tagSetId);
    if (c != 0) return c;
    c = compareNullable(partOfSpeech, other.partOfSpeech);
    if (c != 0) return c;
    c = compareMaps(features, other.features);
    return c != 0 ? c : compareMaps(lexicalProperties, other.lexicalProperties);
  }

  private static Map<String, String> ordered(Map<String, String> source) {
    TreeMap<String, String> sorted = new TreeMap<>(source == null ? Map.of() : source);
    sorted.forEach(
        (key, value) -> {
          if (key == null || value == null)
            throw new IllegalArgumentException("analysis maps cannot contain null");
        });
    return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
  }

  private static int compareNullable(String left, String right) {
    if (left == null) return right == null ? 0 : -1;
    return right == null ? 1 : left.compareTo(right);
  }

  private static int compareMaps(Map<String, String> left, Map<String, String> right) {
    var a = left.entrySet().iterator();
    var b = right.entrySet().iterator();
    while (a.hasNext() && b.hasNext()) {
      var x = a.next();
      var y = b.next();
      int c = x.getKey().compareTo(y.getKey());
      if (c != 0) return c;
      c = x.getValue().compareTo(y.getValue());
      if (c != 0) return c;
    }
    return a.hasNext() ? 1 : b.hasNext() ? -1 : 0;
  }
}
