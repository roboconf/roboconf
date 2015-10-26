/**
 * Copyright 2015 Linagora, Université Joseph Fourier, Floralis
 *
 * The present code is developed in the scope of the joint LINAGORA -
 * Université Joseph Fourier - Floralis research program and is designated
 * as a "Result" pursuant to the terms and conditions of the LINAGORA
 * - Université Joseph Fourier - Floralis research program. Each copyright
 * holder of Results enumerated here above fully & independently holds complete
 * ownership of the complete Intellectual Property rights applicable to the whole
 * of said Results, and may freely exploit it in any manner which does not infringe
 * the moral rights of the other copyright holders.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.roboconf.agent.internal.lifecycle;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.beans.Instance.InstanceStatus;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.messaging.api.client.IAgentClient;
import net.roboconf.plugin.api.PluginException;
import net.roboconf.plugin.api.PluginInterface;

/**
 * @author Vincent Zurczak - Linagora
 */
public class WaitingForAncestor extends AbstractLifeCycleManager {

	/**
	 * Constructor.
	 * @param appName
	 * @param messagingClient
	 */
	public WaitingForAncestor( String appName, IAgentClient messagingClient ) {
		super( appName, messagingClient );
	}


	@Override
	public void changeInstanceState(
			Instance instance,
			PluginInterface plugin,
			InstanceStatus newStatus,
			Map<String,byte[]> fileNameToFileContent )
	throws IOException, PluginException {

		// We can undeploy
		if( newStatus == InstanceStatus.NOT_DEPLOYED )
			undeploy( instance, plugin );

		// We can start
		else if( newStatus == InstanceStatus.DEPLOYED_STARTED )
			start( instance, plugin );

		// Stop is only a status change, no script or notification run
		else if( newStatus == InstanceStatus.DEPLOYED_STOPPED ) {
			instance.setStatus( InstanceStatus.DEPLOYED_STOPPED );

			List<Instance> childrenInstances = InstanceHelpers.buildHierarchicalList( instance );
			childrenInstances.remove( instance );

			for( Instance childInstance : childrenInstances ) {
				// "Waiting..." can only have "deployed stopped", "not deployed" and "waiting for ancestor" child status.
				if( childInstance.getStatus() == InstanceStatus.WAITING_FOR_ANCESTOR )
					childInstance.setStatus( InstanceStatus.DEPLOYED_STOPPED );
			}
		}
	}
}
