# Solr 10.x

Solr uses the shared analyzer JAR directly. Build with JDK 21 and copy
`build/libs/bitext-lucene-english-analyzer-1.0.0.jar` to Solr's global library
directory before the node starts (the official container image uses
`/opt/solr/lib`). Restart Solr or reload the core after installing the JAR.

No Solr plugin ZIP is required. Configure the analyzer through the Schema API;
this is the same public configuration exercised by the integration test:

```json
{
  "add-field-type": {
    "name": "bitext_java_english",
    "class": "solr.TextField",
    "indexAnalyzer": {
      "class": "com.bitext.lucene.analysis.en.EnglishLemmatizingAnalyzer"
    },
    "queryAnalyzer": {
      "class": "com.bitext.lucene.analysis.en.EnglishLemmatizingAnalyzer"
    }
  },
  "add-field": {
    "name": "text",
    "type": "bitext_java_english",
    "stored": true,
    "multiValued": true
  }
}
```

The JAR includes the bundled `lexicon.bfx` and `entities.bfx` resources. For
`Children. States.`, the analyzer emits `children`, `child`, `states`, and
`state`. The direct-JAR integration supports Solr 10.x and is tested with Solr 10.0.0.
