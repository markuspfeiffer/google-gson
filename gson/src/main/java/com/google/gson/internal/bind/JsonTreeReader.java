/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.gson.internal.bind;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * This reader walks the elements of a JsonElement as if it was coming from a
 * character stream.
 *
 * @author Jesse Wilson
 */
public final class JsonTreeReader extends JsonReader {

    private static final Reader UNREADABLE_READER = new Reader() {

                                                      @Override
                                                      public int read(final char[] buffer, final int offset, final int count) throws IOException {
                                                          throw new AssertionError();
                                                      }

                                                      @Override
                                                      public void close() throws IOException {
                                                          throw new AssertionError();
                                                      }
                                                  };
    private static final Object SENTINEL_CLOSED   = new Object();

    private final List<Object>  stack             = new ArrayList<Object>();

    public JsonTreeReader(final JsonElement element) {
        super(UNREADABLE_READER);
        this.stack.add(element);
    }

    @Override
    public void beginArray() throws IOException {
        this.expect(JsonToken.BEGIN_ARRAY);
        final JsonArray array = (JsonArray) this.peekStack();
        this.stack.add(array.iterator());
    }

    @Override
    public void endArray() throws IOException {
        this.expect(JsonToken.END_ARRAY);
        this.popStack(); // empty iterator
        this.popStack(); // array
    }

    @Override
    public void beginObject() throws IOException {
        this.expect(JsonToken.BEGIN_OBJECT);
        final JsonObject object = (JsonObject) this.peekStack();
        this.stack.add(object.entrySet().iterator());
    }

    @Override
    public void endObject() throws IOException {
        this.expect(JsonToken.END_OBJECT);
        this.popStack(); // empty iterator
        this.popStack(); // object
    }

    @Override
    public boolean hasNext() throws IOException {
        final JsonToken token = this.peek();
        return token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY;
    }

    @Override
    public JsonToken peek() throws IOException {
        if (this.stack.isEmpty()) {
            return JsonToken.END_DOCUMENT;
        }

        final Object o = this.peekStack();
        if (o instanceof Iterator) {
            final boolean isObject = this.stack.get(this.stack.size() - 2) instanceof JsonObject;
            final Iterator<?> iterator = (Iterator<?>) o;
            if (iterator.hasNext()) {
                if (isObject) {
                    return JsonToken.NAME;
                } else {
                    this.stack.add(iterator.next());
                    return this.peek();
                }
            } else {
                return isObject ? JsonToken.END_OBJECT : JsonToken.END_ARRAY;
            }
        } else if (o instanceof JsonObject) {
            return JsonToken.BEGIN_OBJECT;
        } else if (o instanceof JsonArray) {
            return JsonToken.BEGIN_ARRAY;
        } else if (o instanceof JsonPrimitive) {
            final JsonPrimitive primitive = (JsonPrimitive) o;
            if (primitive.isString()) {
                return JsonToken.STRING;
            } else if (primitive.isBoolean()) {
                return JsonToken.BOOLEAN;
            } else if (primitive.isNumber()) {
                return JsonToken.NUMBER;
            } else {
                throw new AssertionError();
            }
        } else if (o instanceof JsonNull) {
            return JsonToken.NULL;
        } else if (o == SENTINEL_CLOSED) {
            throw new IllegalStateException("JsonReader is closed");
        } else {
            throw new AssertionError();
        }
    }

    private Object peekStack() {
        return this.stack.get(this.stack.size() - 1);
    }

    private Object popStack() {
        return this.stack.remove(this.stack.size() - 1);
    }

    private void expect(final JsonToken expected) throws IOException {
        if (this.peek() != expected) {
            throw new IllegalStateException("Expected " + expected + " but was " + this.peek());
        }
    }

    @Override
    public String nextName() throws IOException {
        this.expect(JsonToken.NAME);
        final Iterator<?> i = (Iterator<?>) this.peekStack();
        final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
        this.stack.add(entry.getValue());
        return (String) entry.getKey();
    }

    @Override
    public String nextString() throws IOException {
        final JsonToken token = this.peek();
        if (token != JsonToken.STRING && token != JsonToken.NUMBER) {
            throw new IllegalStateException("Expected " + JsonToken.STRING + " but was " + token);
        }
        return ((JsonPrimitive) this.popStack()).getAsString();
    }

    @Override
    public boolean nextBoolean() throws IOException {
        this.expect(JsonToken.BOOLEAN);
        return ((JsonPrimitive) this.popStack()).getAsBoolean();
    }

    @Override
    public void nextNull() throws IOException {
        this.expect(JsonToken.NULL);
        this.popStack();
    }

    @Override
    public double nextDouble() throws IOException {
        final JsonToken token = this.peek();
        if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
            throw new IllegalStateException("Expected " + JsonToken.NUMBER + " but was " + token);
        }
        final double result = ((JsonPrimitive) this.peekStack()).getAsDouble();
        if (!this.isLenient() && (Double.isNaN(result) || Double.isInfinite(result))) {
            throw new NumberFormatException("JSON forbids NaN and infinities: " + result);
        }
        this.popStack();
        return result;
    }

    @Override
    public long nextLong() throws IOException {
        final JsonToken token = this.peek();
        if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
            throw new IllegalStateException("Expected " + JsonToken.NUMBER + " but was " + token);
        }
        final long result = ((JsonPrimitive) this.peekStack()).getAsLong();
        this.popStack();
        return result;
    }

    @Override
    public int nextInt() throws IOException {
        final JsonToken token = this.peek();
        if (token != JsonToken.NUMBER && token != JsonToken.STRING) {
            throw new IllegalStateException("Expected " + JsonToken.NUMBER + " but was " + token);
        }
        final int result = ((JsonPrimitive) this.peekStack()).getAsInt();
        this.popStack();
        return result;
    }

    @Override
    public void close() throws IOException {
        this.stack.clear();
        this.stack.add(SENTINEL_CLOSED);
    }

    @Override
    public void skipValue() throws IOException {
        if (this.peek() == JsonToken.NAME) {
            this.nextName();
        } else {
            this.popStack();
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName();
    }

    public void promoteNameToValue() throws IOException {
        this.expect(JsonToken.NAME);
        final Iterator<?> i = (Iterator<?>) this.peekStack();
        final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) i.next();
        this.stack.add(entry.getValue());
        this.stack.add(new JsonPrimitive((String) entry.getKey()));
    }
}
