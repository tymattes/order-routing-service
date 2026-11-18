package com.tylermatteson.orderrouting.supplier;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.tylermatteson.orderrouting.config.AppProperties;
import com.tylermatteson.orderrouting.product.Category;

class SupplierConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(TestConfig.class, SupplierConfiguration.class);

	@Test
	void loadsSuppliersFromConfiguredDataDir() {
		Path dataDir = SupplierCsvLoaderTests.fixture("datadir/suppliers.csv").getParent();
		contextRunner.withPropertyValues("app.data-dir=" + dataDir)
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(context.getBean(SupplierRepository.class).findByCategory(Category.WHEELCHAIR))
					.extracting(Supplier::id)
					.containsExactly("SUP-001", "SUP-003");
			});
	}

	@Test
	void failsToStartWhenSuppliersFileIsMissing(@TempDir Path emptyDir) {
		contextRunner.withPropertyValues("app.data-dir=" + emptyDir)
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(SupplierDataException.class)
				.hasMessageContaining("Supplier data file not found")
				.hasMessageContaining(emptyDir.resolve("suppliers.csv").toString()));
	}

	@Test
	void failsToStartWhenRequiredColumnIsMissing(@TempDir Path dataDir) throws IOException {
		Files.copy(SupplierCsvLoaderTests.fixture("missing-column.csv"), dataDir.resolve("suppliers.csv"));

		contextRunner.withPropertyValues("app.data-dir=" + dataDir)
			.run(context -> assertThat(context).hasFailed()
				.getFailure()
				.rootCause()
				.isInstanceOf(SupplierDataException.class)
				.hasMessageContaining("missing required column(s) [can_mail_order?]"));
	}

	@EnableConfigurationProperties(AppProperties.class)
	static class TestConfig {

	}

}
