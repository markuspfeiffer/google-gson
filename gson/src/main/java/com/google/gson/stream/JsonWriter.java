/*
 * Copyright (C) 2010 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson.stream;

import static com.google.gson.stream.JsonScope.DANGLING_NAME;
import static com.google.gson.stream.JsonScope.EMPTY_ARRAY;
import static com.google.gson.stream.JsonScope.EMPTY_DOCUMENT;
import static com.google.gson.stream.JsonScope.EMPTY_OBJECT;
import static com.google.gson.stream.JsonScope.NONEMPTY_ARRAY;
import static com.google.gson.stream.JsonScope.NONEMPTY_DOCUMENT;
import static com.google.gson.stream.JsonScope.NONEMPTY_OBJECT;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.Writer;


/**
 * Writes a JSON (<a href="http://www.ietf.org/rfc/rfc4627.txt">RFC 4627</a>)
 * encoded value to a stream, one token at a time. The stream includes both
 * literal values (strings, numbers, booleans and nulls) as well as the begin
 * and end delimiters of objects and arrays.
 *
 * <h3>Encoding JSON</h3>
 * To encode your data as JSON, create a new {@code JsonWriter}. Each JSON
 * document must contain one top-level array or object. Call methods on the
 * writer as you walk the structure's contents, nesting arrays and objects as
 * necessary:
 * <ul>
 *   <li>To write <strong>arrays</strong>, first call {@link #beginArray()}.
 *       Write each of the array's elements with the appropriate {@link #value}
 *       methods or by nesting other arrays and objects. Finally close the array
 *       using {@link #endArray()}.
 *   <li>To write <strong>objects</strong>, first call {@link #beginObject()}.
 *       Write each of the object's properties by alternating calls to
 *       {@link #name} with the property's value. Write property values with the
 *       appropriate {@link #value} method or by nesting other objects or arrays.
 *       Finally close the object using {@link #endObject()}.
 * </ul>
 *
 * <h3>Example</h3>
 * Suppose we'd like to encode a stream of messages such as the following: <pre> {@code
 * [
 *   {
 *     "id": 912345678901,
 *     "text": "How do I stream JSON in Java?",
 *     "geo": null,
 *     "user": {
 *       "name": "json_newb",
 *       "followers_count": 41
 *      }
 *   },
 *   {
 *     "id": 912345678902,
 *     "text": "@json_newb just use JsonWriter!",
 *     "geo": [50.454722, -104.606667],
 *     "user": {
 *       "name": "jesse",
 *       "followers_count": 2
 *     }
 *   }
 * ]}</pre>
 * This code encodes the above structure: <pre>   {@code
 *   public void writeJsonStream(OutputStream out, List<Message> messages) throws IOException {
 *     JsonWriter writer = new JsonWriter(new OutputStreamWriter(out, "UTF-8"));
 *     writer.setIndentSpaces(4);
 *     writeMessagesArray(writer, messages);
 *     writer.close();
 *   }
 *
 *   public void writeMessagesArray(JsonWriter writer, List<Message> messages) throws IOException {
 *     writer.beginArray();
 *     for (Message message : messages) {
 *       writeMessage(writer, message);
 *     }
 *     writer.endArray();
 *   }
 *
 *   public void writeMessage(JsonWriter writer, Message message) throws IOException {
 *     writer.beginObject();
 *     writer.name("id").value(message.getId());
 *     writer.name("text").value(message.getText());
 *     if (message.getGeo() != null) {
 *       writer.name("geo");
 *       writeDoublesArray(writer, message.getGeo());
 *     } else {
 *       writer.name("geo").nullValue();
 *     }
 *     writer.name("user");
 *     writeUser(writer, message.getUser());
 *     writer.endObject();
 *   }
 *
 *   public void writeUser(JsonWriter writer, User user) throws IOException {
 *     writer.beginObject();
 *     writer.name("name").value(user.getName());
 *     writer.name("followers_count").value(user.getFollowersCount());
 *     writer.endObject();
 *   }
 *
 *   public void writeDoublesArray(JsonWriter writer, List<Double> doubles) throws IOException {
 *     writer.beginArray();
 *     for (Double value : doubles) {
 *       writer.value(value);
 *     }
 *     writer.endArray();
 *   }}</pre>
 *
 * <p>Each {@code JsonWriter} may be used to write a single JSON stream.
 * Instances of this class are not thread safe. Calls that would result in a
 * malformed JSON string will fail with an {@link IllegalStateException}.
 *
 * @author Jesse Wilson
 * @since 1.6
 */
public class JsonWriter implements Closeable, Flushable {

    /*
     * From RFC 4627, "All Unicode characters may be placed within the
     * quotation marks except for the characters that must be escaped:
     * quotation mark, reverse solidus, and the control characters
     * (U+0000 through U+001F)."
     * 
     * We also escape '\u2028' and '\u2029', which JavaScript interprets as
     * newline characters. This prevents eval() from failing with a syntax
     * error. http://code.google.com/p/google-gson/issues/detail?id=341
     */
    private static final String[] REPLACEMENT_CHARS;
    private static final String[] HTML_SAFE_REPLACEMENT_CHARS;
    static {
        REPLACEMENT_CHARS = new String[128];
        for (int i = 0; i <= 0x1f; i++) {
            REPLACEMENT_CHARS[i] = String.format("\\u%04x", i);
        }
        REPLACEMENT_CHARS['"'] = "\\\"";
        REPLACEMENT_CHARS['\\'] = "\\\\";
        REPLACEMENT_CHARS['\t'] = "\\t";
        REPLACEMENT_CHARS['\b'] = "\\b";
        REPLACEMENT_CHARS['\n'] = "\\n";
        REPLACEMENT_CHARS['\r'] = "\\r";
        REPLACEMENT_CHARS['\f'] = "\\f";
        HTML_SAFE_REPLACEMENT_CHARS = REPLACEMENT_CHARS.clone();
        HTML_SAFE_REPLACEMENT_CHARS['<'] = "\\u003c";
        HTML_SAFE_REPLACEMENT_CHARS['>'] = "\\u003e";
        HTML_SAFE_REPLACEMENT_CHARS['&'] = "\\u0026";
        HTML_SAFE_REPLACEMENT_CHARS['='] = "\\u003d";
        HTML_SAFE_REPLACEMENT_CHARS['\''] = "\\u0027";
    }

    /** The output data, containing at most one top-level array or object. */
    private final Writer          out;

    private int[]                 stack          = new int[32];
    private int                   stackSize      = 0;
    {
        this.push(EMPTY_DOCUMENT);
    }

    /**
     * A string containing a full set of spaces for a single level of
     * indentation, or null for no pretty printing.
     */
    private String                indent;

    /**
     * The name/value separator; either ":" or ": ".
     */
    private String                separator      = ":";

    private boolean               lenient;

    private boolean               htmlSafe;

    private String                deferredName;

    private boolean               serializeNulls = true;

    /**
     * Creates a new instance that writes a JSON-encoded stream to {@code out}.
     * For best performance, ensure {@link Writer} is buffered; wrapping in
     * {@link java.io.BufferedWriter BufferedWriter} if necessary.
     */
    public JsonWriter(final Writer out) {
        if (out == null) {
            throw new NullPointerException("out == null");
        }
        this.out = out;
    }

    /**
     * Sets the indentation string to be repeated for each level of indentation
     * in the encoded document. If {@code indent.isEmpty()} the encoded document
     * will be compact. Otherwise the encoded document will be more
     * human-readable.
     *
     * @param indent a string containing only whitespace.
     */
    public final void setIndent(final String indent) {
        if (indent.length() == 0) {
            this.indent = null;
            this.separator = ":";
        } else {
            this.indent = indent;
            this.separator = ": ";
        }
    }

    /**
     * Configure this writer to relax its syntax rules. By default, this writer
     * only emits well-formed JSON as specified by <a
     * href="http://www.ietf.org/rfc/rfc4627.txt">RFC 4627</a>. Setting the writer
     * to lenient permits the following:
     * <ul>
     *   <li>Top-level values of any type. With strict writing, the top-level
     *       value must be an object or an array.
     *   <li>Numbers may be {@link Double#isNaN() NaNs} or {@link
     *       Double#isInfinite() infinities}.
     * </ul>
     */
    public final void setLenient(final boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * Returns true if this writer has relaxed syntax rules.
     */
    public boolean isLenient() {
        return this.lenient;
    }

    /**
     * Configure this writer to emit JSON that's safe for direct inclusion in HTML
     * and XML documents. This escapes the HTML characters {@code <}, {@code >},
     * {@code &} and {@code =} before writing them to the stream. Without this
     * setting, your XML/HTML encoder should replace these characters with the
     * corresponding escape sequences.
     */
    public final void setHtmlSafe(final boolean htmlSafe) {
        this.htmlSafe = htmlSafe;
    }

    /**
     * Returns true if this writer writes JSON that's safe for inclusion in HTML
     * and XML documents.
     */
    public final boolean isHtmlSafe() {
        return this.htmlSafe;
    }

    /**
     * Sets whether object members are serialized when their value is null.
     * This has no impact on array elements. The default is true.
     */
    public final void setSerializeNulls(final boolean serializeNulls) {
        this.serializeNulls = serializeNulls;
    }

    /**
     * Returns true if object members are serialized when their value is null.
     * This has no impact on array elements. The default is true.
     */
    public final boolean getSerializeNulls() {
        return this.serializeNulls;
    }

    /**
     * Begins encoding a new array. Each call to this method must be paired with
     * a call to {@link #endArray}.
     *
     * @return this writer.
     */
    public JsonWriter beginArray() throws IOException {
        this.writeDeferredName();
        return this.open(EMPTY_ARRAY, "[");
    }

    /**
     * Ends encoding the current array.
     *
     * @return this writer.
     */
    public JsonWriter endArray() throws IOException {
        return this.close(EMPTY_ARRAY, NONEMPTY_ARRAY, "]");
    }

    /**
     * Begins encoding a new object. Each call to this method must be paired
     * with a call to {@link #endObject}.
     *
     * @return this writer.
     */
    public JsonWriter beginObject() throws IOException {
        this.writeDeferredName();
        return this.open(EMPTY_OBJECT, "{");
    }

    /**
     * Ends encoding the current object.
     *
     * @return this writer.
     */
    public JsonWriter endObject() throws IOException {
        return this.close(EMPTY_OBJECT, NONEMPTY_OBJECT, "}");
    }

    /**
     * Enters a new scope by appending any necessary whitespace and the given
     * bracket.
     */
    private JsonWriter open(final int empty, final String openBracket) throws IOException {
        this.beforeValue(true);
        this.push(empty);
        this.out.write(openBracket);
        return this;
    }

    /**
     * Closes the current scope by appending any necessary whitespace and the
     * given bracket.
     */
    private JsonWriter close(final int empty, final int nonempty, final String closeBracket)
            throws IOException {
        final int context = this.peek();
        if (context != nonempty && context != empty) {
            throw new IllegalStateException("Nesting problem.");
        }
        if (this.deferredName != null) {
            throw new IllegalStateException("Dangling name: " + this.deferredName);
        }

        this.stackSize--;
        if (context == nonempty) {
            this.newline();
        }
        this.out.write(closeBracket);
        return this;
    }

    private void push(final int newTop) {
        if (this.stackSize == this.stack.length) {
            final int[] newStack = new int[this.stackSize * 2];
            System.arraycopy(this.stack, 0, newStack, 0, this.stackSize);
            this.stack = newStack;
        }
        this.stack[this.stackSize++] = newTop;
    }

    /**
     * Returns the value on the top of the stack.
     */
    private int peek() {
        if (this.stackSize == 0) {
            throw new IllegalStateException("JsonWriter is closed.");
        }
        return this.stack[this.stackSize - 1];
    }

    /**
     * Replace the value on the top of the stack with the given value.
     */
    private void replaceTop(final int topOfStack) {
        this.stack[this.stackSize - 1] = topOfStack;
    }

    /**
     * Encodes the property name.
     *
     * @param name the name of the forthcoming value. May not be null.
     * @return this writer.
     */
    public JsonWriter name(final String name) throws IOException {
        if (name == null) {
            throw new NullPointerException("name == null");
        }
        if (this.deferredName != null) {
            throw new IllegalStateException();
        }
        if (this.stackSize == 0) {
            throw new IllegalStateException("JsonWriter is closed.");
        }
        this.deferredName = name;
        return this;
    }

    private void writeDeferredName() throws IOException {
        if (this.deferredName != null) {
            this.beforeName();
            this.string(this.deferredName);
            this.deferredName = null;
        }
    }

    /**
     * Encodes {@code value}.
     *
     * @param value the literal string value, or null to encode a null literal.
     * @return this writer.
     */
    public JsonWriter value(final String value) throws IOException {
        if (value == null) {
            return this.nullValue();
        }
        this.writeDeferredName();
        this.beforeValue(false);
        this.string(value);
        return this;
    }

    /**
     * Encodes {@code null}.
     *
     * @return this writer.
     */
    public JsonWriter nullValue() throws IOException {
        if (this.deferredName != null) {
            if (this.serializeNulls) {
                this.writeDeferredName();
            } else {
                this.deferredName = null;
                return this; // skip the name and the value
            }
        }
        this.beforeValue(false);
        this.out.write("null");
        return this;
    }

    /**
     * Encodes {@code value}.
     *
     * @return this writer.
     */
    public JsonWriter value(final boolean value) throws IOException {
        this.writeDeferredName();
        this.beforeValue(false);
        this.out.write(value ? "true" : "false");
        return this;
    }

    /**
     * Encodes {@code value}.
     *
     * @param value a finite value. May not be {@link Double#isNaN() NaNs} or
     *     {@link Double#isInfinite() infinities}.
     * @return this writer.
     */
    public JsonWriter value(final double value) throws IOException {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("Numeric values must be finite, but was " + value);
        }
        this.writeDeferredName();
        this.beforeValue(false);
        this.out.append(Double.toString(value));
        return this;
    }

    /**
     * Encodes {@code value}.
     *
     * @return this writer.
     */
    public JsonWriter value(final long value) throws IOException {
        this.writeDeferredName();
        this.beforeValue(false);
        this.out.write(Long.toString(value));
        return this;
    }

    /**
     * Encodes {@code value}.
     *
     * @param value a finite value. May not be {@link Double#isNaN() NaNs} or
     *     {@link Double#isInfinite() infinities}.
     * @return this writer.
     */
    public JsonWriter value(final Number value) throws IOException {
        if (value == null) {
            return this.nullValue();
        }

        this.writeDeferredName();
        final String string = value.toString();
        if (!this.lenient
                && (string.equals("-Infinity") || string.equals("Infinity") || string.equals("NaN"))) {
            throw new IllegalArgumentException("Numeric values must be finite, but was " + value);
        }
        this.beforeValue(false);
        this.out.append(string);
        return this;
    }

    /**
     * Ensures all buffered data is written to the underlying {@link Writer}
     * and flushes that writer.
     */
    public void flush() throws IOException {
        if (this.stackSize == 0) {
            throw new IllegalStateException("JsonWriter is closed.");
        }
        this.out.flush();
    }

    /**
     * Flushes and closes this writer and the underlying {@link Writer}.
     *
     * @throws IOException if the JSON document is incomplete.
     */
    public void close() throws IOException {
        this.out.close();

        final int size = this.stackSize;
        if (size > 1 || size == 1 && this.stack[size - 1] != NONEMPTY_DOCUMENT) {
            throw new IOException("Incomplete document");
        }
        this.stackSize = 0;
    }

    private void string(final String value) throws IOException {
        final String[] replacements = this.htmlSafe ? HTML_SAFE_REPLACEMENT_CHARS : REPLACEMENT_CHARS;
        this.out.write("\"");
        int last = 0;
        final int length = value.length();
        for (int i = 0; i < length; i++) {
            final char c = value.charAt(i);
            String replacement;
            if (c < 128) {
                replacement = replacements[c];
                if (replacement == null) {
                    continue;
                }
            } else if (c == '\u2028') {
                replacement = "\\u2028";
            } else if (c == '\u2029') {
                replacement = "\\u2029";
            } else {
                continue;
            }
            if (last < i) {
                this.out.write(value, last, i - last);
            }
            this.out.write(replacement);
            last = i + 1;
        }
        if (last < length) {
            this.out.write(value, last, length - last);
        }
        this.out.write("\"");
    }

    private void newline() throws IOException {
        if (this.indent == null) {
            return;
        }

        this.out.write("\n");
        for (int i = 1, size = this.stackSize; i < size; i++) {
            this.out.write(this.indent);
        }
    }

    /**
     * Inserts any necessary separators and whitespace before a name. Also
     * adjusts the stack to expect the name's value.
     */
    private void beforeName() throws IOException {
        final int context = this.peek();
        if (context == NONEMPTY_OBJECT) { // first in object
            this.out.write(',');
        } else if (context != EMPTY_OBJECT) { // not in an object!
            throw new IllegalStateException("Nesting problem.");
        }
        this.newline();
        this.replaceTop(DANGLING_NAME);
    }

    /**
     * Inserts any necessary separators and whitespace before a literal value,
     * inline array, or inline object. Also adjusts the stack to expect either a
     * closing bracket or another element.
     *
     * @param root true if the value is a new array or object, the two values
     *     permitted as top-level elements.
     */
    @SuppressWarnings("fallthrough")
    private void beforeValue(final boolean root) throws IOException {
        switch (this.peek()) {
            case NONEMPTY_DOCUMENT:
                if (!this.lenient) {
                    throw new IllegalStateException(
                            "JSON must have only one top-level value.");
                }
                // fall-through
            case EMPTY_DOCUMENT: // first in document
                if (!this.lenient && !root) {
                    throw new IllegalStateException(
                            "JSON must start with an array or an object.");
                }
                this.replaceTop(NONEMPTY_DOCUMENT);
                break;

            case EMPTY_ARRAY: // first in array
                this.replaceTop(NONEMPTY_ARRAY);
                this.newline();
                break;

            case NONEMPTY_ARRAY: // another in array
                this.out.append(',');
                this.newline();
                break;

            case DANGLING_NAME: // value for name
                this.out.append(this.separator);
                this.replaceTop(NONEMPTY_OBJECT);
                break;

            default:
                throw new IllegalStateException("Nesting problem.");
        }
    }
}
