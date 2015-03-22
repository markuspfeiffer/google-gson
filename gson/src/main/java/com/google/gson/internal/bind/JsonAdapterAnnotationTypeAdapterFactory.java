/*
 * Copyright (C) 2014 Google Inc.
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

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.internal.ConstructorConstructor;
import com.google.gson.reflect.TypeToken;


/**
 * Given a type T, looks for the annotation {@link JsonAdapter} and uses an instance of the
 * specified class as the default type adapter.
 *
 * @since 2.3
 */
public final class JsonAdapterAnnotationTypeAdapterFactory implements TypeAdapterFactory {

    private final ConstructorConstructor constructorConstructor;

    public JsonAdapterAnnotationTypeAdapterFactory(final ConstructorConstructor constructorConstructor) {
        this.constructorConstructor = constructorConstructor;
    }

    @SuppressWarnings("unchecked")
    public <T> TypeAdapter<T> create(final Gson gson, final TypeToken<T> targetType) {
        final JsonAdapter annotation = targetType.getRawType().getAnnotation(JsonAdapter.class);
        if (annotation == null) {
            return null;
        }
        return (TypeAdapter<T>) getTypeAdapter(this.constructorConstructor, gson, targetType, annotation);
    }

    @SuppressWarnings("unchecked")
    // Casts guarded by conditionals.
    static TypeAdapter<?> getTypeAdapter(final ConstructorConstructor constructorConstructor, final Gson gson,
            final TypeToken<?> fieldType, final JsonAdapter annotation) {
        final Class<?> value = annotation.value();
        if (TypeAdapter.class.isAssignableFrom(value)) {
            final Class<TypeAdapter<?>> typeAdapter = (Class<TypeAdapter<?>>) value;
            return constructorConstructor.get(TypeToken.get(typeAdapter)).construct();
        }
        if (TypeAdapterFactory.class.isAssignableFrom(value)) {
            final Class<TypeAdapterFactory> typeAdapterFactory = (Class<TypeAdapterFactory>) value;
            return constructorConstructor.get(TypeToken.get(typeAdapterFactory))
                    .construct()
                    .create(gson, fieldType);
        }

        throw new IllegalArgumentException(
                "@JsonAdapter value must be TypeAdapter or TypeAdapterFactory reference.");
    }
}