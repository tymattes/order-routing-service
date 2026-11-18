package com.tylermatteson.orderrouting.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Application settings bound from the {@code app.*} namespace.
 *
 * @param dataDir directory holding the reference data files ({@code products.csv}, {@code suppliers.csv});
 *                set via {@code DATA_DIR}, defaulting to {@code data/} relative to the working directory
 * @param routing routing settings ({@code app.routing.*})
 */
@ConfigurationProperties("app")
public record AppProperties(Path dataDir, @DefaultValue Routing routing) {

	public AppProperties {
		if (dataDir == null) {
			throw new IllegalArgumentException("app.data-dir must be set (env var DATA_DIR)");
		}
	}

	/**
	 * Routing settings.
	 *
	 * @param ratingTolerance how far apart two plans' ratings can be and still count as equal, so the plan with more
	 *                        local suppliers wins ({@code app.routing.rating-tolerance}, default 0.5, must be &ge; 0)
	 */
	public record Routing(@DefaultValue("0.5") double ratingTolerance) {

		public Routing {
			if (ratingTolerance < 0 || Double.isNaN(ratingTolerance)) {
				throw new IllegalArgumentException(
						"app.routing.rating-tolerance must be >= 0, was " + ratingTolerance);
			}
		}

	}

}
