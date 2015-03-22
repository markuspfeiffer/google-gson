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

package com.google.gson;

import com.google.gson.internal.$Gson$Preconditions;
import com.google.gson.internal.Streams;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;


/**
 * Adapts a Gson 1.x tree-style adapter as a streaming TypeAdapter. Since the
 * tree adapter may be serialization-only or deserialization-only, this class
 * has a facility to lookup a delegate type adapter on demand.
 */
final class TreeTypeAdapter<T> extends TypeAdapter<T> {

    private final JsonSerializer<T>   serializer;
    private final JsonDeserializer<T> deserializer;
    private final Gson                gson;
    private final TypeToken<T>        typeToken;
    private final TypeAdapterFactory  skipPast;

    /** The delegate is lazily created because it may not be needed, and creating it may fail. */
    private TypeAdapter<T>            delegate;

    private TreeTypeAdapter(final JsonSerializer<T> serializer, final JsonDeserializer<T> deserializer,
            final Gson gson, final TypeToken<T> typeToken, final TypeAdapterFactory skipPast) {
        this.serializer = serializer;
        this.deserializer = deserializer;
        this.gson = gson;
        this.typeToken = typeToken;
        this.skipPast = skipPast;
    }

    @Override
    public T read(final JsonReader in) throws IOException {
        if (this.deserializer == null) {
            return this.delegate().read(in);
        }
        final JsonElement value = Streams.parse(in);
        if (value.isJsonNull()) {
            return null;
        }
        return this.deserializer.deserialize(value, this.typeToken.getType(), this.gson.deserializationContext);
    }

    @Override
    public void write(final JsonWriter out, final T value) throws IOException {
        if (this.serializer == null) {
            this.delegate().write(out, value);
            return;
        }
        if (value == null) {
            out.nullValue();
            return;
        }
        final JsonElement tree = this.serializer.serialize(value, this.typeToken.getType(), this.gson.serializationContext);
        Streams.write(tree, out);
    }

    private TypeAdapter<T> delegate() {
        final TypeAdapter<T> d = this.delegate;
        return d != null
                ? d
                : (this.delegate = this.gson.getDelegateAdapter(this.skipPast, this.typeToken));
    }

    /**
     * Returns a new factory that will match each type against {@code exactType}.
     */
    public static TypeAdapterFactory newFactory(final TypeToken<?> exactType, final Object typeAdapter) {
        return new SingleTypeFactory(typeAdapter, exactType, false, null);
    }

    /**
     * Returns a new factory that will match each type and its raw type against
     * {@code exactType}.
     */
    public static TypeAdapterFactory newFactoryWithMatchRawType(
            final TypeToken<?> exactType, final Object typeAdapter) {
        // only bother matching raw types if exact type is a raw type
        final boolean matchRawType = exactType.getType() == exactType.getRawType();
        return new SingleTypeFactory(typeAdapter, exactType, matchRawType, null);
    }

    /**
     * Returns a new factory that will match each type's raw type for assignability
     * to {@code hierarchyType}.
     */
    public static TypeAdapterFactory newTypeHierarchyFactory(
            final Class<?> hierarchyType, final Object typeAdapter) {
        return new SingleTypeFactory(typeAdapter, null, false, hierarchyType);
    }

    private static class SingleTypeFactory implements TypeAdapterFactory {

        private final TypeToken<?>        exactType;
        private final boolean             matchRawType;
        private final Class<?>            hierarchyType;
        private final JsonSerializer<?>   serializer;
        private final JsonDeserializer<?> deserializer;

        private SingleTypeFactory(final Object typeAdapter, final TypeToken<?> exactType, final boolean matchRawType,
                final Class<?> hierarchyType) {
            this.serializer = typeAdapter instanceof JsonSerializer
                    ? (JsonSerializer<?>) typeAdapter
                    : null;
            this.deserializer = typeAdapter instanceof JsonDeserializer
                    ? (JsonDeserializer<?>) typeAdapter
                    : null;
            $Gson$Preconditions.checkArgument(this.serializer != null || this.deserializer != null);
            this.exactType = exactType;
            this.matchRawType = matchRawType;
            this.hierarchyType = hierarchyType;
        }

        @SuppressWarnings("unchecked")
        // guarded by typeToken.equals() call
        public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
            final boolean matches = this.exactType != null
                    ? this.exactType.equals(type) || this.matchRawType && this.exactType.getType() == type.getRawType()
                    : this.hierarchyType.isAssignableFrom(type.getRawType());
            return matches
                    ? new TreeTypeAdapter<T>((JsonSerializer<T>) this.serializer,
                            (JsonDeserializer<T>) this.deserializer, gson, type, this)
                    : null;
        }
    }
}
