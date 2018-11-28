package org.springframework.cloud.dataflow.server.db.migration.mysql;

import java.sql.Connection;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.flywaydb.core.api.migration.jdbc.BaseJdbcMigration;

public class V3__INITIAL_SETUP extends BaseJdbcMigration {

	private static final Log logger = LogFactory.getLog(V3__INITIAL_SETUP.class);

	@Override
	public void migrate(Connection connection) throws Exception {
		logger.info("Hi from V3!!!");
	}

}
