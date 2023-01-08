package datadog.trace.instrumentation.kotlin.coroutines;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import kotlin.coroutines.CoroutineContext;
import net.bytebuddy.asm.Advice;

@AutoService(Instrumenter.class)
public class CoroutineContextKtInstrumentation extends AbstractKotlinCoroutinesInstrumentation
    implements Instrumenter.ForSingleType {

  public CoroutineContextKtInstrumentation() {
    super("kotlin-coroutine-context-kt");
  }

  @Override
  public String instrumentedType() {
    return COROUTINE_CONTEXT_KT_CLASS_NAME;
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isMethod()
            .and(isDeclaredBy(named(COROUTINE_CONTEXT_KT_CLASS_NAME)))
            .and(named("newCoroutineContext"))
            .and(takesArguments(2))
            .and(takesArgument(1, named(COROUTINE_CONTEXT_CLASS_NAME))),
        CoroutineContextKtInstrumentation.class.getName() + "$NewCoroutineContextAdvice");
  }

  public static class NewCoroutineContextAdvice {
    @SuppressWarnings("UnusedAssignment")
    @Advice.OnMethodEnter
    public static void newCoroutineInvocation(
        @Advice.Argument(value = 1, readOnly = false) CoroutineContext coroutineContext) {
      if (coroutineContext != null) {
        coroutineContext = coroutineContext.plus(new ScopeStateCoroutineContext());
      }
    }
  }
}
