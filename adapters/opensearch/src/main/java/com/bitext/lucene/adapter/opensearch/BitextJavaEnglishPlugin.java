package com.bitext.lucene.adapter.opensearch;

import com.bitext.lucene.analysis.en.EnglishLemmatizingAnalyzer;
import com.bitext.lucene.analysis.en.model.OutputMode;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.opensearch.common.settings.Settings;
import org.opensearch.index.analysis.AnalyzerProvider;
import org.opensearch.index.analysis.AnalyzerScope;
import org.opensearch.indices.analysis.AnalysisModule;
import org.opensearch.plugins.AnalysisPlugin;
import org.opensearch.plugins.Plugin;

/** Registers the bundled Bitext English analyzer with OpenSearch. */
public final class BitextJavaEnglishPlugin extends Plugin implements AnalysisPlugin {
  @Override
  public Map<String, AnalysisModule.AnalysisProvider<AnalyzerProvider<? extends Analyzer>>>
      getAnalyzers() {
    return Map.of(
        "bitext_java_english",
        (indexSettings, environment, name, settings) ->
            new NamedAnalyzer(name, outputMode(settings)));
  }

  private static OutputMode outputMode(Settings settings) {
    String value = settings.get("output_mode", "EXPAND");
    try {
      return OutputMode.valueOf(value.toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("unknown output_mode: " + value, error);
    }
  }

  private record NamedAnalyzer(String name, OutputMode outputMode)
      implements AnalyzerProvider<Analyzer> {
    @Override
    public AnalyzerScope scope() {
      return AnalyzerScope.INDICES;
    }

    @Override
    public Analyzer get() {
      return outputMode == OutputMode.EXPAND
          ? new EnglishLemmatizingAnalyzer()
          : new EnglishLemmatizingAnalyzer(outputMode);
    }
  }
}
