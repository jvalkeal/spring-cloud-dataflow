/*
 * Copyright 2013-2017 the original author or authors.
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.hateoas.mediatype.problem.Problem;
import org.springframework.hateoas.mediatype.problem.Problem.ExtendedProblem;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.util.StringUtils;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.ResponseExtractor;

/**
 * Extension of {@link DefaultResponseErrorHandler} that knows how to de-serialize a
 * {@link Problem} structure.
 *
 * @author Eric Bottard
 * @author Gunnar Hillert
 */
public class VndErrorResponseErrorHandler extends DefaultResponseErrorHandler {

	// private ResponseExtractor<Problem> errorExtractor;
	private ResponseExtractor<ExtendedProblem<Map<String, Object>>> errorExtractor;
	// ExtendedProblem<Map<String, Object>>

	public VndErrorResponseErrorHandler(List<HttpMessageConverter<?>> messageConverters) {
		// errorExtractor = new HttpMessageConverterExtractor<Problem>(Problem.class, messageConverters);
		errorExtractor = new HttpMessageConverterExtractor<ExtendedProblem<Map<String, Object>>>(ExtendedProblem.class, messageConverters);
	}

	@Override
	public void handleError(ClientHttpResponse response) throws IOException {
		ExtendedProblem<Map<String, Object>> problem = null;
		try {
			if (HttpStatus.FORBIDDEN.equals(response.getStatusCode())) {
				problem = errorExtractor.extractData(response);
				// vndErrors = new VndErrors(errorExtractor.extractData(response));
			}
			else {
				problem = errorExtractor.extractData(response);
				// vndErrors = vndErrorsExtractor.extractData(response);
			}
		}
		catch (Exception e) {
			super.handleError(response);
		}
		if (problem != null) {
			throw new DataFlowClientException(problem);
		}
		else {
			final String message = StringUtils.hasText(response.getStatusText())
					? response.getStatusText()
					: String.valueOf(response.getStatusCode());

			String logref = String.valueOf(response.getStatusCode());
			problem = Problem.create().withProperties(map -> {
				map.put("logref", logref);
				map.put("message", message);
			});

			throw new DataFlowClientException(problem);
			// throw new DataFlowClientException(
			// 	new VndErrors(String.valueOf(response.getStatusCode()), message));
		}

		// VndErrors vndErrors = null;
		// try {
		// 	if (HttpStatus.FORBIDDEN.equals(response.getStatusCode())) {
		// 		vndErrors = new VndErrors(vndErrorExtractor.extractData(response));
		// 	}
		// 	else {
		// 		vndErrors = vndErrorsExtractor.extractData(response);
		// 	}
		// }
		// catch (Exception e) {
		// 	super.handleError(response);
		// }
		// if (vndErrors != null) {
		// 	throw new DataFlowClientException(vndErrors);
		// }
		// else {
		// 	//see https://github.com/spring-cloud/spring-cloud-dataflow/issues/2898
		// 	final String message = StringUtils.hasText(response.getStatusText())
		// 			? response.getStatusText()
		// 			: String.valueOf(response.getStatusCode());

		// 	throw new DataFlowClientException(
		// 		new VndErrors(String.valueOf(response.getStatusCode()), message));
		// }

	}
}
