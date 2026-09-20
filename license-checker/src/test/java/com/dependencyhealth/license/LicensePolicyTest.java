package com.dependencyhealth.license;

import com.dependencyhealth.contract.Severity;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LicensePolicyTest {
    private LicensePolicy policy(String... rules) {
        var registry = URI.create("https://example.test");
        return new LicensePolicy(new LicenseProperties(List.of(rules), registry, registry, registry, 5));
    }

    @Test
    void recognizesMissingUnknownAndUnlicensedMetadata() {
        for (var licenses : List.of(List.<String>of(), List.of(""), List.of("UNKNOWN"), List.of("UNLICENSED"))) {
            assertThat(policy("GPL-*").evaluate(licenses).severity()).isEqualTo(Severity.WARNING);
        }
    }

    @Test
    void matchesGplFamilyWithoutMatchingLgplOrAFalseSubstring() {
        assertThat(policy("GPL-*").evaluate(List.of("GPL-3.0-only")).matches()).containsExactly("GPL-*");
        assertThat(policy("GPL-*").evaluate(List.of("LGPL-2.1-only")).matches()).isEmpty();
        assertThat(policy("GPL-*").evaluate(List.of("LicenseRef-NotGPL-3.0")).matches()).isEmpty();
        assertThat(policy("LGPL-*").evaluate(List.of("GNU Lesser General Public License v2.1 (LGPLv2.1)"))
                .matches()).containsExactly("LGPL-*");
    }

    @Test
    void flagsOrAlternativesAndExceptionsForReview() {
        assertThat(policy("GPL-*").evaluate(List.of("(MIT OR GPL-3.0-only)")).severity()).isEqualTo(Severity.WARNING);
        assertThat(policy("GPL-*").evaluate(List.of("GPL-2.0-only WITH Classpath-exception-2.0")).matches())
                .containsExactly("GPL-*");
    }

    @Test
    void acceptsPermissiveMetadataAndRequestsSeparateFileReview() {
        assertThat(policy("GPL-*", "LGPL-*", "AGPL-*").evaluate(List.of("MIT", "Apache-2.0", "BSD-3-Clause")).severity())
                .isEqualTo(Severity.INFO);
        assertThat(policy("GPL-*").evaluate(List.of("SEE LICENSE IN LICENSE.txt")).severity()).isEqualTo(Severity.WARNING);
    }
}
