package org.main.pack;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal SemVer 2.0 precedence implementation for pack dependency resolution.
 */
public record SemanticVersion(long major, long minor, long patch, List<String> preRelease)
        implements Comparable<SemanticVersion> {
    public SemanticVersion {
        preRelease = preRelease == null ? List.of() : List.copyOf(preRelease);
    }

    public static SemanticVersion parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SemVer is required.");
        }
        String withoutBuild = value.trim().split("[+]", 2)[0];
        String[] releaseAndPre = withoutBuild.split("-", 2);
        String[] release = releaseAndPre[0].split("[.]", -1);
        if (release.length != 3) {
            throw new IllegalArgumentException("Invalid SemVer: " + value);
        }
        List<String> pre = releaseAndPre.length == 1
                ? List.of() : List.of(releaseAndPre[1].split("[.]", -1));
        return new SemanticVersion(number(release[0], value), number(release[1], value),
                number(release[2], value), pre);
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int release = Long.compare(major, other.major);
        if (release == 0) release = Long.compare(minor, other.minor);
        if (release == 0) release = Long.compare(patch, other.patch);
        if (release != 0) return release;
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return preRelease.isEmpty() == other.preRelease.isEmpty()
                    ? 0 : (preRelease.isEmpty() ? 1 : -1);
        }
        int count = Math.max(preRelease.size(), other.preRelease.size());
        for (int index = 0; index < count; index++) {
            if (index >= preRelease.size()) return -1;
            if (index >= other.preRelease.size()) return 1;
            String left = preRelease.get(index);
            String right = other.preRelease.get(index);
            boolean leftNumber = left.matches("[0-9]+");
            boolean rightNumber = right.matches("[0-9]+");
            int comparison;
            if (leftNumber && rightNumber) {
                comparison = new java.math.BigInteger(left).compareTo(new java.math.BigInteger(right));
            } else if (leftNumber != rightNumber) {
                comparison = leftNumber ? -1 : 1;
            } else {
                comparison = left.compareTo(right);
            }
            if (comparison != 0) return comparison;
        }
        return 0;
    }

    private static long number(String part, String original) {
        if (!part.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException("Invalid SemVer: " + original);
        }
        try {
            return Long.parseLong(part);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("SemVer number is too large: " + original, error);
        }
    }
}
