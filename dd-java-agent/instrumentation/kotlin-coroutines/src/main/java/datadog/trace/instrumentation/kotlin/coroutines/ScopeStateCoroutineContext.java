package datadog.trace.instrumentation.kotlin.coroutines;

import static datadog.trace.instrumentation.kotlin.coroutines.CoroutineContextHelper.getJob;

import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentTracer;
import datadog.trace.bootstrap.instrumentation.api.ScopeState;
import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.ThreadContextElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ScopeStateCoroutineContext
    implements ThreadContextElement<ScopeState>, Function1<Throwable, Unit> {

  private static final Key<ScopeStateCoroutineContext> KEY = new ContextElementKey();
  private final ScopeState coroutineScopeState;
  @Nullable private final AgentScope.Continuation continuation;
  @Nullable private AgentScope continuationScope;

  public ScopeStateCoroutineContext() {
    coroutineScopeState = AgentTracer.get().newScopeState();
    final AgentScope activeScope = AgentTracer.get().activeScope();
    if (activeScope != null) {
      activeScope.setAsyncPropagation(true);
      continuation = activeScope.captureConcurrent();
    } else {
      continuation = null;
    }
  }

  @Override
  public void restoreThreadContext(
      @NotNull final CoroutineContext coroutineContext, final ScopeState oldState) {
    oldState.activate();
  }

  @Override
  public ScopeState updateThreadContext(@NotNull final CoroutineContext coroutineContext) {
    final ScopeState oldScopeState = AgentTracer.get().newScopeState();
    oldScopeState.fetchFromActive();

    coroutineScopeState.activate();

    if (continuation != null && continuationScope == null) {
      continuationScope = continuation.activate();
      registerOnCompletionCallback(coroutineContext);
    }

    return oldScopeState;
  }

  @Nullable
  @Override
  public <E extends Element> E get(@NotNull final Key<E> key) {
    return CoroutineContext.Element.DefaultImpls.get(this, key);
  }

  @NotNull
  @Override
  public CoroutineContext minusKey(@NotNull final Key<?> key) {
    return CoroutineContext.Element.DefaultImpls.minusKey(this, key);
  }

  @NotNull
  @Override
  public CoroutineContext plus(@NotNull final CoroutineContext coroutineContext) {
    return CoroutineContext.DefaultImpls.plus(this, coroutineContext);
  }

  @Override
  public <R> R fold(
      R initial, @NotNull Function2<? super R, ? super Element, ? extends R> operation) {
    return CoroutineContext.Element.DefaultImpls.fold(this, initial, operation);
  }

  @NotNull
  @Override
  public Key<?> getKey() {
    return KEY;
  }

  public void registerOnCompletionCallback(final CoroutineContext coroutineContext) {
    getJob(coroutineContext).invokeOnCompletion(this);
  }

  private void closeScopeAndCancelContinuation() {
    final ScopeState currentThreadScopeState = AgentTracer.get().newScopeState();
    currentThreadScopeState.fetchFromActive();

    coroutineScopeState.activate();

    if (continuationScope != null) {
      continuationScope.close();
    }
    continuation.cancel();

    currentThreadScopeState.activate();
  }

  @Override
  public Unit invoke(Throwable throwable) {
    closeScopeAndCancelContinuation();

    return Unit.INSTANCE;
  }

  static class ContextElementKey implements Key<ScopeStateCoroutineContext> {}
}
