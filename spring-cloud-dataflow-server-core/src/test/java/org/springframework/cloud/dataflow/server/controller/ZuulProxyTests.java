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

package org.springframework.cloud.dataflow.server.controller;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.client.discovery.noop.NoopDiscoveryClientAutoConfiguration;
import org.springframework.cloud.dataflow.server.config.DataFlowControllerAutoConfiguration.ZuulProxyConfiguration;
import org.springframework.cloud.dataflow.server.configuration.TestDependencies;
import org.springframework.cloud.netflix.ribbon.RibbonAutoConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.context.WebApplicationContext;

/**
 * Tests for embedded zuul proxy.
 *
 * @author Janne Valkealahti
 *
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = { TestDependencies.class, ZuulProxyConfiguration.class, NoopDiscoveryClientAutoConfiguration.class,
		RibbonAutoConfiguration.class }, properties = "spring.cloud.dataflow.metrics.collector.url=http://example.com")
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
public class ZuulProxyTests {

	@Autowired
	private WebApplicationContext wac;

	@Test
	public void proxyEnabled() {
		assertThat(wac.containsBean("zuulEndpoint"), is(true));
	}

}
