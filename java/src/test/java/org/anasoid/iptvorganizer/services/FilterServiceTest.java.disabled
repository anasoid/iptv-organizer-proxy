package org.anasoid.iptvorganizer.services;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.anasoid.iptvorganizer.models.Filter;
import org.anasoid.iptvorganizer.models.filtering.FilterAction;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.models.filtering.FilterField;
import org.anasoid.iptvorganizer.models.filtering.FilterRule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
public class FilterServiceTest {

    @Inject
    FilterService filterService;

    @Test
    void testParseFilterConfigValidYaml() {
        String yaml = """
            rules:
              - field: name
                pattern: ".*Sports.*"
              - field: isAdult
                value: true
            """;

        FilterConfig config = filterService.parseFilterConfig(yaml);

        assertNotNull(config);
        assertNotNull(config.getRules());
        assertEquals(2, config.getRules().size());

        FilterRule rule1 = config.getRules().get(0);
        assertEquals(FilterField.NAME, rule1.getField());
        assertEquals(".*Sports.*", rule1.getPattern());

        FilterRule rule2 = config.getRules().get(1);
        assertEquals(FilterField.IS_ADULT, rule2.getField());
        assertEquals(true, rule2.getValue());
    }

    @Test
    void testParseFilterConfigInvalidYaml() {
        String yaml = """
            invalid: [
                invalid yaml structure
            """;

        assertThrows(Exception.class, () -> filterService.parseFilterConfig(yaml));
    }

    @Test
    void testParseFilterConfigEmptyRules() {
        String yaml = "rules: []";

        FilterConfig config = filterService.parseFilterConfig(yaml);

        assertNotNull(config);
        assertNotNull(config.getRules());
        assertEquals(0, config.getRules().size());
    }

    @Test
    void testParseFilterConfigNullRules() {
        String yaml = "rules: null";

        FilterConfig config = filterService.parseFilterConfig(yaml);

        assertNotNull(config);
        assertNull(config.getRules());
    }

    @Test
    void testMatchesWithIncludePattern() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*Sports.*")
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item1 = new TestLiveStream("Sports Channel", false);
        TestLiveStream item2 = new TestLiveStream("News Channel", false);

        assertTrue(filterService.matches(config, item1));
        assertFalse(filterService.matches(config, item2));
    }

    @Test
    void testMatchesWithExcludePattern() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*Adult.*")
            .action(FilterAction.EXCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item1 = new TestLiveStream("Adult Content", false);
        TestLiveStream item2 = new TestLiveStream("News Channel", false);

        assertFalse(filterService.matches(config, item1));
        assertTrue(filterService.matches(config, item2));
    }

    @Test
    void testMatchesWithValueInclude() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.IS_ADULT)
            .value(true)
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item1 = new TestLiveStream("Channel", true);
        TestLiveStream item2 = new TestLiveStream("Channel", false);

        assertTrue(filterService.matches(config, item1));
        assertFalse(filterService.matches(config, item2));
    }

    @Test
    void testMatchesWithValueExclude() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.IS_ADULT)
            .value(true)
            .action(FilterAction.EXCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item1 = new TestLiveStream("Channel", true);
        TestLiveStream item2 = new TestLiveStream("Channel", false);

        assertFalse(filterService.matches(config, item1));
        assertTrue(filterService.matches(config, item2));
    }

    @Test
    void testMatchesMultipleRulesAllMustPass() {
        FilterRule rule1 = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*Sports.*")
            .action(FilterAction.INCLUDE)
            .build();

        FilterRule rule2 = FilterRule.builder()
            .field(FilterField.IS_ADULT)
            .value(false)
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule1, rule2))
            .build();

        TestLiveStream item1 = new TestLiveStream("Sports Channel", false); // Matches both
        TestLiveStream item2 = new TestLiveStream("Sports Channel", true);  // Fails rule2
        TestLiveStream item3 = new TestLiveStream("News Channel", false);   // Fails rule1

        assertTrue(filterService.matches(config, item1));
        assertFalse(filterService.matches(config, item2));
        assertFalse(filterService.matches(config, item3));
    }

    @Test
    void testMatchesEmptyConfig() {
        FilterConfig config = FilterConfig.builder()
            .rules(List.of())
            .build();

        TestLiveStream item = new TestLiveStream("Any Channel", true);

        assertTrue(filterService.matches(config, item));
    }

    @Test
    void testMatchesNullConfig() {
        TestLiveStream item = new TestLiveStream("Any Channel", true);

        assertTrue(filterService.matches(null, item));
    }

    @Test
    void testCacheInvalidation() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*Sports.*")
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        Filter filter = new Filter();
        filter.setId(1L);

        // Should not throw when invalidating non-cached entry
        assertDoesNotThrow(() -> filterService.invalidateCache(1L));
    }

    @Test
    void testRegexPatternCaching() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*Sports.*")
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item = new TestLiveStream("Sports Channel", false);

        // Call matches multiple times - pattern should be cached
        assertTrue(filterService.matches(config, item));
        assertTrue(filterService.matches(config, item));
        assertTrue(filterService.matches(config, item));
    }

    @Test
    void testInvalidRegexPattern() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern("[invalid(regex") // Invalid regex - unmatched bracket
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item = new TestLiveStream("Any Channel", false);

        // Should return false due to regex compilation error
        assertFalse(filterService.matches(config, item));
    }

    @Test
    void testComplexRegexPattern() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern("^(Sports|News|Entertainment).*HD$")
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        TestLiveStream item1 = new TestLiveStream("Sports Channel HD", false);
        TestLiveStream item2 = new TestLiveStream("Sports Channel", false);
        TestLiveStream item3 = new TestLiveStream("News HD", false);

        assertTrue(filterService.matches(config, item1));
        assertFalse(filterService.matches(config, item2));
        assertTrue(filterService.matches(config, item3));
    }

    @Test
    void testFilterActionEnum() {
        assertEquals("INCLUDE", FilterAction.INCLUDE.name());
        assertEquals("EXCLUDE", FilterAction.EXCLUDE.name());
    }

    @Test
    void testFilterFieldEnum() {
        assertEquals("name", FilterField.NAME.getFieldName());
        assertEquals("categoryName", FilterField.CATEGORY_NAME.getFieldName());
        assertEquals("isAdult", FilterField.IS_ADULT.getFieldName());
        assertEquals("labels", FilterField.LABELS.getFieldName());
    }

    @Test
    void testFilterRuleBuilder() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*test.*")
            .value("test")
            .action(FilterAction.INCLUDE)
            .build();

        assertEquals(FilterField.NAME, rule.getField());
        assertEquals(".*test.*", rule.getPattern());
        assertEquals("test", rule.getValue());
        assertEquals(FilterAction.INCLUDE, rule.getAction());
    }

    @Test
    void testFilterConfigBuilder() {
        FilterRule rule = FilterRule.builder()
            .field(FilterField.NAME)
            .pattern(".*test.*")
            .action(FilterAction.INCLUDE)
            .build();

        FilterConfig config = FilterConfig.builder()
            .rules(List.of(rule))
            .build();

        assertNotNull(config.getRules());
        assertEquals(1, config.getRules().size());
        assertEquals(rule, config.getRules().get(0));
    }

    // Test POJO for filter matching
    public static class TestLiveStream {
        private String name;
        private Boolean isAdult;

        public TestLiveStream(String name, Boolean isAdult) {
            this.name = name;
            this.isAdult = isAdult;
        }

        public String getName() {
            return name;
        }

        public Boolean getIsAdult() {
            return isAdult;
        }
    }
}
