package com.vectara.examples.quanta;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.internal.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.vectara.QueryServiceGrpc;
import com.vectara.QueryServiceGrpc.QueryServiceBlockingStub;
import com.vectara.StatusProtos.Status;
import com.vectara.StatusProtos.StatusCode;
import com.vectara.examples.quanta.util.HttpConfig;
import com.vectara.examples.quanta.util.JwtFetcher;
import com.vectara.examples.quanta.util.StatusUtils;
import com.vectara.examples.quanta.util.VectaraArgs;
import com.vectara.indexing.IndexingProtos.Document;
import io.grpc.ManagedChannel;
import io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.NettyChannelBuilder;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URL;
import java.security.CodeSource;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static com.vectara.examples.quanta.util.StatusUtils.ok;

/**
 * Provide search services on Quanta Magazine.
 */
public class QuantaServe {
  private static final FluentLogger LOG;

  static {
    System.setProperty(
        "flogger.backend_factory",
        "com.google.common.flogger.backend.log4j2.Log4j2BackendFactory#getInstance");
    LOG = FluentLogger.forEnclosingClass();
  }

  private VectaraArgs args;
  private ManagedChannel servingChannel;
  private QueryServiceBlockingStub syncStub;
  private Map<String, com.vectara.indexing.IndexingProtos.Document> documents;

  public QuantaServe(VectaraArgs args) {
    this.args = args;
  }

  private int start() throws Exception {
    documents = Maps.newHashMap();
    Status articleStatus = loadArticles();
    if (!ok(articleStatus)) {
      LOG.atSevere().log("Unable to load articles: %s: %s",
          articleStatus.getCode(), articleStatus.getStatusDetail());
    }
    JwtFetcher jwt_fetcher = new JwtFetcher(
        URI.create(args.auth_auth_url),
        args.auth_app_id,
        args.auth_app_secret);
    // Setup the connection to the Vectara Platform.
    this.servingChannel = NettyChannelBuilder
        .forAddress("serving.vectara.dev", 443)
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
                documents,
                jwt_fetcher,
                syncStub)), "/query");
    // Setup static file serving.
    URL webRoot = QuantaServe.class.getClassLoader().getResource("web");
    coreServletHandler.setBaseResource(Resource.newResource(webRoot));
    ServletHolder holderPwd =
        new ServletHolder("default", new DefaultServlet());
    holderPwd.setInitParameter("dirAllowed", "false");
    coreServletHandler.addServlet(holderPwd, "/");
    return httpConfig.startJoin();
  }

  private Status loadArticles() throws IOException {
    var parser = JsonFormat.parser().ignoringUnknownFields();
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    URL root = cl.getResource("articles");
    File[] files = new File(root.getPath()).listFiles();
    if (files != null) {
      for (File file : new File(root.getPath()).listFiles()) {
        try (var in = new BufferedReader(new FileReader(file))) {
          loadArticle(parser, in, documents);
        } catch (FileNotFoundException e) {
          LOG.atWarning().log("File not found: %s", file);
        }
      }
    } else {
      CodeSource src = this.getClass().getProtectionDomain().getCodeSource();
      if (src != null) {
        URL jar = src.getLocation();
        ZipInputStream zip = new ZipInputStream(jar.openStream());
        while (true) {
          ZipEntry e = zip.getNextEntry();
          if (e == null)
            break;
          String name = e.getName();
          if (name.startsWith("articles/")) {
            loadArticle(parser, new InputStreamReader(zip), documents);
          }
        }
      } else {
        return StatusUtils.status(StatusCode.FAILURE, "CodeSource is null. Cannot load articles.");
      }
    }
    return ok();
  }

  /**
   * Load a single article.
   */
  private Status loadArticle(Parser parser, Reader in, Map<String, Document> docMap) {
    var doc = com.vectara.indexing.IndexingProtos.Document.newBuilder();
    try {
      parser.merge(in, doc);
      docMap.put(Files.getNameWithoutExtension(doc.getDocumentId()), doc.build());
      LOG.atInfo().log("Loaded [%s]", doc.getDocumentId());
      return StatusUtils.ok();
    } catch (InvalidProtocolBufferException e) {
      LOG.atWarning().log("Invalid article data.");
      return StatusUtils.status(e);
    } catch (IOException e) {
      LOG.atWarning().log("Error reading article.");
      return StatusUtils.status(e);
    }
  }

  /**
   * Configure and return a thread pool for the server.
   */
  private ThreadPool getThreadPool() {
    return new QueuedThreadPool(200, 8);
  }

  public static void main(String[] argv) throws Exception {
    VectaraArgs args = new VectaraArgs();
    JCommander.newBuilder().addObject(args).build().parse(argv);
    System.exit(new QuantaServe(args).start());
  }
}
