Gson is a Java library that can be used to convert a Java object into its JSON representation. It can also be used to convert a JSON string into an equivalent Java object. Gson can work with arbitrary Java objects including pre-existing objects that you do not have source-code of.

Complete Gson documentation is available at its project page
http://code.google.com/p/google-gson

This clone adds the ability to specify whether to serialize null values on a per-field basis, using the new `@Serialize` annotation.

If the `@Serialize` annotation is not specified, or its value is set to `Inclusion.DEFAULT` the behavior of Gson remains unchanged. Put simply: A class member's value will be included in the JSON if it has a value other than `null`. If Gson has been configured to always include null values the member's value will be included even if it's `null`.

By adding `@Serialize(Inclusion.ALWAYS)` to a class member, it will always be included in the JSON even if its value is `null`. Basically this annotation makes it possible to enable inclusion of nulls on a per-field basis. If Gson has been configured to always include null values adding the annotation in this form does nothing.

By adding `@Serialize(Inclusion.NON_NULL)` to a class member, it will always be excluded from the JSON if its value is `null`. Normally this matches Gson's default behavior, unless Gson has been configured to include null values. In this case the annotation allows to exclude nulls on a per-field basis.

**Be aware** that this does not change any other of Gson's rules. If a member is excluded for any other reason, for example if it has a `transient` modifier, this annotation will not change this. Also if Gson has been configured to exclude fields that do not have an `@Expose` annotation you will still need to add @Expose to class members that should be serialized. In other words the `@Serialize` annotation only works on members that are serialized under normal circumstances and only changes the behavior of Gson in regards to `null`.

**Examples**
```
public class Main {

    public static class Example {

        @Serialize(Inclusion.ALWAYS)
        public String field1 = null;

		 @Serialize(Inclusion.NON_NULL)
        public String field2 = null;

		 //@Serialize(Inclusion.DEFAULT)
		 public String field3 = null;

        public String field4 = "Field 4";
    }

    public static void main(final String[] args) {

        final Gson gson1 = new GsonBuilder().create();
        final Gson gson2 = new GsonBuilder().serializeNulls().create();

        final Example example1 = new Example();
        
        final String json1 = gson1.toJson(example1);
        System.out.println(json1);
        
        final String json2 = gson2.toJson(example1);
        System.out.println(json2);
    }
}
```

Without support for `@Serialize` the output of Gson would be:
```
// Output for gson1 (don't serialize nulls)
{ "field4": "Field 4" }

// Output for gson2 (serialize nulls)
{ "field1": null, "field2": null, "field3": null, "field4": "Field 4" }
```

**With** support for `@Serialize` its output instead becomes:
```
// Output for gson1 (don't serialize nulls)
{ "field1": null, "field4": "Field 4" }

// Output for gson2 (serialize nulls)
{ "field1": null, "field3": null, "field4": "Field 4" }
```