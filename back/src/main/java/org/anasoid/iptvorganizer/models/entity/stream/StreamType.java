package org.anasoid.iptvorganizer.models.entity.stream;

import lombok.Getter;

@Getter
public enum StreamType {
  LIVE("live", "live", "live streams", "get_live_categories", "get_live_streams"),
  VOD("vod", "movie", "VOD streams", "get_vod_categories", "get_vod_streams"),
  SERIES("series", "series", "series", "get_series_categories", "get_series");

  private final String categoryType;
  private final String streamPath;
  private final String streamTypeName;
  private final String categoryAction;
  private final String streamAction;

  StreamType(
      String categoryType,
      String streamPath,
      String streamTypeName,
      String categoryAction,
      String streamAction) {
    this.categoryType = categoryType;
    this.streamPath = streamPath;
    this.streamTypeName = streamTypeName;
    this.categoryAction = categoryAction;
    this.streamAction = streamAction;
  }
}
