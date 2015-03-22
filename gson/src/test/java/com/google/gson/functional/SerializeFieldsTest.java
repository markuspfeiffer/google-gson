
package com.google.gson.functional;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Serialize;
import com.google.gson.annotations.Serialize.Inclusion;

import junit.framework.TestCase;


public class SerializeFieldsTest extends TestCase {

    private static boolean includes(final String json, final String field, final String value) {
        final String v = value == null ? "null" : "\"" + value + "\"";
        final String s = String.format("\"%s\":%s", field, v);
        return json.contains(s);
    }

    public void testSerializeDefaultExclusion() {
        final Gson gson = new GsonBuilder().create();
        final TestObject obj = new TestObject();

        final String json = gson.toJson(obj);

        assertEquals(includes(json, "empty_1", null), false);
        assertEquals(includes(json, "empty_2", null), false);
        assertEquals(includes(json, "empty_3", null), true);
        assertEquals(includes(json, "empty_4", null), false);
        assertEquals(includes(json, "value_1", "value-1"), true);
        assertEquals(includes(json, "value_2", "value-2"), true);
        assertEquals(includes(json, "value_3", "value-3"), true);
        assertEquals(includes(json, "value_4", "value-4"), true);
    }

    public void testSerializeNonNullExclusion() {
        final Gson gson = new GsonBuilder().serializeNulls().create();
        final TestObject obj = new TestObject();

        final String json = gson.toJson(obj);

        assertEquals(includes(json, "empty_1", null), true);
        assertEquals(includes(json, "empty_2", null), true);
        assertEquals(includes(json, "empty_3", null), true);
        assertEquals(includes(json, "empty_4", null), false);
        assertEquals(includes(json, "value_1", "value-1"), true);
        assertEquals(includes(json, "value_2", "value-2"), true);
        assertEquals(includes(json, "value_3", "value-3"), true);
        assertEquals(includes(json, "value_4", "value-4"), true);
    }

    public void testDeserializeDefaultExclusion() {
        final Gson gson = new GsonBuilder().create();
        final TestObject source = new TestObject();

        final String json = gson.toJson(source);
        final TestObject target = gson.fromJson(json, TestObject.class);

        assertEquals(target.empty_1, null);
        assertEquals(target.empty_2, null);
        assertEquals(target.empty_3, null);
        assertEquals(target.empty_4, null);
        assertEquals(target.value_1, "value-1");
        assertEquals(target.value_2, "value-2");
        assertEquals(target.value_3, "value-3");
        assertEquals(target.value_4, "value-4");
    }

    public void testDeserializeNonNullExclusion() {
        final Gson gson = new GsonBuilder().serializeNulls().create();
        final TestObject source = new TestObject();

        final String json = gson.toJson(source);
        final TestObject target = gson.fromJson(json, TestObject.class);

        assertEquals(target.empty_1, null);
        assertEquals(target.empty_2, null);
        assertEquals(target.empty_3, null);
        assertEquals(target.empty_4, null);
        assertEquals(target.value_1, "value-1");
        assertEquals(target.value_2, "value-2");
        assertEquals(target.value_3, "value-3");
        assertEquals(target.value_4, "value-4");
    }

    public static class TestObject {

        public final String empty_1 = null;

        @Serialize(Inclusion.DEFAULT)
        public final String empty_2 = null;

        @Serialize(Inclusion.ALWAYS)
        public final String empty_3 = null;

        @Serialize(Inclusion.NON_NULL)
        public final String empty_4 = null;

        public final String value_1 = "value-1";

        @Serialize(Inclusion.DEFAULT)
        public final String value_2 = "value-2";

        @Serialize(Inclusion.ALWAYS)
        public final String value_3 = "value-3";

        @Serialize(Inclusion.NON_NULL)
        public final String value_4 = "value-4";
    }
}
