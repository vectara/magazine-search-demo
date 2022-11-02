package com.vectara.examples.quanta;

import java.util.List;

public class QuantaArticle {
  private final String docId;
  private final String url;
  private final String title;
  private final String desc;
  private final String date;
  private final String img;
  private final String category;
  private final List<String> authors;
  private final List<String> tags;
  private final List<QuantaArticleSection> sections;

  public QuantaArticle(
      String docId,
      String url,
      String title,
      String desc,
      String date,
      String img,
      String category,
      List<String> authors,
      List<String> tags,
      List<QuantaArticleSection> sections) {
    this.docId = docId;
    this.url = url;
    this.title = title;
    this.desc = desc;
    this.date = date;
    this.img = img;
    this.category = category;
    this.authors = authors;
    this.tags = tags;
    this.sections = sections;
  }

  public String docId() {
    return docId;
  }

  public String url() {
    return url;
  }

  public String title() {
    return title;
  }

  public String desc() {
    return desc;
  }

  public String date() {
    return date;
  }

  public String img() {
    return img;
  }

  public String category() {
    return category;
  }

  public List<String> authors() {
    return authors;
  }

  public List<String> tags() {
    return tags;
  }

  public List<QuantaArticleSection> sections() {
    return sections;
  }

  @Override
  public String toString() {
    return "QuantaArticle{" +
        "docId='" + docId + '\'' +
        ", url='" + url + '\'' +
        ", title='" + title + '\'' +
        ", desc='" + desc + '\'' +
        ", date='" + date + '\'' +
        ", img='" + img + '\'' +
        ", category='" + category + '\'' +
        ", authors=" + authors +
        ", tags=" + tags +
        ", sections=" + sections +
        '}';
  }
}
