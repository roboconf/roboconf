/**
 * Copyright 2015-2016 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.karaf.commands.agent.plugins;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author Pierre-Yves Gibello - Linagora
 */
public class CancelRecipeCommandTest {

	@Test
	public void testExecute_noAgent() throws Exception {

		CancelRecipeCommand cmd = new CancelRecipeCommand();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		cmd.out = new PrintStream( os, true, "UTF-8" );

		cmd.execute();
		Assert.assertTrue( os.toString( "UTF-8" ).contains( "No agent found" ));
	}

	/*
	@Test
	public void testExecute_valid() throws Exception {

		// Prepare the command
		CancelRecipeCommand cmd = new CancelRecipeCommand();
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		cmd.out = new PrintStream( os, true, "UTF-8" );
		cmd.applicationName = "testApplication";
		cmd.roboconfVersion = "0.5";

		// Mock the session
		final String expected = "bundle:install --start mvn:net.roboconf/roboconf-target-docker/0.5";
		Session session = Mockito.mock( Session.class );
		Mockito.when( session.execute( expected )).thenReturn( null );

		// Inject the session
		for( Class<?> c = cmd.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field field = c.getDeclaredField( "session" );
				field.setAccessible( true );
				field.set( cmd, session );

			} catch( NoSuchFieldException e ) {
				// nothing
			}
		}

		// Verify the execution
		cmd.execute();
		String s = os.toString( "UTF-8" );
		Assert.assertFalse( s.contains( "the Roboconf version" ));
		Assert.assertFalse( s.contains( "Unknown target" ));
		Mockito.verify( session ).execute( expected );
	}
	*/
}
