package org.springframework.cloud.dataflow.core.dsl;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class StaticParserService {

	private final List<ParserService> parserServices;
	private static StaticParserService instance;

	public StaticParserService() {
		this.parserServices = loadParserServices(ClassUtils.getDefaultClassLoader());
	}

	public static StaticParserService getInstance() {
		if (instance == null) {
			instance = new StaticParserService();
		}
		return instance;
	}

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
				services.add((ParserService) constructor.newInstance());
			}
			catch (Throwable ex) {
				// logger.trace(LogMessage.format("Failed to load %s", analyzerName), ex);
			}
		}
		services.add(new DefaultParserService());
		AnnotationAwareOrderComparator.sort(services);
		return services;
	}

	// public TaskNode parseTask(String dsl) {
	// 	return null;
	// }

	private static class DefaultParserService implements ParserService {

		@Override
		public Optional<StreamNode> parseStream(String dsl, String name) {
			return Optional.of(new StreamParser(name, dsl).parse());
		}

		// @Override
		// public Optional<TaskNode> parseTask(String taskName, String taskDefinition, boolean inAppMode, boolean validate) {
		// 	return Optional.of(new TaskParser(taskName, taskDefinition, inAppMode, validate).parse());
		// }
	}

}
