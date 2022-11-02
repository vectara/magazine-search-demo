package com.vectara.examples.salpha;

import com.google.common.base.Strings;
import com.vectara.indexing.IndexingProtos.Document;
import com.vectara.indexing.IndexingProtos.Section;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nullable;

public interface DocumentIndex {

  Collection<Document> all();

  @Nullable
  Document get(String docId);

  @Nullable
  Section getSection(String docId, int sectionId);

  @Nullable
  Section getParent(String docId, int sectionId);

  @Nullable
  default String getTitle(String docId, int sectionId) {
    Section s = getSection(docId, sectionId);
    while (s != null) {
      if (!Strings.isNullOrEmpty(s.getTitle())) {
        return s.getTitle();
      }
      s = getParent(docId, s.getId());
    }
    return null;
  }
}
