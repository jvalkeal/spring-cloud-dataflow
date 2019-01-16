/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.server.db.migration;

import javax.sql.DataSource;

import org.flywaydb.core.api.configuration.FluentConfiguration;

import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.jdbc.DatabaseDriver;
import org.springframework.cloud.dataflow.server.db.migration.db2.Db2BaselineCallback;
import org.springframework.cloud.dataflow.server.db.migration.oracle.OracleBaselineCallback;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

public class DataFlowFlywayConfigurationCustomizer implements FlywayConfigurationCustomizer {

	@Override
	public void customize(FluentConfiguration configuration) {
		DataSource dataSource = configuration.getDataSource();
		DatabaseDriver databaseDriver = getDatabaseDriver(dataSource);
		if (databaseDriver == DatabaseDriver.ORACLE) {
			configuration.callbacks(new OracleBaselineCallback());
		}
		else if (databaseDriver == DatabaseDriver.DB2) {
			configuration.callbacks(new Db2BaselineCallback());
		}
	}

	private DatabaseDriver getDatabaseDriver(DataSource dataSource) {
		try {
			String url = JdbcUtils.extractDatabaseMetaData(dataSource, "getURL");
			return DatabaseDriver.fromJdbcUrl(url);
		}
		catch (MetaDataAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
