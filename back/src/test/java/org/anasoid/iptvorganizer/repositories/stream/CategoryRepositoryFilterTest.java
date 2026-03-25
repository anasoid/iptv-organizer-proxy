package org.anasoid.iptvorganizer.repositories.stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import java.util.List;
import org.anasoid.iptvorganizer.SQLiteTestProfile;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream.AllowDenyStatus;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.repositories.synch.SourceRepository;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SQLiteTestProfile.class)
class CategoryRepositoryFilterTest {

  @Inject SourceRepository sourceRepository;

  @Inject CategoryRepository categoryRepository;

  @Test
  void shouldApplyCombinedServerSideFiltersToListAndCount() {
    Long sourceId = createSource("combined-filters");
    seedCategories(sourceId);

    List<Category> categories =
        categoryRepository.findBySourceIdFiltered(
            sourceId, null, null, "default", "default", 1, 10);
    Long total =
        categoryRepository.countBySourceIdFiltered(sourceId, null, null, "default", "default");

    assertEquals(1, categories.size());
    assertEquals(1L, total);
    assertEquals("Delta", categories.getFirst().getName());
  }

  @Test
  void shouldKeepPaginationConsistentForBlackListFilters() {
    Long sourceId = createSource("blacklist-pagination");
    seedCategories(sourceId);

    List<Category> firstPage =
        categoryRepository.findBySourceIdFiltered(sourceId, "live", null, null, "hidden", 1, 1);
    List<Category> secondPage =
        categoryRepository.findBySourceIdFiltered(sourceId, "live", null, null, "hidden", 2, 1);
    Long total = categoryRepository.countBySourceIdFiltered(sourceId, "live", null, null, "hidden");

    assertEquals(1, firstPage.size());
    assertEquals(1, secondPage.size());
    assertEquals(2L, total);
    assertEquals("Gamma", firstPage.getFirst().getName());
    assertEquals("Beta", secondPage.getFirst().getName());
  }

  private Long createSource(String suffix) {
    Source source = new Source();
    source.setName("Test Source " + suffix);
    source.setUrl("http://example.com/" + suffix);
    source.setUsername("user-" + suffix);
    source.setPassword("pass-" + suffix);
    source.setSyncInterval(60);
    source.setIsActive(true);
    source.setEnableProxy(false);
    return sourceRepository.insert(source);
  }

  private void seedCategories(Long sourceId) {
    createCategory(
        sourceId, 1, "Alpha", "live", AllowDenyStatus.ALLOW, Category.BlackListStatus.DEFAULT);
    createCategory(sourceId, 2, "Beta", "live", null, Category.BlackListStatus.HIDE);
    createCategory(sourceId, 3, "Gamma", "live", null, Category.BlackListStatus.FORCE_HIDE);
    createCategory(sourceId, 4, "Delta", "vod", null, Category.BlackListStatus.DEFAULT);
    createCategory(
        sourceId,
        5,
        "Epsilon",
        "live",
        AllowDenyStatus.DENY,
        Category.BlackListStatus.FORCE_VISIBLE);
    createCategory(sourceId, 6, "Zeta", "live", null, Category.BlackListStatus.VISIBLE);
  }

  private void createCategory(
      Long sourceId,
      int externalId,
      String name,
      String type,
      AllowDenyStatus allowDeny,
      Category.BlackListStatus blackListStatus) {
    Category category = new Category();
    category.setSourceId(sourceId);
    category.setExternalId(externalId);
    category.setName(name);
    category.setType(type);
    category.setNum(externalId);
    category.setAllowDeny(allowDeny);
    category.setBlackList(blackListStatus);
    categoryRepository.insert(category);
  }
}
