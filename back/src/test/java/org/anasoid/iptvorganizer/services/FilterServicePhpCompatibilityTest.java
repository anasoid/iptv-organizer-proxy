package org.anasoid.iptvorganizer.services;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.filtering.CategoryMatch;
import org.anasoid.iptvorganizer.models.filtering.ChannelMatch;
import org.anasoid.iptvorganizer.models.filtering.FilterAction;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.models.filtering.FilterRule;
import org.anasoid.iptvorganizer.models.filtering.MatchCriteria;
import org.anasoid.iptvorganizer.repositories.FilterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * PHP-Compatible Filter Service Tests
 *
 * <p>Tests the FilterService implementation to ensure PHP compatibility with: - Wildcard pattern
 * matching (* and ?) - Label-based filtering (comma-separated) - Priority-based stream filtering -
 * Category visibility rules - First-match-wins rule processing
 */
@DisplayName("PHP-Compatible FilterService Tests")
@ExtendWith(MockitoExtension.class)
public class FilterServicePhpCompatibilityTest {

  @Mock private FilterRepository filterRepository;
  @Mock private ObjectMapper objectMapper;

  // Test helper class that extends FilterService to access protected methods
  private static class TestableFilterService extends FilterService {
    // Provides access to protected methods for testing
  }

  private TestableFilterService filterService;

  @BeforeEach
  public void setUp() {
    filterService = new TestableFilterService();
    // Initialize the repository field via reflection since it's injected normally
    try {
      java.lang.reflect.Field repositoryField = FilterService.class.getDeclaredField("repository");
      repositoryField.setAccessible(true);
      repositoryField.set(filterService, filterRepository);

      java.lang.reflect.Field mapperField = FilterService.class.getDeclaredField("objectMapper");
      mapperField.setAccessible(true);
      mapperField.set(filterService, objectMapper);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new RuntimeException("Failed to initialize test service", e);
    }
  }

  // ==================== Pattern Matching Tests ====================

  @DisplayName("Pattern matching - wildcard asterisk")
  @Test
  public void testPatternMatchingWildcardAsterisk() {
    assertTrue(filterService.matchesPattern("HBO HD", "HBO*"));
    assertTrue(filterService.matchesPattern("HBO", "HBO*"));
    assertTrue(filterService.matchesPattern("HBO Channel", "HBO*"));
    assertFalse(filterService.matchesPattern("ABC HD", "HBO*"));
  }

  @DisplayName("Pattern matching - wildcard question mark")
  @Test
  public void testPatternMatchingWildcardQuestionMark() {
    assertTrue(filterService.matchesPattern("HBO", "HB?"));
    assertTrue(filterService.matchesPattern("HBO", "H?O"));
    assertFalse(filterService.matchesPattern("HBOO", "HB?"));
  }

  @DisplayName("Pattern matching - case insensitive")
  @Test
  public void testPatternMatchingCaseInsensitive() {
    assertTrue(filterService.matchesPattern("HBO", "hbo"));
    assertTrue(filterService.matchesPattern("espn", "ESPN"));
    assertTrue(filterService.matchesPattern("Football League", "league"));
  }

  @DisplayName("Pattern matching - null/empty patterns")
  @Test
  public void testPatternMatchingNullEmpty() {
    assertTrue(filterService.matchesPattern("text", null));
    assertTrue(filterService.matchesPattern("text", ""));
    assertTrue(filterService.matchesPattern(null, "pattern"));
  }

  // ==================== Label Matching Tests ====================

  @DisplayName("Label matching - comma-separated labels")
  @Test
  public void testLabelMatchingCommaSeparated() {
    ChannelMatch criteria = ChannelMatch.builder().byLabels(Arrays.asList("hd", "sports")).build();

    assertTrue(filterService.matchesChannelCriteria("", "hd,news", criteria));
    assertTrue(filterService.matchesChannelCriteria("", "sports,news", criteria));
    assertFalse(filterService.matchesChannelCriteria("", "sd,news", criteria));
  }

  @DisplayName("Label matching - wildcards in labels")
  @Test
  public void testLabelMatchingWildcardsInLabels() {
    ChannelMatch criteria =
        ChannelMatch.builder().byLabels(Collections.singletonList("*hd*")).build();

    assertTrue(filterService.matchesChannelCriteria("", "uhd,fhd", criteria));
    assertTrue(filterService.matchesChannelCriteria("", "full-hd", criteria));
    assertFalse(filterService.matchesChannelCriteria("", "sd,4k", criteria));
  }

  // ==================== Matching Criteria Tests ====================

  @DisplayName("Matching - channel only criteria")
  @Test
  public void testMatchingChannelOnlyCriteria() {
    BaseStream stream = LiveStream.builder().name("ESPN Sports").labels("hd").build();

    Category category = Category.builder().name("Sports").build();

    MatchCriteria match =
        MatchCriteria.builder()
            .channels(ChannelMatch.builder().byName(Collections.singletonList("ESPN*")).build())
            .build();

    assertTrue(filterService.matchStream(stream, category, match, new HashMap<>()));
  }

  @DisplayName("Matching - category only criteria")
  @Test
  public void testMatchingCategoryOnlyCriteria() {
    BaseStream stream = LiveStream.builder().name("Channel").build();

    Category category = Category.builder().name("Sports").build();

    MatchCriteria match =
        MatchCriteria.builder()
            .categories(CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
            .build();

    assertTrue(filterService.matchStream(stream, category, match, new HashMap<>()));
  }

  @DisplayName("Matching - both channel and category criteria (AND logic)")
  @Test
  public void testMatchingBothCriteria() {
    BaseStream stream = LiveStream.builder().name("ESPN Sports").build();

    Category category = Category.builder().name("Sports").build();

    MatchCriteria match =
        MatchCriteria.builder()
            .channels(ChannelMatch.builder().byName(Collections.singletonList("ESPN*")).build())
            .categories(CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
            .build();

    // Both match
    assertTrue(filterService.matchStream(stream, category, match, new HashMap<>()));

    // Channel doesn't match
    MatchCriteria mismatchChannel =
        MatchCriteria.builder()
            .channels(ChannelMatch.builder().byName(Collections.singletonList("HBO*")).build())
            .categories(CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
            .build();
    assertFalse(filterService.matchStream(stream, category, mismatchChannel, new HashMap<>()));
  }

  // ==================== Priority-Based Filtering Tests ====================

  @DisplayName("Priority 1 - Stream allow_deny='allow'")
  @Test
  public void testPriority1StreamAllow() {
    BaseStream stream =
        LiveStream.builder().allowDeny(BaseStream.AllowDenyStatus.ALLOW).isAdult(true).build();

    Category category = Category.builder().allowDeny(BaseStream.AllowDenyStatus.DENY).build();

    // Even with adult content and category deny, stream allow wins
    assertTrue(filterService.shouldIncludeStream(stream, category, null, true));
  }

  @DisplayName("Priority 2 - Stream allow_deny='deny'")
  @Test
  public void testPriority2StreamDeny() {
    BaseStream stream = LiveStream.builder().allowDeny(BaseStream.AllowDenyStatus.DENY).build();

    Category category = Category.builder().allowDeny(BaseStream.AllowDenyStatus.ALLOW).build();

    // Stream deny overrides category allow
    assertFalse(filterService.shouldIncludeStream(stream, category, null, false));
  }

  @DisplayName("Priority 3 - Category allow_deny='allow'")
  @Test
  public void testPriority3CategoryAllow() {
    BaseStream stream = LiveStream.builder().build();

    Category category = Category.builder().allowDeny(BaseStream.AllowDenyStatus.ALLOW).build();

    // Category allow includes stream
    assertTrue(filterService.shouldIncludeStream(stream, category, null, false));
  }

  @DisplayName("Priority 4 - Category allow_deny='deny'")
  @Test
  public void testPriority4CategoryDeny() {
    BaseStream stream = LiveStream.builder().build();

    Category category = Category.builder().allowDeny(BaseStream.AllowDenyStatus.DENY).build();

    // Category deny excludes stream
    assertFalse(filterService.shouldIncludeStream(stream, category, null, false));
  }

  @DisplayName("Priority 5 - Adult content filtering")
  @Test
  public void testPriority5AdultContent() {
    BaseStream adultStream = LiveStream.builder().isAdult(true).build();

    // Adult content hidden
    assertFalse(filterService.shouldIncludeStream(adultStream, null, null, true));

    // Adult content shown
    assertTrue(filterService.shouldIncludeStream(adultStream, null, null, false));
  }

  // ==================== Filter Rules Tests ====================

  @DisplayName("Filter rules - include rule (first-match-wins)")
  @Test
  public void testFilterRulesIncludeFirstMatch() {
    BaseStream stream = LiveStream.builder().name("Sports Channel").build();

    Category category = Category.builder().name("Sports").build();

    FilterRule includeRule =
        FilterRule.builder()
            .type(FilterAction.INCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Collections.singletonList(includeRule)).build();

    assertTrue(filterService.shouldIncludeStream(stream, category, config, false));
  }

  @DisplayName("Filter rules - exclude rule (first-match-wins)")
  @Test
  public void testFilterRulesExcludeFirstMatch() {
    BaseStream stream = LiveStream.builder().name("Adult Channel").build();

    Category category = Category.builder().name("Adults").build();

    FilterRule excludeRule =
        FilterRule.builder()
            .type(FilterAction.EXCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Adults")).build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Collections.singletonList(excludeRule)).build();

    assertFalse(filterService.shouldIncludeStream(stream, category, config, false));
  }

  @DisplayName("Filter rules - first match wins with multiple rules")
  @Test
  public void testFilterRulesFirstMatchWinsMultiple() {
    BaseStream stream = LiveStream.builder().name("ESPN Sports").build();

    Category category = Category.builder().name("Sports").build();

    // First rule: include sports
    // Second rule: exclude sports
    // First match wins, so include
    FilterRule includeRule =
        FilterRule.builder()
            .type(FilterAction.INCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
                    .build())
            .build();

    FilterRule excludeRule =
        FilterRule.builder()
            .type(FilterAction.EXCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Arrays.asList(includeRule, excludeRule)).build();

    assertTrue(filterService.shouldIncludeStream(stream, category, config, false));
  }

  @DisplayName("Filter rules - no match with include rules → reject")
  @Test
  public void testFilterRulesNoMatchWithIncludeRules() {
    BaseStream stream = LiveStream.builder().name("Movies Channel").build();

    Category category = Category.builder().name("Movies").build();

    // Only include rules exist, stream doesn't match
    FilterRule includeRule =
        FilterRule.builder()
            .type(FilterAction.INCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Sports")).build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Collections.singletonList(includeRule)).build();

    // No match with include rules → reject
    assertFalse(filterService.shouldIncludeStream(stream, category, config, false));
  }

  @DisplayName("Filter rules - no match with only exclude rules → accept")
  @Test
  public void testFilterRulesNoMatchOnlyExclude() {
    BaseStream stream = LiveStream.builder().name("Sports Channel").build();

    Category category = Category.builder().name("Sports").build();

    // Only exclude rules exist, stream doesn't match
    FilterRule excludeRule =
        FilterRule.builder()
            .type(FilterAction.EXCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Adults")).build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Collections.singletonList(excludeRule)).build();

    // No match with only exclude rules → accept
    assertTrue(filterService.shouldIncludeStream(stream, category, config, false));
  }

  // ==================== Category Filtering Tests ====================

  @DisplayName("Category filtering - default keep (no rules match)")
  @Test
  public void testCategoryFilteringDefaultKeep() {
    List<Category> categories =
        Arrays.asList(
            Category.builder().name("Sports").build(), Category.builder().name("Movies").build());

    // No rules - all kept
    List<Category> filtered = filterService.filterCategories(categories, "live", null);
    assertEquals(2, filtered.size());
  }

  @DisplayName("Category filtering - explicit exclude")
  @Test
  public void testCategoryFilteringExclude() {
    List<Category> categories =
        Arrays.asList(
            Category.builder().name("Sports").build(),
            Category.builder().name("Adults Only").build());

    FilterRule excludeRule =
        FilterRule.builder()
            .type(FilterAction.EXCLUDE)
            .match(
                MatchCriteria.builder()
                    .categories(
                        CategoryMatch.builder()
                            .byName(Collections.singletonList("Adults*"))
                            .build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Collections.singletonList(excludeRule)).build();

    List<Category> filtered = filterService.filterCategories(categories, "live", config);
    assertEquals(1, filtered.size());
    assertEquals("Sports", filtered.get(0).getName());
  }

  @DisplayName("Category filtering - stream_type filter")
  @Test
  public void testCategoryFilteringStreamTypeFilter() {
    List<Category> categories =
        Collections.singletonList(Category.builder().name("Adult Content").build());

    // Rule applies only to 'vod' type
    FilterRule excludeRule =
        FilterRule.builder()
            .type(FilterAction.EXCLUDE)
            .match(
                MatchCriteria.builder()
                    .streamType(Collections.singletonList("vod"))
                    .categories(
                        CategoryMatch.builder().byName(Collections.singletonList("Adult*")).build())
                    .build())
            .build();

    FilterConfig config =
        FilterConfig.builder().rules(Collections.singletonList(excludeRule)).build();

    // For 'live' type, rule doesn't apply - category is kept
    List<Category> liveFiltered = filterService.filterCategories(categories, "live", config);
    assertEquals(1, liveFiltered.size());

    // For 'vod' type, rule applies - category is excluded
    List<Category> vodFiltered = filterService.filterCategories(categories, "vod", config);
    assertEquals(0, vodFiltered.size());
  }

  // ==================== Apply To Streams Tests ====================

  @DisplayName("Apply to streams - priority separation")
  @Test
  public void testApplyToStreamsPrioritySeparation() {
    LiveStream allowStream =
        LiveStream.builder()
            .externalId(1)
            .categoryId(1)
            .name("Allow")
            .allowDeny(BaseStream.AllowDenyStatus.ALLOW)
            .build();

    LiveStream denyStream =
        LiveStream.builder()
            .externalId(2)
            .categoryId(1)
            .name("Deny")
            .allowDeny(BaseStream.AllowDenyStatus.DENY)
            .build();

    LiveStream normalStream =
        LiveStream.builder().externalId(3).categoryId(1).name("Normal").build();

    List<BaseStream> streams = Arrays.asList(allowStream, denyStream, normalStream);

    Map<Integer, Category> cache = new HashMap<>();
    cache.put(1, Category.builder().name("Test").build());

    List<BaseStream> filtered = filterService.applyToStreams(streams, cache, false);

    // Allow and Normal kept, Deny filtered out
    assertEquals(2, filtered.size());
    assertTrue(filtered.stream().anyMatch(s -> "Allow".equals(s.getName())));
    assertTrue(filtered.stream().anyMatch(s -> "Normal".equals(s.getName())));
  }
}
