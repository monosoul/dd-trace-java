package datadog.trace.instrumentation.jetty70;

import static datadog.trace.agent.tooling.bytebuddy.matcher.NameMatchers.named;
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activateSpan;
import static datadog.trace.bootstrap.instrumentation.decorator.HttpServerDecorator.DD_SPAN_ATTRIBUTE;
import static datadog.trace.instrumentation.jetty70.JettyDecorator.DECORATE;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesNoArguments;

import com.google.auto.service.AutoService;
import datadog.trace.agent.tooling.Instrumenter;
import datadog.trace.api.Config;
import datadog.trace.api.CorrelationIdentifier;
import datadog.trace.api.GlobalTracer;
import datadog.trace.api.ProductActivationConfig;
import datadog.trace.bootstrap.InstrumentationContext;
import datadog.trace.bootstrap.instrumentation.api.AgentScope;
import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import datadog.trace.instrumentation.jetty.ConnectionHandleRequestVisitor;
import java.security.ProtectionDomain;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Opcodes;
import net.bytebuddy.pool.TypePool;
import net.bytebuddy.utility.JavaModule;
import org.eclipse.jetty.http.Generator;
import org.eclipse.jetty.server.HttpConnection;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;

@AutoService(Instrumenter.class)
public final class JettyServerInstrumentation extends Instrumenter.Tracing
    implements Instrumenter.ForSingleType {

  public JettyServerInstrumentation() {
    super("jetty");
  }

  @Override
  public String instrumentedType() {
    return "org.eclipse.jetty.server.HttpConnection";
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".ExtractAdapter",
      packageName + ".ExtractAdapter$Request",
      packageName + ".ExtractAdapter$Response",
      packageName + ".JettyDecorator",
      packageName + ".RequestURIDataAdapter",
      "datadog.trace.instrumentation.jetty.JettyBlockingHelper",
    };
  }

  @Override
  public Map<String, String> contextStore() {
    // The lifecycle of these objects are aligned, and are recycled by jetty, minimizing leak risk.
    return singletonMap("org.eclipse.jetty.http.Generator", "org.eclipse.jetty.server.Response");
  }

  @Override
  public void adviceTransformations(AdviceTransformation transformation) {
    transformation.applyAdvice(
        isConstructor(), JettyServerInstrumentation.class.getName() + "$ConstructorAdvice");
    transformation.applyAdvice(
        named("handleRequest").and(takesNoArguments()),
        JettyServerInstrumentation.class.getName() + "$HandleRequestAdvice");
    transformation.applyAdvice(
        named("reset").and(takesArgument(0, boolean.class)),
        JettyServerInstrumentation.class.getName() + "$ResetAdvice");
  }

  @Override
  public AdviceTransformer transformer() {
    return new AdviceTransformer() {
      @Override
      public DynamicType.Builder<?> transform(
          DynamicType.Builder<?> builder,
          TypeDescription typeDescription,
          ClassLoader classLoader,
          JavaModule module,
          ProtectionDomain pd) {
        return builder.visit(new ConnectionHandleRequestVisitorWrapper());
      }
    };
  }

  private static class ConnectionHandleRequestVisitorWrapper implements AsmVisitorWrapper {

    @Override
    public int mergeWriter(int flags) {
      return flags | ClassWriter.COMPUTE_MAXS;
    }

    @Override
    public int mergeReader(int flags) {
      return flags;
    }

    @Override
    public ClassVisitor wrap(
        TypeDescription instrumentedType,
        ClassVisitor classVisitor,
        Implementation.Context implementationContext,
        TypePool typePool,
        FieldList<FieldDescription.InDefinedShape> fields,
        MethodList<?> methods,
        int writerFlags,
        int readerFlags) {
      if (Config.get().getAppSecEnabledConfig() == ProductActivationConfig.FULLY_DISABLED) {
        return classVisitor;
      }

      return new ConnectionHandleRequestVisitor(
          Opcodes.ASM7, classVisitor, "org/eclipse/jetty/server/HttpConnection");
    }
  }

  /**
   * HttpConnection's have both a generator and a response instance. The generator is what writes
   * out the final bytes that are sent back to the requestor. We read the status code from the
   * response in ResetAdvice, but in some cases the final status code is only set in the generator
   * directly, not the response. (For example, this happens when an exception is thrown and jetty
   * must send a 500 status.) We use the JettyGeneratorInstrumentation to ensure that the response
   * is updated when the generator is. Since the status on the response is reset when the connection
   * is reset, this minor change in behavior is inconsequential. This advice provides the needed
   * link between generator -> response to enable this.
   */
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void link(
        @Advice.FieldValue("_generator") final Generator generator,
        @Advice.FieldValue("_response") final Response response) {
      InstrumentationContext.get(Generator.class, Response.class).put(generator, response);
    }
  }

  /**
   * The handleRequest call denotes the earliest point at which the incoming request is fully
   * parsed. This allows us to read the headers from the request to extract propagation info.
   */
  public static class HandleRequestAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static AgentScope onEnter(
        @Advice.This final HttpConnection connection, @Advice.Local("agentSpan") AgentSpan span) {
      Request req = connection.getRequest();

      Object existingSpan = req.getAttribute(DD_SPAN_ATTRIBUTE);
      if (existingSpan instanceof AgentSpan) {
        // Request already gone through initial processing, so just activate the span.
        return activateSpan((AgentSpan) existingSpan);
      }

      final AgentSpan.Context.Extracted extractedContext = DECORATE.extract(req);
      span = DECORATE.startSpan(req, extractedContext);
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, req, req, extractedContext);

      final AgentScope scope = activateSpan(span);
      scope.setAsyncPropagation(true);
      req.setAttribute(DD_SPAN_ATTRIBUTE, span);
      req.setAttribute(CorrelationIdentifier.getTraceIdKey(), GlobalTracer.get().getTraceId());
      req.setAttribute(CorrelationIdentifier.getSpanIdKey(), GlobalTracer.get().getSpanId());
      return scope;
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void closeScope(@Advice.Enter final AgentScope scope) {
      // Span is finished when the connection is reset, so we only need to close the scope here.
      scope.close();
    }
  }

  /**
   * Jetty ensures that connections are reset immediately after the response is sent. This provides
   * a reliable point to finish the server span at the last possible moment.
   */
  public static class ResetAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void stopSpan(@Advice.This final HttpConnection channel) {
      Request req = channel.getRequest();
      Object spanObj = req.getAttribute(DD_SPAN_ATTRIBUTE);
      if (spanObj instanceof AgentSpan) {
        final AgentSpan span = (AgentSpan) spanObj;
        DECORATE.onResponse(span, channel);
        DECORATE.beforeFinish(span);
        span.finish();
      }
    }
  }
}
