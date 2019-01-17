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
import org.springframework.cloud.dataflow.server.db.migration.db2.Db2BeforeBaselineCallback;
import org.springframework.cloud.dataflow.server.db.migration.oracle.OracleBeforeBaselineCallback;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

/**
 * Flyway {@link FlywayConfigurationCustomizer} bean customizing callbacks per
 * active db vendor.
 *
 * @author Janne Valkealahti
 *
 */
public class DataFlowFlywayConfigurationCustomizer implements FlywayConfigurationCustomizer {

	// TODO: should try to PR this concept to boot so that we could just create callbacks
	//       and then boot would associate those only with active db vendor.

	@Override
	public void customize(FluentConfiguration configuration) {
		// boot's flyway auto-config doesn't allow to define callbacks per
		// vendor id, so essentially customizing those here.
		DataSource dataSource = configuration.getDataSource();
		DatabaseDriver databaseDriver = getDatabaseDriver(dataSource);
		if (databaseDriver == DatabaseDriver.ORACLE) {
			configuration.callbacks(new OracleBeforeBaselineCallback());
		}
		else if (databaseDriver == DatabaseDriver.DB2) {
			configuration.callbacks(new Db2BeforeBaselineCallback());
		}
	}

	private DatabaseDriver getDatabaseDriver(DataSource dataSource) {
		// copied from boot's flyway auto-config to get matching db vendor id
		try {
			String url = JdbcUtils.extractDatabaseMetaData(dataSource, "getURL");
			return DatabaseDriver.fromJdbcUrl(url);
		}
		catch (MetaDataAccessException ex) {
			throw new IllegalStateException(ex);
		}
	}
}
