# Task ID: 9

**Title:** Filter Application Logic & Favoris System

**Status:** pending

**Dependencies:** 8

**Priority:** high

**Description:** Implement YAML filter parsing and application engine with include/exclude rules and favoris virtual categories

**Details:**

1. Create `src/Services/FilterService.php`:
   - Constructor accepts Filter model (nullable)
   - Method: parseFilter()
     - Parse YAML filter_config using symfony/yaml: `composer require symfony/yaml`
     - Extract 'rules' section (array of include/exclude rules)
     - Extract 'favoris' section (array of favoris rules)
     - Validate structure
   - Method: applyToStreams($streams, $client)
     - For each stream:
       1. Check adult content FIRST: if client.hide_adult_content=1 AND stream.is_adult=1: reject
       2. Iterate through rules array (include/exclude)
       3. For each rule, check if stream matches (by name or labels)
       4. If rule.type=include AND matches: accept stream
       5. If rule.type=exclude AND matches: reject stream
       6. Continue to next rule
     - Return filtered streams array
   - Method: generateFavorisCategories($streamType)
     - streamType: 'live', 'vod', 'series'
     - Iterate through favoris array in order
     - For each favoris:
       - Assign category_id starting from 100000 (increment)
       - Create virtual category object:
         - category_id: 100000 + index
         - category_name: favoris.target_group
         - parent_id: 0
     - Return array of virtual categories
   - Method: applyFavoris($streams, $favorisCategoryId)
     - Get favoris rule by category_id (100000 = index 0, 100001 = index 1, etc.)
     - Filter streams matching favoris criteria
     - Return matching streams
   - Method: matchStream($stream, $matchCriteria)
     - matchCriteria: {categories: {by_name: [], by_labels: []}, channels: {by_name: [], by_labels: []}}
     - Check if stream.name matches any in channels.by_name (partial match)
     - Check if any stream.labels match channels.by_labels (exact match in comma-separated string)
     - Check if stream.category_name matches categories.by_name
     - Check if any category.labels match categories.by_labels
     - Return true if any match, false otherwise
2. Label matching algorithm:
   - Parse stream.labels (comma-separated string)
   - Check if label exists in array using explode() and in_array()
   - Handle case-insensitive matching (optional, configurable)
3. Name matching algorithm:
   - Use stripos() for case-insensitive partial matching
   - Example: "ESPN" matches "ESPN HD [Sports]"
4. Category order:
   - When returning categories, append favoris first (100000+), then regular categories

**Test Strategy:**

1. Unit test YAML parsing with valid filter configs
2. Test YAML parsing fails gracefully with invalid syntax
3. Test include rules accept matching streams
4. Test exclude rules reject matching streams
5. Test adult content filter executes first (priority)
6. Test label matching with comma-separated labels
7. Test name matching with partial strings
8. Test favoris categories generated with correct IDs (100000+)
9. Test favoris matching returns correct streams
10. Test category order: favoris first, then regular
11. Integration test: apply filter to real stream data
12. Test empty filter (no rules) returns all streams
