/**
 * Copyright 2014-2015 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.tooling.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import net.roboconf.core.Constants;
import net.roboconf.core.utils.Utils;

/**
 * @author Vincent Zurczak - Linagora
 */
public final class ProjectUtils {

	static final String GRAPH_EP = "main.graph";
	static final String INSTANCES_EP = "model.instances";

	private static final String TPL_NAME = "${NAME}";
	private static final String TPL_DESCRIPTION = "${DESCRIPTION}";
	private static final String TPL_VERSION = "${VERSION}";
	private static final String TPL_POM_GROUP = "${GROUPD_ID}";
	private static final String TPL_POM_ARTIFACT = "${ARTIFACT_ID}";
	private static final String TPL_POM_PLUGIN_VERSION = "${PLUGIN_VERSION}";

	private static String[] DIRECTORIES = {
		Constants.PROJECT_DIR_DESC,
		Constants.PROJECT_DIR_GRAPH,
		Constants.PROJECT_DIR_INSTANCES
	};


	/**
	 * Empty private constructor.
	 */
	private ProjectUtils() {
		// nothing
	}


	/**
	 * Creates a project for Roboconf.
	 * @param targetDirectory the directory into which the Roboconf files must be copied
	 * @param creationBean the creation properties
	 * @throws IOException if something went wrong
	 */
	public static void createProjectSkeleton( File targetDirectory, CreationBean creationBean ) throws IOException {

		if( creationBean.isMavenProject())
			createMavenProject( targetDirectory, creationBean );
		else
			createSimpleProject( targetDirectory, creationBean );
	}


	/**
	 * @return a non-null list of versions for the Roboconf Maven plug-in
	 */
	public static List<String> listMavenPluginVersions() {
		return Arrays.asList( "0.2-SNAPSHOT" );
	}


	/**
	 * Creates a simple project for Roboconf.
	 * @param targetDirectory the directory into which the Roboconf files must be copied
	 * @param creationBean the creation properties
	 * @throws IOException if something went wrong
	 */
	private static void createSimpleProject( File targetDirectory, CreationBean creationBean ) throws IOException {

		// Create the directory structure
		for( String s : DIRECTORIES ) {
			File dir = new File( targetDirectory, s );
			Utils.createDirectory( dir );
		}

		// Create the descriptor
		InputStream in = ProjectUtils.class.getResourceAsStream( "/application-skeleton.props" );
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Utils.copyStreamSafely( in, out );
		String tpl = out.toString( "UTF-8" )
				.replace( TPL_NAME, creationBean.getProjectName())
				.replace( TPL_VERSION, creationBean.getProjectVersion())
				.replace( TPL_DESCRIPTION, creationBean.getProjectDescription());

		// Create the rest of the project
		completeProjectCreation( targetDirectory, tpl );
	}


	/**
	 * Creates a Maven project for Roboconf.
	 * @param targetDirectory the directory into which the Roboconf files must be copied
	 * @param creationBean the creation properties
	 * @throws IOException if something went wrong
	 */
	private static void createMavenProject( File targetDirectory, CreationBean creationBean ) throws IOException {

		// Create the directory structure
		File rootDir = new File( targetDirectory, "src/main/model" );
		for( String s : DIRECTORIES ) {
			File dir = new File( rootDir, s );
			Utils.createDirectory( dir );
		}

		// Create a POM?
		InputStream in;
		if( Utils.isEmptyOrWhitespaces( creationBean.getCustomPomLocation()))
			in = ProjectUtils.class.getResourceAsStream( "/pom-skeleton.xml" );
		else
			in = new FileInputStream( creationBean.getCustomPomLocation());

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		Utils.copyStreamSafely( in, out );
		String tpl = out.toString( "UTF-8" )
				.replace( TPL_NAME, creationBean.getProjectName())
				.replace( TPL_POM_GROUP, creationBean.getGroupId())
				.replace( TPL_POM_PLUGIN_VERSION, creationBean.getPluginVersion())
				.replace( TPL_VERSION, creationBean.getProjectVersion())
				.replace( TPL_POM_ARTIFACT, creationBean.getArtifactId())
				.replace( TPL_DESCRIPTION, creationBean.getProjectDescription());

		File pomFile = new File( targetDirectory, "pom.xml" );
		Utils.copyStream( new ByteArrayInputStream( tpl.getBytes( "UTF-8" )), pomFile );

		// Create the descriptor
		in = ProjectUtils.class.getResourceAsStream( "/application-skeleton.props" );
		out = new ByteArrayOutputStream();
		Utils.copyStreamSafely( in, out );
		tpl = out.toString( "UTF-8" )
				.replace( TPL_NAME, creationBean.getProjectName())
				.replace( TPL_VERSION, "${project.version}--${timestamp}" )
				.replace( TPL_DESCRIPTION, "${project.description}" );

		// Create the rest of the project
		completeProjectCreation( rootDir, tpl );
	}


	/**
	 * Completes the creation of a Roboconf project.
	 * @param targetDirectory the directory into which the Roboconf files must be copied
	 * @param descriptorContent the descriptor's content
	 * @throws IOException if something went wrong
	 */
	private static void completeProjectCreation( File targetDirectory, String descriptorContent ) throws IOException {

		// Write the descriptor
		File f = new File( targetDirectory, Constants.PROJECT_DIR_DESC + "/" + Constants.PROJECT_FILE_DESCRIPTOR );
		Utils.writeStringInto( descriptorContent, f );

		// Create a sample graph file
		f = new File( targetDirectory, Constants.PROJECT_DIR_GRAPH + "/" + GRAPH_EP );
		InputStream in = ProjectUtils.class.getResourceAsStream( "/graph-skeleton.graph" );
		Utils.copyStream( in, f );

		// Create a sample instances file
		f = new File( targetDirectory, Constants.PROJECT_DIR_INSTANCES + "/" + INSTANCES_EP );
		in = ProjectUtils.class.getResourceAsStream( "/instances-skeleton.instances" );
		Utils.copyStream( in, f );
	}


	/**
	 * @author Vincent Zurczak - Linagora
	 */
	public static class CreationBean {
		private String projectName, projectDescription, projectVersion;
		private String artifactId, pluginVersion, groupId;
		private String customPomLocation;
		private boolean mavenProject = true;


		public String getProjectName() {
			return getNonNullString( this.projectName );
		}

		public CreationBean projectName( String projectName ) {
			this.projectName = projectName;
			return this;
		}

		public String getProjectDescription() {
			return getNonNullString( this.projectDescription );
		}

		public CreationBean projectDescription( String projectDescription ) {
			this.projectDescription = projectDescription;
			return this;
		}

		public boolean isMavenProject() {
			return this.mavenProject;
		}

		public CreationBean mavenProject( boolean mavenProject ) {
			this.mavenProject = mavenProject;
			return this;
		}

		public String getProjectVersion() {
			return getNonNullString( this.projectVersion );
		}

		public CreationBean projectVersion( String projectVersion ) {
			this.projectVersion = projectVersion;
			return this;
		}

		public String getGroupId() {
			return this.groupId;
		}

		public CreationBean groupId( String groupId ) {
			this.groupId = groupId;
			return this;
		}

		public String getArtifactId() {
			return getNonNullString( this.artifactId );
		}

		public CreationBean artifactId( String artifactId ) {
			this.artifactId = artifactId;
			return this;
		}

		public String getPluginVersion() {
			return getNonNullString( this.pluginVersion );
		}

		public CreationBean pluginVersion( String pluginVersion ) {
			this.pluginVersion = pluginVersion;
			return this;
		}

		public String getCustomPomLocation() {
			return this.customPomLocation;
		}

		public CreationBean customPomLocation( String customPomLocation ) {
			this.customPomLocation = customPomLocation;
			return this;
		}

		static String getNonNullString( String s ) {
			return Utils.isEmptyOrWhitespaces( s ) ? "" : s.trim();
		}
	}
}
