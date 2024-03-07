/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.cloud.dataflow.server.batch;

//TODO: Boot3x followup
public class JobRestartRuntimeException extends RuntimeException {

	public JobRestartRuntimeException(Long jobExecutionId, Exception cause) {
		super(String.format("JobExecutionId '%d' was not restarted.", jobExecutionId), cause);
	}

	public JobRestartRuntimeException(Long jobExecutionId) {
		super(String.format("JobExecutionId '%d' was not restarted.", jobExecutionId));
	}
}