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

package com.google.gson.internal;

import java.io.ObjectStreamException;
import java.math.BigDecimal;


/**
 * This class holds a number value that is lazily converted to a specific number type
 *
 * @author Inderjeet Singh
 */
public final class LazilyParsedNumber extends Number {

    private final String value;

    public LazilyParsedNumber(final String value) {
        this.value = value;
    }

    @Override
    public int intValue() {
        try {
            return Integer.parseInt(this.value);
        } catch (final NumberFormatException e) {
            try {
                return (int) Long.parseLong(this.value);
            } catch (final NumberFormatException nfe) {
                return new BigDecimal(this.value).intValue();
            }
        }
    }

    @Override
    public long longValue() {
        try {
            return Long.parseLong(this.value);
        } catch (final NumberFormatException e) {
            return new BigDecimal(this.value).longValue();
        }
    }

    @Override
    public float floatValue() {
        return Float.parseFloat(this.value);
    }

    @Override
    public double doubleValue() {
        return Double.parseDouble(this.value);
    }

    @Override
    public String toString() {
        return this.value;
    }

    /**
     * If somebody is unlucky enough to have to serialize one of these, serialize
     * it as a BigDecimal so that they won't need Gson on the other side to
     * deserialize it.
     */
    private Object writeReplace() throws ObjectStreamException {
        return new BigDecimal(this.value);
    }
}