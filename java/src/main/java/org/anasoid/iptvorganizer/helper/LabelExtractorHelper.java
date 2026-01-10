package org.anasoid.iptvorganizer.helper;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extracts labels from names using delimiter-based parsing Matches PHP implementation: brackets [],
 * pipes |, and dashes -
 */
@ApplicationScoped
public class LabelExtractorHelper {

  private static final Pattern BRACKET_PATTERN = Pattern.compile("\\[([^\\]]+)\\]");

  /**
   * Extract labels from text (category name or stream name) Uses delimiter-based parsing: brackets
   * [], pipes |, and dashes -
   *
   * @param text Input text to extract labels from
   * @return Comma-separated labels string
   */
  public String extractLabels(String text) {
    Set<String> labels = new HashSet<>();

    if (text == null || text.isBlank()) {
      return "";
    }

    // Step 1: Extract text between brackets [TAG]
    var bracketMatcher = BRACKET_PATTERN.matcher(text);
    while (bracketMatcher.find()) {
      String label = bracketMatcher.group(1).trim();
      if (!label.isBlank()) {
        labels.add(label);
      }
    }

    // Remove brackets from text for further processing
    String textWithoutBrackets = BRACKET_PATTERN.matcher(text).replaceAll("");

    // Step 2: Split by '-' delimiter
    for (String part : textWithoutBrackets.split("-")) {
      String label = part.trim();
      if (!label.isBlank()) {
        labels.add(label);
      }
    }

    // Step 3: Split by '|' delimiter
    for (String part : textWithoutBrackets.split("\\|")) {
      String label = part.trim();
      if (!label.isBlank()) {
        labels.add(label);
      }
    }

    // Remove duplicates and empty values (HashSet handles duplicates automatically)
    labels.removeIf(String::isBlank);

    // Convert to comma-separated string
    if (labels.isEmpty()) {
      return "";
    }

    List<String> sortedLabels = new ArrayList<>(labels);
    sortedLabels.sort(String::compareTo);
    return String.join(",", sortedLabels);
  }

  /** Convert labels list to comma-separated string */
  public String labelsToString(List<String> labels) {
    if (labels == null || labels.isEmpty()) {
      return "";
    }
    return String.join(",", labels);
  }

  /** Convert comma-separated string to labels list */
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
