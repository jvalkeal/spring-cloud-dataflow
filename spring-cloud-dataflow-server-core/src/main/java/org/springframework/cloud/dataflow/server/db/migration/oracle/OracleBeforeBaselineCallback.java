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
package org.springframework.cloud.dataflow.server.db.migration.oracle;

import java.util.Arrays;
import java.util.List;

import org.flywaydb.core.api.callback.Event;

import org.springframework.cloud.dataflow.server.db.migration.AbstractCallback;
import org.springframework.cloud.dataflow.server.db.migration.SqlCommand;

/**
 * For Oracle {@code beforeBaseline} callback to drop indexes as these cannot be
 * done in sql. Java based callback is run before sql based callbacks.
 *
 * @author Janne Valkealahti
 *
 */
public class OracleBeforeBaselineCallback extends AbstractCallback {

	// ORA-01418: specified index does not exist
	private final static int ORA01418 = 1418;
	private final static List<SqlCommand> commands = Arrays.asList(
			SqlCommand.from("drop index AUDIT_RECORDS_AUDIT_ACTION_IDX", ORA01418),
			SqlCommand.from("drop index AUDIT_RECORDS_AUDIT_OPERATION_IDX", ORA01418),
			SqlCommand.from("drop index AUDIT_RECORDS_CORRELATION_ID_IDX", ORA01418),
			SqlCommand.from("drop index AUDIT_RECORDS_CREATED_ON_IDX", ORA01418));

	public OracleBeforeBaselineCallback() {
		super(Event.BEFORE_BASELINE, commands);
	}
}
