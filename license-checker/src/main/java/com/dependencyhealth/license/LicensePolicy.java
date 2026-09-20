package com.dependencyhealth.license;

import com.dependencyhealth.contract.Severity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Metadata screening policy; OR branches and exceptions are deliberately flagged for review. */
@Component
public class LicensePolicy {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.+-]*");
    private final List<String> disallowed;

    public LicensePolicy(LicenseProperties properties) {
        disallowed = properties.disallowed().stream().map(s -> s.trim().toUpperCase(Locale.ROOT)).toList();
    }

    public record Decision(Severity severity, String summary, List<String> matches) {}

    public Decision evaluate(List<String> metadata) {
        List<String> licenses = metadata.stream().filter(s -> s != null && !s.isBlank())
                .filter(s -> !Set.of("UNKNOWN", "NONE", "NOASSERTION", "N/A").contains(s.trim().toUpperCase(Locale.ROOT))).toList();
        if (licenses.isEmpty()) return new Decision(Severity.WARNING, "No license declared in registry metadata", List.of());
        Set<String> matches = new LinkedHashSet<>();
        for (String expression : licenses) {
            String upper = expression.toUpperCase(Locale.ROOT);
            if (upper.equals("UNLICENSED")) {
                return new Decision(Severity.WARNING, "Package is explicitly UNLICENSED", List.of("UNLICENSED"));
            }
            var tokens = new ArrayList<String>();
            IDENTIFIER.matcher(upper).results().forEach(m -> tokens.add(m.group()));
            tokens.add(upper.trim()); // Allows configurable non-SPDX license names too.
            // Registry/POM metadata often uses full names instead of SPDX identifiers.
            if (upper.contains("GNU AFFERO GENERAL PUBLIC LICENSE")) tokens.add("AGPL-UNSPECIFIED");
            if (upper.contains("GNU LESSER GENERAL PUBLIC LICENSE") || upper.contains("GNU LIBRARY GENERAL PUBLIC LICENSE")) tokens.add("LGPL-UNSPECIFIED");
            if (upper.contains("GNU GENERAL PUBLIC LICENSE")) tokens.add("GPL-UNSPECIFIED");
            for (String rule : disallowed) {
                if (rule.isBlank()) continue;
                boolean match = tokens.stream().anyMatch(token -> matches(rule, token));
                if (match) matches.add(rule);
            }
        }
        if (!matches.isEmpty()) {
            return new Decision(Severity.WARNING, "License metadata matches disallowed policy: " + String.join(", ", matches)
                    + "; review alternatives and exceptions", List.copyOf(matches));
        }
        if (licenses.stream().anyMatch(s -> s.toUpperCase(Locale.ROOT).startsWith("SEE LICENSE"))) {
            return new Decision(Severity.WARNING, "License is declared in a separate file; metadata requires review", List.of());
        }
        return new Decision(Severity.INFO, "License metadata found; no configured disallowed terms matched", List.of());
    }

    private boolean matches(String rule, String token) {
        if (rule.endsWith("*")) {
            String prefix = rule.substring(0, rule.length() - 1);
            return token.startsWith(prefix) || (prefix.endsWith("-") && token.equals(prefix.substring(0, prefix.length() - 1)));
        }
        return token.equals(rule);
    }
}
