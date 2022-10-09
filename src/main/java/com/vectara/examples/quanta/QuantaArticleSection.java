package com.vectara.examples.quanta;

public class QuantaArticleSection {
  private final int id;
  private final String title;
  private final String text;

  public QuantaArticleSection (int id, String title, String text) {
    this.id = id;
    this.title = title;
    this.text = text;
  }

  public int id() {
    return id;
  }

  public String title() {
    return title;
  }

  public String text() {
    return text;
  }

  @Override
  public String toString() {
    return "QuantaArticleSection{" +
        "id=" + id +
        ", title='" + title + '\'' +
        ", text='" + text + '\'' +
        '}';
  }
}
