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

package com.google.gson;

import com.google.gson.internal.$Gson$Preconditions;
import com.google.gson.internal.LazilyParsedNumber;

import java.math.BigDecimal;
import java.math.BigInteger;


/**
 * A class representing a Json primitive value. A primitive value
 * is either a String, a Java primitive, or a Java primitive
 * wrapper type.
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 */
public final class JsonPrimitive extends JsonElement {

    private static final Class<?>[] PRIMITIVE_TYPES = {int.class, long.class, short.class,
                                                    float.class, double.class, byte.class, boolean.class, char.class, Integer.class, Long.class,
                                                    Short.class, Float.class, Double.class, Byte.class, Boolean.class, Character.class};

    private Object                  value;

    /**
     * Create a primitive containing a boolean value.
     *
     * @param bool the value to create the primitive with.
     */
    public JsonPrimitive(final Boolean bool) {
        this.setValue(bool);
    }

    /**
     * Create a primitive containing a {@link Number}.
     *
     * @param number the value to create the primitive with.
     */
    public JsonPrimitive(final Number number) {
        this.setValue(number);
    }

    /**
     * Create a primitive containing a String value.
     *
     * @param string the value to create the primitive with.
     */
    public JsonPrimitive(final String string) {
        this.setValue(string);
    }

    /**
     * Create a primitive containing a character. The character is turned into a one character String
     * since Json only supports String.
     *
     * @param c the value to create the primitive with.
     */
    public JsonPrimitive(final Character c) {
        this.setValue(c);
    }

    /**
     * Create a primitive using the specified Object. It must be an instance of {@link Number}, a
     * Java primitive type, or a String.
     *
     * @param primitive the value to create the primitive with.
     */
    JsonPrimitive(final Object primitive) {
        this.setValue(primitive);
    }

    @Override
    JsonPrimitive deepCopy() {
        return this;
    }

    void setValue(final Object primitive) {
        if (primitive instanceof Character) {
            // convert characters to strings since in JSON, characters are represented as a single
            // character string
            final char c = ((Character) primitive).charValue();
            this.value = String.valueOf(c);
        } else {
            $Gson$Preconditions.checkArgument(primitive instanceof Number
                    || isPrimitiveOrString(primitive));
            this.value = primitive;
        }
    }

    /**
     * Check whether this primitive contains a boolean value.
     *
     * @return true if this primitive contains a boolean value, false otherwise.
     */
    public boolean isBoolean() {
        return this.value instanceof Boolean;
    }

    /**
     * convenience method to get this element as a {@link Boolean}.
     *
     * @return get this element as a {@link Boolean}.
     */
    @Override
    Boolean getAsBooleanWrapper() {
        return (Boolean) this.value;
    }

    /**
     * convenience method to get this element as a boolean value.
     *
     * @return get this element as a primitive boolean value.
     */
    @Override
    public boolean getAsBoolean() {
        if (this.isBoolean()) {
            return this.getAsBooleanWrapper().booleanValue();
        } else {
            // Check to see if the value as a String is "true" in any case.
            return Boolean.parseBoolean(this.getAsString());
        }
    }

    /**
     * Check whether this primitive contains a Number.
     *
     * @return true if this primitive contains a Number, false otherwise.
     */
    public boolean isNumber() {
        return this.value instanceof Number;
    }

    /**
     * convenience method to get this element as a Number.
     *
     * @return get this element as a Number.
     * @throws NumberFormatException if the value contained is not a valid Number.
     */
    @Override
    public Number getAsNumber() {
        return this.value instanceof String ? new LazilyParsedNumber((String) this.value) : (Number) this.value;
    }

    /**
     * Check whether this primitive contains a String value.
     *
     * @return true if this primitive contains a String value, false otherwise.
     */
    public boolean isString() {
        return this.value instanceof String;
    }

    /**
     * convenience method to get this element as a String.
     *
     * @return get this element as a String.
     */
    @Override
    public String getAsString() {
        if (this.isNumber()) {
            return this.getAsNumber().toString();
        } else if (this.isBoolean()) {
            return this.getAsBooleanWrapper().toString();
        } else {
            return (String) this.value;
        }
    }

    /**
     * convenience method to get this element as a primitive double.
     *
     * @return get this element as a primitive double.
     * @throws NumberFormatException if the value contained is not a valid double.
     */
    @Override
    public double getAsDouble() {
        return this.isNumber() ? this.getAsNumber().doubleValue() : Double.parseDouble(this.getAsString());
    }

    /**
     * convenience method to get this element as a {@link BigDecimal}.
     *
     * @return get this element as a {@link BigDecimal}.
     * @throws NumberFormatException if the value contained is not a valid {@link BigDecimal}.
     */
    @Override
    public BigDecimal getAsBigDecimal() {
        return this.value instanceof BigDecimal ? (BigDecimal) this.value : new BigDecimal(this.value.toString());
    }

    /**
     * convenience method to get this element as a {@link BigInteger}.
     *
     * @return get this element as a {@link BigInteger}.
     * @throws NumberFormatException if the value contained is not a valid {@link BigInteger}.
     */
    @Override
    public BigInteger getAsBigInteger() {
        return this.value instanceof BigInteger ?
                (BigInteger) this.value : new BigInteger(this.value.toString());
    }

    /**
     * convenience method to get this element as a float.
     *
     * @return get this element as a float.
     * @throws NumberFormatException if the value contained is not a valid float.
     */
    @Override
    public float getAsFloat() {
        return this.isNumber() ? this.getAsNumber().floatValue() : Float.parseFloat(this.getAsString());
    }

    /**
     * convenience method to get this element as a primitive long.
     *
     * @return get this element as a primitive long.
     * @throws NumberFormatException if the value contained is not a valid long.
     */
    @Override
    public long getAsLong() {
        return this.isNumber() ? this.getAsNumber().longValue() : Long.parseLong(this.getAsString());
    }

    /**
     * convenience method to get this element as a primitive short.
     *
     * @return get this element as a primitive short.
     * @throws NumberFormatException if the value contained is not a valid short value.
     */
    @Override
    public short getAsShort() {
        return this.isNumber() ? this.getAsNumber().shortValue() : Short.parseShort(this.getAsString());
    }

    /**
     * convenience method to get this element as a primitive integer.
     *
     * @return get this element as a primitive integer.
     * @throws NumberFormatException if the value contained is not a valid integer.
     */
    @Override
    public int getAsInt() {
        return this.isNumber() ? this.getAsNumber().intValue() : Integer.parseInt(this.getAsString());
    }

    @Override
    public byte getAsByte() {
        return this.isNumber() ? this.getAsNumber().byteValue() : Byte.parseByte(this.getAsString());
    }

    @Override
    public char getAsCharacter() {
        return this.getAsString().charAt(0);
    }

    private static boolean isPrimitiveOrString(final Object target) {
        if (target instanceof String) {
            return true;
        }

        final Class<?> classOfPrimitive = target.getClass();
        for (final Class<?> standardPrimitive : PRIMITIVE_TYPES) {
            if (standardPrimitive.isAssignableFrom(classOfPrimitive)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int hashCode() {
        if (this.value == null) {
            return 31;
        }
        // Using recommended hashing algorithm from Effective Java for longs and doubles
        if (isIntegral(this)) {
            final long value = this.getAsNumber().longValue();
            return (int) (value ^ (value >>> 32));
        }
        if (this.value instanceof Number) {
            final long value = Double.doubleToLongBits(this.getAsNumber().doubleValue());
            return (int) (value ^ (value >>> 32));
        }
        return this.value.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        }
        final JsonPrimitive other = (JsonPrimitive) obj;
        if (this.value == null) {
            return other.value == null;
        }
        if (isIntegral(this) && isIntegral(other)) {
            return this.getAsNumber().longValue() == other.getAsNumber().longValue();
        }
        if (this.value instanceof Number && other.value instanceof Number) {
            final double a = this.getAsNumber().doubleValue();
            // Java standard types other than double return true for two NaN. So, need
            // special handling for double.
            final double b = other.getAsNumber().doubleValue();
            return a == b || (Double.isNaN(a) && Double.isNaN(b));
        }
        return this.value.equals(other.value);
    }

    /**
     * Returns true if the specified number is an integral type
     * (Long, Integer, Short, Byte, BigInteger)
     */
    private static boolean isIntegral(final JsonPrimitive primitive) {
        if (primitive.value instanceof Number) {
            final Number number = (Number) primitive.value;
            return number instanceof BigInteger || number instanceof Long || number instanceof Integer
                    || number instanceof Short || number instanceof Byte;
        }
        return false;
    }
}
