package datadog.trace.instrumentation.kotlin.coroutines;

import datadog.trace.agent.tooling.Instrumenter;

abstract class AbstractKotlinCoroutinesInstrumentation extends Instrumenter.Tracing {
  protected static final String ABSTRACT_COROUTINE_CLASS_NAME =
      "kotlinx.coroutines.AbstractCoroutine";
  protected static final String COROUTINE_CONTEXT_KT_CLASS_NAME =
      "kotlinx.coroutines.CoroutineContextKt";
  protected static final String COROUTINE_CONTEXT_CLASS_NAME = "kotlin.coroutines.CoroutineContext";

  public AbstractKotlinCoroutinesInstrumentation(final String instrumentationName) {
    super(instrumentationName);
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ScopeStateCoroutineContext",
      packageName + ".ScopeStateCoroutineContext$ContextElementKey",
      packageName + ".CoroutineContextHelper",
    };
  }
}
