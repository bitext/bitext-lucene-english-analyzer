package com.bitext.lucene.adapter.elasticsearch;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

class BitextJavaEnglishAnalyzerFactoryTest {
  @Test
  void defaultConfigurationUsesTheReleasedAnalyzer() throws Exception {
    BitextJavaEnglishAnalyzerFactory factory =
        new BitextJavaEnglishAnalyzerFactory(defaultSettings());
    try (Analyzer analyzer = factory.create()) {
      assertEquals(
          List.of("children", "child", "states", "state"), terms(analyzer, "Children. States."));
    }
  }

  @Test
  void replaceOutputModeEmitsOnlyTheNormalizedForm() throws Exception {
    BitextJavaEnglishAnalyzerFactory factory =
        new BitextJavaEnglishAnalyzerFactory(settings("REPLACE"));
    try (Analyzer analyzer = factory.create()) {
      assertEquals(List.of("child"), terms(analyzer, "Children"));
    }
  }

  private static List<String> terms(Analyzer analyzer, String input) throws IOException {
    List<String> terms = new ArrayList<>();
    try (TokenStream stream = analyzer.tokenStream("test", input)) {
      CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
      stream.reset();
      while (stream.incrementToken()) {
        terms.add(term.toString());
      }
      stream.end();
    }
    return terms;
  }

  private static BitextAnalysisSettings defaultSettings() {
    return settings("EXPAND");
  }

  private static BitextAnalysisSettings settings(String outputMode) {
    return new BitextAnalysisSettings() {
      @Override
      public String outputMode() {
        return outputMode;
      }
    };
  }
}
