package org.springframework.cloud.data.yarn.container;

import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.module.launcher.ModuleLauncher;
import org.springframework.cloud.stream.module.launcher.ModuleLauncherConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.yarn.annotation.OnContainerStart;
import org.springframework.yarn.annotation.YarnComponent;
import org.springframework.yarn.annotation.YarnParameter;
import org.springframework.yarn.annotation.YarnParameters;
import org.springframework.yarn.container.YarnContainerSupport;

@SpringBootApplication
@Import(ModuleLauncherConfiguration.class)
public class ContainerApplication {

	private final static Log log = LogFactory.getLog(ContainerApplication.class);

	@YarnComponent
	static class ModuleHandler extends YarnContainerSupport {

		@Autowired
		private ModuleLauncher moduleLauncher;

		@OnContainerStart
		public void runModule(@YarnParameters Properties properties, @YarnParameter("containerModules") String module) {
			log.info("runModule properies=" + properties);
			log.info("runModule module=" + module);
			log.info("moduleLauncher=" + moduleLauncher);
			moduleLauncher.launch(new String[] { module }, new String[0]);
			try {
				Thread.sleep(Long.MAX_VALUE);
			} catch (InterruptedException e) {
			}
		}

	}

	public static void main(String[] args) {
		SpringApplication.run(ContainerApplication.class, args);
	}

}
