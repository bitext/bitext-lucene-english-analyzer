package com.bitext.lucene.adapter.elasticsearch;

import org.elasticsearch.plugin.settings.AnalysisSettings;
import org.elasticsearch.plugin.settings.StringSetting;

/** Per-analysis-component settings exposed by Elasticsearch's stable plugin API. */
@AnalysisSettings
public interface BitextAnalysisSettings {
  @StringSetting(path = "output_mode", defaultValue = "EXPAND")
  String outputMode();
}
