package org.anasoid.iptvorganizer.models.stream;

/** Interface for stream-like entities (LiveStream, VodStream, Series) */
public interface StreamLike {
  Long getSourceId();

  Integer getExternalId();

  Integer getNum();

  void setNum(Integer num);

  Integer getCategoryId();

  void setCategoryId(Integer categoryId);

  Long getId();

  void setId(Long id);
}
