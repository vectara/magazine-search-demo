package com.vectara.examples.quanta;

import com.beust.jcommander.JCommander;
import com.google.gson.Gson;
import com.google.protobuf.util.JsonFormat;
import com.vectara.examples.quanta.util.VectaraArgs;
import com.vectara.indexing.IndexingProtos;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class QuantaCrawler {
  private final static int MAX_ARTICLES = 500;

  public static void main(String[] argv) throws Exception {
    System.out.println("Quanta Crawler and Indexer started.");
    VectaraArgs args = new VectaraArgs();
    JCommander.newBuilder().addObject(args).build().parse(argv);
    System.out.println(args);
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    DocumentBuilder db = dbf.newDocumentBuilder();
    String articlesXmlUrl = "https://api.quantamagazine.org/sitemap-posttype-post.2020.xml";
    Document doc = db.parse(new URL(articlesXmlUrl).openStream());
    NodeList childNodes = doc.getElementsByTagName("url");
    Set<String> articleURLs = new HashSet<>();
    for (int i = 0; i < childNodes.getLength(); i++) {
      Node item = childNodes.item(i);
      Element element = (Element) item;
      NodeList loc = element.getElementsByTagName("loc");
      Node locItem = loc.item(0);
      String articleURL = locItem.getTextContent();
      articleURLs.add(articleURL);
      if (articleURLs.size() >= MAX_ARTICLES) break;
    }
    final ExecutorService es = Executors.newFixedThreadPool(10);
    final AtomicInteger counter = new AtomicInteger();
    final QuantaIndexService quantaIndexService = new QuantaIndexService(args);
    for (String articleURL : articleURLs) {
      es.execute(() -> {
        try {
          final QuantaArticle quantaArticle = new QuantaPageParser(articleURL).parse();
          if (quantaArticle != null) {
            IndexingProtos.Document indexDoc = toDocument(quantaArticle);
            String filename = quantaArticle.docId() + ".json";
            quantaIndexService.index(indexDoc);
            store(filename, indexDoc);
            counter.incrementAndGet();
          }
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
    }
    es.awaitTermination(1, TimeUnit.MINUTES);
    System.out.println("total " + articleURLs.size() + " completed " + counter);
  }

  public static File store(String filename, IndexingProtos.Document indexDoc) throws Exception {
    Path articlesPath = Path.of("server", "src", "main", "resources", "articles");
    Files.createDirectories(articlesPath);
    Path path = Path.of("server", "src", "main", "resources", "articles", filename);
    File file = path.toFile();
    String json = JsonFormat.printer().print(indexDoc);
    Files.writeString(path, json, Charset.defaultCharset());
    return file;
  }

  public static IndexingProtos.Document toDocument(QuantaArticle article) {
    // create the document metadata json
    Gson gson = new Gson();
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("tag", article.tags());
    metadata.put("author", article.authors());
    metadata.put("desc", article.desc());
    metadata.put("url", article.url());
    metadata.put("img", article.img());
    metadata.put("category", article.category());
    metadata.put("date", article.date());
    String metadataJson = gson.toJson(metadata);
    // create the document with sections.
    IndexingProtos.Document.Builder builder = IndexingProtos.Document.newBuilder();
    builder.setDocumentId(article.docId())
        .setTitle(article.title())
        .setDescription(article.desc())
        .setMetadataJson(metadataJson);
    // add each document section
    for (QuantaArticleSection section : article.sections()) {
      IndexingProtos.Section.Builder sectionBuilder = IndexingProtos.Section.newBuilder();
      sectionBuilder.setId(section.id())
          .setText(section.text());
      if (section.title() != null) {
        sectionBuilder.setTitle(section.title());
      }
      builder.addSection(sectionBuilder.build());
    }
    return builder.build();
  }
}
