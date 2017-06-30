/**
 * Copyright 2014-2017 Linagora, Université Joseph Fourier, Floralis
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

package net.roboconf.target.occi.internal;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.roboconf.core.utils.Utils;
import net.roboconf.target.api.TargetException;

/**
 * Utilitary class to create and manage VMs using OCCI.
 * @author Pierre-Yves Gibello - Linagora
 */
public class OcciVMUtils {

	/**
	 * Creates a VM (OCCI / VMWare) using HTTP rendering.
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @param template VM image ID (null means no image specified)
	 * @param title VM title
	 * @param summary VM summary
	 * @param userData User data for the VM
	 * @param user VM credentials (user name)
	 * @param password VM credentials (password)
	 * @param config A map of parameters (eg. mixin attributes)
	 * @return The VM ID
	 */
	public static String createVM(
			String hostIpPort,
			String id,
			String template,
			String title,
			String summary,
			String userData,
			String user,
			String password,
			Map<String,String> config )
	throws TargetException {

		//TODO This is a HACK for CloudAutomation APIs. Expecting interoperable implementation !
		if(hostIpPort.contains("multi-language-connector")) {
			return createCloudAutomationVM(hostIpPort, template, title, userData, config, false);
		}

		String ret = null;
		URL url = null;
		try {
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
			url = new URL("http://" + hostIpPort + "/compute/" + id);
		} catch (MalformedURLException e) {
			throw new TargetException(e);
		}

		if(Utils.isEmptyOrWhitespaces(title)) title = "Roboconf";
		if(Utils.isEmptyOrWhitespaces(summary)) summary = "Generated by Roboconf";

		HttpURLConnection httpURLConnection = null;
		DataInputStream in = null;
		try {
			httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setRequestMethod("PUT");
			httpURLConnection.setRequestProperty("Content-Type", "text/occi");
			httpURLConnection.setRequestProperty("Accept", "*/*");
			StringBuffer category = new StringBuffer("compute; scheme=\"http://schemas.ogf.org/occi/infrastructure#\"; class=\"kind\"");
			if(template != null) {
				category.append(", medium; scheme=\"http://schemas.ogf.org/occi/infrastructure/compute/template/1.1#\"; class=\"mixin\""
						+ ", vmimage; scheme=\"http://occiware.org/occi/infrastructure/crtp/backend#vmimage\"; class=\"mixin\""
						+ ", vmwarefolders; scheme=\"http://occiware.org/occi/infrastructure/crtp/backend#vmwarefolders\"; class=\"mixin\""
						+ ", user_data; scheme=\"http://occiware.org/occi/infrastructure/compute#user_data\"; class=\"mixin\""
						+ ", credential; scheme=\"http://occiware.org/occi/infrastructure/crtp/backend#credential\"; class=\"mixin\"");
						// OLD API: comment 4 lines above and uncomment 2 below...
						//+ ", vmaddon; scheme=\"http://occiware.org/occi/vmwarecrtp#\"; class=\"mixin\""
						//+ ", vmwarefolders; scheme=\"http://occiware.org/occi/vmwarecrtp#\"; class=\"mixin\"");
			}
			category.append(";");
			httpURLConnection.setRequestProperty("Category", category.toString());
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.core.id=\"" + id + "\"");
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.core.title=\"" + title + "\"");
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.core.summary=\"" + summary + "\"");
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.compute.architecture=\"x64\"");
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.compute.cores=2");
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.compute.memory=2");
			httpURLConnection.addRequestProperty("X-OCCI-Attribute",
					"occi.compute.state=\"active\"");
			if(template != null) {
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"imagename=\"" + template + "\"");
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"datacentername=\"" + config.get(VmwareFoldersMixin.DATACENTERNAME) + "\"");
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"datastorename=\"" + config.get(VmwareFoldersMixin.DATASTORENAME) + "\"");
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"clustername=\"" + config.get(VmwareFoldersMixin.CLUSTERNAME) + "\"");
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"hostsystemname=\"" + config.get(VmwareFoldersMixin.HOSTSYSTEMNAME) + "\"");
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"inventorypath=\"" + config.get(VmwareFoldersMixin.INVENTORYPATH) + "\"");
			}

			if(! Utils.isEmptyOrWhitespaces(userData)) {
				String userDataScript = "printf \'"
						+ userData.replaceAll("\n\r", "\\\\n")
							.replaceAll("\n", "\\\\n")
							.replaceAll(System.lineSeparator(), "\\\\n")
							+ "\' > /tmp/roboconf.properties";
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"occi.compute.userdata=\"" + userDataScript + "\"");
				if(Utils.isEmptyOrWhitespaces(user)) user="";
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"user=\"" + user + "\"");
				if(Utils.isEmptyOrWhitespaces(password)) password="";
				httpURLConnection.addRequestProperty("X-OCCI-Attribute",
						"password=\"" + password + "\"");
			}

			in = new DataInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.copyStreamSafely(in, out);
			ret = out.toString( "UTF-8" );

		} catch (IOException e) {
			throw new TargetException(e);
		}  finally {
			Utils.closeQuietly(in);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}
		return ("OK".equalsIgnoreCase(ret.trim()) ? id : null);
	}


	/**
	 * Creates a VM (OCCI / VMWare) using JSON rendering.
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @param template VM image ID (null means no image specified)
	 * @param title VM title
	 * @param summary VM summary
	 * @param userData User data for the VM
	 * @param user VM credentials (user name)
	 * @param password VM credentials (password)
	 * @param config A map of parameters (eg. mixin attributes)
	 * @param waitForActive If true, wait until VM is active
	 * @return The VM ID
	 */
	public static String createVMJson(
			String hostIpPort,
			String id,
			String template,
			String title,
			String summary,
			String userData,
			String user,
			String password,
			Map<String,String> config,
			boolean waitForActive )
	throws TargetException {

		//TODO This is a HACK for CloudAutomation APIs. Expecting interoperable implementation !
		if(hostIpPort.contains("multi-language-connector")) {
			return createCloudAutomationVM(hostIpPort, template, title, userData, config, false);
		} else {

			String vmId = null;
			URL url = null;
			try {
				CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
				url = new URL("http://" + hostIpPort + "/vm/");
			} catch (MalformedURLException e) {
				throw new TargetException(e);
			}

			HttpURLConnection httpURLConnection = null;
			DataInputStream in = null;
			DataOutputStream output = null;
			try {
				httpURLConnection = (HttpURLConnection) url.openConnection();
				httpURLConnection.setRequestMethod("PUT");
				httpURLConnection.setRequestProperty("Content-Type", "application/json");
				httpURLConnection.setRequestProperty("Accept", "application/json");
				httpURLConnection.setDoInput(true);
				httpURLConnection.setDoOutput(true);

				String userDataString = "name: value";
				if(userData != null) {
					userDataString =
							userData.replaceAll("\n\r", "\\\\n")
							.replaceAll("\n", "\\\\n")
							.replaceAll(System.lineSeparator(), "\\\\n");
				}

				String request = "{\n"
						+ "\"id\": \"" + id + "\",\n"
						+ "\"title\": \"" + title + "\",\n"
						+ "\"summary\": \"" + summary + "\",\n"
						+ "\"kind\": \"http://schemas.ogf.org/occi/infrastructure#compute\",\n"
						+ "\"mixins\": ["
						+ "\"http://occiware.org/occi/infrastructure/crtp/backend#vmimage\",\n"
						+ "\"http://occiware.org/occi/infrastructure/crtp/backend#vmwarefolders\",\n"
						+ "\"http://schemas.ogf.org/occi/infrastructure/compute#user_data\",\n"
						+ "\"http://occiware.org/occi/infrastructure/crtp/backend#credential\"\n"
						+ "],\n"
						+ "\"attributes\": {\n"
						+ "\"occi.compute.state\": \"" + "active" + "\",\n"
						+ "\"occi.compute.speed\": " + 3 + ",\n"
						+ "\"occi.compute.memory\": " + 2 + ",\n"
						+ "\"occi.compute.cores\": " + 2 + ",\n"
						+ "\"occi.compute.architecture\": \"" + "x64" + "\",\n"
						+ "\"imagename\": \"" + template + "\",\n"
						+ "\"datacentername\": \"" + config.get(VmwareFoldersMixin.DATACENTERNAME) + "\",\n"
						+ "\"datastorename\": \"" + config.get(VmwareFoldersMixin.DATASTORENAME) + "\",\n"
						+ "\"clustername\": \"" + config.get(VmwareFoldersMixin.CLUSTERNAME) + "\",\n"
						+ "\"hostsystemname\": \"" + config.get(VmwareFoldersMixin.HOSTSYSTEMNAME) + "\",\n"
						+ "\"inventorypath\": \"" + config.get(VmwareFoldersMixin.INVENTORYPATH) + "\",\n"
						+ "\"occi.compute.userdata\": \"" + userDataString + "\",\n"
						+ "\"user\": \"" + user + "\",\n"
						+ "\"password\": \"" + password + "\"\n"
						+ "}\n}";

				final Logger logger = Logger.getLogger( OcciVMUtils.class.getName());
				logger.finest(request);

				httpURLConnection.setRequestProperty(
						"Content-Length",
						Integer.toString(request.getBytes( StandardCharsets.UTF_8 ).length));

				output = new DataOutputStream(httpURLConnection.getOutputStream());
				output.writeBytes(request);
				output.flush();
				Utils.closeQuietly(output);
				output = null;

				in = new DataInputStream(httpURLConnection.getInputStream());
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				Utils.copyStreamSafely(in, out);

				// Parse JSON response to extract VM ID
				ObjectMapper objectMapper = new ObjectMapper();
				JsonResponse rsp = objectMapper.readValue(out.toString( "UTF-8" ), JsonResponse.class);
				vmId = rsp.getId();

				if(! Utils.isEmptyOrWhitespaces(vmId)) {
					if(vmId.startsWith("urn:uuid:")) vmId = vmId.substring(9);

					// Wait until VM is active, if requested
					if(waitForActive) {
						int retries = 15;
						boolean active = false;
						while(! active && retries-- > 0) {
							logger.finest("retry: " + retries);
							try {
								Thread.sleep(10000);  // 10 seconds
							} catch (InterruptedException e) {
								// ignore
							}
							active = !Utils.isEmptyOrWhitespaces(getVMIP(hostIpPort, vmId));
							//active = "ACTIVE".equalsIgnoreCase(getVMStatus(hostIpPort, ret));
						}
					}
				}

			} catch (IOException e) {
				throw new TargetException(e);

			}  finally {
				Utils.closeQuietly(in);
				Utils.closeQuietly(output);
				if (httpURLConnection != null) {
					httpURLConnection.disconnect();
				}
			}

			return (vmId);
		}
	}


	/**
	 * Creates a VM from an image on ActiveEon Proactive Cloud Automation.
	 * @param hostIpPort
	 * @param image The image ID (eg. when backed by OpenStack, the OpenStack image ID).
	 * @param title
	 * @param waitForActive If true, return only when VM is active.
	 * @return The VM ID
	 * @throws TargetException
	 */
	public static String createCloudAutomationVM(
			String hostIpPort,
			String image,
			String title,
			String userData,
			Map<String,String> config,
			boolean waitForActive )
	throws TargetException {

		String vmId = null;
		URL url = null;
		try {
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
			url = new URL("http://" + hostIpPort + "/compute/");

		} catch (MalformedURLException e) {
			throw new TargetException(e);
		}

		HttpURLConnection httpURLConnection = null;
		DataInputStream in = null;
		DataOutputStream output = null;
		try {
			httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setRequestMethod("POST");
			httpURLConnection.setRequestProperty("Content-Type", "application/json");
			httpURLConnection.setRequestProperty("Accept", "application/json");
			httpURLConnection.setDoInput(true);
			httpURLConnection.setDoOutput(true);

			String userDataScript = "printf test > /tmp/roboconf.properties";
			if(userData != null) {
				userDataScript = "printf \'"
					+ userData.replaceAll("\n\r", "\\\\n")
						.replaceAll("\n", "\\\\n")
						.replaceAll(System.lineSeparator(), "\\\\n")
						+ "\' > /tmp/roboconf.properties";
			}

			String request = "{\n"
					+ "\"attributes\": {\n"
						+ "\"occi.entity.title\": \"" + title + "\",\n"
						+ "\"vmimage\": {\n"
						+ "\"imagename\": \"" + image + "\",\n"
						+ "\"occi.category.title\": \"ubuntuMixin\"\n"
					+ "},\n"
						+ "\"user_data\": {\n"
						+ "\"occi.compute.userdata\": \"" + userDataScript + "\",\n"
						+ "\"occi.category.title\": \"scriptMixin\"\n"
					+ "}\n}\n}";

			final Logger logger = Logger.getLogger( OcciVMUtils.class.getName());
			logger.finest(request);
			httpURLConnection.setRequestProperty(
					"Content-Length",
					Integer.toString(request.getBytes( StandardCharsets.UTF_8 ).length));

			output = new DataOutputStream(httpURLConnection.getOutputStream());
			output.writeBytes(request);
			output.flush();
			Utils.closeQuietly(output);
			output = null;

			in = new DataInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.copyStreamSafely(in, out);

			// Parse JSON response to extract VM ID
			ObjectMapper objectMapper = new ObjectMapper();
			JsonResponse rsp = objectMapper.readValue(out.toString( "UTF-8" ), JsonResponse.class);
			vmId = rsp.getId();

			// Wait until VM is active
			if(waitForActive && !Utils.isEmptyOrWhitespaces(vmId)) {
				int retries = 15;
				boolean active = false;
				while(! active && retries-- > 0) {
					logger.finest("retry: " + retries);
					try {
						Thread.sleep(10000);  // 10 seconds
					} catch (InterruptedException e) {
						// ignore
					}
					active = !Utils.isEmptyOrWhitespaces(getVMIP(hostIpPort, vmId));
					//active = "ACTIVE".equalsIgnoreCase(getVMStatus(hostIpPort, ret));
				}
			}
		} catch (IOException e) {
			throw new TargetException(e);

		}  finally {
			Utils.closeQuietly(in);
			Utils.closeQuietly(output);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}

		return (vmId);
	}


	/**
	 * Retrieves VM status (tested on CA only).
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @return The VM's status
	 * @throws TargetException
	 */
	public static String getVMStatus(String hostIpPort, String id) throws TargetException {

		String status = null;
		URL url = null;
		try {
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
			url = new URL("http://" + hostIpPort + "/compute/" + id);
		} catch (MalformedURLException e) {
			throw new TargetException(e);
		}

		HttpURLConnection httpURLConnection = null;
		DataInputStream in = null;
		try {
			httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setRequestMethod("GET");
			httpURLConnection.setRequestProperty("Accept", "application/json");

			in = new DataInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.copyStreamSafely(in, out);

			// Parse JSON response to extract VM status
			ObjectMapper objectMapper = new ObjectMapper();
			JsonResponse rsp = objectMapper.readValue(out.toString( "UTF-8" ), JsonResponse.class);
			status = rsp.getState();

		} catch (IOException e) {
			throw new TargetException(e);

		}  finally {
			Utils.closeQuietly(in);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}

		return status;
	}


	/**
	 * Deletes a VM (OCCI / VMWare).
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @return true if deletion OK, false otherwise
	 */
	public static boolean deleteVM(String hostIpPort, String id) throws TargetException {

		String ret = null;
		URL url = null;
		try {
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
			//TODO This is a HACK for CloudAutomation APIs. Expecting interoperable implementation !
			if(hostIpPort.contains("multi-language-connector")) {
				url = new URL("http://" + hostIpPort + "/compute/" + id);
			} else {
				url = new URL("http://" + hostIpPort + "/" + id);
			}
		} catch (MalformedURLException e) {
			throw new TargetException(e);
		}

		HttpURLConnection httpURLConnection = null;
		DataInputStream in = null;
		try {
			httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setRequestMethod("DELETE");
			httpURLConnection.setRequestProperty("Content-Type", "text/occi");
			httpURLConnection.setRequestProperty("Accept", "*/*");

			in = new DataInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.copyStreamSafely(in, out);
			ret = out.toString( "UTF-8" );

		} catch (IOException e) {
			throw new TargetException(e);

		}  finally {
			Utils.closeQuietly(in);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}

		return ("OK".equalsIgnoreCase(ret));
	}

	/**
	 * Retrieves VM IP.
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @return The VM's IP address
	 * @throws TargetException
	 */
	public static String getVMIP(String hostIpPort, String id) throws TargetException {

		String vmIp = null;
		URL url = null;
		try {
			CookieHandler.setDefault(new CookieManager(null, CookiePolicy.ACCEPT_ALL));
			url = new URL("http://" + hostIpPort + "/compute/" + id);
		} catch (MalformedURLException e) {
			throw new TargetException(e);
		}

		HttpURLConnection httpURLConnection = null;
		DataInputStream in = null;
		try {
			httpURLConnection = (HttpURLConnection) url.openConnection();
			httpURLConnection.setRequestMethod("GET");
			httpURLConnection.setRequestProperty("Accept", "application/json");

			in = new DataInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.copyStreamSafely(in, out);

			// Parse JSON response to extract VM IP
			ObjectMapper objectMapper = new ObjectMapper();
			JsonResponse rsp = objectMapper.readValue(out.toString( "UTF-8" ), JsonResponse.class);
			vmIp = rsp.getHostsystemname();
			if(Utils.isEmptyOrWhitespaces(vmIp)) vmIp = rsp.getHostname();

		} catch (IOException e) {
			throw new TargetException(e);

		}  finally {
			Utils.closeQuietly(in);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}

		return vmIp;
	}

	/**
	 * Checks if VM is running.
	 * @param hostIpPort
	 * @param id
	 * @return
	 * @throws TargetException
	 */
	public static boolean isVMRunning(String hostIpPort, String id)
	throws TargetException {

		boolean result = false;

		String ip = OcciVMUtils.getVMIP(hostIpPort, id);
		try {
			InetAddress inet = InetAddress.getByName(ip);
			result = inet.isReachable(5000);

		} catch (Exception e) {
			result = false;
			final Logger logger = Logger.getLogger( OcciVMUtils.class.getName());
			logger.info(e.getMessage());
		}

		return result;
	}


	/**
	 * Test main program.
	 * @param args
	 * @throws InterruptedException
	 */
	/*
	public static void main(String[] args) throws Exception {

		//String id = createCloudAutomationVM("81.200.35.140:8080/multi-language-connector/occi", "aab7ea48-0585-44b2-afd4-19e99b6581e7", "testCAjava", true);
		//System.out.println("Created VM on CA:" + id);

		//System.out.println("VM IP:" + getVMIP("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
		//System.out.println("VM status:" + getVMStatus("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
		//System.out.println("Delete VM: " + deleteVM("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));

		java.util.Properties p = new java.util.Properties();
		p.setProperty("key1", "value1");
		p.setProperty("key2", "value2");
		p.setProperty("key3", "value3");
		p.setProperty("key4", "value4");
		java.io.StringWriter writer = new java.io.StringWriter();
		p.store( writer, "" );
		String s = writer.toString();
		//System.out.println(s);
		String userdata = s.replaceAll("\n\r", "\\\\n")
				.replaceAll("\n", "\\\\n")
				.replaceAll(System.lineSeparator(), "\\\\n");
		System.out.println(userdata);

		System.out.println("Create VM (JSON): " +
				createVMJson("172.16.225.80:8080", "6157c4d2-08b3-4204-be85-d1828df74c25", "RoboconfAgentOcciware090117", "javaTest", "Java Test", userdata, "ubuntu", "ubuntu", null, false));

		//System.out.println("IP: " + getVMIP("172.16.225.80:8080", "6157c4d2-08b3-4204-be85-d1828df74c25"));
		// curl -v -X DELETE http://172.16.225.80:8080/6157c4d2-08b3-4204-be85-d1828df74c25
		System.exit(0);

		//System.out.println("Create VM: " +
			//	createVM("81.200.35.140:8080/multi-language-connector/occi", //CA/OW2Stack
				//		"", "e3161161-02a4-4685-ad99-8ac36b3e66ea", "UbuntuTest", "Ubuntu Test", null, null, null));
		//System.out.println("Create VM: " +
				//createVM("81.200.35.140:8080/multi-language-connector/occi", //CA/OW2Stack
					//	"", "e906f16e-a3cb-414c-9a7e-c308dff4897d", "javaTest", "Java Test", userdata, null, null));
			//createVM("172.16.225.91:8080", //VMWare
				//"6157c4d2-08b3-4204-be85-d1828df74c22", "RoboconfAgentOcciware090117", "javaTest", "Java Test", null, "occiware", "Occiware1234", null));
		//Thread.sleep(40000);
		//System.out.println("Delete VM: " + deleteVM("81.200.35.151:8080", "8e0cb600-4478-4687-9fa4-135f5985efdf"));
		//System.out.println("Delete VM: " + deleteVM("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
	}
	*/
}


/**
 * Bean for JSON parsing (Jackson ObjectMapper).
 * @author Pierre-Yves Gibello - Linagora
 */
@JsonIgnoreProperties(ignoreUnknown = true)
class JsonResponse {

	private String id;
	private String hostsystemname;
	@JsonProperty("occi.compute.hostname")
	private String hostname;
	@JsonProperty("occi.compute.state")
	private String state;


	public String getId() {
		return this.id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getHostsystemname() {
		return this.hostsystemname;
	}
	public void setHostsystemname(String hostsystemname) {
		this.hostsystemname = hostsystemname;
	}
	public String getHostname() {
		return this.hostname;
	}
	public void setHostname(String hostname) {
		this.hostname = hostname;
	}
	public String getState() {
		return this.state;
	}
	public void setState(String state) {
		this.state = state;
	}
}
