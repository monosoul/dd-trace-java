package datadog.trace.instrumentation.springweb6.urlhandlermapping

import datadog.trace.agent.test.asserts.TraceAssert
import datadog.trace.agent.test.base.HttpServer
import datadog.trace.agent.test.base.HttpServerTest
import datadog.trace.api.ConfigDefaults
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import datadog.trace.core.DDSpan
import datadog.trace.instrumentation.springweb6.SpringWebHttpServerDecorator
import datadog.trace.instrumentation.springweb6.boot.SecurityConfig
import datadog.trace.instrumentation.tomcat.TomcatDecorator
import org.springframework.boot.SpringApplication
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.ConfigurableApplicationContext

import static datadog.trace.agent.test.base.HttpServerTest.ServerEndpoint.*
import static java.util.Collections.singletonMap

/**
 * Test instrumentation of UriTemplateVariablesHandlerInterceptor
 * (used by AbstractUrlHandlerMapping).
 */
class UrlHandlerMappingTest extends HttpServerTest<ConfigurableApplicationContext> {

  class SpringBootServer implements HttpServer {
    def port = 0
    def context
    final app = new SpringApplication(UrlHandlerMappingAppConfig, SecurityConfig)



    @Override
    void start() {
      app.setDefaultProperties(singletonMap("server.port", 0))
      context = app.run()
      port = (context as ServletWebServerApplicationContext).webServer.port
      assert port > 0
    }

    @Override
    void stop() {
      context.close()
    }

    @Override
    URI address() {
      new URI("http://localhost:$port/")
    }

    @Override
    String toString() {
      this.class.name
    }
  }

  @Override
  HttpServer server() {
    new SpringBootServer()
  }

  @Override
  String component() {
    TomcatDecorator.DECORATE.component()
  }

  @Override
  String expectedOperationName() {
    'servlet.request'
  }

  @Override
  boolean testException() {
    // generates extra trace for the error handling invocation
    false
  }

  @Override
  boolean testRedirect() {
    // generates extra trace at the end
    false
  }

  @Override
  boolean hasHandlerSpan() {
    true
  }

  @Override
  boolean hasExtraErrorInformation() {
    true
  }

  @Override
  String expectedServiceName() {
    return ConfigDefaults.DEFAULT_SERVLET_ROOT_CONTEXT_SERVICE_NAME
  }

  @Override
  Serializable expectedServerSpanRoute(ServerEndpoint endpoint) {
    switch (endpoint) {
      case LOGIN:
      case NOT_FOUND:
        return null
      case PATH_PARAM:
        return testPathParam()
      default:
        return null
    }
  }

  @Override
  String testPathParam() {
    '/path/{id:\\d+}/param'
  }

  @Override
  Map<String, Serializable> expectedExtraServerTags(ServerEndpoint endpoint) {
    ['servlet.path': endpoint.path,
      'servlet.context': "/"
    ]
  }

  @Override
  void handlerSpan(TraceAssert trace, ServerEndpoint endpoint = SUCCESS) {
    trace.span {
      serviceName expectedServiceName()
      operationName "spring.handler"
      spanType DDSpanTypes.HTTP_SERVER
      errored endpoint == EXCEPTION
      childOfPrevious()
      tags {
        "$Tags.COMPONENT" SpringWebHttpServerDecorator.DECORATE.component()
        "$Tags.SPAN_KIND" Tags.SPAN_KIND_SERVER
        if (endpoint == EXCEPTION) {
          errorTags(Exception, EXCEPTION.body)
        }
        defaultTags()
      }
    }
  }


  def 'template var is pushed to IG'() {
    setup:
    def request = request(PATH_PARAM, 'GET', null).header(HttpServerTest.IG_EXTRA_SPAN_NAME_HEADER, 'appsec-span').build()

    when:
    def response = client.newCall(request).execute()
    TEST_WRITER.waitForTraces(2)
    DDSpan span = TEST_WRITER.flatten().find {it.operationName =='appsec-span' }

    then:
    response.code() == PATH_PARAM.status
    span.getTag(HttpServerTest.IG_PATH_PARAMS_TAG) == [id: '123']
  }
}
