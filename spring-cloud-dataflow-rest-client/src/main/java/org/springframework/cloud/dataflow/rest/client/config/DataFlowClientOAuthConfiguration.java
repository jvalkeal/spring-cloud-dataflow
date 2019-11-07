/*
 * Copyright 2019 the original author or authors.
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
package org.springframework.cloud.dataflow.rest.client.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.common.security.support.DefaultOAuth2TokenUtilsService;
import org.springframework.cloud.common.security.support.OAuth2TokenUtilsService;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;

public class DataFlowClientOAuthConfiguration {

	@Autowired
	protected OAuth2AuthorizedClientService oauth2AuthorizedClientService;

	@Bean
	protected OAuth2TokenUtilsService oauth2TokenUtilsService() {
		return new DefaultOAuth2TokenUtilsService(this.oauth2AuthorizedClientService);
	}

}
