/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.dataflow.rest.client;

import java.util.Map;

import org.springframework.hateoas.mediatype.problem.Problem.ExtendedProblem;
import org.springframework.util.Assert;

/**
 * A Java exception that wraps the serialized {@link VndErrors} object.
 *
 * @author Eric Bottard
 * @author Mark Fisher
 * @author Gunnar Hillert
 */
public class DataFlowClientException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private final ExtendedProblem<Map<String, Object>> problem;

	/**
	 * Initializes a {@link DataFlowClientException} with the provided (mandatory error).
	 *
	 * @param problem Must not be null
	 */
	public DataFlowClientException(ExtendedProblem<Map<String, Object>> problem) {
		Assert.notNull(problem, "The provided problem parameter must not be null.");
		this.problem = problem;
	}

	@Override
	public String getMessage() {
		return (String) problem.getProperties().get("message");
	}
}
