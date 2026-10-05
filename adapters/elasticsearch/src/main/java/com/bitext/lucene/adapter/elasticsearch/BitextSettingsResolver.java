package com.bitext.lucene.adapter.elasticsearch;

import com.bitext.lucene.analysis.en.model.OutputMode;
import java.util.Locale;

/** Translates the small set of supported public analyzer options. */
final class BitextSettingsResolver {
  private BitextSettingsResolver() {}

  static OutputMode outputMode(BitextAnalysisSettings settings) {
    try {
      return OutputMode.valueOf(settings.outputMode().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException error) {
      throw new IllegalArgumentException("unknown output_mode: " + settings.outputMode(), error);
    }
  }
}
