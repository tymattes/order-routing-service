package com.tylermatteson.orderrouting.supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tylermatteson.orderrouting.config.AppProperties;

/**
 * Loads suppliers at startup. Any {@link SupplierDataException} here fails application context startup.
 */
@Configuration(proxyBeanMethods = false)
public class SupplierConfiguration {

	static final String SUPPLIERS_FILE = "suppliers.csv";

	@Bean
	SupplierCsvLoader supplierCsvLoader() {
		return new SupplierCsvLoader();
	}

	@Bean
	SupplierRepository supplierRepository(AppProperties properties, SupplierCsvLoader loader) {
		return new InMemorySupplierRepository(loader.load(properties.dataDir().resolve(SUPPLIERS_FILE)));
	}

}
