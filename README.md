# Bitext Lucene English Analyzer

Bitext Lucene English Analyzer is a Java 21 / Lucene 10.4 English analyzer that performs dictionary-based lemmatization rather than Porter stemming. It preserves useful surface forms, supports sentence-aware contextual analysis, and ships the released English lexicon and entity resources required at runtime.

The project provides integrations for:

- [Elasticsearch 9.x](docs/elasticsearch.md)
- [OpenSearch 3.4+](docs/opensearch.md)
- [Solr 10.x](docs/solr.md)

The Elasticsearch integration is a stable analysis plugin. The OpenSearch integration is distributed as an OpenSearch plugin. Solr uses the shared analyzer JAR directly.

## Background

We have published a [benchmark](https://github.com/bitext/bitext-search-benchmarks) to test the impact of linguistic knowledge on lexical search accuracy at the preprocessing stage. So far, we have tested English and German[\*](#note-on-the-german-benchmark). The goal of this repository is to share the linguistic analyzer used for English so that the benchmark is fully reproducible.

## Introduction

Prior to indexing, text is typically split into words using a tokenizer and is sometimes analyzed with a stemmer. The benchmark uses these two common approaches, plain tokenization and stemming, as baselines.

The benchmark tests whether deeper linguistic knowledge, including tokenization, lemmatization, capitalization, and other language phenomena, can improve text preprocessing and replace stemming to improve lexical search results.

The results show that both languages benefit from deeper linguistic analysis:

- **English:** F1@10 improves by more than 18%
- **German:** F1@10 improves by more than 36%

For English, we are publishing both the benchmark results and the analyzer used to obtain them so that the benchmark can be reproduced, tested, and reused.

## The three analyzers we benchmarked

The English benchmark compares three approaches to text analysis:

1. **Elasticsearch Standard:** standard tokenization and lowercasing, with no English-specific stemming or lemmatization
2. **Elasticsearch English:** possessive normalization, lowercasing, English stopword removal, and Porter stemming
3. **Bitext English:** sentence-aware tokenization, possessive normalization, contextual handling of named entities, lemmatization, lowercasing, and English stopword removal

### Elasticsearch Standard

Elasticsearch Standard performs Unicode-aware tokenization and lowercasing, but no English-specific stemming or lemmatization. This preserves surface distinctions, but misses many useful morphological relationships.

### Elasticsearch English

Elasticsearch English adds possessive normalization, lowercasing, English stopword removal, and Porter stemming.

This improves lexical recall over standard analysis, but Porter stemming is algorithmic and suffix-oriented. It produces stems rather than dictionary lemmas, so it can miss irregular relationships and sometimes collapse terms too aggressively.

### Bitext English

Bitext English builds on the same general pipeline while replacing and reordering several steps. It uses dictionary-based lemmatization instead of Porter stemming and adds sentence-aware contextual handling for capitalization and named entities.

## What the Bitext analyzer does

The analyzer includes:

- sentence-aware tokenization for better capitalization and entity decisions
- English possessive normalization
- dictionary-based lemmatization
- preservation of original word forms alongside lemmas where useful
- contextual protection of named-entity terms from inappropriate ordinary-word normalization
- deferred lowercasing until after lexical and entity decisions have been made
- duplicate removal when original forms and lemmas converge
- English stopword removal

The sentence-aware logic matters because capitalization is ambiguous. At the start of a sentence, `Children` may simply be the plural of `child`. In `Save the Children`, the same form is part of a name and should remain protected. The analyzer uses sentence position and local entity context to make that distinction without requiring a full named-entity extraction model at query time.

Dictionary-based lemmatization also connects irregular relationships that suffix stemming can miss, such as `sell / sold`, `come / came`, and `make / made`, while avoiding some of the false conflations introduced by aggressive stemming.

## Benchmark results

We use MIRACL to measure whether the additional linguistic analysis improves actual lexical retrieval rather than only producing linguistically cleaner tokens.

On the English MIRACL benchmark, Bitext improves F1@10 by more than **18%** over Elasticsearch Standard. Our German benchmark shows a larger gain of more than **36%** over the corresponding standard baseline.

The current reproducible English benchmark uses Elasticsearch 9.5.4, 32,893,221 English Wikipedia passages, 799 development queries, and 8,350 original MIRACL relevance judgments.

For complete benchmark results, examples, methodology, and reproduction scripts, see:

[bitext-search-benchmarks](https://github.com/bitext/bitext-search-benchmarks)

## Build and test

Use JDK 21 and the Gradle Wrapper:

```bash
./gradlew test
./gradlew build
```

For direct Lucene use, construct the released analyzer without resource or
pipeline configuration:

```java
try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
  // Use analyzer.tokenStream(...)
}
```

The root build produces the analyzer JAR at:

```text
build/libs/bitext-lucene-english-analyzer-1.0.0.jar
```

The Elasticsearch plugin ZIP is written to:

```text
build/distributions/bitext-java-english-elasticsearch-1.0.0.zip
```

The OpenSearch plugin ZIP is written to:

```text
build/distributions/bitext-java-english-opensearch-1.0.0.zip
```

The engine distributions are built as part of `./gradlew build`. They can also be built directly with:

```bash
./gradlew :adapters:elasticsearch:pluginZip
./gradlew :adapters:opensearch:pluginZip
```

## Integrations

### Elasticsearch

The analyzer is packaged as an Elasticsearch 9.x stable analysis plugin. Version 1.0.0 is built and tested with Elasticsearch 9.5.4.

See [Elasticsearch installation and configuration](docs/elasticsearch.md).

### OpenSearch

The analyzer is packaged as an OpenSearch plugin supporting OpenSearch 3.4+.

Version 1.0.0 is tested with OpenSearch 3.4.0 and 3.9.0 using the same plugin ZIP.

See [OpenSearch installation and configuration](docs/opensearch.md).

### Solr

Solr uses the shared analyzer JAR directly rather than an engine-specific plugin ZIP.

The integration supports Solr 10.x and is tested with Solr 10.0.0.

See [Solr installation and configuration](docs/solr.md).

## Integration tests

Docker-backed engine checks are intentionally separate from normal unit tests:

```bash
./gradlew integrationTest
```

They require Docker, Bash, `curl`, and `jq`. Normal `./gradlew test` and
`./gradlew build` do not require Docker.

They build and exercise the public analyzer artifacts against Elasticsearch, OpenSearch, and Solr without indexing a benchmark corpus.

The default integration versions match the qualification baseline. Alternate compatible engine versions can be selected with the documented environment variables. For example:

```bash
OPENSEARCH_VERSION=3.9.0 ./gradlew integrationTest
```

See the engine-specific documentation for the supported compatibility ranges and tested versions.

## Java and C implementations

This repository contains the Java implementation used by the public benchmark and search-engine integrations. Bitext also maintains a C implementation for commercial use. Both implementations follow the same linguistic analysis approach and resource model.

## Note on the German benchmark

In German, the increased accuracy provided by linguistic analysis, including lemmatization and decompounding, is even higher. German has more complex morphology than English, including highly productive compounding, and we believe the greater benefit of linguistic analysis is related to this morphological complexity, including both inflection and word formation.

The German benchmark has not yet been packaged as a fully reproducible workflow. See the [German benchmark results](https://github.com/bitext/bitext-search-benchmarks/blob/main/docs/german-benchmark.md) for the published evaluation.

## License

The project is licensed under the [Apache License 2.0](LICENSE).
