package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.attributes.LookupEvidenceAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologyAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologySelectionAttribute;
import com.bitext.lucene.analysis.en.attributes.OrthographicContextAttribute;
import com.bitext.lucene.analysis.en.model.CandidateAnalysis;
import com.bitext.lucene.analysis.en.model.LookupEvidence;
import com.bitext.lucene.analysis.en.model.MorphologyBinding;
import com.bitext.lucene.analysis.en.model.MorphologySnapshot;
import com.bitext.lucene.analysis.en.model.OutputMode;
import com.bitext.lucene.analysis.en.model.SelectionDecision;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.KeywordAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionLengthAttribute;

/** Emits the surface and/or selected lemmas without performing lookup or selection. */
final class EnglishLemmaOutputFilter extends TokenFilter {
  private final OutputMode mode;
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
  private State base;
  private Iterator<String> extra = List.<String>of().iterator();

  public EnglishLemmaOutputFilter(TokenStream input, OutputMode mode) {
    super(input);
    this.mode = Objects.requireNonNull(mode, "mode");
  }

  @Override
  public boolean incrementToken() throws IOException {
    if (extra.hasNext()) {
      restoreState(base);
      term.setEmpty().append(extra.next());
      increment.setPositionIncrement(0);
      clearMetadata();
      return true;
    }
    if (!input.incrementToken()) {
      return false;
    }

    MorphologySnapshot snapshot = morphology.getSnapshot();
    SelectionDecision decision = selection.getDecision();
    if (snapshot == null || decision == null) {
      throw new IllegalStateException("lemma output requires current morphology and selection");
    }
    LookupEvidence pendingEvidence = evidence.getEvidence();
    if (pendingEvidence != null && pendingEvidence.selectionPending()) {
      if (pendingEvidence.snapshot() != snapshot) {
        throw new IllegalStateException(
            "pending entity evidence belongs to a different morphology snapshot");
      }
      throw new IllegalStateException(
          "entity lookup evidence reached output without its required selection filter");
    }
    checkBinding(snapshot, decision);

    List<String> lemmas =
        keyword.isKeyword()
                || snapshot.keywordAtAnalysis()
                || decision.kind() == SelectionDecision.Kind.SURFACE_ONLY
            ? List.of()
            : decision.candidates().stream().map(CandidateAnalysis::lemma).distinct().toList();
    if (snapshot.keywordAtAnalysis()) {
      keyword.setKeyword(true);
    }

    List<String> outputs = new ArrayList<>();
    if (mode == OutputMode.EXPAND || lemmas.isEmpty()) {
      outputs.add(term.toString());
    }
    for (String lemma : lemmas) {
      if (!outputs.contains(lemma)) {
        outputs.add(lemma);
      }
    }
    String first = outputs.removeFirst();
    base = captureState();
    term.setEmpty().append(first);
    clearMetadata();
    extra = outputs.iterator();
    return true;
  }

  private void checkBinding(MorphologySnapshot snapshot, SelectionDecision decision) {
    MorphologyBinding binding = snapshot.binding();
    if (!term.toString().equals(binding.surface())
        || offsets.startOffset() != binding.startOffset()
        || offsets.endOffset() != binding.endOffset()
        || increment.getPositionIncrement() != binding.positionIncrement()
        || length.getPositionLength() != binding.positionLength()) {
      throw new IllegalStateException("morphology binding does not match live token");
    }
    if (decision.snapshot() != snapshot) {
      throw new IllegalStateException("selection belongs to a different morphology snapshot");
    }
  }

  private void clearMetadata() {
    morphology.setSnapshot(null);
    selection.setDecision(null);
    context.setObservation(null);
    evidence.setEvidence(null);
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    base = null;
    extra = List.<String>of().iterator();
  }

  @Override
  public void close() throws IOException {
    base = null;
    extra = List.<String>of().iterator();
    super.close();
  }
}
