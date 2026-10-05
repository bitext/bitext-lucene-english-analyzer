package com.bitext.lucene.analysis.en;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.bitext.lucene.analysis.en.model.OutputMode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.junit.jupiter.api.Test;

class PublicEnglishAnalyzerTest {
  @Test
  void releasedLexiconsLoadFromTheNormalRuntimeResourceTree() {
    assertEquals(
        "lucene-10.4.0-lowercase-codepoint-v1",
        LexiconKeyProfile.NORMALIZED_LOWERCASE.id());
    EnglishLexicon lexicon =
        EnglishLexiconResources.fromClasspath(
            "classpath:bitext/english/lexicon.bfx", "bitext-english-main-lexicon-v1");
    EnglishLexicon entities =
        EnglishLexiconResources.fromClasspath(
            "classpath:bitext/english/entities.bfx", "bitext-english-entity-v1");

    assertInstanceOf(FstEnglishLexicon.class, lexicon);
    assertInstanceOf(FstEnglishLexicon.class, entities);
    assertEquals("child", lexicon.lookup("children").getFirst().lemma());
  }

  @Test
  void defaultAnalyzerProvidesReleasedEntityAwareSentenceBehavior() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("children", "states"), terms(analyzer, "Children States."));
      assertEquals(
          List.of("children", "child", "states", "state"), terms(analyzer, "Children. States."));
      assertEquals(List.of("children", "child"), terms(analyzer, "children's"));
    }
  }

  @Test
  void standardTokenizationSplitsPunctuationAndKeepsOrdinaryTerms() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(
          List.of("children", "child", "states", "state"), terms(analyzer, "Children, states."));
    }
  }

  @Test
  void regularPluralLemmatizationPreservesTheOriginalForm() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("states", "state"), terms(analyzer, "states"));
    }
  }

  @Test
  void irregularMorphologyPreservesTheOriginalFormAndLemma() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("children", "child"), terms(analyzer, "children"));
    }
  }

  @Test
  void possessivesAreNormalizedBeforeLexicalLookup() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("children", "child"), terms(analyzer, "children's"));
    }
  }

  @Test
  void outputIsLowercasedAfterCaseSensitiveLookup() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("children"), terms(analyzer, "CHILDREN"));
    }
  }

  @Test
  void stopwordsAreRemovedAndDuplicateOutputsAreCollapsed() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("child"), terms(analyzer, "the child"));
    }
  }

  @Test
  void entityEvidenceProtectsANameWhileSentenceInitialWordsUseOrdinaryLookup() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer()) {
      assertEquals(List.of("children", "states"), terms(analyzer, "Children States."));
      assertEquals(List.of("children", "child"), terms(analyzer, "Children."));
    }
  }

  @Test
  void replaceModeEmitsOnlyTheSelectedLemma() throws Exception {
    try (Analyzer analyzer = new EnglishLemmatizingAnalyzer(OutputMode.REPLACE)) {
      assertEquals(List.of("child"), terms(analyzer, "children"));
    }
  }

  @Test
  void publicAnalyzerConstructionIsLimitedToReleasedModes() throws Exception {
    assertEquals(2, EnglishLemmatizingAnalyzer.class.getConstructors().length);
    assertNotNull(EnglishLemmatizingAnalyzer.class.getConstructor());
    assertNotNull(EnglishLemmatizingAnalyzer.class.getConstructor(OutputMode.class));
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
}
