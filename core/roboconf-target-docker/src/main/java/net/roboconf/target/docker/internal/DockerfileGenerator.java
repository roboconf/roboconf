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

package net.roboconf.target.docker.internal;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import net.roboconf.core.utils.Utils;

/**
 * Generate a Dockerfile directory with all necessary stuff to setup a Roboconf agent.
 * @author Pierre-Yves Gibello - Linagora
 */
public class DockerfileGenerator {

	private final Logger logger = Logger.getLogger( getClass().getName());
	private final File agentPack;

	private String packages = "openjdk-7-jre-headless";
	private boolean isTar = true;


	/**
	 * Constructor for docker file generator.
	 * @param agentPack path to the agent tarball or zip
	 * @param packages packages to be installed using apt-get (including JRE)
	 */
	public DockerfileGenerator(String agentPack, String packages) {
		this.agentPack = new File(agentPack);
		if(packages != null)
			this.packages = packages;

		if(agentPack.endsWith(".zip"))
			this.isTar = false;
	}


	/**
	 * Generate docker file.
	 * @return path to a full-fledged temporary Dockerfile directory
	 * @throws IOException
	 */
	public File generateDockerfile() throws IOException {
		// Create temporary dockerfile directory
		Path dockerfile = Files.createTempDirectory("roboconf_");

		// Copy agent package in temp dockerfile directory
		File tmpPack = new File(dockerfile.toFile(), this.agentPack.getName());
		Utils.copyStream(this.agentPack, tmpPack);
		tmpPack.setReadable(true);

		this.logger.fine( "Generating a Dockerfile." );
		File generated = new File(dockerfile.toFile(), "Dockerfile");
		PrintWriter out = null;
		try {
			out = new PrintWriter( generated, "UTF-8" );
			out.println("FROM ubuntu");
			out.println("COPY " + this.agentPack.getName() + " /usr/local/");
			out.println("RUN apt-get update");
			out.println("RUN apt-get -y install " + this.packages);
			out.println("RUN cd /usr/local; " + (this.isTar ? "tar xvzf " : "unzip ") + this.agentPack.getName());

			// Remove extension from file name (generally .zip or .tar.gz, possibly .tgz or .tar)
			String extractDir = this.agentPack.getName();
			int pos;
			if((pos = extractDir.lastIndexOf('.')) > 0)
				extractDir = extractDir.substring(0, pos);

			if(extractDir.endsWith(".tar"))
				extractDir = extractDir.substring(0, extractDir.length() - 4);

			out.println("RUN ln -s /usr/local/" + extractDir + " /usr/local/roboconf-agent");
			// The rc.local and start.sh files will be generated as well!
			out.println("COPY rc.local /etc/");
			out.println("COPY start.sh /usr/local/roboconf-agent/");

		} finally {
			Utils.closeQuietly(out);
		}

		// Generate start.sh startup script for roboconf agent
		this.logger.fine( "Generating the start script for the Roboconf agent." );
		generated = new File(dockerfile.toFile(), "start.sh");
		out = null;
		try {
			out = new PrintWriter( generated, "UTF-8" );
			out.println("#!/bin/bash");
			out.println("# Startup script for roboconf agent on Docker");
			out.println("cd /usr/local/roboconf-agent");
			out.println("echo \"# Roboconf agent configuration - DO NOT EDIT: Generated by roboconf\" >  etc/net.roboconf.agent.configuration.cfg");
			out.println("for p in \"$@\"");
			out.println("do");
			out.println("echo $p >> etc/net.roboconf.agent.configuration.cfg");
			out.println("done");
			out.println("cd bin");
			out.println("./karaf");

		} finally {
			Utils.closeQuietly(out);
		}

		generated.setExecutable(true, false);

		// Generate rc.local script to launch roboconf agent at boot time
		this.logger.fine( "Generating a rc.local file." );
		generated = new File(dockerfile.toFile(), "rc.local");
		out = null;
		try {
			out = new PrintWriter( generated, "UTF-8" );
			out.println("#!/bin/sh -e");
			out.println("# Generated by roboconf...");
			out.println("apt-get update");
			out.println("cd /usr/local/roboconf-agent");
			out.println("./start.sh");
			out.println("exit 0");

		} finally {
			Utils.closeQuietly(out);
		}

		generated.setExecutable(true, false);
		return dockerfile.toFile();
	}


	/**
	 * Retrieve packages list (for apt-get).
	 * @return The packages list
	 */
	public String getPackages() {
		return this.packages;
	}


	/**
	 * Determine if the agent package is a tarball (tar/tgz) file.
	 * @return true for a tarball, false otherwise
	 */
	public boolean isTar() {
		return this.isTar;
	}
}
