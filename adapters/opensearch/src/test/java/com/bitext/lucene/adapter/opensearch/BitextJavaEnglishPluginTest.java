package com.bitext.lucene.adapter.opensearch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;
import org.opensearch.common.settings.Settings;

class BitextJavaEnglishPluginTest {
  @Test
  void registersOnlyTheReleasedDefaultAnalyzer() {
    BitextJavaEnglishPlugin plugin = new BitextJavaEnglishPlugin();
    assertTrue(plugin.getAnalyzers().containsKey("bitext_java_english"));
    assertTrue(plugin.getTokenizers().isEmpty());
    assertTrue(plugin.getTokenFilters().isEmpty());
  }

  @Test
  void defaultAnalyzerProviderUsesReleasedBehavior() throws Exception {
    BitextJavaEnglishPlugin plugin = new BitextJavaEnglishPlugin();
    Object provider =
        plugin
            .getAnalyzers()
            .get("bitext_java_english")
            .get(null, null, "bitext_english", Settings.EMPTY);
    Method get = provider.getClass().getMethod("get");
    try (Analyzer analyzer = (Analyzer) get.invoke(provider)) {
      assertEquals("EnglishLemmatizingAnalyzer", analyzer.getClass().getSimpleName());
    }
  }

  @Test
  void replaceOutputModeEmitsOnlyTheNormalizedForm() throws Exception {
    BitextJavaEnglishPlugin plugin = new BitextJavaEnglishPlugin();
    Settings settings = Settings.builder().put("output_mode", "REPLACE").build();
    Object provider =
        plugin.getAnalyzers().get("bitext_java_english").get(null, null, "bitext_english", settings);
    Method get = provider.getClass().getMethod("get");
    try (Analyzer analyzer = (Analyzer) get.invoke(provider)) {
      assertEquals(List.of("child"), terms(analyzer, "Children"));
    }
  }

  private static List<String> terms(Analyzer analyzer, String input) throws Exception {
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
}
