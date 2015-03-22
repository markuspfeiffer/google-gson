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

import static com.google.gson.internal.bind.JsonAdapterAnnotationTypeAdapterFactory.getTypeAdapter;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.Serialize;
import com.google.gson.annotations.Serialize.Inclusion;
import com.google.gson.annotations.SerializedName;
import com.google.gson.internal.$Gson$Types;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.internal.Excluder;
import com.google.gson.internal.ObjectConstructor;
import com.google.gson.internal.Primitives;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.LinkedHashMap;
import java.util.Map;


/**
 * Type adapter that reflects over the fields and methods of a class.
 */
public final class ReflectiveTypeAdapterFactory implements TypeAdapterFactory {

    private final ConstructorConstructor constructorConstructor;
    private final FieldNamingStrategy    fieldNamingPolicy;
    private final Excluder               excluder;

    public ReflectiveTypeAdapterFactory(final ConstructorConstructor constructorConstructor,
            final FieldNamingStrategy fieldNamingPolicy, final Excluder excluder) {
        this.constructorConstructor = constructorConstructor;
        this.fieldNamingPolicy = fieldNamingPolicy;
        this.excluder = excluder;
    }

    public boolean excludeField(final Field f, final boolean serialize) {
        return excludeField(f, serialize, this.excluder);
    }

    static boolean excludeField(final Field f, final boolean serialize, final Excluder excluder) {
        return !excluder.excludeClass(f.getType(), serialize) && !excluder.excludeField(f, serialize);
    }

    private String getFieldName(final Field f) {
        return getFieldName(this.fieldNamingPolicy, f);
    }

    static String getFieldName(final FieldNamingStrategy fieldNamingPolicy, final Field f) {
        final SerializedName serializedName = f.getAnnotation(SerializedName.class);
        return serializedName == null ? fieldNamingPolicy.translateName(f) : serializedName.value();
    }

    private Inclusion getInclusion(final Field f) {

        final Serialize annotation = f.getAnnotation(Serialize.class);

        if (annotation == null || annotation.value() == null) {
            return Inclusion.DEFAULT;
        }

        return annotation.value();
    }

    public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
        final Class<? super T> raw = type.getRawType();

        if (!Object.class.isAssignableFrom(raw)) {
            return null; // it's a primitive!
        }

        final ObjectConstructor<T> constructor = this.constructorConstructor.get(type);
        return new Adapter<T>(constructor, this.getBoundFields(gson, type, raw));
    }

    private ReflectiveTypeAdapterFactory.BoundField createBoundField(
            final Gson context,
            final Field field,
            final String name,
            final Inclusion inclusion,
            final TypeToken<?> fieldType,
            final boolean serialize,
            final boolean deserialize) {
        final boolean isPrimitive = Primitives.isPrimitive(fieldType.getRawType());

        // special casing primitives here saves ~5% on Android...
        return new ReflectiveTypeAdapterFactory.BoundField(name, inclusion, serialize, deserialize) {

            final TypeAdapter<?> typeAdapter = ReflectiveTypeAdapterFactory.this.getFieldAdapter(context, field, fieldType);

            @SuppressWarnings({"unchecked", "rawtypes"})
            // the type adapter and field type always agree
            @Override
            void write(final JsonWriter writer, final Object value)
                    throws IOException, IllegalAccessException {
                final Object fieldValue = field.get(value);
                final TypeAdapter t =
                        new TypeAdapterRuntimeTypeWrapper(context, this.typeAdapter, fieldType.getType());
                t.write(writer, fieldValue);
            }

            @Override
            void read(final JsonReader reader, final Object value)
                    throws IOException, IllegalAccessException {
                final Object fieldValue = this.typeAdapter.read(reader);
                if (fieldValue != null || !isPrimitive) {
                    field.set(value, fieldValue);
                }
            }

            @Override
            public boolean writeField(final Object value) throws IOException, IllegalAccessException {
                if (!this.serialized) {
                    return false;
                }
                final Object fieldValue = field.get(value);
                return fieldValue != value; // avoid recursion for example for Throwable.cause
            }
        };
    }

    private TypeAdapter<?> getFieldAdapter(final Gson gson, final Field field, final TypeToken<?> fieldType) {
        final JsonAdapter annotation = field.getAnnotation(JsonAdapter.class);
        if (annotation != null) {
            final TypeAdapter<?> adapter = getTypeAdapter(this.constructorConstructor, gson, fieldType, annotation);
            if (adapter != null) {
                return adapter;
            }
        }
        return gson.getAdapter(fieldType);
    }

    private Map<String, BoundField> getBoundFields(final Gson context, TypeToken<?> type, Class<?> raw) {
        final Map<String, BoundField> result = new LinkedHashMap<String, BoundField>();
        if (raw.isInterface()) {
            return result;
        }

        final Type declaredType = type.getType();
        while (raw != Object.class) {
            final Field[] fields = raw.getDeclaredFields();
            for (final Field field : fields) {
                final boolean serialize = this.excludeField(field, true);
                final boolean deserialize = this.excludeField(field, false);

                if (!serialize && !deserialize) {
                    continue;
                }

                field.setAccessible(true);
                final Type fieldType = $Gson$Types.resolve(type.getType(), raw, field.getGenericType());
                final BoundField boundField = this.createBoundField(
                        context,
                        field,
                        this.getFieldName(field),
                        this.getInclusion(field),
                        TypeToken.get(fieldType),
                        serialize,
                        deserialize);

                final BoundField previous = result.put(boundField.name, boundField);
                if (previous != null) {
                    throw new IllegalArgumentException(declaredType
                            + " declares multiple JSON fields named " + previous.name);
                }
            }
            type = TypeToken.get($Gson$Types.resolve(type.getType(), raw, raw.getGenericSuperclass()));
            raw = type.getRawType();
        }
        return result;
    }

    static abstract class BoundField {

        final String    name;
        final Inclusion inclusion;
        final boolean   serialized;
        final boolean   deserialized;

        protected BoundField(final String name, final Inclusion inclusion, final boolean serialized, final boolean deserialized) {
            this.name = name;
            this.inclusion = inclusion;
            this.serialized = serialized;
            this.deserialized = deserialized;
        }

        abstract boolean writeField(Object value) throws IOException, IllegalAccessException;

        abstract void write(JsonWriter writer, Object value) throws IOException, IllegalAccessException;

        abstract void read(JsonReader reader, Object value) throws IOException, IllegalAccessException;
    }

    public static final class Adapter<T> extends TypeAdapter<T> {

        private final ObjectConstructor<T>    constructor;
        private final Map<String, BoundField> boundFields;

        private Adapter(final ObjectConstructor<T> constructor, final Map<String, BoundField> boundFields) {
            this.constructor = constructor;
            this.boundFields = boundFields;
        }

        @Override
        public T read(final JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            final T instance = this.constructor.construct();

            try {
                in.beginObject();
                while (in.hasNext()) {
                    final String name = in.nextName();
                    final BoundField field = this.boundFields.get(name);
                    if (field == null || !field.deserialized) {
                        in.skipValue();
                    } else {
                        field.read(in, instance);
                    }
                }
            } catch (final IllegalStateException e) {
                throw new JsonSyntaxException(e);
            } catch (final IllegalAccessException e) {
                throw new AssertionError(e);
            }
            in.endObject();
            return instance;
        }

        @Override
        public void write(final JsonWriter out, final T value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();
            try {
                for (final BoundField boundField : this.boundFields.values()) {
                    if (boundField.writeField(value)) {
                        out.name(boundField.name);
                        out.inclusion(boundField.inclusion);
                        boundField.write(out, value);
                    }
                }
            } catch (final IllegalAccessException e) {
                throw new AssertionError();
            }
            out.endObject();
        }
    }
}
