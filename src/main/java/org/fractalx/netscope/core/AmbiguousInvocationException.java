package org.fractalx.netscope.core;

import org.fractalx.netscope.model.NetworkMethodDefinition;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Thrown when a member name matches multiple overloaded methods and
 * the caller did not supply parameter_types to disambiguate.
 */
public class AmbiguousInvocationException extends RuntimeException {

    private final List<NetworkMethodDefinition> candidates;

    public AmbiguousInvocationException(String beanName, String memberName,
                                        List<NetworkMethodDefinition> candidates) {
        super(buildMessage(beanName, memberName, candidates));
        this.candidates = List.copyOf(candidates);
    }

    public List<NetworkMethodDefinition> getCandidates() {
        return candidates;
    }

    private static String buildMessage(String beanName, String memberName,
                                       List<NetworkMethodDefinition> candidates) {
        String signatures = candidates.stream()
                .map(c -> {
                    String params = Arrays.stream(c.getParameters())
                            .map(p -> p.getType() + " " + p.getName())
                            .collect(Collectors.joining(", "));
                    return memberName + "(" + params + ")";
                })
                .collect(Collectors.joining(", "));
        return "Ambiguous method '" + memberName + "' on " + beanName
                + " — specify parameter_types to disambiguate. Available: [" + signatures + "]";
    }
}
