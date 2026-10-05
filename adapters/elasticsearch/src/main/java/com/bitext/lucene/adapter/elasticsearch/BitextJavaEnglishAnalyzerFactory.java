package com.bitext.lucene.adapter.elasticsearch;

import com.bitext.lucene.analysis.en.EnglishLemmatizingAnalyzer;
import com.bitext.lucene.analysis.en.model.OutputMode;
import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.plugin.Inject;
import org.elasticsearch.plugin.NamedComponent;
import org.elasticsearch.plugin.analysis.AnalyzerFactory;

/** Stable-plugin factory for the bundled Bitext English analyzer. */
@NamedComponent("bitext_java_english")
public final class BitextJavaEnglishAnalyzerFactory implements AnalyzerFactory {
  private final OutputMode outputMode;

  @Inject
  public BitextJavaEnglishAnalyzerFactory(BitextAnalysisSettings settings) {
    outputMode = BitextSettingsResolver.outputMode(settings);
  }

  @Override
  public Analyzer create() {
    return outputMode == OutputMode.EXPAND
        ? new EnglishLemmatizingAnalyzer()
        : new EnglishLemmatizingAnalyzer(outputMode);
  }
}
