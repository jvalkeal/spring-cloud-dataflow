/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.server.repository.support;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

public class SqlPagingQueryProviderFactoryBeanTests {

	@Test
	public void testAllowedSortKeys() {
		DataSource dataSource = mock(DataSource.class);
		SqlPagingQueryProviderFactoryBean factory = new SqlPagingQueryProviderFactoryBean();
		factory.setDataSource(dataSource);
		factory.setDatabaseType("mysql");
		factory.setFromClause("from foo");
		factory.setSelectClause("select *");
		Map<String, Order> sortKeys = new HashMap<>();
		sortKeys.put("TASK_EXECUTION_ID", Order.ASCENDING);
		factory.setSortKeys(sortKeys);

		assertThatCode(() -> factory.getObject()).doesNotThrowAnyException();

		sortKeys.clear();
		sortKeys.put("NOT_FOUND", Order.ASCENDING);

		assertThatThrownBy(() -> factory.getObject())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("At least one sorting key is not in allowed sorting keys");

		factory.setAllowedSortKeys(Arrays.asList("NOT_FOUND", "SOMETHING_ELSE"));

		assertThatCode(() -> factory.getObject()).doesNotThrowAnyException();
	}
}
