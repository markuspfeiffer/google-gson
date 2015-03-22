/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.gson.internal;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.Since;
import com.google.gson.annotations.Until;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * This class selects which fields and types to omit. It is configurable,
 * supporting version attributes {@link Since} and {@link Until}, modifiers,
 * synthetic fields, anonymous and local classes, inner classes, and fields with
 * the {@link Expose} annotation.
 *
 * <p>This class is a type adapter factory; types that are excluded will be
 * adapted to null. It may delegate to another type adapter if only one
 * direction is excluded.
 *
 * @author Joel Leitch
 * @author Jesse Wilson
 */
public final class Excluder implements TypeAdapterFactory, Cloneable {

    private static final double     IGNORE_VERSIONS           = -1.0d;
    public static final Excluder    DEFAULT                   = new Excluder();

    private double                  version                   = IGNORE_VERSIONS;
    private int                     modifiers                 = Modifier.TRANSIENT | Modifier.STATIC;
    private boolean                 serializeInnerClasses     = true;
    private boolean                 requireExpose;
    private List<ExclusionStrategy> serializationStrategies   = Collections.emptyList();
    private List<ExclusionStrategy> deserializationStrategies = Collections.emptyList();

    @Override
    protected Excluder clone() {
        try {
            return (Excluder) super.clone();
        } catch (final CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    public Excluder withVersion(final double ignoreVersionsAfter) {
        final Excluder result = this.clone();
        result.version = ignoreVersionsAfter;
        return result;
    }

    public Excluder withModifiers(final int... modifiers) {
        final Excluder result = this.clone();
        result.modifiers = 0;
        for (final int modifier : modifiers) {
            result.modifiers |= modifier;
        }
        return result;
    }

    public Excluder disableInnerClassSerialization() {
        final Excluder result = this.clone();
        result.serializeInnerClasses = false;
        return result;
    }

    public Excluder excludeFieldsWithoutExposeAnnotation() {
        final Excluder result = this.clone();
        result.requireExpose = true;
        return result;
    }

    public Excluder withExclusionStrategy(final ExclusionStrategy exclusionStrategy,
            final boolean serialization, final boolean deserialization) {
        final Excluder result = this.clone();
        if (serialization) {
            result.serializationStrategies = new ArrayList<ExclusionStrategy>(this.serializationStrategies);
            result.serializationStrategies.add(exclusionStrategy);
        }
        if (deserialization) {
            result.deserializationStrategies = new ArrayList<ExclusionStrategy>(this.deserializationStrategies);
            result.deserializationStrategies.add(exclusionStrategy);
        }
        return result;
    }

    public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> type) {
        final Class<?> rawType = type.getRawType();
        final boolean skipSerialize = this.excludeClass(rawType, true);
        final boolean skipDeserialize = this.excludeClass(rawType, false);

        if (!skipSerialize && !skipDeserialize) {
            return null;
        }

        return new TypeAdapter<T>() {

            /** The delegate is lazily created because it may not be needed, and creating it may fail. */
            private TypeAdapter<T> delegate;

            @Override
            public T read(final JsonReader in) throws IOException {
                if (skipDeserialize) {
                    in.skipValue();
                    return null;
                }
                return this.delegate().read(in);
            }

            @Override
            public void write(final JsonWriter out, final T value) throws IOException {
                if (skipSerialize) {
                    out.nullValue();
                    return;
                }
                this.delegate().write(out, value);
            }

            private TypeAdapter<T> delegate() {
                final TypeAdapter<T> d = this.delegate;
                return d != null
                        ? d
                        : (this.delegate = gson.getDelegateAdapter(Excluder.this, type));
            }
        };
    }

    public boolean excludeField(final Field field, final boolean serialize) {
        if ((this.modifiers & field.getModifiers()) != 0) {
            return true;
        }

        if (this.version != Excluder.IGNORE_VERSIONS
                && !this.isValidVersion(field.getAnnotation(Since.class), field.getAnnotation(Until.class))) {
            return true;
        }

        if (field.isSynthetic()) {
            return true;
        }

        if (this.requireExpose) {
            final Expose annotation = field.getAnnotation(Expose.class);
            if (annotation == null || (serialize ? !annotation.serialize() : !annotation.deserialize())) {
                return true;
            }
        }

        if (!this.serializeInnerClasses && this.isInnerClass(field.getType())) {
            return true;
        }

        if (this.isAnonymousOrLocal(field.getType())) {
            return true;
        }

        final List<ExclusionStrategy> list = serialize ? this.serializationStrategies : this.deserializationStrategies;
        if (!list.isEmpty()) {
            final FieldAttributes fieldAttributes = new FieldAttributes(field);
            for (final ExclusionStrategy exclusionStrategy : list) {
                if (exclusionStrategy.shouldSkipField(fieldAttributes)) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean excludeClass(final Class<?> clazz, final boolean serialize) {
        if (this.version != Excluder.IGNORE_VERSIONS
                && !this.isValidVersion(clazz.getAnnotation(Since.class), clazz.getAnnotation(Until.class))) {
            return true;
        }

        if (!this.serializeInnerClasses && this.isInnerClass(clazz)) {
            return true;
        }

        if (this.isAnonymousOrLocal(clazz)) {
            return true;
        }

        final List<ExclusionStrategy> list = serialize ? this.serializationStrategies : this.deserializationStrategies;
        for (final ExclusionStrategy exclusionStrategy : list) {
            if (exclusionStrategy.shouldSkipClass(clazz)) {
                return true;
            }
        }

        return false;
    }

    private boolean isAnonymousOrLocal(final Class<?> clazz) {
        return !Enum.class.isAssignableFrom(clazz)
                && (clazz.isAnonymousClass() || clazz.isLocalClass());
    }

    private boolean isInnerClass(final Class<?> clazz) {
        return clazz.isMemberClass() && !this.isStatic(clazz);
    }

    private boolean isStatic(final Class<?> clazz) {
        return (clazz.getModifiers() & Modifier.STATIC) != 0;
    }

    private boolean isValidVersion(final Since since, final Until until) {
        return this.isValidSince(since) && this.isValidUntil(until);
    }

    private boolean isValidSince(final Since annotation) {
        if (annotation != null) {
            final double annotationVersion = annotation.value();
            if (annotationVersion > this.version) {
                return false;
            }
        }
        return true;
    }

    private boolean isValidUntil(final Until annotation) {
        if (annotation != null) {
            final double annotationVersion = annotation.value();
            if (annotationVersion <= this.version) {
                return false;
            }
        }
        return true;
    }
}
