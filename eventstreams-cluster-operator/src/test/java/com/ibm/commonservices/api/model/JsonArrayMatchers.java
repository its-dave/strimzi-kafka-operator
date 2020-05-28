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

import io.vertx.core.json.JsonArray;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class JsonArrayMatchers {

    public static Matcher<JsonArray> hasSize(int size) {
        return new TypeSafeDiagnosingMatcher<JsonArray>() {

            @Override
            protected boolean matchesSafely(JsonArray actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual.size());
                if (size != actual.size()) {
                    mismatchDescription.appendText("\n There are actually ")
                            .appendValue(actual.size())
                            .appendText(" entries : ")
                            .appendValue(actual.toString());
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
    public static Matcher<JsonArray> hasElement(Object element) {
        return new TypeSafeDiagnosingMatcher<JsonArray>() {

            @Override
            protected boolean matchesSafely(JsonArray actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual.toString());
                return actual.contains(element);
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Expected JsonArray to contain element ").appendValue(element.toString());
            }
        };
    }
}
