/**
 * Copyright 2014 Linagora, Université Joseph Fourier
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

package net.roboconf.dm.management;

import java.io.File;

import junit.framework.Assert;
import net.roboconf.core.model.runtime.Application;
import net.roboconf.dm.internal.TestApplication;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author Vincent Zurczak - Linagora
 */
public class ManagedApplicationTest {

	@Rule
	public final TemporaryFolder folder = new TemporaryFolder();


	@Test
	public void testConstructor() throws Exception {

		File f = this.folder.newFolder( "Roboconf_test" );
		ManagedApplication ma = null;
		try {
			Application app = new TestApplication();
			ma = new ManagedApplication( app, f );

			Assert.assertNotNull( ma.getLogger());
			Assert.assertTrue( ma.getLogger().getName().endsWith( "." + app.getName()));

			Assert.assertNotNull( ma.getMonitor());
			Assert.assertEquals( app, ma.getApplication());

		} finally {
			if( ma != null
					&& ma.getMonitor() != null )
				ma.getMonitor().stopTimer();
		}
	}
}
