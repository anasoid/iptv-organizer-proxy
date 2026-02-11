package org.anasoid.iptvorganizer.services.synch;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Source;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.Category;
import org.anasoid.iptvorganizer.models.entity.stream.StreamType;
import org.anasoid.iptvorganizer.repositories.stream.BaseStreamRepository;
import org.anasoid.iptvorganizer.repositories.stream.CategoryRepository;

/**
 * Service for cleaning up streams that belong to blacklisted categories. Used during import
 * synchronization to enforce blacklist rules.
 */
@Slf4j
@ApplicationScoped
public class BlackListCleanupService {

  @Inject CategoryRepository categoryRepository;

  /**
   * Delete all streams for blacklisted categories of a specific type. Returns the total number of
   * streams deleted.
   *
   * @param source Source being synced
   * @param streamType Stream type (LIVE, VOD, SERIES)
   * @param streamRepository Repository for the stream type
   * @return Number of streams deleted
   */
  public <T extends BaseStream> int cleanupBlacklistedStreams(
      Source source, StreamType streamType, BaseStreamRepository<T> streamRepository) {

    String categoryType = streamType.getCategoryType();
    log.debug(
        "Cleaning up blacklisted streams for source {} type {}", source.getName(), categoryType);

    // Find all blacklisted categories for this source and type
    List<Category> blacklistedCategories =
        categoryRepository.findBlacklistedBySourceAndType(source.getId(), categoryType);

    if (blacklistedCategories.isEmpty()) {
      log.debug("No blacklisted categories found for cleanup");
      return 0;
    }

    int totalDeleted = 0;
    for (Category category : blacklistedCategories) {
      // Check if streams exist for this category
      long count = streamRepository.countByCategory(source.getId(), category.getExternalId());
      if (count > 0) {
        log.info(
            "Deleting {} streams from blacklisted category: {} (external_id: {})",
            count,
            category.getName(),
            category.getExternalId());

        int deleted = streamRepository.deleteByCategory(source.getId(), category.getExternalId());
        totalDeleted += deleted;
      }
    }

    if (totalDeleted > 0) {
      log.info(
          "Cleaned up {} streams from {} blacklisted categories",
          totalDeleted,
          blacklistedCategories.size());
    }

    return totalDeleted;
  }
}
