package org.anasoid.iptvorganizer.controllers.xtream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;
import org.anasoid.iptvorganizer.models.entity.stream.LiveStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class XtreamControllerTest {

  private XtreamController xtreamController;
  private Method convertStreamToXtreamMapMethod;

  @BeforeEach
  void setUp() throws NoSuchMethodException {
    xtreamController = new XtreamController();
    xtreamController.objectMapper = new ObjectMapper();
    convertStreamToXtreamMapMethod =
        XtreamController.class.getDeclaredMethod("convertStreamToXtreamMap", BaseStream.class);
    convertStreamToXtreamMapMethod.setAccessible(true);
  }

  @Test
  void convertStreamToXtreamMap_parsesCommaSeparatedCategoryIds() throws Exception {
    LiveStream stream =
        LiveStream.builder()
            .num(1)
            .externalId(100)
            .name("Test Stream")
            .categoryId(10)
            .categoryIds("10, 20,30")
            .isAdult(false)
            .build();

    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) convertStreamToXtreamMapMethod.invoke(xtreamController, stream);

    assertThat(result.get("category_ids")).isEqualTo(List.of(10, 20, 30));
  }

  @Test
  void convertStreamToXtreamMap_parsesJsonStyleCategoryIds() throws Exception {
    LiveStream stream =
        LiveStream.builder()
            .num(1)
            .externalId(100)
            .name("Test Stream")
            .categoryId(10)
            .categoryIds("[10, 20, 30]")
            .isAdult(false)
            .build();

    @SuppressWarnings("unchecked")
    Map<String, Object> result =
        (Map<String, Object>) convertStreamToXtreamMapMethod.invoke(xtreamController, stream);

    assertThat(result.get("category_ids")).isEqualTo(List.of(10, 20, 30));
  }
}
