package org.springframework.cloud.data.yarn.appmaster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.yarn.api.records.Container;
import org.springframework.yarn.am.cluster.ContainerCluster;
import org.springframework.yarn.am.cluster.ManagedContainerClusterAppmaster;

public class XdAppmaster extends ManagedContainerClusterAppmaster {

	private final static Log log = LogFactory.getLog(XdAppmaster.class);

	@Override
	protected List<String> onContainerLaunchCommands(Container container, ContainerCluster cluster,
			List<String> commands) {

		ArrayList<String> list = new ArrayList<String>(commands);
		Map<String, Object> extraProperties = cluster.getExtraProperties();

		log.debug("onContainerLaunchCommands extraProperties=" + extraProperties);

		if (extraProperties != null && extraProperties.containsKey("containerModules")) {
			String value = "containerModules=" + cluster.getExtraProperties().get("containerModules");
			list.add(Math.max(list.size() - 2, 0), value);
		}
		list.add(1, "-Dserver.port=0");
		return list;
	}

}
