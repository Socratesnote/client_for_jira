package com.almworks.jira.provider3.sync.download2.details.fields;

import com.almworks.items.entities.api.EntityKey;
import com.almworks.jira.provider3.sync.download2.details.JsonIssueField;
import com.almworks.jira.provider3.sync.download2.rest.AdfText;
import com.almworks.util.collections.Convertor;
import org.almworks.util.Collections15;

import java.util.Collection;
import java.util.List;

/**
 * Lands a rich-text field as two values: the plain text every consumer already reads, and the server's own
 * ADF document beside it.<br>
 * Keeping the document is what lets an edit rebuild it rather than replace it with flat paragraphs, and what
 * makes a formatting-only server change visible to conflict detection.
 */
public class AdfScalarField<T> implements JsonIssueField {
  private final Convertor<Object, T> myConvertor;
  private final EntityKey<T> myKey;
  private final EntityKey<String> myAdfKey;

  private AdfScalarField(Convertor<Object, T> convertor, EntityKey<T> key, EntityKey<String> adfKey) {
    myConvertor = convertor;
    myKey = key;
    myAdfKey = adfKey;
  }

  public static <T> AdfScalarField<T> create(EntityKey<T> key, Convertor<Object, T> convertor, EntityKey<String> adfKey) {
    return new AdfScalarField<T>(convertor, key, adfKey);
  }

  @Override
  public Collection<? extends ParsedValue> loadValue(Object jsonValue) {
    return pair(myConvertor.convert(jsonValue), AdfText.RAW_ADF.convert(jsonValue));
  }

  /**
   * Both halves are cleared together, so a field that loses its value never keeps a stale document.
   */
  @Override
  public Collection<? extends ParsedValue> loadNull() {
    return pair(null, null);
  }

  private Collection<? extends ParsedValue> pair(T value, String rawAdf) {
    List<ParsedValue> result = Collections15.arrayList();
    result.add(new SimpleKeyValue<T>(myKey, value));
    result.add(new SimpleKeyValue<String>(myAdfKey, rawAdf));
    return result;
  }
}
