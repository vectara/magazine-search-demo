package com.vectara.examples.quanta;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;

public class QuantaPageParser {
  final String url;
  final String docId;

  public QuantaPageParser(String url) {
    this.url = url;
    this.docId = url
        .substring("https://www.quantamagazine.org/".length())
        .replaceAll("/", "");
    System.out.println(docId);
  }

  public QuantaArticle parse() {
    try {
      Document doc = Jsoup.connect(url).get();
      String title = findMetaProperty(doc, "og:title");
      String date = extractDate(doc);
      Element body = doc.body();
      String img = findMetaProperty(doc, "og:image");
      List<String> authors = findClass(body, "byline__author");
      List<String> tags = findClass(body, "sidebar__tag");
      String desc = findFirstClass(body, "post__title__excerpt");
      if (desc == null) {
        desc = "no description found";
      }
      String category = findFirstClass(body, "kicker");
      List<QuantaArticleSection> sections = new LinkedList<>();
      sections.add(new QuantaArticleSection(0, null, desc));
      findSections(doc, sections);
      System.out.println(title + " done");
      return new QuantaArticle(docId, url, title, desc, date, img, category, authors, tags, sections);
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Failed URL: " + url);
      return null;
    }
  }

  private String extractDate(Document doc) {
    String publishDate = findMetaProperty(doc, "article:published_time");
    if (publishDate == null) return null;
    DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_DATE_TIME;
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(publishDate, timeFormatter);
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMMM d, uuuu")
        .withZone(ZoneId.systemDefault());
    return formatter.format(offsetDateTime);
  }

  private static String findMetaProperty(Document doc, String propertyName) {
    Elements elements = doc.select("meta[property=" + propertyName + "]");
    if (elements.size() == 0) return null;
    Element element = elements.get(0);
    if (element == null) return null;
    return element.attr("content");
  }

  private static List<String> findClass(Element body, String className) {
    Elements elements = body.getElementsByClass(className);
    List<String> values = new LinkedList<>();
    for (Element element : elements) {
      values.add(element.text());
    }
    return values;
  }

  private static List<QuantaArticleSection> findSections(Element body, List<QuantaArticleSection> sections) {
    Elements elements = body.getElementsByClass("post__content__section");
    int id = 1;
    for (Element element : elements) {
      sections.add(new QuantaArticleSection(sections.size(), null, element.text()));
    }
    return sections;
  }

  private static String findFirstClass(Element body, String className) {
    List<String> results = findClass(body, className);
    if (results.size() == 0) return null;
    return results.get(0);
  }

  // for testing only
  public static void main(String[] args) throws Exception {
    String url = "https://www.quantamagazine.org/the-most-famous-paradox-in-physics-nears-its-end-20201029/";
    QuantaPageParser pageParser = new QuantaPageParser(url);
    QuantaArticle article = pageParser.parse();
    System.out.println(article);
    for (QuantaArticleSection section : article.sections()) {
      System.out.println(section.id() + "  " + section.text().substring(0, 10));
    }
  }
}
