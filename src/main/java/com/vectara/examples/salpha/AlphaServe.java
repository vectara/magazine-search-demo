package com.vectara.examples.salpha;

import static com.vectara.examples.salpha.util.StatusUtils.ok;

import com.beust.jcommander.JCommander;
import com.google.common.flogger.FluentLogger;
import com.vectara.QueryServiceGrpc;
import com.vectara.QueryServiceGrpc.QueryServiceBlockingStub;
import com.vectara.examples.salpha.util.AlphaArgs;
import com.vectara.examples.salpha.util.HttpConfig;
import com.vectara.examples.salpha.util.JwtFetcher;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import java.io.File;
import java.net.URI;
import java.net.URL;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;

/**
 * Provide search services for seeking alpha.
 */
public class AlphaServe {
  private static final FluentLogger LOG;

  static {
    System.setProperty(
        "flogger.backend_factory",
        "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance");
    LOG = FluentLogger.forEnclosingClass();
  }

  private AlphaArgs args;
  private ManagedChannel servingChannel;
  private QueryServiceBlockingStub syncStub;
  private DocumentIndex index;

  public AlphaServe(AlphaArgs args) {
    this.args = args;
  }

  private int start() throws Exception {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL root = cl.getResource("articles");
    index = new DefaultDocumentIndex(root);

    JwtFetcher jwt_fetcher = new JwtFetcher(
        URI.create(args.auth_auth_url),
        args.auth_app_id,
        args.auth_app_secret);
    // Setup the connection to the Vectara Platform.
    this.servingChannel = NettyChannelBuilder
        .forAddress("serving.vectara.io", 443)
        .sslContext(GrpcSslContexts.forClient()
            .trustManager((File) null)
            .build())
        .build();
    this.syncStub = QueryServiceGrpc.newBlockingStub(servingChannel);
    // Setup the HTTP Server
    Server server = new Server(getThreadPool());
    HttpConfig httpConfig = new HttpConfig(args, server, "Quanta Search Club");
    var httpStatus = httpConfig.configure();
    if (!ok(httpStatus)) {
      LOG.atSevere().log("Unable to configure HTTP subsystem: %s", httpStatus);
      return 1;
    }
    ContextHandlerCollection contexts = new ContextHandlerCollection();
    server.setHandler(contexts);
    ServletContextHandler coreServletHandler = new ServletContextHandler(
        contexts, "/");
    coreServletHandler.addServlet(
        new ServletHolder(
            new QueryServlet(
                args.customer_id,
                args.corpus_id,
                index,
                jwt_fetcher,
                syncStub)), "/query");
    // Setup static file serving.
    URL webRoot = AlphaServe.class.getClassLoader().getResource("web");
    coreServletHandler.setBaseResource(Resource.newResource(webRoot));
    ServletHolder holderPwd =
        new ServletHolder("default", new DefaultServlet());
    holderPwd.setInitParameter("dirAllowed", "false");
    coreServletHandler.addServlet(holderPwd, "/");
    return httpConfig.startJoin();
  }

  /**
   * Configure and return a thread pool for the server.
   */
  private ThreadPool getThreadPool() {
    return new QueuedThreadPool(200, 8);
  }

  public static void main(String[] argv) throws Exception {
    AlphaArgs args = new AlphaArgs();
    JCommander.newBuilder().addObject(args).build().parse(argv);
    System.exit(new AlphaServe(args).start());
  }
}
