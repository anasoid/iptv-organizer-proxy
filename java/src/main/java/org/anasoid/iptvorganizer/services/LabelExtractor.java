package org.anasoid.iptvorganizer.services;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts labels from stream names using regex patterns
 */
@ApplicationScoped
public class LabelExtractor {

    // Common patterns for stream metadata extraction
    private static final Pattern QUALITY_PATTERN = Pattern.compile("(?i)(\\d{3,4}p|4k|8k|uhd|hd|sd)");
    private static final Pattern CODEC_PATTERN = Pattern.compile("(?i)(h\\.?264|h\\.?265|hevc|mpeg-?2|vc-?1)");
    private static final Pattern SOURCE_PATTERN = Pattern.compile("(?i)(web-?dl|brrip|hdrip|dvdrip|hdtv|tvrip)");
    private static final Pattern LANGUAGE_PATTERN = Pattern.compile("(?i)\\b(eng|fra|deu|spa|ita|por|rus|jpn|kor|chi|ara|heb)\\b");
    private static final Pattern ADULT_PATTERN = Pattern.compile("(?i)(xxx|adult|18\\+|porn|nsfw)");

    /**
     * Extract labels from stream name
     */
    public List<String> extractLabels(String streamName) {
        List<String> labels = new ArrayList<>();

        if (streamName == null || streamName.isBlank()) {
            return labels;
        }

        // Extract quality labels
        Matcher qualityMatcher = QUALITY_PATTERN.matcher(streamName);
        if (qualityMatcher.find()) {
            labels.add("quality:" + qualityMatcher.group().toLowerCase());
        }

        // Extract codec labels
        Matcher codecMatcher = CODEC_PATTERN.matcher(streamName);
        if (codecMatcher.find()) {
            labels.add("codec:" + normalizeCodec(codecMatcher.group()));
        }

        // Extract source labels
        Matcher sourceMatcher = SOURCE_PATTERN.matcher(streamName);
        if (sourceMatcher.find()) {
            labels.add("source:" + sourceMatcher.group().toLowerCase());
        }

        // Extract language labels
        Matcher languageMatcher = LANGUAGE_PATTERN.matcher(streamName);
        while (languageMatcher.find()) {
            labels.add("lang:" + languageMatcher.group().toLowerCase());
        }

        // Check for adult content
        if (ADULT_PATTERN.matcher(streamName).find()) {
            labels.add("adult");
        }

        // Extract bracketed tags [TAG]
        Pattern bracketPattern = Pattern.compile("\\[([^\\]]+)\\]");
        Matcher bracketMatcher = bracketPattern.matcher(streamName);
        while (bracketMatcher.find()) {
            labels.add("tag:" + bracketMatcher.group(1).toLowerCase());
        }

        return labels;
    }

    /**
     * Normalize codec names
     */
    private String normalizeCodec(String codec) {
        String normalized = codec.toLowerCase().replace(".", "");
        if (normalized.equals("h264")) return "h264";
        if (normalized.equals("h265") || normalized.equals("hevc")) return "h265";
        return normalized;
    }

    /**
     * Convert labels list to comma-separated string for storage
     */
    public String labelsToString(List<String> labels) {
        return String.join(",", labels);
    }

    /**
     * Convert comma-separated string back to labels list
     */
    public List<String> stringToLabels(String labelsStr) {
        List<String> labels = new ArrayList<>();
        if (labelsStr != null && !labelsStr.isBlank()) {
            for (String label : labelsStr.split(",")) {
                String trimmed = label.trim();
                if (!trimmed.isBlank()) {
                    labels.add(trimmed);
                }
            }
        }
        return labels;
    }
}
