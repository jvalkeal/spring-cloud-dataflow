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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.actuate.metrics.Metric;
import org.springframework.cloud.dataflow.server.config.MetricsProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

@Component
public class MetricStore {

	private final RestTemplate restTemplate;
	private final MetricsProperties metricsProperties;

	public MetricStore(MetricsProperties metricsProperties) {
		this.metricsProperties = metricsProperties;
		List<HttpMessageConverter<?>> messageConverters = new ArrayList<>();
		ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json()
				.serializerByType(Metric.class, new BootMetricJsonSerializer.Serializer())
				.deserializerByType(Metric.class, new BootMetricJsonSerializer.Deserializer())
				.build();
		messageConverters.add(new MappingJackson2HttpMessageConverter(objectMapper));
		restTemplate = new RestTemplate(messageConverters);
	}

	@HystrixCommand(fallbackMethod = "defaultMetrics")
	public List<GroupedMetrics> getMetrics() {
		List<GroupedMetrics> metrics = null;
		if (StringUtils.hasText(metricsProperties.getCollector().getUrl())) {
			metrics = restTemplate
					.exchange(metricsProperties.getCollector().getUrl(), HttpMethod.GET, null, new ParameterizedTypeReference<List<GroupedMetrics>>() {
					}).getBody();
		} else {
			metrics = defaultMetrics();
		}
		return metrics;
	}

	public List<GroupedMetrics> defaultMetrics() {
		return new ArrayList<GroupedMetrics>();
	}
}
