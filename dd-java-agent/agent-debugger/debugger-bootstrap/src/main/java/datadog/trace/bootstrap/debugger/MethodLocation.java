package datadog.trace.bootstrap.debugger;

public enum MethodLocation {
  DEFAULT,
  ENTRY,
  EXIT;

  public static boolean isSame(MethodLocation methodLocation, MethodLocation evaluateAt) {
    if (methodLocation == MethodLocation.DEFAULT) {
      // line probe, no evaluation of probe's evaluateAt
      return true;
    }
    if (methodLocation == MethodLocation.ENTRY) {
      return evaluateAt == MethodLocation.DEFAULT || evaluateAt == MethodLocation.ENTRY;
    }
    return methodLocation == evaluateAt;
  }
}
