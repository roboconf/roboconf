/**
 * Copyright 2014 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.messaging.client;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import net.roboconf.messaging.messages.Message;

/**
 * @author Vincent Zurczak - Linagora
 */
public interface IClient {

	/**
	 * Start or stop listening to events.
	 * @author Vincent Zurczak - Linagora
	 */
	public enum ListenerCommand {
		START, STOP
	};


	/**
	 * Sets the connection parameters.
	 * @param messageServerIp the IP address of the messaging server
	 * @param messageServerUsername the user name to connect to the server
	 * @param messageServerPassword the password to connect to the server
	 */
	void setParameters( String messageServerIp, String messageServerUsername, String messageServerPassword );

	/**
	 * Sets the message queue where the client can store the messages to process.
	 * @param messageQueue the message queue
	 */
	void setMessageQueue( LinkedBlockingQueue<Message> messageQueue );

	/**
	 * @return true if the client is connected, false otherwise
	 */
	boolean isConnected();

	/**
	 * Opens a connection with the message server.
	 */
	void openConnection() throws IOException;

	/**
	 * Closes the connection with the message server.
	 * <p>
	 * There is no need to check {@link #isConnected()} before invoking this method.
	 * </p>
	 */
	void closeConnection() throws IOException;
}
