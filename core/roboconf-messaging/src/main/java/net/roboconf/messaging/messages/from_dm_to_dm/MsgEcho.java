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

package net.roboconf.messaging.messages.from_dm_to_dm;

import net.roboconf.messaging.messages.Message;

import java.util.Objects;
import java.util.UUID;

/**
 * A message sent on the DM debug queue.
 *
 * @author Pierre bourret - Université Joseph Fourier
 */
public class MsgEcho extends Message {

	private static final long serialVersionUID = 3568910235669142257L;

	// The content of the message
	private final String content;

	/**
	 * Constructs an Echo message with the given content.
	 *
	 * @param content the content of the Echo message.
	 * @throws java.lang.NullPointerException id {@code content} is {@code null}.
	 */
	public MsgEcho( String content ) {
		Objects.requireNonNull( content, "content is null" );
		this.content = content;
	}

	/**
	 * @return the content of this message.
	 */
	public String getContent() {
		return content;
	}

}