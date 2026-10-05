package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.attributes.LookupEvidenceAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologyAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologySelectionAttribute;
import com.bitext.lucene.analysis.en.attributes.OrthographicContextAttribute;
import com.bitext.lucene.analysis.en.model.CandidateAnalysis;
import com.bitext.lucene.analysis.en.model.ContextObservation;
import com.bitext.lucene.analysis.en.model.LookupBranch;
import com.bitext.lucene.analysis.en.model.LookupEvidence;
import com.bitext.lucene.analysis.en.model.LookupStatus;
import com.bitext.lucene.analysis.en.model.MorphologyBinding;
import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import com.bitext.lucene.analysis.en.model.SelectionDecision;
import com.bitext.lucene.analysis.en.model.TokenCase;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;

/** Computes an immutable morphology snapshot without rewriting the input edge. */
final class EnglishMorphologyFilter extends TokenFilter {
  private final EnglishLexicon lexicon;
  private final EnglishLexicon entityLexicon;
  private final LookupStrategy strategy;
  private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
  private final OffsetAttribute offsets = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute increment =
      addAttribute(PositionIncrementAttribute.class);
  private final PositionLengthAttribute length = addAttribute(PositionLengthAttribute.class);
  private final KeywordAttribute keyword = addAttribute(KeywordAttribute.class);
  private final MorphologyAttribute morphology = addAttribute(MorphologyAttribute.class);
  private final MorphologySelectionAttribute selection =
      addAttribute(MorphologySelectionAttribute.class);
  private final OrthographicContextAttribute context =
      addAttribute(OrthographicContextAttribute.class);
  private final LookupEvidenceAttribute evidence = addAttribute(LookupEvidenceAttribute.class);

  public EnglishMorphologyFilter(TokenStream input, EnglishLexicon lexicon) {
    this(input, lexicon, null);
  }

  /**
   * Creates an explicit exact-resource lookup policy for normalized-key resources.
   */
  public EnglishMorphologyFilter(
      TokenStream input, EnglishLexicon lexicon, LookupStrategy strategy) {
    this(input, lexicon, null, strategy);
  }

  /** Creates an entity-aware producer with separate ordinary and entity resources. */
  public EnglishMorphologyFilter(
      TokenStream input,
      EnglishLexicon lexicon,
      EnglishLexicon entityLexicon,
      LookupStrategy strategy) {
    super(input);
    this.lexicon = Objects.requireNonNull(lexicon, "lexicon");
    this.entityLexicon = entityLexicon;
    this.strategy = strategy;
    validateStrategy(lexicon, strategy);
    if (strategy == LookupStrategy.ENTITY_AWARE
        && (entityLexicon == null
            || !LexiconKeyProfile.EXACT.id().equals(entityLexicon.normalizationId()))) {
      throw new IllegalArgumentException("entity-aware lookup requires an exact entity resource");
    }
  }

  /** Validates the resource/profile boundary before a lookup policy is assembled. */
  public static void validateStrategy(EnglishLexicon lexicon, LookupStrategy strategy) {
    Objects.requireNonNull(lexicon, "lexicon");
    boolean exact = LexiconKeyProfile.EXACT.id().equals(lexicon.normalizationId());
    if (exact && strategy == null) {
      throw new IllegalArgumentException("exact-key resource requires an explicit lookup strategy");
    }
    if (!exact && strategy != null) {
      throw new IllegalArgumentException(
          "lookup strategy " + strategy + " requires an exact-key resource");
    }
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (!input.incrementToken()) {
      return false;
    }

    String surface = term.toString();
    String lookupKey = null;
    LookupStatus status;
    List<CandidateAnalysis> candidates;
    LookupEvidence branchEvidence;
    if (keyword.isKeyword()) {
      status = LookupStatus.KEYWORD_BYPASS;
      candidates = List.of();
      // Entity-aware lookup may inspect a protected nonempty neighbor in the entity dictionary
      // while
      // leaving that neighbor protected from ordinary morphology and output expansion.
      branchEvidence =
          strategy == LookupStrategy.ENTITY_AWARE && !surface.isEmpty()
              ? new LookupEvidence(
                  List.of(branch(LookupBranch.Role.ENTITY_EXACT, entityLexicon, surface, false)))
              : LookupEvidence.none();
    } else if (surface.isEmpty()) {
      status = LookupStatus.EMPTY_TERM_BYPASS;
      candidates = List.of();
      branchEvidence = LookupEvidence.none();
    } else {
      if (strategy == LookupStrategy.ENTITY_AWARE) {
        // Normalized-key resources use the lowercased surface form as their ordinary fallback.
        String lower = surface.toLowerCase(Locale.ROOT);
        LookupBranch ordinaryExact =
            branch(LookupBranch.Role.ORDINARY_EXACT, lexicon, surface, false);
        LookupBranch ordinaryLower =
            lower.equals(surface)
                ? ordinaryExact
                : branch(LookupBranch.Role.ORDINARY_LOWERCASE, lexicon, lower, true);
        LookupBranch entityExact =
            branch(LookupBranch.Role.ENTITY_EXACT, entityLexicon, surface, false);
        branchEvidence =
            new LookupEvidence(
                lower.equals(surface)
                    ? List.of(ordinaryExact, entityExact)
                    : List.of(ordinaryExact, ordinaryLower, entityExact));
        lookupKey = surface;
        LinkedHashSet<CandidateAnalysis> union = new LinkedHashSet<>();
        for (LookupBranch branch : branchEvidence.branches()) union.addAll(branch.candidates());
        candidates = new ArrayList<>(union);
        candidates.sort(null);
        status = candidates.isEmpty() ? LookupStatus.NOT_FOUND : LookupStatus.FOUND;
      } else {
        lookupKey = strategy == LookupStrategy.EXACT ? surface : EnglishText.lower(surface);
        candidates = lexicon.lookup(lookupKey);
        status = candidates.isEmpty() ? LookupStatus.NOT_FOUND : LookupStatus.FOUND;
        LookupBranch.Role role =
            strategy == LookupStrategy.LOWERCASE_CONTROL
                ? LookupBranch.Role.ORDINARY_LOWERCASE
                : LookupBranch.Role.ORDINARY_EXACT;
        branchEvidence =
            new LookupEvidence(
                List.of(
                    new LookupBranch(
                        role,
                        lexicon.resourceId(),
                        lookupKey,
                        strategy == LookupStrategy.LOWERCASE_CONTROL,
                        status,
                        candidates)));
      }
    }

    MorphologySnapshot snapshot =
        new MorphologySnapshot(
            new MorphologyBinding(
                surface,
                offsets.startOffset(),
                offsets.endOffset(),
                increment.getPositionIncrement(),
                length.getPositionLength()),
            lookupKey,
            status,
            candidates,
            keyword.isKeyword(),
            lexicon.resourceId(),
            tokenCase(surface));
    morphology.setSnapshot(snapshot);
    evidence.setEvidence(
        strategy == LookupStrategy.ENTITY_AWARE
            ? LookupEvidence.pending(snapshot, branchEvidence.branches())
            : new LookupEvidence(branchEvidence.branches(), snapshot, false));
    selection.setDecision(
        status == LookupStatus.KEYWORD_BYPASS
            ? SelectionDecision.surfaceOnly(snapshot, SelectionDecision.Reason.UPSTREAM_KEYWORD)
            : status == LookupStatus.EMPTY_TERM_BYPASS
                ? SelectionDecision.surfaceOnly(
                    snapshot, SelectionDecision.Reason.NO_SURVIVING_ANALYSIS)
                : strategy == LookupStrategy.ENTITY_AWARE
                    ? SelectionDecision.surfaceOnly(
                        snapshot, SelectionDecision.Reason.NO_SURVIVING_ANALYSIS)
                    : SelectionDecision.all(snapshot));
    context.setObservation(ContextObservation.notEvaluated());
    return true;
  }

  private static LookupBranch branch(
      LookupBranch.Role role, EnglishLexicon resource, String spelling, boolean transformed) {
    List<CandidateAnalysis> values = resource.lookup(spelling);
    return new LookupBranch(
        role,
        resource.resourceId(),
        spelling,
        transformed,
        values.isEmpty() ? LookupStatus.NOT_FOUND : LookupStatus.FOUND,
        values);
  }

  static TokenCase tokenCase(String text) {
    boolean seenCased = false;
    boolean allUpper = true;
    boolean allLower = true;
    boolean firstUpper = false;
    boolean laterLower = true;
    boolean first = true;
    for (int offset = 0; offset < text.length(); ) {
      int codePoint = text.codePointAt(offset);
      if (Character.isUpperCase(codePoint)
          || Character.isTitleCase(codePoint)
          || Character.isLowerCase(codePoint)) {
        boolean upper = Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint);
        boolean lower = Character.isLowerCase(codePoint);
        seenCased = true;
        allUpper &= upper;
        allLower &= lower;
        if (first) {
          firstUpper = upper;
          first = false;
        } else {
          laterLower &= lower;
        }
      }
      offset += Character.charCount(codePoint);
    }
    if (!seenCased) return TokenCase.NO_CASE;
    if (allUpper) return TokenCase.UPPER;
    if (allLower) return TokenCase.LOWER;
    return firstUpper && laterLower ? TokenCase.INITIAL_CAPITAL : TokenCase.MIXED;
  }
}
