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

package net.roboconf.dm.management.api;

import net.roboconf.core.model.beans.Instance;
import net.roboconf.dm.management.ManagedApplication;
import net.roboconf.messaging.api.messages.from_agent_to_dm.MsgNotifAutonomic;

/**
 * FIXME: to replace by the commands API and/or autonomic API.
 * @author Vincent Zurczak - Linagora
 */
public interface IRuleBasedEventHandler {

	/**
	 * Machines created by the autonomic may be deleted by hand.
	 * <p>
	 * In such a case, we should update the VM counter.
	 * </p>
	 * @param rootInstance a root instance
	 */
	void notifyVmWasDeletedByHand( Instance rootInstance );


	/**
	 * @return the number of root instances created by the autonomic
	 */
	int getAutonomicInstancesCount();


	/**
	 * Reacts upon autonomic monitoring message (aka "autonomic event").
	 * @param event The autonomic event message
	 */
	void handleEvent( ManagedApplication ma, MsgNotifAutonomic event );
}
