package cz.kb.git;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Stack;

public class HtmlBuilder {

    public static final String DATE_FORMAT = "yyyy-MM-dd-HH-mm-ss";

    private BufferedWriter writer;
    private Stack<String> tags;

    public HtmlBuilder() throws IOException {
        this(String.format("report_%s.html", ZonedDateTime.now().format(DateTimeFormatter.ofPattern(DATE_FORMAT))));
    }

    public HtmlBuilder(final String fileName) throws IOException {
        this.writer = new BufferedWriter(new FileWriter(fileName, false));
        tags = new Stack<>();
    }

    public void startTags(final String... tagNames) throws IOException {
        for (final String tagName : tagNames) {
            startTag(tagName);
        }
    }

    public void startTag(final String tagName) throws IOException {
        indentTag();
        writer.append("<" + tagName + ">\n");
        tags.add(tagName);
    }

    public void addTag(final String tagName, final String value) throws IOException {
        indentTag();
        writer.append("<" + tagName + ">" + value + "</" + tagName + ">\n");
    }

    public void endTags(int numberOfTags) throws IOException {
        for (int i = 0; i < numberOfTags; i++) {
            endTag();
        }
    }

    public void endTag() throws IOException {
        String tagName = tags.pop();
        indentTag();
        writer.append("</" + tagName + ">\n");
    }

    public void finish() throws IOException {
        endTags(tags.size());
        writer.close();
    }

    public static String formatValue(String value, String refValue) {
        if (value == null) {
            return "<p style=\"color:red;\">-</p>";
        }
        if (!value.equals(refValue)) {
            return "<p style=\"color:red;\">" + value + "</p>";
        }
        return value;
    }

    private void indentTag() throws IOException {
        for (int i = 0; i < tags.size(); i++) {
            writer.append("\t");
        }
    }
}
