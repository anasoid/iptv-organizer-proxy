package org.anasoid.iptvorganizer.models.stream;

import lombok.Getter;

@Getter
public enum StreamType {
  LIVE("live", "live streams", "get_live_categories", "get_live_streams"),
  VOD("vod", "VOD streams", "get_vod_categories", "get_vod_streams"),
  SERIES("series", "series", "get_series_categories", "get_series");

  private final String categoryType;
  private final String streamTypeName;
  private final String categoryAction;
  private final String streamAction;

  StreamType(
      String categoryType, String streamTypeName, String categoryAction, String streamAction) {
    this.categoryType = categoryType;
    this.streamTypeName = streamTypeName;
    this.categoryAction = categoryAction;
    this.streamAction = streamAction;
  }
}
