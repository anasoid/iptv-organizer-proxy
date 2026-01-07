# Filter YAML Configuration Examples

## Overview

Filters consist of TWO SEPARATE storage fields in the database:
- **filter_config field** (REQUIRED): YAML with `rules:` section containing array of include/exclude rules
- **favoris field** (OPTIONAL): YAML array of virtual category definitions

**Structure:**
```
filter_config field:
  rules:
    - rule 1
    - rule 2

favoris field (separate):
  - favoris 1
  - favoris 2
```

⚠️ **Important:** The favoris are NOT part of the filter_config YAML. They are stored completely separately in the `favoris` field.

---

## Filter Logic

### Processing Order
1. **Adult Content Check** (if `hide_adult_content` is enabled)
   - Any stream with `is_adult = true` is rejected immediately

2. **Rule Processing** (in defined order)
   - For each rule in order:
     - If `type: include` and stream matches → **ACCEPT** the stream
     - If `type: exclude` and stream matches → **REJECT** the stream
     - If stream matches → stop processing remaining rules

3. **Unmatched Streams**
   - If there are **include rules** and stream doesn't match ANY → **REJECT** (ignored)
   - If there are **only exclude rules** and stream doesn't match → **ACCEPT**

### Rejection Tracking
The filter service tracks why each stream was rejected:
- `by_adult_content`: Stream marked as adult content
- `by_rule`: Stream matched an exclude rule (with rule name)
- `ignored`: Stream didn't match any include rule (when include rules exist)

---

## Rule Structure (filter_config field)

The `filter_config` field contains YAML with a `rules:` section followed by an array of rules:

```yaml
# This goes in the filter_config field
rules:
  - name: "Display Name"           # Descriptive rule name
    type: include                   # or exclude
    match:
      categories:                   # Optional - match categories
        by_name: ["Category1", "Category2"]
        by_labels: ["label1", "label2"]
      channels:                     # Optional - match channels/streams
        by_name: ["Channel1", "Channel2"]
        by_labels: ["stream_label1"]
```

**Important:** Must start with `rules:` followed by the array of rules.

**Matching Logic:**
- If ANY category `by_name` matches OR ANY category `by_label` matches → **include that criteria**
- AND if ANY channel `by_name` matches OR ANY channel `by_label` matches → **include that criteria**
- All specified criteria are combined with OR logic within each type

---

## Favoris Structure (favoris field)

The `favoris` field is SEPARATE from `filter_config` and contains a YAML array of virtual categories:

```yaml
# This goes in the favoris field (completely separate from rules)
- name: "Internal Name"          # For identification
  target_group: "Display Name"   # What users see in the client
  match:
    categories:                  # Optional - match categories
      by_name: ["Category1"]
      by_labels: ["label1"]
    channels:                    # Optional - match channels/streams
      by_name: ["Channel*"]
      by_labels: ["label1", "label2"]

- name: "Another Favoris"
  target_group: "Another Group"
  match:
    channels:
      by_name: ["HD*"]
```

**Do NOT wrap in `favoris:` section - just the array directly.**

**Each favoris gets a unique ID:**
- First favoris: ID 100000
- Second favoris: ID 100001
- Third favoris: ID 100002
- etc.

---

## Example 1: Block Adult Content

```yaml
rules:
  - name: "Block Adult Content"
    type: exclude
    match:
      categories:
        by_name: ["*Adult*", "*XXX*", "*18+*"]
        by_labels: ["adult", "18+"]
      channels:
        by_name: ["Playboy*", "*Adult*"]
        by_labels: ["adult", "xxx"]
```

**Logic:**
- Any stream in category with "Adult" in name → REJECT
- Any stream in category with "XXX" in name → REJECT
- Any stream with "adult" OR "18+" label → REJECT
- Any stream with name starting "Playboy" → REJECT
- Any stream with name containing "Adult" → REJECT
- Any stream with "adult" OR "xxx" label → REJECT

**Wildcard Notes:**
- `*Adult*` matches "Adult", "Adults", "My Adult Channel", etc.
- `*XXX*` matches "XXX", "XXXTV", "Premium XXX", etc.
- `Playboy*` matches "Playboy", "Playboy TV", "Playboy HD", etc.

---

## Example 2: Sports Only Filter

```yaml
rules:
  - name: "Include Sports"
    type: include
    match:
      categories:
        by_name: ["*Sports*", "*Football*", "*Basketball*"]
        by_labels: ["sports"]
      channels:
        by_labels: ["sports", "live"]

  - name: "Block Adult"
    type: exclude
    match:
      channels:
        by_labels: ["adult", "xxx"]
```

**Logic:**
1. Sports category OR Sports/ESPN label → **ACCEPT**
2. If not matched above, check if stream has Adult label → **REJECT**
3. If doesn't match any rule and include rules exist → **REJECT** (ignored)

---

## Example 3: HD Channels with Tiers

```yaml
- name: "Require HD"
  type: include
  match:
    channels:
      by_name: ["HD", "1080p", "4K", "Ultra", "UHD"]

- name: "Exclude Low Quality"
  type: exclude
  match:
    channels:
      by_name: ["SD", "480p", "Low Quality"]
```

**Logic:**
1. Must have "HD", "1080p", "4K" in name → **ACCEPT**
2. If contains "SD" or "480p" → **REJECT**
3. Doesn't match HD names and has include rule → **REJECT** (ignored)

---

## Example 4: Movies & Series with Favoris

```yaml
# Rules Section
- name: "Include Movies and Series"
  type: include
  match:
    categories:
      by_name: ["Movies", "Series", "TV Shows"]
      by_labels: ["movie", "series"]
    channels:
      by_name: ["Film", "Cinema", "Episode"]

- name: "Exclude Adult Content"
  type: exclude
  match:
    channels:
      by_labels: ["Adult", "XXX"]
```

**Favoris Section (optional):**
```yaml
- name: "Action Movies"
  target_group: "Action"
  match:
    channels:
      by_name: ["Action", "Thriller", "Adventure"]
      by_labels: ["action", "movie"]

- name: "Comedy Series"
  target_group: "Comedy"
  match:
    channels:
      by_name: ["Comedy", "Funny"]
      by_labels: ["comedy", "series"]

- name: "Drama"
  target_group: "Drama"
  match:
    channels:
      by_labels: ["drama"]
```

**Benefits:**
- Rules filter to movies/series only
- Favoris create virtual categories (IDs: 100000, 100001, 100002, etc.)
- Client can browse "Action", "Comedy", "Drama" as separate categories
- Each favoris category only shows matching streams

---

## Example 5: News with Regional Organization

```yaml
# Rules
- name: "Include News"
  type: include
  match:
    categories:
      by_name: ["*News*", "*Journalism*"]  # Wildcard for variations
      by_labels: ["news", "breaking"]
    channels:
      by_name: ["*CNN*", "*BBC*", "Reuters*", "*AP*"]  # Wildcard patterns
      by_labels: ["news"]

- name: "Exclude Opinion"
  type: exclude
  match:
    channels:
      by_name: ["*Opinion*", "*Commentary*", "*Analysis*"]
      by_labels: ["opinion"]
```

**Favoris:**
```yaml
- name: "International News"
  target_group: "World News"
  match:
    channels:
      by_name: ["BBC*", "France*", "Al Jazeera*", "Reuters*", "Euro*"]
      by_labels: ["international"]

- name: "Local & Regional News"
  target_group: "Local News"
  match:
    channels:
      by_name: ["*Local*", "*Regional*"]
      by_labels: ["local"]

- name: "Breaking News"
  target_group: "Breaking"
  match:
    channels:
      by_labels: ["breaking", "live"]
```

---

## Example 6: Include-Only Filter (Sports)

```yaml
- name: "Sports Only"
  type: include
  match:
    categories:
      by_name: ["Sports", "Football", "Basketball", "Soccer"]
      by_labels: ["Sports"]
    channels:
      by_name: ["ESPN", "Sports", "League"]
      by_labels: ["sports", "live"]

- name: "Premium Quality"
  type: include
  match:
    channels:
      by_name: ["HD", "1080p", "4K"]
      by_labels: ["hd"]
```

**Logic:**
- Stream must match BOTH rules (processed in order)
- First rule: must be sports → ACCEPT
- If not sports, second rule won't help (already rejected)
- Must be sports AND HD to be accepted
- Anything else is rejected as ignored

---

## Example 7: Exclude-Only Filter (Blocklist)

```yaml
- name: "Block Adult"
  type: exclude
  match:
    channels:
      by_labels: ["adult", "xxx"]

- name: "Block Shopping"
  type: exclude
  match:
    categories:
      by_name: ["Shopping", "QVC", "HSN"]

- name: "Block Religious"
  type: exclude
  match:
    categories:
      by_labels: ["religious"]
```

**Logic:**
- All streams accepted EXCEPT:
  - Those with "adult" or "xxx" label → REJECT
  - Those in "Shopping" category → REJECT
  - Those with "religious" label → REJECT
- Everything else is ACCEPTED

---

## Match Criteria Details

### Matching Logic Rules

#### by_name: Wildcard Patterns (OR Logic)
- **Case-insensitive** matching
- Supports **wildcards**: `*` (any characters), `?` (one character)
- Matches if **ANY** pattern matches (OR logic)
- Examples:
  - `FR*` matches "FRANCE", "French", "FR Sport"
  - `*HD` matches "ABC HD", "FOX HD", "Sports HD"
  - `ESPN?` matches "ESPN1", "ESPN2"

#### by_labels: Exact Match (AND Logic)
- **Case-insensitive** exact match
- NO wildcard support (exact string match)
- Matches if stream has **ALL** labels (AND logic)
- Examples:
  - `["sports"]` - stream must have "sports" label
  - `["sports", "hd"]` - stream must have BOTH "sports" AND "hd" labels
  - `["sports", "hd", "live"]` - stream must have all three labels

### Categories
```yaml
categories:
  # Any pattern matches (case-insensitive wildcard patterns)
  by_name: ["Kids", "*Sports*", "Comedy*"]

  # All labels must exist (case-insensitive exact match, AND logic)
  by_labels: ["family", "kids"]  # Must have BOTH labels
```

### Channels
```yaml
channels:
  # Any pattern matches (case-insensitive wildcard patterns)
  by_name: ["Disney", "*NBC*", "FR*"]

  # All labels must exist (case-insensitive exact match, AND logic)
  by_labels: ["hd", "live"]  # Must have BOTH labels
```

### Combining Criteria
```yaml
match:
  categories:
    by_name: ["Kids", "Family"]     # OR: Kids OR Family category
    by_labels: ["safe"]              # AND: AND must have "safe" label
  channels:
    by_name: ["Disney*", "PBS"]     # OR: Disney* OR PBS channel
    by_labels: ["kids", "clean"]    # AND: AND must have BOTH kids AND clean labels
```

**Logic:** If both category and channel criteria exist, BOTH must match (AND).
**Logic:** If only one type exists, that one must match.

**Note:** Empty arrays are skipped. You don't need to define unused criteria.

---

## Wildcard Pattern Examples

### Simple Wildcards

```yaml
# Match anything starting with "FR"
by_name: ["FR*"]
# Matches: FRANCE, French, FR Sport, FRBC, FR1, FR2

# Match anything ending with "HD"
by_name: ["*HD"]
# Matches: ABC HD, CBS HD, Sports HD, 1080 HD

# Match anything containing "ESPN"
by_name: ["*ESPN*"]
# Matches: ESPN, ESPN1, My ESPN, ESPN Sports, ESPN HD

# Match exact length with ?
by_name: ["ESPN?"]
# Matches: ESPN1, ESPN2, ESPNx
# Does NOT match: ESPN, ESPN12
```

### Using Labels with Wildcards

```yaml
# French channels with HD quality
channels:
  by_name: ["FR*"]           # Any French channel (wildcard)
  by_labels: ["hd"]          # Must have HD label (exact match)

# Sports channels (any) that are live and HD
channels:
  by_name: ["*Sport*"]       # Any channel with "Sport" in name
  by_labels: ["live", "hd"]  # Must have BOTH live AND hd labels
```

### Combined Examples

```yaml
# Example 1: Include International Channels
match:
  channels:
    by_name: ["FR*", "DE*", "ES*", "IT*"]    # French, German, Spanish, Italian
    by_labels: ["international", "hd"]       # AND must have both labels

# Example 2: Block Low Quality
match:
  channels:
    by_name: ["*Test*", "*Demo*"]             # Any test or demo channel
    by_labels: ["low-quality"]                # AND must have low-quality label

# Example 3: Sports with Category
match:
  categories:
    by_name: ["*Sports*"]                     # Any category with Sports
    by_labels: ["live"]                       # AND must have live label
  channels:
    by_name: ["ESPN*", "*Sports*", "*League"]  # OR ESPN*, OR *Sports*, OR *League
    by_labels: ["live", "hd"]                 # AND must have BOTH live AND hd

# Example 4: Premium Channels
match:
  channels:
    by_name: ["HBO", "Showtime*", "Premium*"]  # OR HBO OR Showtime* OR Premium*
    by_labels: ["premium"]                     # AND must have premium label
```

---

## Best Practices

1. **Order Matters** - Place restrictive rules first
   ```yaml
   - name: "Block Adult"    # Exclude first to reject ASAP
     type: exclude
   - name: "Include Sports" # Then include to accept
     type: include
   ```

2. **Use Wildcards for Flexibility** - Match name patterns efficiently
   ```yaml
   # Good - wildcard reduces multiple patterns
   by_name: ["FR*", "DE*", "ES*"]  # All French, German, Spanish

   # Less efficient (still valid)
   by_name: ["France", "French", "FR1", "FR2", "FR3"]

   # Good - wildcard for prefix or suffix
   by_name: ["*HD", "*FHD", "*4K"]  # Any quality tier suffix
   ```

3. **Understand Label Logic** - Labels use AND logic (all must match)
   ```yaml
   # CORRECT: Channel must be BOTH sports AND live
   by_labels: ["sports", "live"]

   # WRONG interpretation: This means BOTH must exist, not either
   by_labels: ["sports", "live"]  # NOT "sports OR live"

   # For OR logic with labels, use separate rules
   - name: "Rule 1"
     type: include
     match:
       channels:
         by_labels: ["sports"]
   - name: "Rule 2"
     type: include
     match:
       channels:
         by_labels: ["live"]
   ```

4. **Use Labels for Guaranteed Metadata** - More reliable than names
   ```yaml
   # More reliable (assuming labels are populated)
   by_labels: ["hd", "sports"]

   # Less reliable (names vary by provider)
   by_name: ["HD Sports", "Sports HD", "Sport HD"]
   ```

5. **Combine Patterns and Labels**
   ```yaml
   # Include broadly with wildcards, refine with labels
   - name: "Include International"
     type: include
     match:
       channels:
         by_name: ["FR*", "DE*", "ES*", "IT*"]  # Wildcard patterns
         by_labels: ["international"]           # AND must have label

   - name: "Exclude Low Quality International"
     type: exclude
     match:
       channels:
         by_name: ["FR*", "DE*"]                # Wildcard patterns
         by_labels: ["low-quality"]             # AND must have label
   ```

6. **Case Sensitivity** - Everything is case-insensitive
   ```yaml
   # All equivalent (case-insensitive):
   by_name: ["ESPN"]
   by_name: ["espn"]
   by_name: ["EsPn"]

   by_labels: ["HD"]
   by_labels: ["hd"]
   by_labels: ["Hd"]
   ```

7. **Wildcard Examples**
   ```yaml
   # Common patterns
   "*" followed by text:     by_name: ["*NBA*"]      # Contains NBA
   Text followed by "*":     by_name: ["HBO*"]       # Starts with HBO
   "*" before and after:     by_name: ["*HD*"]       # Contains HD
   "?" for single char:      by_name: ["ESPN?"]      # ESPN1, ESPN2, etc

   # Real-world examples
   French channels:          by_name: ["FR*"]
   UK channels:              by_name: ["*BBC*", "*ITV*"]
   US networks:              by_name: ["NBC*", "CBS*", "ABC*", "FOX*"]
   ```

8. **Test Your Filters**
   - Use the backoffice preview to see matching streams
   - Check rejection tracking to understand why streams were excluded
   - Adjust patterns if needed and re-test

---

## Favoris Virtual Category IDs

Generated favoris get sequential IDs starting at 100000:

```yaml
- name: "Favoris 1"
  target_group: "First Category"    # ID: 100000

- name: "Favoris 2"
  target_group: "Second Category"   # ID: 100001

- name: "Favoris 3"
  target_group: "Third Category"    # ID: 100002
```

These virtual categories appear in:
- M3U8 playlists as `category_id=100000`, `category_id=100001`, etc.
- API responses
- Client interfaces

---

## Debugging Tips

Use the backoffice to:
1. **View Rejection Tracking** - See which streams were rejected and why
2. **Test with Sample Streams** - Check if your rules work as expected
3. **Analyze Ignored Streams** - Find streams not matching include rules
4. **Review Rule Order** - Ensure rules are in logical order

Example rejection report:
```json
{
  "accepted": 450,
  "rejected": {
    "by_adult_content": 12,
    "by_rule": {
      "Block Adult Content": 45,
      "Exclude Opinion": 8
    },
    "ignored": 85
  }
}
```

This shows:
- 450 streams accepted
- 12 filtered by adult content check
- 45 rejected by "Block Adult Content" rule
- 8 rejected by "Exclude Opinion" rule
- 85 didn't match any include rule (ignored)
