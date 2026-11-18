package com.tylermatteson.orderrouting.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application settings bound from the {@code app.*} namespace.
 *
 * @param dataDir directory holding the reference data files ({@code products.csv}, {@code suppliers.csv});
 *                set via {@code DATA_DIR}, defaulting to {@code data/} relative to the working directory
 */
@ConfigurationProperties("app")
public record AppProperties(Path dataDir) {

	public AppProperties {
		if (dataDir == null) {
			throw new IllegalArgumentException("app.data-dir must be set (env var DATA_DIR)");
		}
	}

}
