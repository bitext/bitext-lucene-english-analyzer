package com.bitext.lucene.analysis.en;

import com.bitext.lucene.analysis.en.attributes.MorphologyAttribute;
import com.bitext.lucene.analysis.en.attributes.MorphologySelectionAttribute;
import com.bitext.lucene.analysis.en.attributes.OrthographicContextAttribute;
import com.bitext.lucene.analysis.en.model.*;
import java.io.IOException;
import java.util.*;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.*;

/** Bounded observer for one unresolved declared scope. */
final class EnglishContextFilter extends TokenFilter {
  static final class Config {
    private final ContextObservation.Scope scope;
    private final ContextBudget budget;
    private final CaseStyleDetector detector;
    private final EnglishSelectionPolicy policy;

    public Config(ContextObservation.Scope scope, ContextBudget budget, String policyId) {
      this(
          scope,
          budget,
          EnglishCaseStyleDetector.INSTANCE,
          EnglishSelectionPolicies.named(policyId));
    }

    public Config(
        ContextObservation.Scope scope,
        ContextBudget budget,
        CaseStyleDetector detector,
        EnglishSelectionPolicy policy) {
      this.scope = Objects.requireNonNull(scope);
      this.budget = Objects.requireNonNull(budget);
      this.detector = Objects.requireNonNull(detector);
      this.policy = Objects.requireNonNull(policy);
    }

    public ContextObservation.Scope scope() {
      return scope;
    }

    public ContextBudget budget() {
      return budget;
    }

    public CaseStyleDetector detector() {
      return detector;
    }

    public EnglishSelectionPolicy policy() {
      return policy;
    }

    public String policyId() {
      return policy.id();
    }
  }

  private record Pending(State state, String surface) {}

  private final Config config;
  private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
  private final OffsetAttribute offsets = addAttribute(OffsetAttribute.class);
  private final PositionIncrementAttribute increment =
      addAttribute(PositionIncrementAttribute.class);
  private final PositionLengthAttribute length = addAttribute(PositionLengthAttribute.class);
  private final MorphologyAttribute morphology = addAttribute(MorphologyAttribute.class);
  private final MorphologySelectionAttribute selection =
      addAttribute(MorphologySelectionAttribute.class);
  private final OrthographicContextAttribute context =
      addAttribute(OrthographicContextAttribute.class);
  private final SentenceAttribute sentence = addAttribute(SentenceAttribute.class);
  private final Deque<Pending> pending = new ArrayDeque<>();
  private Pending deferred;
  private int pendingChars;
  private Integer sentenceId;
  private Integer incompleteSentenceId;
  private boolean exhausted, outputReady, scopeFallback, fieldNonLinear;

  public EnglishContextFilter(TokenStream input, Config config) {
    super(input);
    this.config = Objects.requireNonNull(config);
  }

  public int pendingTokenCount() {
    return pending.size() + (deferred == null ? 0 : 1);
  }

  public int pendingSurfaceChars() {
    return pendingChars + (deferred == null ? 0 : deferred.surface().length());
  }

  @Override
  public boolean incrementToken() throws IOException {
    while (true) {
      if (outputReady && !pending.isEmpty()) {
        Pending next = pending.removeFirst();
        pendingChars -= next.surface().length();
        restoreState(next.state());
        return true;
      }
      if (outputReady) {
        outputReady = false;
        clearPending();
        if (exhausted) return false;
      }
      if (deferred != null) {
        Pending next = deferred;
        deferred = null;
        restoreState(next.state());
      } else {
        if (exhausted) return false;
        if (!input.incrementToken()) {
          exhausted = true;
          if (!pending.isEmpty()) {
            finalizeScope(finalStatus());
            continue;
          }
          return false;
        }
      }
      if (nonLinear()) fieldNonLinear = true;
      ContextObservation.Status transition = handleScopeTransition();
      if (outputReady) continue;
      if (fieldNonLinear) {
        if (!pending.isEmpty()) {
          // The old prefix must replay before the graph edge that triggered fallback.
          defer();
          finalizeScope(ContextObservation.Status.NON_LINEAR_INPUT);
          continue;
        }
        directFallback(ContextObservation.Status.NON_LINEAR_INPUT);
        return true;
      }
      if (transition != null) {
        directFallback(transition);
        return true;
      }
      if (scopeFallback) {
        directFallback(ContextObservation.Status.LIMIT_REACHED);
        return true;
      }
      if (exceedsBudget()) {
        scopeFallback = true;
        if (!pending.isEmpty()) {
          defer();
          finalizeScope(ContextObservation.Status.LIMIT_REACHED);
          continue;
        }
        directFallback(ContextObservation.Status.LIMIT_REACHED);
        return true;
      }
      addCurrent();
    }
  }

  private ContextObservation.Status handleScopeTransition() {
    if (config.scope() != ContextObservation.Scope.SUPPLIED_SENTENCES) return null;
    if (!sentence.hasSentenceMetadata()) {
      if (sentenceId != null) incompleteSentenceId = sentenceId;
      sentenceId = null;
      if (!pending.isEmpty()) {
        defer();
        finalizeScope(ContextObservation.Status.MISSING_BOUNDARY_METADATA);
      }
      return ContextObservation.Status.MISSING_BOUNDARY_METADATA;
    }
    Integer current = sentence.sentenceId();
    if (incompleteSentenceId != null && incompleteSentenceId.equals(current)) {
      sentenceId = current;
      return ContextObservation.Status.MISSING_BOUNDARY_METADATA;
    }
    if (incompleteSentenceId != null && !incompleteSentenceId.equals(current)) {
      incompleteSentenceId = null;
      scopeFallback = false;
    }
    if (sentenceId != null && !sentenceId.equals(current)) {
      if (!pending.isEmpty()) {
        defer();
        finalizeScope(finalStatus());
        return null;
      }
      scopeFallback = false;
    }
    sentenceId = current;
    return null;
  }

  private boolean nonLinear() {
    return increment.getPositionIncrement() == 0
        || increment.getPositionIncrement() > 1
        || length.getPositionLength() != 1;
  }

  private boolean exceedsBudget() {
    return pending.size() >= config.budget().maxPendingTokens()
        || pendingChars + term.length() > config.budget().maxPendingSurfaceChars();
  }

  private void defer() {
    deferred = new Pending(captureState(), term.toString());
  }

  private void addCurrent() {
    String surface = term.toString();
    pending.addLast(new Pending(captureState(), surface));
    pendingChars += surface.length();
  }

  private ContextObservation.Status finalStatus() {
    return fieldNonLinear
        ? ContextObservation.Status.NON_LINEAR_INPUT
        : scopeFallback
            ? ContextObservation.Status.LIMIT_REACHED
            : ContextObservation.Status.COMPLETE;
  }

  private void finalizeScope(ContextObservation.Status status) {
    ContextSpan span = span();
    ContextObservation observed = observe(status, span);
    int index = 0;
    for (int remaining = pending.size(); remaining > 0; remaining--) {
      Pending token = pending.removeFirst();
      restoreState(token.state());
      MorphologySnapshot snapshot = morphology.getSnapshot();
      SelectionDecision current = selection.getDecision();
      validateLive(snapshot, current);
      context.setObservation(observed);
      if (status == ContextObservation.Status.COMPLETE) {
        selection.setDecision(
            validatePolicy(
                snapshot,
                current,
                config
                    .policy()
                    .select(new SelectionContext(observed, span, index), snapshot, current)));
      }
      pending.addLast(new Pending(captureState(), token.surface()));
      index++;
    }
    outputReady = true;
  }

  private void directFallback(ContextObservation.Status status) {
    validateLive(morphology.getSnapshot(), selection.getDecision());
    context.setObservation(fallback(status));
  }

  private void validateLive(MorphologySnapshot snapshot, SelectionDecision current) {
    if (snapshot == null || current == null || current.snapshot() != snapshot) {
      throw new IllegalStateException(
          "context selection is not bound to the live morphology snapshot");
    }
    MorphologyBinding binding = snapshot.binding();
    if (!binding.surface().contentEquals(term)
        || binding.startOffset() != offsets.startOffset()
        || binding.endOffset() != offsets.endOffset()
        || binding.positionIncrement() != increment.getPositionIncrement()
        || binding.positionLength() != length.getPositionLength()) {
      throw new IllegalStateException(
          "live token does not match morphology binding before contextual selection");
    }
  }

  private ContextSpan span() {
    List<ContextToken> tokens = new ArrayList<>(pending.size());
    for (Pending token : pending) {
      restoreState(token.state());
      MorphologySnapshot snapshot = morphology.getSnapshot();
      SelectionDecision current = selection.getDecision();
      validateLive(snapshot, current);
      tokens.add(new ContextToken(token.surface(), snapshot, current));
    }
    return new ContextSpan(tokens);
  }

  private ContextObservation observe(ContextObservation.Status status, ContextSpan span) {
    List<String> surfaces = span.tokens().stream().map(ContextToken::surface).toList();
    int cased =
        (int)
            surfaces.stream()
                .filter(s -> EnglishMorphologyFilter.tokenCase(s) != TokenCase.NO_CASE)
                .count();
    return new ContextObservation(
        config.scope(),
        status,
        config.detector().observe(span),
        cased,
        count(surfaces, TokenCase.LOWER),
        count(surfaces, TokenCase.INITIAL_CAPITAL),
        count(surfaces, TokenCase.UPPER),
        count(surfaces, TokenCase.MIXED),
        status == ContextObservation.Status.COMPLETE,
        config.detector().id(),
        config.policyId(),
        numeric(surfaces),
        alphanumeric(surfaces));
  }

  private static int count(List<String> values, TokenCase tokenCase) {
    return (int)
        values.stream().filter(v -> EnglishMorphologyFilter.tokenCase(v) == tokenCase).count();
  }

  private static int numeric(List<String> values) {
    return (int)
        values.stream()
            .filter(v -> !v.isEmpty() && v.codePoints().allMatch(Character::isDigit))
            .count();
  }

  private static int alphanumeric(List<String> values) {
    return (int)
        values.stream()
            .filter(
                v ->
                    v.codePoints().anyMatch(Character::isDigit)
                        && v.codePoints().anyMatch(Character::isLetter))
            .count();
  }

  private static SelectionDecision validatePolicy(
      MorphologySnapshot snapshot, SelectionDecision current, SelectionDecision proposed) {
    if (proposed == null || proposed.snapshot() != snapshot) {
      throw new IllegalStateException("selection policy returned a foreign morphology snapshot");
    }
    if (current.kind() == SelectionDecision.Kind.SURFACE_ONLY
        && proposed.kind() != SelectionDecision.Kind.SURFACE_ONLY) {
      throw new IllegalStateException("selection policy cannot undo surface protection");
    }
    if (current.kind() == SelectionDecision.Kind.SUBSET
        && (proposed.kind() == SelectionDecision.Kind.ALL
            || (proposed.kind() == SelectionDecision.Kind.SUBSET
                && !current.ordinals().containsAll(proposed.ordinals())))) {
      throw new IllegalStateException("selection policy cannot re-enable removed candidates");
    }
    return proposed;
  }

  private ContextObservation fallback(ContextObservation.Status status) {
    return new ContextObservation(
        config.scope(),
        status,
        ContextObservation.Style.UNDETERMINED,
        0,
        0,
        0,
        0,
        0,
        false,
        config.detector().id(),
        config.policyId());
  }

  private void clearPending() {
    pending.clear();
    pendingChars = 0;
  }

  @Override
  public void reset() throws IOException {
    super.reset();
    clearPending();
    deferred = null;
    sentenceId = null;
    incompleteSentenceId = null;
    exhausted = false;
    outputReady = false;
    scopeFallback = false;
    fieldNonLinear = false;
  }

  @Override
  public void close() throws IOException {
    clearPending();
    deferred = null;
    super.close();
  }
}
