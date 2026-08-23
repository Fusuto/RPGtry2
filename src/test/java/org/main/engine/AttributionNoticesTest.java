package org.main.engine;

import org.junit.jupiter.api.Test;

class AttributionNoticesTest {
    @Test
    void generatedPackagedNoticeMatchesCanonicalManifest() throws Exception {
        AttributionNotices.verifyPackagedNotice();
    }
}
