package com.vectara.examples.quanta;

import com.google.common.base.Joiner;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.common.io.Files;
import com.google.common.net.UrlEscapers;
import com.google.common.primitives.Ints;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Printer;
import com.vectara.QueryServiceGrpc.QueryServiceBlockingStub;
import com.vectara.StatusProtos.StatusCode;
import com.vectara.examples.quanta.QuantaProtos.Crumb;
import com.vectara.examples.quanta.QuantaProtos.WebDoc;
import com.vectara.examples.quanta.QuantaProtos.WebDoc.Builder;
import com.vectara.examples.quanta.util.ExpiringMemoizingSupplier;
import com.vectara.examples.quanta.util.JwtFetcher;
import com.vectara.examples.quanta.util.StatusOr;
import com.vectara.examples.quanta.util.StatusUtils;
import com.vectara.examples.quanta.util.TokenResponse;
import com.vectara.examples.quanta.util.VectaraCallCredentials;
import com.vectara.examples.quanta.util.VectaraCallCredentials.AuthType;
import com.vectara.indexing.IndexingProtos.Section;
import com.vectara.serving.ServingProtos.Attribute;
import com.vectara.serving.ServingProtos.BatchQueryRequest;
import com.vectara.serving.ServingProtos.BatchQueryResponse;
import com.vectara.serving.ServingProtos.CorpusKey;
import com.vectara.serving.ServingProtos.QueryRequest;
import com.vectara.serving.ServingProtos.Response;
import com.vectara.serving.ServingProtos.ResponseSet;
import com.vectara.serving.ServingProtos.ResponseSet.Document;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author aahmad
 * @see "https://aws.amazon.com/blogs/mobile/understanding-amazon-cognito-user-pool-oauth-2-0-grants/"
 */
public class QueryServlet extends HttpServlet {
  private static final long serialVersionUID = 1730611064190799175L;
  private static final FluentLogger LOG = FluentLogger.forEnclosingClass();
  private static final String REQ_QUERY = "q";
  private static final String REQ_NUM_RESULTS = "n";
  private long customerId;
  private int corpusId;
  private QueryServiceBlockingStub vectaraClient;
  private ExpiringMemoizingSupplier<StatusOr<TokenResponse>> tokenSupplier;
  private Map<String, com.vectara.indexing.IndexingProtos.Document> documents;
  private Map<String, String> docCategory;

  public QueryServlet(
      long customerId,
      int corpusId,
      Map<String, com.vectara.indexing.IndexingProtos.Document> documents,
      JwtFetcher fetcher,
      QueryServiceBlockingStub queryService) throws IOException {
    this.customerId = customerId;
    this.corpusId = corpusId;
    this.vectaraClient = queryService;
    this.documents = documents;
    this.docCategory = Maps.newHashMap();
    Gson gson = new Gson();
    for (String key : documents.keySet()) {
      com.vectara.indexing.IndexingProtos.Document d = documents.get(key);
      @SuppressWarnings("unchecked")
      Map<String, Object> metadata = gson.fromJson(d.getMetadataJson(), Map.class);
      Object value = metadata.get("category");
      if (value instanceof String) {
        docCategory.put(key, (String) value);
      }
    }
    this.tokenSupplier = new ExpiringMemoizingSupplier<StatusOr<TokenResponse>>(
        new ExpiringMemoizingSupplier.ExpiringSupplier<StatusOr<TokenResponse>>() {
          @Override
          public ExpiringMemoizingSupplier.ValueAndExpiration<StatusOr<TokenResponse>> get() {
            StatusOr<TokenResponse> fetch = fetcher.fetchClientCredentials();
            if (!fetch.ok()) {
              return new ExpiringMemoizingSupplier.ValueAndExpiration<StatusOr<TokenResponse>>(fetch, 1, TimeUnit.MINUTES);
            } else {
              Duration d = fetch.get().getExpiresIn();
              return new ExpiringMemoizingSupplier.ValueAndExpiration<StatusOr<TokenResponse>>(fetch, d.getSeconds(), TimeUnit.SECONDS);
            }
          }
        }
    );
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws IOException {
    Instant start = Instant.now();
    // Perform the cheapest check, query validation, first.
    String query = req.getParameter(REQ_QUERY);
    if (Strings.isNullOrEmpty(query)) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
      return;
    }
    int numResults = 5;
    String unparsedNumResults = req.getParameter(REQ_NUM_RESULTS);
    if (!Strings.isNullOrEmpty(unparsedNumResults)) {
      try {
        numResults = Integer.parseInt(unparsedNumResults);
      } catch (NumberFormatException e) {
        // ignore
      }
    }
    numResults = Ints.constrainToRange(numResults, 1, 25);
    // Obtain a machine-to-machine auth token for the request
    // to Holly Oak.
    StatusOr<TokenResponse> fetch = tokenSupplier.get();
    if (!fetch.ok()) {
      resp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    try {
      StatusOr<List<WebDoc>> docsOr = vectaraSearch(
          query,
          numResults,
          /*rrf_k = */ 0,
          resp,
          fetch
      );
      if (docsOr.ok()) {
        LOG.atInfo().log("docsOr.get().size(): %d", docsOr.get().size());
      } else {
        LOG.atWarning().log("Unexpected search failure: %s", docsOr.status());
      }
    } catch (Exception e) {
      LOG.atSevere().withCause(e).log("Unexpected search failure.");
    }
  }

  /**
   * Search using the Vectara backend. This method commits results to the
   * client. The only reason it returns results is so that logging can
   * occur.
   */
  private StatusOr<List<WebDoc>> vectaraSearch(
      String query,
      int numResults,
      int rrf_k,
      HttpServletResponse resp,
      StatusOr<TokenResponse> fetch)
      throws IOException {
    BatchQueryRequest.Builder servingRequest = BatchQueryRequest.newBuilder();
    final QueryRequest.Builder titleQuery = QueryRequest.newBuilder();
    titleQuery.setQuery(query);
    titleQuery.setNumResults(100);
    int corpusId = this.corpusId;
    titleQuery.addCorpusKey(
        CorpusKey.newBuilder()
            .setCorpusId(corpusId)
            .setMetadataFilter("part.is_title=true")
    );
    servingRequest.addQuery(titleQuery);
    final QueryRequest.Builder nonTitleQuery = QueryRequest.newBuilder();
    nonTitleQuery.setQuery(query);
    nonTitleQuery.setNumResults(100);
    nonTitleQuery.addCorpusKey(
        CorpusKey.newBuilder()
            .setCorpusId(this.corpusId)
            .setMetadataFilter("part.is_title is null")
    );
    servingRequest.addQuery(nonTitleQuery);
    var timer = Stopwatch.createStarted();
    BatchQueryResponse response = vectaraClient
        .withCallCredentials(new VectaraCallCredentials(AuthType.OAUTH_TOKEN,
            fetch.get().getAccessToken().getToken(),
            customerId))
        .query(servingRequest.build());
    ResponseSet titleSet = response.getResponseSet(0);
    ResponseSet articleSet = response.getResponseSet(1);
    LOG.atFine().log(
        "Vectara returned %d results in %d ms.",
        articleSet.getResponseCount(), timer.elapsed(TimeUnit.MILLISECONDS));
    // Check if there are statuses attached to the response and log them.
    if (response.getStatusCount() > 0) {
      LOG.atInfo().log("Vectara returned status: %s", Joiner.on(" ; ").join(response.getStatusList()));
    }
    if (titleSet.getStatusCount() > 0) {
      LOG.atInfo().log("Vectara returned status: %s", Joiner.on(" ; ").join(titleSet.getStatusList()));
    }
    if (articleSet.getStatusCount() > 0) {
      LOG.atInfo().log("Vectara returned status: %s", Joiner.on(" ; ").join(articleSet.getStatusList()));
    }
    StatusOr<List<WebDoc>> docsOr = getDocuments(query, titleSet, articleSet, numResults, rrf_k);
    if (!docsOr.ok()) {
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } else {
      var results = QuantaProtos.SearchResults.newBuilder()
          .addAllDocs(docsOr.get());
      Printer printer = JsonFormat.printer()
          .includingDefaultValueFields();
      resp.setContentType("application/json; charset=UTF-8");
      resp.setCharacterEncoding("UTF-8");
      printer.appendTo(results.build(), resp.getWriter());
    }
    return docsOr;
  }

  /**
   * Helper class that represents a (possibly) merged result.
   */
  private static class Result {
    Response title, body;
    Document servingDoc;
    com.vectara.indexing.IndexingProtos.Document fullDoc;
    int titleRank, bodyRank;
  }

  private StatusOr<List<WebDoc>> getDocuments(
      String query, ResponseSet titleSet, ResponseSet bodySet, int numResults, int rrf_k) {
    Map<String, Result> resultMap = Maps.newHashMap();
    //
    for (int i = 0; i < titleSet.getResponseCount(); i++) {
      Result result = new Result();
      result.title = titleSet.getResponse(i);
      result.servingDoc = titleSet.getDocument(result.title.getDocumentIndex());
      result.titleRank = i + 1;
      result.bodyRank = bodySet.getResponseCount() + 1; // Initial value
      String id = massageId(result.servingDoc.getId());
      result.fullDoc = getFullDoc(id);
      if (result.fullDoc != null) {
        resultMap.put(id, result);
      }
    }
    // Join the entries in the resultMap with the body matches. There
    // are three possibilities for every body match:
    //
    // 1. The title of the article also matched, so update the existing
    //    result entry.
    // 2. The title did not match, so create a new result entry.
    // 3. There was a previous (higher-scoring) body match already, in
    //    which case, we leave the body score unchanged.
    //
    for (int i = 0; i < bodySet.getResponseCount(); i++) {
      Response r = bodySet.getResponse(i);
      Document d = bodySet.getDocument(r.getDocumentIndex());
      String id = massageId(d.getId());
      if (resultMap.containsKey(id)) {
        Result result = resultMap.get(id);
        if (result.body == null) {  // Only register the highest scoring snippet
          result.servingDoc = d;
          result.bodyRank = i + 1;
          result.body = r;
        }
      } else {
        Result result = new Result();
        result.body = r;
        result.servingDoc = d;
        result.titleRank = titleSet.getResponseCount() + 1;
        result.bodyRank = i + 1;
        id = massageId(result.servingDoc.getId());
        result.fullDoc = getFullDoc(id);
        if (result.fullDoc != null) {
          resultMap.put(id, result);
        }
      }
    }
    // Sort the entries from highest to lowest score. This is based on a slight modification of the Reciprocal Rank
    // Fusion method (https://plg.uwaterloo.ca/~gvcormac/cormacksigir09-rrf.pdf) where each term in the summation is
    // weighted by the corresponding encoder score.
    //
    List<Result> resultList = Lists.newArrayList(resultMap.values());
    resultList.sort(new Comparator<Result>() {
      @Override
      public int compare(Result x, Result y) {
        float xScore = (1 / (rrf_k + x.bodyRank)) + (1 / (rrf_k + x.titleRank));
        float yScore = (1 / (rrf_k + y.bodyRank)) + (1 / (rrf_k + y.titleRank));
        return Float.compare(yScore, xScore);
      }
    });
    LOG.atInfo().log("After merging, there are %d results.", resultList.size());
    // Process the results and create the list of results that
    // will be sent to the UI for rendering.
    //
    List<WebDoc> docs = Lists.newArrayList();
    int skipped = 0;
    for (int i = 0; docs.size() < Math.min(numResults, resultList.size()); i++) {
      Result r = resultList.get(i);
      if (isSubtitleMatch(r.body)) {  // Don't have a nice way to present this yet.
        ++skipped;
        continue;
      }
      WebDoc.Builder result = WebDoc.newBuilder();
      result.setTitle(stripQuantaSuffix(r.fullDoc.getTitle()));
      result.setUrl(getUrl(r.servingDoc));
      result.setAuthority("quantamagazine.org");
      result.setSiteCategory(getCategory(massageId(r.fullDoc.getDocumentId())));
      var timeStatus = getPublishedTimestamp(r.servingDoc);
      if (timeStatus.ok()) {
        result.setDate(timeStatus.get());
      }
      String author = getAuthor(r.servingDoc);
      if (!Strings.isNullOrEmpty(author)) {
        result.setAuthor(author);
      }
      String image = getImage(r.servingDoc);
      if (!Strings.isNullOrEmpty(image)) {
        result.setImageUrl(image);
      }
      setTags(result, r.servingDoc);
      var sec = QuantaProtos.Section.newBuilder();
      if (r.body == null || isTitleMatch(r.body)) {
        sec.setText(r.fullDoc.getSection(0).getText());
      } else {
        var section = getSection(r.fullDoc, r.body);
        String body = section == null ? "" : section.getText();
        int offset = getOffset(r.body);
        // text - the bold snippet
        // pre  - context that appears prior to the text
        // post - context that appears after the text
        // textFragment - Specifies a text fragment URI, which allows
        //        "deep linking" directly to the relevant snippet in
        //        the article. Ref: https://web.dev/text-fragments/
        //
        sec.setText(r.body.getText());
        result.setTextFragment(makeFragment(r.body.getText()));
        sec.setPre(getPre(r.body, offset, body));
        sec.setPost(getPost(r.body, offset, body));
        setBreadcrumb(result, section);
      }
      result.addSections(sec.build());
      docs.add(result.build());
    }
    if (skipped > 0) {
      LOG.atInfo().log("Skipped %s documents that had issues.", skipped);
    }
    return new StatusOr<>(docs);
  }

  /**
   * Generate a text fragment.
   *
   * @see "https://web.dev/text-fragments/"
   */
  private String makeFragment(String snippet) {
    return "#:~:text=" + UrlEscapers.urlFragmentEscaper().escape(snippet);
  }

  /**
   * Work around a bug in the indexing layer.
   */
  private String massageId(String id) {
    return Files.getNameWithoutExtension(id);
  }

  private boolean isTitleMatch(Response body) {
    if (body == null) {
      return false;
    }
    boolean isTitle = false;
    int level = -1;
    for (Attribute a : body.getMetadataList()) {
      if ("is_title".equalsIgnoreCase(a.getName())) {
        isTitle = Boolean.parseBoolean(a.getValue());
      }
      if ("title_level".equalsIgnoreCase(a.getName())) {
        level = Integer.parseInt(a.getValue());
      }
    }
    return isTitle && level == 1;
  }

  private boolean isSubtitleMatch(Response body) {
    if (body == null) {
      return false;
    }
    boolean isTitle = false;
    int level = -1;
    for (Attribute a : body.getMetadataList()) {
      if ("is_title".equalsIgnoreCase(a.getName())) {
        isTitle = Boolean.parseBoolean(a.getValue());
      }
      if ("title_level".equalsIgnoreCase(a.getName())) {
        level = Integer.parseInt(a.getValue());
      }
    }
    return isTitle && level > 1;
  }

  @Nullable
  private String getImage(Document servingDoc) {
    for (Attribute a : servingDoc.getMetadataList()) {
      if ("img".equalsIgnoreCase(a.getName())) {
        return a.getValue();
      }
    }
    return null;
  }

  private void setTags(Builder result, Document servingDoc) {
    for (Attribute a : servingDoc.getMetadataList()) {
      if ("tag".equalsIgnoreCase(a.getName())) {
        Gson gson = new Gson();
        for (String tag : gson.fromJson(a.getValue(), String[].class)) {
          result.addTags(tag);
        }
      }
    }
  }

  @Nullable
  private String getAuthor(Document servingDoc) {
    for (Attribute a : servingDoc.getMetadataList()) {
      if ("author".equalsIgnoreCase(a.getName())) {
        Gson gson = new Gson();
        for (String tag : gson.fromJson(a.getValue(), String[].class)) {
          return tag;
        }
      }
    }
    return null;
  }

  /**
   * Return the website category.
   */
  private String getCategory(String id) {
    return docCategory.getOrDefault(id, "");
  }

  @Nullable
  private com.vectara.indexing.IndexingProtos.Document getFullDoc(String docId) {
    com.vectara.indexing.IndexingProtos.Document article = documents.get(docId);
    if (article == null) {
      article = documents.get(Files.getNameWithoutExtension(docId));
      if (article == null) {
        LOG.atWarning().log(
            "Document %s does not exist in cache. JAR and index are out of sync.", docId);
      }
    }
    return article;
  }

  private void setBreadcrumb(WebDoc.Builder result, Section section) {
    if (section != null && !Strings.isNullOrEmpty(section.getTitle())) {
      result.addBreadcrumb(Crumb.newBuilder().setDisplay(section.getTitle()));
    }
  }

  private String stripQuantaSuffix(String title) {
    if (title.endsWith(" | Quanta Magazine")) {
      return title.substring(0, title.length() - 18);
    }
    return title;
  }

  /**
   * Returns the offset of the text in the body.
   */
  private int getOffset(Response r) {
    for (Attribute a : r.getMetadataList()) {
      if ("offset".equalsIgnoreCase(a.getName())) {
        return Integer.parseInt(a.getValue());
      }
    }
    return 0;
  }

  /**
   * Return the URL of the specific story, if it's available.
   */
  private String getUrl(Document article) {
    for (Attribute a : article.getMetadataList()) {
      if ("url".equalsIgnoreCase(a.getName())) {
        return a.getValue();
      }
    }
    return "https://www.quantamagazine.org";
  }

  private Section getSection(com.vectara.indexing.IndexingProtos.Document article, Response r) {
    int section = getSection(r);
    for (Section s : article.getSectionList()) {
      if (s.getId() == section) {
        return s;
      }
    }
    return null;
  }

  /**
   * Returns the section number from which this response was drawn.
   */
  private int getSection(Response r) {
    for (Attribute a : r.getMetadataList()) {
      if ("section".equalsIgnoreCase(a.getName())) {
        return Integer.parseInt(a.getValue());
      }
    }
    return 0;
  }

  private StatusOr<Long> getPublishedTimestamp(Document doc) {
    for (Attribute a : doc.getMetadataList()) {
      if ("date".equalsIgnoreCase(a.getName())) {
        try {
          return new StatusOr<>(
              LocalDate.parse(
                      a.getValue(),
                      DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.US)
                  )
                  .toEpochSecond(LocalTime.NOON, ZoneOffset.UTC));
        } catch (DateTimeParseException e) {
          return new StatusOr<>(e);
        }
      }
    }
    return new StatusOr<>(StatusUtils.status(StatusCode.NOT_FOUND));
  }

  private String getPre(Response response, int offset, String body) {
    if (body == null) {
      return "";
    }
    if (offset <= 0 || offset >= body.length()) {
      offset = body.indexOf(response.getText());
      if (offset == -1) {
        return "[..] ";
      }
    }
    for (int j = offset; j > -1; j--) {
      int boundary = offset - 100 < 0 ? 0 : offset - 100;
      if (j > boundary) {
        continue;
      }
      if (Character.isWhitespace(body.charAt(j)) || j <= 0) {
        return "..." + body.substring(j, offset) + " ";
      }
    }
    return body.substring(0, offset);
  }

  private String getPost(Response response, int offset, String body) {
    if (body == null) {
      return "";
    }
    int i = body.indexOf(response.getText());
    if (i == -1) {
      return " [..]";
    }
    int startPos = i + response.getText().length();
    for (int j = startPos; j < body.length(); j++) {
      if (j < startPos + 100) {
        continue;
      }
      if (Character.isWhitespace(body.charAt(j))) {
        return " " + body.substring(startPos, j) + "...";
      }
    }
    return body.substring(i);
  }
}

