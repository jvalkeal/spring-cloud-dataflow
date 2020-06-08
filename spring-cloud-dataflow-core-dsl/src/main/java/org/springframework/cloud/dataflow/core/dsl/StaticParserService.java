/*
 * Copyright 2020 the original author or authors.
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
package org.springframework.cloud.dataflow.core.dsl;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

/**
 * This services uses {@link ParserService} instances loaded via spring
 * factories and consult loaded services which is able to parse dls's.
 *
 * @author Janne Valkealahti
 *
 */
public class StaticParserService {

	private final static Logger log = LoggerFactory.getLogger(StaticParserService.class);
	private final List<ParserService> parserServices;
	private static StaticParserService instance;

	public StaticParserService() {
		this.parserServices = loadParserServices(ClassUtils.getDefaultClassLoader());
	}

	/**
	 * Gets a singleton instance of this service.
	 *
	 * @return singleton instance
	 */
	public static StaticParserService getInstance() {
		if (instance == null) {
			instance = new StaticParserService();
		}
		return instance;
	}

	/**
	 * Parses a given stream dsl by conculting loaded service in order and returns
	 * first result.
	 *
	 * @param dsl the dsl
	 * @param name the name
	 * @return return parsed stream node
	 */
	public StreamNode parseStream(String dsl, String name) {
		for (ParserService service : parserServices) {
			Optional<StreamNode> streamNode = service.parseStream(dsl, name);
			if (streamNode.isPresent()) {
				return streamNode.get();
			}
		}
		throw new RuntimeException("Unable to parse " + dsl);
	}

	private List<ParserService> loadParserServices(ClassLoader classLoader) {
		List<String> serviceNames = SpringFactoriesLoader.loadFactoryNames(ParserService.class, classLoader);
		List<ParserService> services = new ArrayList<>();
		for (String serviceName : serviceNames) {
			try {
				Constructor<?> constructor = ClassUtils.forName(serviceName, classLoader).getDeclaredConstructor();
				ReflectionUtils.makeAccessible(constructor);
				ParserService service = (ParserService) constructor.newInstance();
				log.debug("Loaded parser service {} as {}", serviceName, service);
				services.add(service);
			}
			catch (Throwable ex) {
				log.error("Failed to load {}", serviceName, ex);
			}
		}
		log.debug("Adding default parser service");
		services.add(new DefaultParserService());
		AnnotationAwareOrderComparator.sort(services);
		log.debug("Loaded parser services are {}", services);
		return services;
	}

	@Order(0)
	private static class DefaultParserService implements ParserService {

		@Override
		public Optional<StreamNode> parseStream(String dsl, String name) {
			return Optional.of(new StreamParser(name, dsl).parse());
		}
	}
}
