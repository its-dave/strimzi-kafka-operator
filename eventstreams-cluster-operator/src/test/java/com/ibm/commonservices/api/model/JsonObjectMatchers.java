/*
 * IBM Confidential
 * OCO Source Materials
 *
 * 5737-H33
 *
 * (C) Copyright IBM Corp. 2020  All Rights Reserved.
 *
 * The source code for this program is not published or otherwise
 * divested of its trade secrets, irrespective of what has been
 * deposited with the U.S. Copyright Office.
 */
package com.ibm.commonservices.api.model;

import io.vertx.core.json.JsonObject;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Arrays;

public class JsonObjectMatchers {

    public static Matcher<JsonObject> hasSize(int size) {
        return new TypeSafeDiagnosingMatcher<JsonObject>() {

            @Override
            protected boolean matchesSafely(JsonObject actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual.size());
                if (size != actual.size()) {
                    mismatchDescription.appendText("\n There are actually ")
                            .appendValue(actual.size())
                            .appendText(" entries : ")
                            .appendValue(actual.fieldNames().toString());
                    return false;
                }
                return true;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Expected JsonObject of size ").appendValue(size);
            }
        };
    }
    public static Matcher<JsonObject> hasKey(String key) {
        return new TypeSafeDiagnosingMatcher<JsonObject>() {

            @Override
            protected boolean matchesSafely(JsonObject actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual.fieldNames());
                if (!actual.fieldNames().contains(key)) {
                    mismatchDescription.appendText("\nDoes not contain desired key");
                    return false;
                }
                return true;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Expected JsonObject with key ").appendValue(key);
            }
        };
    }
    public static Matcher<JsonObject> hasKeys(String... keys) {
        return new TypeSafeDiagnosingMatcher<JsonObject>() {

            @Override
            protected boolean matchesSafely(JsonObject actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual.fieldNames());
                boolean matches = true;
                for (String key : keys) {
                    if (!hasKey(key).matches(actual)) {
                        mismatchDescription.appendText("\nDoes not contain key " + key);
                        matches = false;
                    }
                }

                return matches;
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Expected JsonObject with keys ")
                        .appendValue(Arrays.toString(keys));
            }
        };
    }
}
