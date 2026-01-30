package org.anasoid.iptvorganizer.services.xtream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.Client;
import org.anasoid.iptvorganizer.models.entity.Filter;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.anasoid.iptvorganizer.models.filtering.FilterConfig;
import org.anasoid.iptvorganizer.repositories.FilterRepository;
import org.anasoid.iptvorganizer.services.FilterService;
import org.anasoid.iptvorganizer.services.stream.CategoryService;
import org.anasoid.iptvorganizer.services.stream.LiveStreamService;
import org.anasoid.iptvorganizer.services.stream.SeriesService;
import org.anasoid.iptvorganizer.services.stream.VodStreamService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for ContentFilterService using Mockito. */
@ExtendWith(MockitoExtension.class)
class ContentFilterServiceTest {

  @Mock private FilterService filterService;
  @Mock private CategoryService categoryService;
  @Mock private LiveStreamService liveStreamService;
  @Mock private VodStreamService vodStreamService;
  @Mock private SeriesService seriesService;
  @Mock private FilterRepository filterRepository;

  @InjectMocks private ContentFilterService contentFilterService;

  private Client testClient;
  private Filter testFilter;
  private FilterConfig testFilterConfig;

  @BeforeEach
  void setUp() {
    testClient = new Client();
    testClient.setId(1L);
    testClient.setUsername("testclient");
    testClient.setHideAdultContent(false);
    testClient.setFilterId(null);

    testFilter = new Filter();
    testFilter.setId(1L);
    testFilter.setUseSourceFilter(true);

    testFilterConfig = new FilterConfig();
  }

  @Test
  void testBuildFilterContext_NoFilterAssigned() {
    // Given: Client with no filter assigned
    testClient.setFilterId(null);
    testClient.setHideAdultContent(false);

    // When: Build filter context
    FilterContext context = contentFilterService.buildFilterContext(testClient);

    // Then: Context should be created with defaults
    assertThat(context).isNotNull();
    assertThat(context.isHideAdultContent()).isFalse();
    assertThat(context.getFilter()).isNull();
  }

  @Test
  void testBuildFilterContext_WithAdultContentHiding() {
    // Given: Client with adult content hiding enabled
    testClient.setHideAdultContent(true);
    testClient.setFilterId(null);

    // When: Build filter context
    FilterContext context = contentFilterService.buildFilterContext(testClient);

    // Then: Context should reflect hiding preference
    assertThat(context).isNotNull();
    assertThat(context.isHideAdultContent()).isTrue();
  }

  @Test
  void testBuildFilterContext_WithFilterAssigned() {
    // Given: Client with filter assigned
    testClient.setFilterId(1L);
    when(filterRepository.findById(1L)).thenReturn(testFilter);
    when(filterService.getCachedFilterConfig(testFilter)).thenReturn(testFilterConfig);

    // When: Build filter context
    FilterContext context = contentFilterService.buildFilterContext(testClient);

    // Then: Context should include filter config
    assertThat(context).isNotNull();
    assertThat(context.getFilter()).isEqualTo(testFilter);
    assertThat(context.getFilterConfig()).isEqualTo(testFilterConfig);
  }

  @Test
  void testBuildFilterContext_FilterDisabled() {
    // Given: Filter assigned but not active
    testFilter.setUseSourceFilter(false);
    testClient.setFilterId(1L);
    when(filterRepository.findById(1L)).thenReturn(testFilter);

    // When: Build filter context
    FilterContext context = contentFilterService.buildFilterContext(testClient);

    // Then: Filter should not be set in context
    assertThat(context).isNotNull();
    assertThat(context.getFilter()).isNull();
  }

  @Test
  void testGetAllowedCategories_NoFiltering() {
    // Given: No filtering needed
    FilterContext context = new FilterContext();
    context.setHideAdultContent(false);
    context.setFilter(null);

    List<Category> allCategories = createTestCategories(3);
    when(categoryService.findBySourceAndType(1L, "live")).thenReturn(allCategories);

    // When: Get allowed categories
    List<Category> result = contentFilterService.getAllowedCategories(context, 1L, "live");

    // Then: Should return all categories
    assertThat(result).hasSize(3).isEqualTo(allCategories);
  }

  @Test
  void testGetAllowedCategories_WithExplicitAllow() {
    // Given: One category with explicit allow
    FilterContext context = new FilterContext();
    context.setHideAdultContent(false);

    Category allowedCategory = createTestCategory(1, "Allowed");
    allowedCategory.setAllowDeny(BaseStream.AllowDenyStatus.ALLOW);

    Category neutralCategory = createTestCategory(2, "Neutral");
    neutralCategory.setAllowDeny(null);

    List<Category> allCategories = List.of(allowedCategory, neutralCategory);
    when(categoryService.findBySourceAndType(1L, "live")).thenReturn(allCategories);

    // When: Get allowed categories
    List<Category> result = contentFilterService.getAllowedCategories(context, 1L, "live");

    // Then: Should include allowed category
    assertThat(result).extracting(Category::getExternalId).contains(1);
  }

  @Test
  void testGetAllowedCategories_WithExplicitDeny() {
    // Given: One category with explicit deny, one with explicit allow, enable adult content hiding
    // to trigger filtering
    FilterContext context = new FilterContext();
    context.setHideAdultContent(true); // Enable some filtering

    Category deniedCategory = createTestCategory(1, "Denied");
    deniedCategory.setAllowDeny(BaseStream.AllowDenyStatus.DENY);

    Category allowedCategory = createTestCategory(2, "Allowed");
    allowedCategory.setAllowDeny(BaseStream.AllowDenyStatus.ALLOW);

    List<Category> allCategories = List.of(deniedCategory, allowedCategory);
    when(categoryService.findBySourceAndType(1L, "live")).thenReturn(allCategories);

    // When: Get allowed categories
    List<Category> result = contentFilterService.getAllowedCategories(context, 1L, "live");

    // Then: Should only include explicitly allowed categories, exclude denied
    assertThat(result).hasSize(1);
    assertThat(result.get(0).getExternalId()).isEqualTo(2);
  }

  @Test
  void testShouldIncludeStream_NoContext() {
    // Given: No filter context
    BaseStream stream = createTestStream(1, "Stream 1", false);
    Category category = createTestCategory(1, "Category 1");

    // When: Check stream inclusion with null context
    boolean result = contentFilterService.shouldIncludeStream(null, stream, category);

    // Then: Should return false (no context means exclude)
    assertThat(result).isFalse();
  }

  @Test
  void testShouldIncludeStream_ExplicitAllow() {
    // Given: Stream with explicit allow
    FilterContext context = new FilterContext();
    BaseStream stream = createTestStream(1, "Stream 1", false);
    stream.setAllowDeny(BaseStream.AllowDenyStatus.ALLOW);
    Category category = createTestCategory(1, "Category 1");

    // Mock the filter service to allow the stream
    when(filterService.shouldIncludeStream(stream, category, null, false)).thenReturn(true);

    // When: Check stream inclusion
    boolean result = contentFilterService.shouldIncludeStream(context, stream, category);

    // Then: Should return true
    assertThat(result).isTrue();
  }

  @Test
  void testShouldIncludeStream_ExplicitDeny() {
    // Given: Stream with explicit deny
    FilterContext context = new FilterContext();
    BaseStream stream = createTestStream(1, "Stream 1", false);
    stream.setAllowDeny(BaseStream.AllowDenyStatus.DENY);
    Category category = createTestCategory(1, "Category 1");

    // When: Check stream inclusion
    boolean result = contentFilterService.shouldIncludeStream(context, stream, category);

    // Then: Should return false
    assertThat(result).isFalse();
  }

  @Test
  void testShouldIncludeStream_AdultContentHidden() {
    // Given: Adult stream with hiding enabled
    FilterContext context = new FilterContext();
    context.setHideAdultContent(true);
    BaseStream stream = createTestStream(1, "Adult Stream", true);
    Category category = createTestCategory(1, "Category 1");

    // When: Check stream inclusion
    boolean result = contentFilterService.shouldIncludeStream(context, stream, category);

    // Then: Should return false
    assertThat(result).isFalse();
  }

  @Test
  void testShouldIncludeStream_AdultContentAllowed() {
    // Given: Adult stream but hiding disabled
    FilterContext context = new FilterContext();
    context.setHideAdultContent(false);
    BaseStream stream = createTestStream(1, "Adult Stream", true);
    Category category = createTestCategory(1, "Category 1");

    when(filterService.shouldIncludeStream(stream, category, null, false)).thenReturn(true);

    // When: Check stream inclusion
    boolean result = contentFilterService.shouldIncludeStream(context, stream, category);

    // Then: Should return true
    assertThat(result).isTrue();
  }

  @Test
  void testShouldIncludeCategory_ExplicitAllow() {
    // Given: Category with explicit allow
    FilterContext context = new FilterContext();
    Category category = createTestCategory(1, "Allowed");
    category.setAllowDeny(BaseStream.AllowDenyStatus.ALLOW);
    Map<Integer, Category> categoryCache = new HashMap<>();

    // When: Check category inclusion
    boolean result =
        contentFilterService.shouldIncludeCategory(context, category, 1L, "live", categoryCache);

    // Then: Should return true
    assertThat(result).isTrue();
  }

  @Test
  void testShouldIncludeCategory_ExplicitDeny() {
    // Given: Category with explicit deny
    FilterContext context = new FilterContext();
    Category category = createTestCategory(1, "Denied");
    category.setAllowDeny(BaseStream.AllowDenyStatus.DENY);
    Map<Integer, Category> categoryCache = new HashMap<>();

    // When: Check category inclusion
    boolean result =
        contentFilterService.shouldIncludeCategory(context, category, 1L, "live", categoryCache);

    // Then: Should return false
    assertThat(result).isFalse();
  }

  /**
   * Create a test category
   *
   * @param externalId External ID
   * @param name Category name
   * @return Category
   */
  private Category createTestCategory(int externalId, String name) {
    Category category = new Category();
    category.setId((long) externalId);
    category.setExternalId(externalId);
    category.setName(name);
    category.setSourceId(1L);
    category.setType("live");
    category.setAllowDeny(null);
    return category;
  }

  /**
   * Create test categories
   *
   * @param count Number of categories
   * @return List of categories
   */
  private List<Category> createTestCategories(int count) {
    List<Category> categories = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      categories.add(createTestCategory(i, "Category " + i));
    }
    return categories;
  }

  /**
   * Create a test stream
   *
   * @param externalId External stream ID
   * @param name Stream name
   * @param isAdult Whether stream is adult content
   * @return Stream
   */
  private BaseStream createTestStream(int externalId, String name, boolean isAdult) {
    LiveStream stream =
        LiveStream.builder()
            .id((long) externalId)
            .externalId(externalId)
            .name(name)
            .sourceId(1L)
            .isAdult(isAdult)
            .categoryId(1)
            .allowDeny(null)
            .build();
    return stream;
  }
}
