package datadog.trace.bootstrap.instrumentation.api;

import datadog.trace.api.Stateful;
import datadog.trace.api.profiling.Profiling;
import datadog.trace.api.profiling.ProfilingContextAttribute;
import datadog.trace.api.profiling.ProfilingScope;

public interface ProfilingContextIntegration extends Profiling {
  /**
   * invoked when the profiler is started, implementations must not initialise JFR before this is
   * called.
   */
  default void onStart() {}
  /** Invoked when a trace first propagates to a thread */
  default void onAttach() {}

  /** Invoked when a thread exits */
  default void onDetach() {}

  default Stateful newScopeState(ProfilerContext profilerContext) {
    return Stateful.DEFAULT;
  }

  default int encode(CharSequence constant) {
    return 0;
  }

  default int encodeOperationName(CharSequence constant) {
    return 0;
  }

  default int encodeResourceName(CharSequence constant) {
    return 0;
  }

  String name();

  final class NoOp implements ProfilingContextIntegration {

    public static final ProfilingContextIntegration INSTANCE =
        new ProfilingContextIntegration.NoOp();

    @Override
    public ProfilingContextAttribute createContextAttribute(String attribute) {
      return ProfilingContextAttribute.NoOp.INSTANCE;
    }

    @Override
    public ProfilingScope newScope() {
      return ProfilingScope.NO_OP;
    }

    @Override
    public void onAttach() {}

    @Override
    public void onDetach() {}

    @Override
    public String name() {
      return "none";
    }
  }
}
