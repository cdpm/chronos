package org.chronos.chronodb.api.builder.database;

import org.chronos.chronodb.internal.api.ChronoDBConfiguration;

public interface ChronoDBTuplBuilder extends ChronoDBFinalizableBuilder<ChronoDBTuplBuilder> {

	/**
	 * Explicitly sets the size of the cache for the backend store, in bytes.
	 *
	 * <p>
	 * The default value is 209715200 bytes (= 200MB).
	 *
	 * <p>
	 * This setting corresponds to {@link ChronoDBConfiguration#STORAGE_BACKEND_CACHE}.
	 *
	 * @param backendCacheSizeBytes
	 *            The desired maximum size of the backend cache, in bytes. Default is 200MB. Must be strictly greater
	 *            than zero.
	 * @return <code>this</code>, for method chaining.
	 */
	public ChronoDBTuplBuilder withBackendCacheOfSizeInBytes(final long backendCacheSizeBytes);

}
