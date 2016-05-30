/**
 * Copyright 2016 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.core.model;

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import net.roboconf.core.ErrorCode;
import net.roboconf.core.RoboconfError;

/**
 * @author Vincent Zurczak - Linagora
 */
public class ParsingErrorTest {

	@Test
	public void testEquals() {

		File f = new File( "1" );
		ParsingError def1 = new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, f, 1 );
		ParsingError def2 = new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, f, 2 );
		ParsingError def3 = new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, f, 2, "details" );
		RoboconfError def4 = new RoboconfError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, "details" );

		Assert.assertTrue( def1.equals( def1 ));
		Assert.assertTrue( def1.equals( new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, f, 1 )));
		Assert.assertFalse( def1.equals( new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, new File( "2" ), 1 )));
		Assert.assertFalse( def1.equals( null ));
		Assert.assertFalse( def1.equals( new Object()));
		Assert.assertFalse( def1.equals( def2 ));
		Assert.assertFalse( def1.equals( def3 ));
		Assert.assertFalse( def1.equals( def4 ));

		Assert.assertTrue( def2.equals( def2 ));
		Assert.assertTrue( def2.equals( new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, f, 2 )));
		Assert.assertFalse( def2.equals( def3 ));
		Assert.assertFalse( def2.equals( def1 ));
		Assert.assertFalse( def2.equals( def4 ));

		Assert.assertTrue( def3.equals( def3 ));
		Assert.assertTrue( def3.equals( new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, f, 2, "details" )));
		Assert.assertFalse( def3.equals( def1 ));
		Assert.assertFalse( def3.equals( def2 ));
		Assert.assertFalse( def3.equals( def4 ));

		Assert.assertTrue( def4.equals( def4 ));
		Assert.assertTrue( def4.equals( new RoboconfError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, "details" )));
	}


	@Test
	public void testHashCode() {

		Assert.assertTrue( new ParsingError( null, null, 1 ).hashCode() > 0 );
		Assert.assertTrue( new ParsingError( ErrorCode.CMD_CANNOT_HAVE_ANY_PARENT, null, 5 ).hashCode() > 0 );
	}
}
