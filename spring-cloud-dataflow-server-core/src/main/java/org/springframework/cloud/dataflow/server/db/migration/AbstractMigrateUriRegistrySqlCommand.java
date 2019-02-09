/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.dataflow.server.db.migration;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.jdbc.core.JdbcTemplate;

public abstract class AbstractMigrateUriRegistrySqlCommand extends SqlCommand {

	private static final Logger logger = LoggerFactory.getLogger(AbstractMigrateUriRegistrySqlCommand.class);

	@Override
	public void handle(JdbcTemplate jdbcTemplate, Connection connection) {
		boolean migrate = false;
		try {
			jdbcTemplate.execute("select 1 from URI_REGISTRY");
			migrate = true;
			logger.info("Detected URI_REGISTRY, migrating");
		} catch (Exception e) {
			logger.info("Didn't detected URI_REGISTRY, skpping migration");
		}

		if (migrate) {
			updateAppRegistration(jdbcTemplate, createAppRegistrationMigrationData(jdbcTemplate));
			dropUriRegistryTable(jdbcTemplate);
		}
	}

	@Override
	public boolean canHandleInJdbcTemplate() {
		return true;
	}

	/**
	 * Update app registration.
	 *
	 * @param jdbcTemplate the jdbc template
	 * @param data the data
	 */
	protected abstract void updateAppRegistration(JdbcTemplate jdbcTemplate, List<AppRegistrationMigrationData> data);

	/**
	 * Creates a migration data from URI_REGISTRY table to get inserted
	 * into app_registration table. We're working on a raw data level
	 * thus no use hibernate, spring repositories nor entity classes.
	 *
	 * @param jdbcTemplate the jdbc template
	 * @return the list
	 */
	protected List<AppRegistrationMigrationData> createAppRegistrationMigrationData(JdbcTemplate jdbcTemplate) {
		Map<String, AppRegistrationMigrationData> data = new HashMap<>();

		jdbcTemplate.query("select NAME, URI from URI_REGISTRY", rs -> {
			AppRegistrationMigrationData armd;
			String name = rs.getString(1);
			String uri = rs.getString(2);
			String[] split = name.split("\\.");
			if (split.length == 2 || split.length == 3) {
				String key = split[0] + split[1];
				armd = data.getOrDefault(key, new AppRegistrationMigrationData());
				if (split.length == 2) {
					armd.setName(split[1]);
					armd.setType(ApplicationType.valueOf(split[0]).ordinal());
					armd.setUri(uri);
				}
				else {
					armd.setName(split[1]);
					armd.setType(ApplicationType.valueOf(split[0]).ordinal());
					armd.setMetadataUri(uri);
				}
				data.put(key, armd);
			}
		});

		return new ArrayList<>(data.values());
	}

	/**
	 * Drop uri registry table.
	 *
	 * @param jdbcTemplate the jdbc template
	 */
	protected void dropUriRegistryTable(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.execute("drop table URI_REGISTRY");
	}

	/**
	 * Raw migration data from uri registry into app registration.
	 */
	protected static class AppRegistrationMigrationData {

		private String name;
		private int type;
		private String version;
		private String uri;
		private String metadataUri;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public int getType() {
			return type;
		}

		public void setType(int type) {
			this.type = type;
		}

		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		public String getUri() {
			return uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}

		public String getMetadataUri() {
			return metadataUri;
		}

		public void setMetadataUri(String metadataUri) {
			this.metadataUri = metadataUri;
		}
	}
}
