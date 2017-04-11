/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.cloud.dataflow.server.controller.support;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.cloud.netflix.zuul.filters.route.ZuulFallbackProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;

/**
 * {@link ZuulFallbackProvider} providing empty response for metrics.
 *
 * @author Janne Valkealahti
 *
 */
public class MetricsZuulFallbackProvider implements ZuulFallbackProvider {

	private final ClientHttpResponse EMPTY_RESPONSE = new EmptyClientHttpResponse();

	@Override
	public String getRoute() {
		return "*";
	}

	@Override
	public ClientHttpResponse fallbackResponse() {
		return EMPTY_RESPONSE;
	}

	private static class EmptyClientHttpResponse implements ClientHttpResponse {

		private final byte[] emptyArray = "[]".getBytes();

		@Override
		public HttpStatus getStatusCode() throws IOException {
			return HttpStatus.OK;
		}

		@Override
		public int getRawStatusCode() throws IOException {
			return 200;
		}

		@Override
		public String getStatusText() throws IOException {
			return "OK";
		}

		@Override
		public void close() {
		}

		@Override
		public InputStream getBody() throws IOException {
			return new ByteArrayInputStream(emptyArray);
		}

		@Override
		public HttpHeaders getHeaders() {
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			return headers;
		}
	}
}
