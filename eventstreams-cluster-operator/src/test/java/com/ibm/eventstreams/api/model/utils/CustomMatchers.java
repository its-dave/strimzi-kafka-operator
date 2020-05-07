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

package com.ibm.eventstreams.api.model.utils;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.List;
import java.util.stream.Stream;

import io.fabric8.kubernetes.api.model.networking.NetworkPolicyIngressRule;

public class CustomMatchers {
    private CustomMatchers() { }

    /**
     * containsIngressRulesInAnyOrder is a custom matcher to check that a network polices ingresses contains
     * each rule in any order, where order is ignored completely down the nested object.
     * @param networkPolicyIngressRules the ingress rules to match
     * @return a custom Matcher which iterates through ingress rules and checks matches in any order
     */
    public static Matcher<List<NetworkPolicyIngressRule>> containsIngressRulesInAnyOrder(NetworkPolicyIngressRule... networkPolicyIngressRules) {
        return new TypeSafeDiagnosingMatcher<List<NetworkPolicyIngressRule>>() {

            @Override
            protected boolean matchesSafely(List<NetworkPolicyIngressRule> actual, Description mismatchDescription) {
                mismatchDescription.appendText("was ").appendValue(actual);
                if (networkPolicyIngressRules.length != actual.size()) {
                    mismatchDescription.appendText("\n There are actually ").appendValue(actual.size()).appendText(" entries");
                    return false;
                }

                return Stream.of(networkPolicyIngressRules).allMatch(expectedRule -> {
                    boolean hasMatch = actual.stream()
                            .filter(actualRule -> expectedRule.getPorts().containsAll(actualRule.getPorts()))
                            .anyMatch(actualRule -> expectedRule.getFrom().containsAll(actualRule.getFrom()));
                    if (!hasMatch) {
                        mismatchDescription.appendText("\n Did not match ").appendValue(expectedRule);
                    }
                    return hasMatch;
                });
            }

            @Override
            public void describeTo(final Description description) {
                description.appendText("Expected List with entries").appendValue(networkPolicyIngressRules);
            }
        };
    }
}
