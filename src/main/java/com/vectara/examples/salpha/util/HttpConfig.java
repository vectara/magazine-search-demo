package com.vectara.examples.salpha.util;

import com.google.common.flogger.FluentLogger;
import com.vectara.StatusProtos.Status;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;

/**
 * A utility class to easily configure the HTTP interface of demo servers.
 */
public class HttpConfig {
  private static final FluentLogger LOG = FluentLogger.forEnclosingClass();
  private HttpArgs args;
  private Server server;
  private String displayName;

  public HttpConfig(HttpArgs args, Server server, String displayName) {
    this.args = args;
    this.server = server;
    this.displayName = displayName;
  }

  /**
   * Starts and server and waits until shutdown. This method
   * is blocking.
   *
   * @return status code for System.exit.
   */
  public int startJoin() throws InterruptedException {
    try {
      server.start();
    } catch (Exception e) {
      LOG.atSevere().withCause(e).log("%s failed to start.", displayName);
      return 1;
    }
    LOG.atInfo().log("%s (HTTP) started on: %d", displayName, args.http_port);
    server.join();
    return 0;
  }

  /**
   * Configure the server using the arguments provided.
   *
   * @return a status indicating success or failure.
   */
  public Status configure() {
    setupHttpConnector(server);
    server.setStopAtShutdown(true);
    return StatusUtils.ok();
  }

  /**
   * Setup the HTTP connector for the server.
   */
  private void setupHttpConnector(Server server) {
    LOG.atInfo().log("Initializing HTTP connector. Host: %s, Port: %s",
        args.http_host, args.http_port);
    ServerConnector http = new ServerConnector(server,
        new HttpConnectionFactory(new HttpConfiguration()));
    http.setPort(args.http_port);
    server.addConnector(http);
  }
}
