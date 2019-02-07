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
package org.springframework.cloud.dataflow.server.db.migration.mysql;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.dataflow.core.AppRegistration;
import org.springframework.cloud.dataflow.core.ApplicationType;
import org.springframework.cloud.dataflow.registry.repository.AppRegistrationRepository;
import org.springframework.cloud.dataflow.server.db.migration.SqlCommand;
import org.springframework.jdbc.core.JdbcTemplate;

public class MigrateUriRegistrySqlCommand extends SqlCommand {

	private static final Logger logger = LoggerFactory.getLogger(MigrateUriRegistrySqlCommand.class);

	@Override
	public void handle(JdbcTemplate jdbcTemplate) {

		logger.info("XXX1");

		boolean migrate = false;
		try {
			jdbcTemplate.execute("select 1 from URI_REGISTRY");
			migrate = true;
		} catch (Exception e) {
			logger.error("error", e);
		}

		logger.info("XXX2 {}", migrate);

		if (migrate) {

			Map<String, AppRegistration> data = new HashMap<>();

			jdbcTemplate.query("select NAME, URI from URI_REGISTRY", rs -> {
				AppRegistration ap;
				String name = rs.getString(1);
				String uri = rs.getString(2);
				String[] split = name.split("\\.");
				logger.info("XXX22 {} {} {}", name, uri, split.length);
				ap = data.getOrDefault(split[0]+split[1], new AppRegistration());
				if (split.length == 2) {
					ap.setName(split[1]);
					ap.setType(ApplicationType.valueOf(split[0]));
					try {
						ap.setUri(new URI(uri));
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				else {
					ap.setName(split[1]);
					ap.setType(ApplicationType.valueOf(split[0]));
					try {
						ap.setMetadataUri(new URI(uri));
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				data.put(split[0]+split[1], ap);
				logger.info("XXX3 {}", ap);
			});
			logger.info("XXX4 {}", data.size());

			// insert into app_registration
			// (id, object_version, default_version, metadata_uri, name, type, uri, version)
			//

			Long nextVal = jdbcTemplate.queryForObject("select next_val as id_val from hibernate_sequence", Long.class);

			for (AppRegistration ap : data.values()) {
				logger.info("XXX4 {}", ap);
				jdbcTemplate.update("insert into app_registration (id, object_version, default_version, metadata_uri, name, type, uri, version) values (?,?,?,?,?,?,?,?)", nextVal++, 0, 0, ap.getMetadataUri().toString(), ap.getName(), ap.getType().ordinal(), ap.getUri().toString(), 0);
			}
			jdbcTemplate.update("update hibernate_sequence set next_val= ? where next_val=?", nextVal, nextVal -1);

			jdbcTemplate.execute("drop table URI_REGISTRY");
		}
	}

	@Override
	public boolean canHandleInJdbcTemplate() {
		return true;
	}

}
