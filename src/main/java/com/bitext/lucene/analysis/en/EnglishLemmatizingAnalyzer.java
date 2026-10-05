package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.model.OutputMode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.miscellaneous.RemoveDuplicatesTokenFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * English analyzer backed by the bundled Bitext lexicon and entity resources.
 *
 * <p>The no-argument constructor is the supported production configuration. It preserves
 * entity-aware initial-capital handling and sentence boundaries without requiring callers to
 * configure resource locations or lookup policy.
 */
public final class EnglishLemmatizingAnalyzer extends Analyzer {
  private static final String LEXICON_RESOURCE = "classpath:bitext/english/lexicon.bfx";
  private static final String ENTITY_RESOURCE = "classpath:bitext/english/entities.bfx";

  private final EnglishLexicon lexicon;
  private final EnglishLexicon entityLexicon;
  private final OutputMode mode;
  private final EnglishContextFilter.Config context;
  private final LookupStrategy strategy;
  private final SentenceProvider sentenceProvider;

  /** Creates the released English analyzer with its bundled lexical resources. */
  public EnglishLemmatizingAnalyzer() {
    this(
        BundledLexicons.LEXICON,
        BundledLexicons.ENTITIES,
        OutputMode.EXPAND,
        null,
        LookupStrategy.ENTITY_AWARE,
        SentenceProvider.SENTENCE_AWARE);
  }

  /** Creates the released English analyzer with a different output mode. */
  public EnglishLemmatizingAnalyzer(OutputMode mode) {
    this(
        BundledLexicons.LEXICON,
        BundledLexicons.ENTITIES,
        mode,
        null,
        LookupStrategy.ENTITY_AWARE,
        SentenceProvider.SENTENCE_AWARE);
  }

  EnglishLemmatizingAnalyzer(OutputMode mode, EnglishContextFilter.Config context) {
    this(
        BundledLexicons.LEXICON,
        BundledLexicons.ENTITIES,
        mode,
        context,
        LookupStrategy.ENTITY_AWARE,
        SentenceProvider.SENTENCE_AWARE);
  }

  EnglishLemmatizingAnalyzer(
      EnglishLexicon lexicon, OutputMode mode, EnglishContextFilter.Config context) {
    this(lexicon, null, mode, context, null, SentenceProvider.NONE);
  }

  EnglishLemmatizingAnalyzer(
      EnglishLexicon lexicon,
      EnglishLexicon entityLexicon,
      OutputMode mode,
      EnglishContextFilter.Config context,
      LookupStrategy strategy,
      SentenceProvider sentenceProvider) {
    EnglishMorphologyFilter.validateStrategy(lexicon, strategy);
    validateSentenceProvider(strategy, sentenceProvider);
    if (strategy == LookupStrategy.ENTITY_AWARE && entityLexicon == null) {
      throw new IllegalArgumentException("entity-aware lookup requires an entity lexicon");
    }
    this.lexicon = lexicon;
    this.entityLexicon = entityLexicon;
    this.mode = mode;
    this.context = context;
    this.strategy = strategy;
    this.sentenceProvider = sentenceProvider;
  }

  static void validateSentenceProvider(
      LookupStrategy strategy, SentenceProvider sentenceProvider) {
    if (sentenceProvider == SentenceProvider.SENTENCE_AWARE
        && strategy != LookupStrategy.ENTITY_AWARE) {
      throw new IllegalArgumentException(
          "sentence-aware tokenization requires entity-aware lookup");
    }
  }

  @Override
  protected TokenStreamComponents createComponents(String field) {
    Tokenizer source =
        sentenceProvider == SentenceProvider.SENTENCE_AWARE
            ? new SentenceAwareStandardTokenizer()
            : new StandardTokenizer();
    TokenStream stream = new EnglishPossessiveFilter(source);
    stream =
        strategy == null
            ? new EnglishMorphologyFilter(stream, lexicon)
            : new EnglishMorphologyFilter(stream, lexicon, entityLexicon, strategy);
    if (strategy == LookupStrategy.ENTITY_AWARE) {
      stream = new EnglishCaseSelectionFilter(stream);
    }
    if (context != null) {
      stream = new EnglishContextFilter(stream, context);
    }
    stream = new EnglishLemmaOutputFilter(stream, mode);
    stream = new LowerCaseFilter(stream);
    stream = new RemoveDuplicatesTokenFilter(stream);
    stream = new StopFilter(stream, EnglishAnalyzer.ENGLISH_STOP_WORDS_SET);
    return new TokenStreamComponents(source, stream);
  }

  @Override
  protected TokenStream normalize(String field, TokenStream input) {
    return new LowerCaseFilter(input);
  }

  private static final class BundledLexicons {
    private static final EnglishLexicon LEXICON =
        EnglishLexiconResources.fromClasspath(LEXICON_RESOURCE, null);
    private static final EnglishLexicon ENTITIES =
        EnglishLexiconResources.fromClasspath(ENTITY_RESOURCE, null);
  }
}
