package com.almworks.jira.provider3.markup;

import com.almworks.items.gui.edit.editors.text.RichTextTransform;
import com.almworks.jira.provider3.sync.download2.rest.AdfCanonical;
import com.almworks.jira.provider3.sync.download2.rest.AdfDocument;
import com.almworks.jira.provider3.sync.download2.rest.AdfText;
import com.almworks.restconnector.json.JsonKey;
import com.almworks.util.collections.Convertor;
import org.almworks.util.Util;
import org.jetbrains.annotations.Nullable;
import org.json.simple.JSONObject;

import java.util.List;

/**
 * Bridges the editors, which hold Markdown, to the stored ADF document.<br>
 * The placeholder table is not carried between the two directions: rendering is deterministic, so the table
 * is simply rebuilt from the same stored document when the edit comes back. That keeps the whole conversion
 * stateless and means nothing extra has to survive in the edit model.
 */
public class AdfRichTextTransform implements RichTextTransform {
    /**
     * For issue description, environment and textarea custom fields, which store text trimmed as a whole.
     */
    public static final AdfRichTextTransform TRIM = new AdfRichTextTransform(JsonKey.emptyTextToNull(AdfText.adfAware(JsonKey.TEXT_TRIM)));
    /**
     * For comment bodies and worklog comments, which trim each line.
     */
    public static final AdfRichTextTransform TRIM_LINES = new AdfRichTextTransform(JsonKey.emptyTextToNull(AdfText.adfAware(JsonKey.TEXT_TRIM_LINES)));

    /**
     * The same convertor the download path uses for this kind of field. Deriving the plain text any other way
     * would leave the local value differing from what the server echoes back, so the field would always look changed.
     */
    private final Convertor<Object, String> myToPlainText;

    private AdfRichTextTransform(Convertor<Object, String> toPlainText) {
        myToPlainText = toPlainText;
    }

    /**
     * Builds a document for text that never had one, matching what the plain-text upload path would send.
     */
    @Nullable
    public static String documentFromText(@Nullable String text) {
        JSONObject doc = AdfDocument.fromText(text);
        return doc != null ? doc.toJSONString() : null;
    }

    @Override
    public String toEditable(@Nullable String plainText, @Nullable String rawSource) {
        JSONObject doc = AdfCanonical.parse(rawSource);
        // No document means the value predates the companion attribute, or was never rich: show it as it is.
        if (doc == null) return Util.NN(plainText);
        return AdfToMarkdown.render(doc).getMarkdown();
    }

    @Override
    public Result fromEditable(@Nullable String editedText, @Nullable String originalRawSource) {
        JSONObject doc = MarkdownToAdf.parse(editedText, tableFor(originalRawSource));
        String plainText = myToPlainText.convert(doc);
        if (plainText == null || plainText.isEmpty()) {
            // Nothing left to send. JIRA rejects a rich-text value with no content, so the upload falls back to clearing the field.
            return new Result(null, null);
        }
        return new Result(plainText, doc.toJSONString());
    }

    @Override
    public boolean isSameSource(@Nullable String a, @Nullable String b) {
        return AdfCanonical.areEqualRaw(a, b);
    }

    @Nullable
    @Override
    public String checkLoss(@Nullable String editedText, @Nullable String originalRawSource) {
        PlaceholderTable table = tableFor(originalRawSource);
        if (table.isEmpty()) return null;
        List<String> missing = table.describeMissing(Util.NN(editedText));
        if (missing.isEmpty()) return null;
        StringBuilder message = new StringBuilder();
        for (String each : missing) {
            if (message.length() > 0) message.append(", ");
            message.append(each);
        }
        return message.toString();
    }

    /**
     * Rebuilds the placeholder table by rendering the original document again. Rendering is and should be
     * deterministic: the same document always produces the same tokens in the same order.
     */
    private PlaceholderTable tableFor(@Nullable String rawSource) {
        JSONObject doc = AdfCanonical.parse(rawSource);
        if (doc == null) return new PlaceholderTable();
        return AdfToMarkdown.render(doc).getTable();
    }
}
