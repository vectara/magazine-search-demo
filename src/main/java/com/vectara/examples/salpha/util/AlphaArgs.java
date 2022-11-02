package com.vectara.examples.salpha.util;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.IntegerConverter;
import com.beust.jcommander.converters.LongConverter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class AlphaArgs extends HttpArgs {
  public AlphaArgs() {
    Properties properties = loadVectaraConfig();
    if (properties != null) {
      auth_auth_url = properties.getProperty("vectara.auth.url");
      if (!auth_auth_url.startsWith("https://")) {
        System.out.println("Missing configuration! Please update src/main/resources/vectara.properties.");
        System.exit(0);
      }
      try {
        auth_app_id = properties.getProperty("vectara.app.id");
        auth_app_secret = properties.getProperty("vectara.app.secret");
        customer_id = Long.parseLong(properties.getProperty("vectara.customer.id"));
        corpus_id = Integer.parseInt(properties.getProperty("vectara.corpus.id"));
      } catch (NumberFormatException e) {
        System.out.println("Invalid configuration! Please update src/main/resources/vectara.properties.");
        System.out.println(e.getMessage());
        System.exit(0);
      }
    }
  }

  private Properties loadVectaraConfig() {
    Properties prop = null;
    try {
      prop = new Properties();
      String propFileName = "vectara.properties";
      InputStream inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
      if (inputStream != null) {
        prop.load(inputStream);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
    return prop;
  }

  @Parameter(names = {"--customer-id"},
      description = "Customer identifier.",
      converter = LongConverter.class,
      required = false)
  public long customer_id;
  @Parameter(names = {"--corpus-id"},
      description = "Vectara corpus id.",
      converter = IntegerConverter.class
  )
  public int corpus_id;
  @Parameter(names = {"--auth-url"})
  public String auth_auth_url;
  @Parameter(names = {"--auth-app-id"})
  public String auth_app_id;
  @Parameter(names = {"--auth-app-secret"})
  public String auth_app_secret;
  @Parameter(names = {"--refresh-token-timeout"},
      description = "Refresh token timeout, in days.",
      converter = IntegerConverter.class,
      required = false)
  public int auth_refresh_token_timeout = 30;

  @Override
  public String toString() {
    return "VectaraArgs{" +
        "customer_id=" + customer_id +
        ", corpus_id=" + corpus_id +
        ", auth_auth_url='" + auth_auth_url + '\'' +
        ", auth_app_id='" + auth_app_id + '\'' +
        ", auth_app_secret='" + auth_app_secret + '\'' +
        ", auth_refresh_token_timeout=" + auth_refresh_token_timeout +
        '}';
  }
}
