package org.anasoid.iptvorganizer.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.anasoid.iptvorganizer.models.entity.Source;

/**
 * Startup diagnostic that validates Jackson can actually serialize/deserialize the core entities.
 *
 * <p>In GraalVM native mode, if the reflection configuration in {@link NativeReflectionConfig} is
 * incomplete, Jackson silently returns empty JSON ({}) for every entity. This produces HTTP 200
 * responses whose bodies contain no real data, with no exception thrown and therefore no error
 * logged by {@link org.anasoid.iptvorganizer.exceptions.GeneralExceptionHandler}.
 *
 * <p>This validator runs at startup and logs a FATAL-level ERROR immediately if the problem is
 * detected, making it visible before any request is served.
 */
@Slf4j
@ApplicationScoped
public class NativeReflectionValidator {

  @Inject ObjectMapper objectMapper;

  void onStart(@Observes StartupEvent event) {
    validateJacksonReflection();
  }

  private void validateJacksonReflection() {
    try {
      // Build a Source with all fields populated so we can detect if any are lost.
      Source probe =
          Source.builder()
              .id(1L)
              .name("__reflection_probe__")
              .url("http://probe.internal")
              .username("probe")
              .password("probe")
              .syncInterval(60)
              .isActive(true)
              .build();

      String json = objectMapper.writeValueAsString(probe);

      // If Jackson reflection is broken, every field is null and @JsonInclude(NON_NULL)
      // drops them all, resulting in bare "{}".
      if ("{}".equals(json) || !json.contains("__reflection_probe__")) {
        log.error("╔══════════════════════════════════════════════════════════════════════╗");
        log.error("║ CRITICAL: Jackson serialization is BROKEN (native reflection issue) ║");
        log.error("║ All API responses will return empty JSON {{}}.                       ║");
        log.error("║ Fix: rebuild the native image — NativeReflectionConfig must be      ║");
        log.error("║ compiled into the binary for GraalVM reflection to work.            ║");
        log.error("╚══════════════════════════════════════════════════════════════════════╝");
        log.error("Serialized probe result was: {}", json);
        return;
      }

      // Also verify deserialization so we catch POST-body failures early.
      String probeJson = "{\"name\":\"__deser_probe__\",\"url\":\"http://x\",\"password\":\"x\"}";
      Source deserialized = objectMapper.readValue(probeJson, Source.class);
      if (!"__deser_probe__".equals(deserialized.getName())) {
        log.error(
            "CRITICAL: Jackson deserialization is BROKEN (native reflection issue). "
                + "POST /api/sources will always return 400 'Name is required' because "
                + "request bodies cannot be read. Rebuild the native image.");
        log.error(
            "Deserialized name was: '{}' (expected '__deser_probe__')", deserialized.getName());
        return;
      }

      log.info("Jackson reflection validation passed — serialization and deserialization OK.");
    } catch (Exception e) {
      log.error("Jackson reflection validation threw an unexpected exception", e);
    }
  }
}
