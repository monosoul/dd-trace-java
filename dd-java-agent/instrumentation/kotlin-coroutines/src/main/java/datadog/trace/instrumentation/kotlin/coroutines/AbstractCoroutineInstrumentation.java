package datadog.trace.instrumentation.kotlin.coroutines;

import static datadog.trace.agent.tooling.bytebuddy.matcher.HierarchyMatchers.extendsClass;
import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.instrumentation.kotlin.coroutines.CoroutineContextHelper.getScopeStateContext;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isOverriddenFrom;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.AbstractCoroutine;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public class AbstractCoroutineInstrumentation extends AbstractKotlinCoroutinesInstrumentation
    implements Instrumenter.ForTypeHierarchy {

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isConstructor()
            .and(isDeclaredBy(named(ABSTRACT_COROUTINE_CLASS_NAME)))
            .and(takesArguments(2))
            .and(takesArgument(0, named(COROUTINE_CONTEXT_CLASS_NAME)))
            .and(takesArgument(1, named("boolean"))),
        AbstractCoroutineInstrumentation.class.getName() + "$AbstractCoroutineConstructorAdvice");

    transformation.applyAdvice(
        isMethod()
            .and(isOverriddenFrom(named(ABSTRACT_COROUTINE_CLASS_NAME)))
            .and(named("onStart"))
            .and(takesNoArguments())
            .and(returns(void.class)),
        AbstractCoroutineInstrumentation.class.getName() + "$AbstractCoroutineOnStartAdvice");
  }

  @Override
  public String hierarchyMarkerType() {
    return ABSTRACT_COROUTINE_CLASS_NAME;
  }

  @Override
  public ElementMatcher<TypeDescription> hierarchyMatcher() {
    return extendsClass(named(hierarchyMarkerType()));
  }

  public static class AbstractCoroutineConstructorAdvice {
    @Advice.OnMethodEnter
    public static void constructorInvocation(
        @Advice.Argument(value = 0) final CoroutineContext parentContext,
        @Advice.Argument(value = 1) final boolean active) {
      final ScopeStateCoroutineContext scopeStackContext = getScopeStateContext(parentContext);
      if (scopeStackContext != null && active) {
        // if this is not a lazy coroutine, inherit parent span from the coroutine constructor call
        // site
        scopeStackContext.maybeInitialize();
      }
    }
  }

  public static class AbstractCoroutineOnStartAdvice {
    @Advice.OnMethodEnter
    public static void onStartInvocation(@Advice.This final AbstractCoroutine<?> coroutine) {
      final ScopeStateCoroutineContext scopeStackContext =
          getScopeStateContext(coroutine.getContext());
      if (scopeStackContext != null) {
        // try to inherit parent span from the coroutine start call site
        scopeStackContext.maybeInitialize();
      }
    }
  }
}
