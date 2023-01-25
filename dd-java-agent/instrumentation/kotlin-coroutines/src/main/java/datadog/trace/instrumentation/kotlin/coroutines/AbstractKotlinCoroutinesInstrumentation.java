package datadog.trace.instrumentation.kotlin.coroutines;

import datadog.trace.agent.tooling.Instrumenter;

abstract class AbstractKotlinCoroutinesInstrumentation extends Instrumenter.Tracing {
  protected static final String ABSTRACT_COROUTINE_CLASS_NAME =
      "kotlinx.coroutines.AbstractCoroutine";
  protected static final String COROUTINE_CONTEXT_KT_CLASS_NAME =
      "kotlinx.coroutines.CoroutineContextKt";
  protected static final String COROUTINE_CONTEXT_CLASS_NAME = "kotlin.coroutines.CoroutineContext";

  private static final String INSTRUMENTATION_NAME = "kotlin_coroutine.experimental";

  public AbstractKotlinCoroutinesInstrumentation() {
    super(INSTRUMENTATION_NAME);
  }

  static String prefixedPropertyName(final String propertyName) {
    return "dd.integration." + INSTRUMENTATION_NAME + "." + propertyName;
  }

  @Override
  protected final boolean defaultEnabled() {
    return false;
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ScopeStateCoroutineContext",
      packageName + ".ScopeStateCoroutineContext$ContextElementKey",
      packageName + ".CoroutineContextHelper",
      packageName + ".AbstractKotlinCoroutinesInstrumentation",
    };
  }
}
