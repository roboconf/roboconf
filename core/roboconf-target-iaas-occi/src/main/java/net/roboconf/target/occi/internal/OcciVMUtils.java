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
import java.io.StringWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import net.roboconf.core.utils.Utils;
import net.roboconf.target.api.TargetException;

/**
 * @author Pierre-Yves Gibello - Linagora
 */
public class OcciVMUtils {

	//TODO createVM() works on both CA and VMWare IaaS... except if an image is specified (APIs not compatible for that).
	/**
	 * Create a VM (OCCI / VMWare).
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @param template VM image ID (null means no image specified)
	 * @param title VM title
	 * @param summary VM summary
	 * @return The VM ID
	 */
	public static String createVM(String hostIpPort, String id, String template, String title, String summary, String userData) throws TargetException {

		//TODO This is a HACK for CloudAutomation APIs (there should be no CA mixin for images).
		if(hostIpPort.contains("multi-language-connector")) {
			return createCloudAutomationVM(hostIpPort, template, title, userData, false);
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
				//TODO This is a HACK for VMWare API (there should be no "vmware" mixin for images).
				category.append(", medium; scheme=\"http://schemas.ogf.org/occi/infrastructure/compute/template/1.1#\"; class=\"mixin\""
						+ ", vmaddon; scheme=\"http://occiware.org/occi/vmwarecrtp#\"; class=\"mixin\""
						+ ", vmwarefolders; scheme=\"http://occiware.org/occi/vmwarecrtp#\"; class=\"mixin\"");
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
	 * Creates a VM from an image on ActiveEon Proactive Cloud Automation.
	 * @param hostIpPort
	 * @param image The image ID (eg. when backed by OpenStack, the OpenStack image ID).
	 * @param title
	 * @param waitForActive If true, return only when VM is active.
	 * @return The VM ID
	 * @throws TargetException
	 */
	public static String createCloudAutomationVM(String hostIpPort, String image, String title, String userData, boolean waitForActive) throws TargetException {
		String ret = null;
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

			String userDataScript = "touch /tmp/roboconf.properties";
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
			System.out.println(request);
			httpURLConnection.setRequestProperty("Content-Length", "" +
					Integer.toString(request.getBytes().length));

			output = new DataOutputStream(httpURLConnection.getOutputStream());
			output.writeBytes(request);
			output.flush();
			Utils.closeQuietly(output);
			output = null;

			in = new DataInputStream(httpURLConnection.getInputStream());
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			Utils.copyStreamSafely(in, out);
			ret = out.toString();

			int pos;
			if((pos = ret.indexOf("id\":\"")) > 0) {
				ret = ret.substring(pos + 5); // [id":"]
				int end = ret.indexOf("\"");
				ret = ret.substring(0, end);
			}

			// Wait until VM is active
			if(waitForActive && !Utils.isEmptyOrWhitespaces(ret)) {
				int retries = 15;
				boolean active = false;
				while(! active && retries-- > 0) {
					System.out.println("retry: " + retries);
					try {
						Thread.sleep(10000);  // 10 seconds
					} catch (InterruptedException e) {
						// ignore
						//e.printStackTrace();
					}
					active = !Utils.isEmptyOrWhitespaces(getVMIP(hostIpPort, ret));
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

		return (ret);
	}

	/**
	 * Retrieves VM status (tested on CA only).
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @return The VM's status
	 * @throws TargetException
	 */
	public static String getVMStatus(String hostIpPort, String id) throws TargetException {
		String ret = null;
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

			String raw = out.toString();
			int pos = raw.indexOf("occi.compute.state\":");
			if(pos > 0) {
				int len = 21; // [occi.compute.state":"]
				if(raw.indexOf("occi.compute.state\": ") > 0) len++; // a blank after :
				raw = raw.substring(pos + len);
				int end = raw.indexOf("\"");
				ret = raw.substring(0, end);
			}

		} catch (IOException e) {
			throw new TargetException(e);
		}  finally {
			Utils.closeQuietly(in);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}

		return ret;
	}

	/**
	 * Delete a VM (OCCI / VMWare).
	 * @param hostIpPort IP and port of OCCI server (eg. "172.16.225.91:8080")
	 * @param id Unique VM ID
	 * @return true if deletion OK, false otherwise
	 */
	public static boolean deleteVM(String hostIpPort, String id) throws TargetException {
		String ret = null;
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
		String ret = null;
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

			String raw = out.toString();
			int pos = raw.indexOf("hostsystemname\":");
			if(pos > 0) {
				raw = raw.substring(pos + 18); // [hostsystemname": "]
				int end = raw.indexOf("\"");
				ret = raw.substring(0, end);
			} else {
				pos = raw.indexOf("occi.compute.hostname\":");
				if(pos > 0) {
					raw = raw.substring(pos + 24); // [occi.compute.hostname":"]
					int end = raw.indexOf("\"");
					ret = raw.substring(0, end);
				}
			}

		} catch (IOException e) {
			throw new TargetException(e);
		}  finally {
			Utils.closeQuietly(in);
			if (httpURLConnection != null) {
				httpURLConnection.disconnect();
			}
		}

		return ret;
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
			e.printStackTrace(System.err);
		}

		return result;
	}

	/**
	 * Test main program.
	 * @param args
	 * @throws InterruptedException
	 */
	public static void main(String[] args) throws Exception {

		//String id = createCloudAutomationVM("81.200.35.140:8080/multi-language-connector/occi", "aab7ea48-0585-44b2-afd4-19e99b6581e7", "testCAjava", true);
		//System.out.println("Created VM on CA:" + id);

		/*
		//System.out.println("VM IP:" + getVMIP("81.200.35.140:8080/multi-language-connector/occi", id));
		System.out.println("VM IP:" + getVMIP("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
		System.out.println("VM status:" + getVMStatus("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
		//System.out.println("Delete VM: " + deleteVM("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
		System.exit(0);
		*/

		Properties p = new Properties();
		p.setProperty("key1", "value1");
		p.setProperty("key2", "value2");
		p.setProperty("key3", "value3");
		p.setProperty("key4", "value4");
		StringWriter writer = new StringWriter();
		p.store( writer, "" );
		String s = writer.toString();
		//System.out.println(s);
		String userdata = s.replaceAll("\n\r", "\\\\n")
				.replaceAll("\n", "\\\\n")
				.replaceAll(System.lineSeparator(), "\\\\n");
		System.out.println(userdata);

		System.out.println("Create VM: " +
				createVM("81.200.35.140:8080/multi-language-connector/occi", //CA/OW2Stack
						"", "e3161161-02a4-4685-ad99-8ac36b3e66ea", "UbuntuTest", "Ubuntu Test", null));
		System.exit(0);
		System.out.println("Create VM: " +
				createVM("81.200.35.140:8080/multi-language-connector/occi", //CA/OW2Stack
						"", "e906f16e-a3cb-414c-9a7e-c308dff4897d", "javaTest", "Java Test", userdata));
			//createVM("172.16.225.91:8080", //VMWare
			//createVM("localhost:8888",
				//"6157c4d2-08b3-4204-be85-d1828df74c22", "RoboconfAgent180116", "javaTest", "Java Test", null));
		//Thread.sleep(40000);
		//System.out.println("Delete VM: " + deleteVM("81.200.35.151:8080", "8e0cb600-4478-4687-9fa4-135f5985efdf"));
		//System.out.println("Delete VM: " + deleteVM("172.16.225.91:8080", "6157c4d2-08b3-4204-be85-d1828df74c22"));
	}

}
