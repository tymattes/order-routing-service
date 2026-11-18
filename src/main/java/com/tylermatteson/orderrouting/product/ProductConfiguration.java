package com.tylermatteson.orderrouting.product;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.tylermatteson.orderrouting.config.AppProperties;

/**
 * Loads the product catalog at startup. Any {@link ProductDataException} here fails application context startup.
 */
@Configuration(proxyBeanMethods = false)
public class ProductConfiguration {

	static final String PRODUCTS_FILE = "products.csv";

	@Bean
	ProductCsvLoader productCsvLoader() {
		return new ProductCsvLoader();
	}

	@Bean
	ProductRepository productRepository(AppProperties properties, ProductCsvLoader loader) {
		return new InMemoryProductRepository(loader.load(properties.dataDir().resolve(PRODUCTS_FILE)));
	}

}
