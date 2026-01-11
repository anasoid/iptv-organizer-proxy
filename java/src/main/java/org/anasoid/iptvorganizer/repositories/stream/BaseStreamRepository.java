package org.anasoid.iptvorganizer.repositories.stream;

import org.anasoid.iptvorganizer.models.entity.stream.BaseStream;

/**
 * Base repository for stream-like entities (LiveStream, VodStream, Series).
 *
 * @param <T> The stream type extending BaseStream
 */
public abstract class BaseStreamRepository<T extends BaseStream>
    extends SourcedEntityRepository<T> {}
