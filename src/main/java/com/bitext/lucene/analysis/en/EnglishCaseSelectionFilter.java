package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.attributes.LookupEvidenceAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologyAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologySelectionAttribute;
import com.bitext.lucene.analysis.en.model.CandidateAnalysis;
import com.bitext.lucene.analysis.en.model.LookupBranch;
import com.bitext.lucene.analysis.en.model.LookupEvidence;
import com.bitext.lucene.analysis.en.model.LookupStatus;
import com.bitext.lucene.analysis.en.model.MorphologyBinding;
import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import com.bitext.lucene.analysis.en.model.SelectionDecision;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;

/** Applies the bounded initial-capital entity selection policy. */
final class EnglishCaseSelectionFilter extends TokenFilter {
  private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
  private final KeywordAttribute keyword = addAttribute(KeywordAttribute.class);
  private final OffsetAttribute offsets = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute increment =
      addAttribute(PositionIncrementAttribute.class);
  private final PositionLengthAttribute length = addAttribute(PositionLengthAttribute.class);
  private final SentenceAttribute sentence = addAttribute(SentenceAttribute.class);
  private final MorphologyAttribute morphology = addAttribute(MorphologyAttribute.class);
  private final MorphologySelectionAttribute selection =
      addAttribute(MorphologySelectionAttribute.class);
  private final LookupEvidenceAttribute evidence = addAttribute(LookupEvidenceAttribute.class);
  private State deferred;
  private Integer previousSentence;

  public EnglishCaseSelectionFilter(TokenStream input) {
    super(input);
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (deferred != null) {
      restoreState(deferred);
      deferred = null;
    } else if (!input.incrementToken()) {
      return false;
    }

    MorphologySnapshot snapshot = morphology.getSnapshot();
    LookupEvidence branches = evidence.getEvidence();
    validate(snapshot, branches);
    if (keyword.isKeyword() || snapshot.keywordAtAnalysis()) {
      completeSurface(snapshot, branches, SelectionDecision.Reason.UPSTREAM_KEYWORD);
    } else if (snapshot.lookupStatus() == LookupStatus.EMPTY_TERM_BYPASS) {
      completeSurface(snapshot, branches, SelectionDecision.Reason.NO_SURVIVING_ANALYSIS);
    } else if (!isInitialCapital(term.toString())) {
      choose(snapshot, branches.required(LookupBranch.Role.ORDINARY_EXACT));
    } else {
      chooseInitialCapital(snapshot, branches);
    }
    rememberSentence();
    return true;
  }

  private void chooseInitialCapital(MorphologySnapshot snapshot, LookupEvidence branches)
      throws IOException {
    LookupBranch entity = branches.required(LookupBranch.Role.ENTITY_EXACT);
    if (entity.status() != LookupStatus.FOUND) {
      choose(snapshot, ordinaryLower(branches));
      return;
    }
    if (!sentence.hasSentenceMetadata() || !isLinear() || !isSentenceInitial()) {
      choose(snapshot, entity);
      return;
    }
    State current = captureState();
    boolean protect = hasEntityLikeLookahead(sentence.sentenceId());
    restoreState(current);
    choose(snapshot, protect ? entity : ordinaryLower(branches));
  }

  private boolean hasEntityLikeLookahead(int currentSentence) throws IOException {
    if (!input.incrementToken()) {
      return false;
    }
    deferred = captureState();
    LookupEvidence next = evidence.getEvidence();
    return sentence.hasSentenceMetadata()
        && sentence.sentenceId() == currentSentence
        && isLinear()
        && hasUpper(term.toString())
        && next != null
        && next.branches().stream()
            .anyMatch(
                branch ->
                    branch.role() == LookupBranch.Role.ENTITY_EXACT
                        && branch.status() == LookupStatus.FOUND);
  }

  private LookupBranch ordinaryLower(LookupEvidence branches) {
    return branches.branches().stream()
        .filter(branch -> branch.role() == LookupBranch.Role.ORDINARY_LOWERCASE)
        .findFirst()
        .orElseGet(() -> branches.required(LookupBranch.Role.ORDINARY_EXACT));
  }

  private void choose(MorphologySnapshot snapshot, LookupBranch branch) {
    List<Integer> ordinals = new ArrayList<>();
    for (CandidateAnalysis candidate : branch.candidates()) {
      int ordinal = snapshot.candidates().indexOf(candidate);
      if (ordinal < 0) {
        throw new IllegalStateException("branch candidate is absent from live morphology snapshot");
      }
      if (!ordinals.contains(ordinal)) {
        ordinals.add(ordinal);
      }
    }
    ordinals.sort(null);
    selection.setDecision(
        ordinals.isEmpty()
            ? SelectionDecision.surfaceOnly(
                snapshot, SelectionDecision.Reason.NO_SURVIVING_ANALYSIS)
            : SelectionDecision.subset(snapshot, ordinals));
    evidence.setEvidence(evidence.getEvidence().complete(snapshot));
  }

  private void completeSurface(
      MorphologySnapshot snapshot, LookupEvidence branches, SelectionDecision.Reason reason) {
    selection.setDecision(SelectionDecision.surfaceOnly(snapshot, reason));
    evidence.setEvidence(branches.complete(snapshot));
  }

  private boolean isSentenceInitial() {
    return previousSentence == null || !previousSentence.equals(sentence.sentenceId());
  }

  private void rememberSentence() {
    previousSentence = sentence.hasSentenceMetadata() ? sentence.sentenceId() : null;
  }

  private boolean isLinear() {
    return increment.getPositionIncrement() == 1 && length.getPositionLength() == 1;
  }

  private void validate(MorphologySnapshot snapshot, LookupEvidence branches) {
    if (snapshot == null
        || branches == null
        || !branches.selectionPending()
        || branches.snapshot() != snapshot) {
      throw new IllegalStateException(
          "entity selection requires pending lookup evidence bound to live morphology");
    }
    SelectionDecision existing = selection.getDecision();
    if (existing == null
        || existing.snapshot() != snapshot
        || existing.kind() != SelectionDecision.Kind.SURFACE_ONLY
        || (snapshot.keywordAtAnalysis()
            && existing.reason() != SelectionDecision.Reason.UPSTREAM_KEYWORD)
        || (!snapshot.keywordAtAnalysis()
            && existing.reason() != SelectionDecision.Reason.NO_SURVIVING_ANALYSIS)) {
      throw new IllegalStateException("entity selection refuses a foreign or completed decision");
    }
    MorphologyBinding binding = snapshot.binding();
    if (!binding.surface().contentEquals(term)
        || binding.startOffset() != offsets.startOffset()
        || binding.endOffset() != offsets.endOffset()
        || binding.positionIncrement() != increment.getPositionIncrement()
        || binding.positionLength() != length.getPositionLength()) {
      throw new IllegalStateException("live token does not match the morphology binding");
    }
  }

  private static boolean isInitialCapital(String text) {
    if (text.isEmpty()) {
      return false;
    }
    int first = text.codePointAt(0);
    if (!Character.isUpperCase(first) && !Character.isTitleCase(first)) {
      return false;
    }
    boolean lower = false;
    for (int offset = Character.charCount(first); offset < text.length(); ) {
      int codePoint = text.codePointAt(offset);
      if (Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint)) {
        return false;
      }
      lower |= Character.isLowerCase(codePoint);
      offset += Character.charCount(codePoint);
    }
    return lower;
  }

  private static boolean hasUpper(String text) {
    return text.codePoints()
        .anyMatch(
            codePoint -> Character.isUpperCase(codePoint) || Character.isTitleCase(codePoint));
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    deferred = null;
    previousSentence = null;
  }
}
