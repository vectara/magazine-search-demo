package com.vectara.examples.quanta.util;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.BooleanConverter;
import com.beust.jcommander.converters.IntegerConverter;
import com.beust.jcommander.converters.PathConverter;
import com.beust.jcommander.converters.StringConverter;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Provides a basic set of arguments for HTTP servers.
 */
public class HttpArgs {
  public String http_host = "0.0.0.0";
  public int http_port = 8080;
}
