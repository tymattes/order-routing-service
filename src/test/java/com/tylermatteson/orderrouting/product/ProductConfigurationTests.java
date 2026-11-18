package com.tylermatteson.orderrouting.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.tylermatteson.orderrouting.config.AppProperties;

class ProductConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfig.class, ProductConfiguration.class);

	@Test
	void loadsProductsFromConfiguredDataDir() {
		Path dataDir = ProductCsvLoaderTests.fixture("datadir/products.csv").getParent();
		contextRunner.withPropertyValues("app.data-dir=" + dataDir)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(ProductRepository.class).findByCode("WC-STD-001")).isPresent();
			});
	}

	@Test
	void failsToStartWhenProductsFileIsMissing(@TempDir Path emptyDir) {
		contextRunner.withPropertyValues("app.data-dir=" + emptyDir)
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(ProductDataException.class)
				.hasMessageContaining("Product data file not found")
				.hasMessageContaining(emptyDir.resolve("products.csv").toString()));
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class TestConfig {

	}

}
