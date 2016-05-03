/**
 * Copyright 2014-2016 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.agent.monitoring.internal.tests;

import net.roboconf.agent.AgentMessagingInterface;
import net.roboconf.core.model.beans.Instance;
import net.roboconf.core.model.helpers.InstanceHelpers;
import net.roboconf.messaging.api.business.IAgentClient;

/**
 * @author Vincent Zurczak - Linagora
 */
public class MyAgentInterface implements AgentMessagingInterface {

	private final IAgentClient messagingClient;
	private Instance scopedInstance;


	/**
	 * Constructor.
	 * @param messagingClient
	 */
	public MyAgentInterface( IAgentClient messagingClient ) {
		this.messagingClient = messagingClient;
	}

	@Override
	public IAgentClient getMessagingClient() {
		return this.messagingClient;
	}

	@Override
	public String getApplicationName() {
		return "app";
	}

	@Override
	public Instance getScopedInstance() {
		return this.scopedInstance;
	}

	@Override
	public String getScopedInstancePath() {
		return InstanceHelpers.computeInstancePath( this.scopedInstance );
	}

	public void setScopedInstance( Instance scopedInstance ) {
		this.scopedInstance = scopedInstance;
	}

	@Override
	public void forceHeartbeatSending() {
		// nothing
	}

	@Override
	public String agentStatus() {
        return "";
	}
}
