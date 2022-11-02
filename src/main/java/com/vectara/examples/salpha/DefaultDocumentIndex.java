package com.vectara.examples.salpha;

import static com.vectara.examples.salpha.util.StatusUtils.ok;

import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.google.protobuf.util.JsonFormat.Parser;
import com.vectara.StatusProtos.Status;
import com.vectara.StatusProtos.StatusCode;
import com.vectara.examples.salpha.util.StatusException;
import com.vectara.examples.salpha.util.StatusUtils;
import com.vectara.indexing.IndexingProtos.Document;
import com.vectara.indexing.IndexingProtos.Document.Builder;
import com.vectara.indexing.IndexingProtos.Section;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collection;
import java.util.Map;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.annotation.Nullable;

public class DefaultDocumentIndex implements DocumentIndex {
  private static final FluentLogger LOG = FluentLogger.forEnclosingClass();

  private Map<String, Document> documents;
  private Map<String, Section> sections;
  private Map<String, Section> sectionParents;

  public DefaultDocumentIndex(URL root) {
    documents = Maps.newHashMap();
    sections = Maps.newHashMap();
    sectionParents = Maps.newHashMap();

    var status = loadArticles(root);
    if (!StatusUtils.ok(status)) {
      throw new StatusException(status);
    }
  }

  @Override
  public Collection<Document> all() {
    return documents.values();
  }

  @Nullable
  @Override
  public Document get(String docId) {
    return documents.get(docId);
  }

  @Nullable
  @Override
  public Section getSection(String docId, int sectionId) {
    return sections.get(docId + "-" + sectionId);
  }

  @Nullable
  @Override
  public Section getParent(String docId, int sectionId) {
    return sectionParents.get(docId + "-" + sectionId);
  }

  private Status loadArticles(URL root) {
    var parser = JsonFormat.parser().ignoringUnknownFields();
    File[] files = new File(root.getPath()).listFiles();
    if (files != null) {
      for (File file : new File(root.getPath()).listFiles()) {
        try (var in = new BufferedReader(new FileReader(file))) {
          loadArticle(parser, in, documents);
        } catch (FileNotFoundException e) {
          LOG.atWarning().log("File not found: %s", file);
        } catch (IOException e) {
          return StatusUtils.status(e);
        }
      }
    } else {
      CodeSource src = this.getClass().getProtectionDomain().getCodeSource();
      if (src != null) {
        URL jar = src.getLocation();
        try {
          ZipInputStream zip = new ZipInputStream(jar.openStream());
          while (true) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null)
              break;
            String name = entry.getName();
            if (name.startsWith("articles/")) {
              loadArticle(parser, new InputStreamReader(zip), documents);
            }
          }
        } catch (IOException e) {
          return StatusUtils.status(e);
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
      docMap.put(doc.getDocumentId(), doc.build());
      buildSections(doc);
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

  private void buildSections(Builder doc) {
    Stack<Section> sectionStack = new Stack<>();
    sectionStack.addAll(doc.getSectionList());
    while (!sectionStack.isEmpty()) {
      Section s = sectionStack.pop();
      sections.put(doc.getDocumentId() + "-" + s.getId(), s);
      sectionStack.addAll(s.getSectionList());
      for (var child : s.getSectionList()) {
        sectionParents.put(doc.getDocumentId() + "-" + child.getId(), s);
      }
    }
  }
}
