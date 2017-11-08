package io.pivotal.healthcheck;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class HealthCheckServlet extends HttpServlet {

	private static final long serialVersionUID = -7040738151776303470L;

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {

		System.out.println("##### START OF HEALTHCHECK ###########################\n");

		Map<String, String> env = System.getenv();
		String KIE_CONTAINER = env.get("KIE_CONTAINER");

		System.out.println("##### KIE_CONTAINER=" + KIE_CONTAINER + "\n");
		Document kieContainer = null;
		try {
			kieContainer = loadXML(KIE_CONTAINER);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		String returnString = "PCF Health Check ";
		String kcid = kieContainer.getFirstChild().getAttributes().getNamedItem("container-id").getNodeValue();

		try {
			Document d = listContainers();

			if (!doesContainerExist(d, kcid)) {
				System.out.println("##### container does not exist ###########################\n");

				createContainer(kcid, KIE_CONTAINER);
				response.sendError(503);
			} else {
				System.out.println("##### container exist ###########################\n");

				if (!isContainerStarted(d, kcid))
					response.sendError(503);
			}

		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("##### END OF HEALTHCHECK ###########################");

		response.getWriter().println(returnString);
	}
	
	@Override
	public void init() throws ServletException {
		System.out.println("Servlet " + this.getServletName() + " has started");
	}

	@Override
	public void destroy() {
		System.out.println("Servlet " + this.getServletName() + " has stopped");
	}

	private String getAuthorizationString() {
		String username = "kieuser";
		String password = "password1";

		String basicAuth = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

		return "Basic " + basicAuth;
	}

	private String createContainer(String kcid, String container) throws Exception {
		System.out.println("!!!!!!!!!!!!######## create container START");

		Document d = listContainers();

		// if (!isContainerStarted(d, "hello")) {
		//
		// }

		// # Create Container command
		// CONTAINER_CREATE_COMMAND="curl -X PUT -H \"$AUTH_KIE\" -H
		// \"$X_CF_APP_INSTANCE\" -H \"$CONTENT_TYPE\" -d
		// \"$CONTAINER_CREATE_DATA\" $APP_CNTR_CREATION_URL"
		
		//# Request Body data
		// String container="<kie-container
		// container-id=\"hr\"><release-id><artifact-id>guvnor-asset-mgmt-project</artifact-id><group-id>org.guvnor</group-id><version>6.5.0.Final</version></release-id></kie-container>";

		String urlString = "http://localhost:8080/services/rest/server/containers/" + kcid;
		String responsestring = "";

		URL url = new URL(urlString);
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		// HttpsURLConnection c = (HttpsURLConnection) url.openConnection();

		String username = "kieuser";
		String password = "password1";

		String basicAuth = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
		c.setRequestProperty("Authorization", "Basic a2lldXNlcjpwYXNzd29yZDE=");
		c.setRequestProperty("Content-Type", "application/xml");
		c.setUseCaches(false);
		c.setDoInput(true);
		c.setDoOutput(true);
		c.setRequestMethod("PUT");

		// Send request
		DataOutputStream wr = new DataOutputStream(c.getOutputStream());
		wr.writeBytes(container);
		wr.flush();
		wr.close();

		BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
		String str;
		while ((str = in.readLine()) != null) // reading data
			responsestring += str + "\n";// process the response and save it in

		// System.out.println("######## create container results=" +
		// responsestring);

		Document doc = loadXML(responsestring);

		return container;
	}

	private Document listContainers() throws Exception {
		String urlString = "http://localhost:8080/services/rest/server/containers";
		String responsestring = "";

		URL url = new URL(urlString);
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		c.setRequestProperty("Authorization", "Basic a2lldXNlcjpwYXNzd29yZDE=");

		c.setRequestMethod("GET");
		BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
		String str;
		while ((str = in.readLine()) != null) // reading data
		{
			// System.out.println("###### readline=" + str.trim());
			responsestring += str.trim();// process the response and save it in
		}
		// System.out.println("###### responsestring=" + responsestring);
		return loadXML(responsestring);
	}

	private Document loadXML(String xml) throws Exception {

		System.out.println("#################### start loadXML ###########################");
		System.out.println("####################" + xml);

		DocumentBuilderFactory fctr = DocumentBuilderFactory.newInstance();
		DocumentBuilder bldr = fctr.newDocumentBuilder();
		InputSource insrc = new InputSource(new StringReader(xml));
		Document d = bldr.parse(insrc);

		System.out.println("#################### end loadXML ###########################");

		return d;
	}

	private boolean doesContainerExist(Document document, String cid) {
		NodeList kieContainers = document.getElementsByTagName("kie-containers");

		// there is only 1 kie-containers elements
		System.out.println("#### doesContainerExist kieContainers.getLength()=" + kieContainers.getLength());

		for (int i = 0; i < kieContainers.getLength(); i++) {
			Node container = kieContainers.item(i);

			// are there are kie-container elements, if not return false
			if (!container.hasChildNodes())
				return false;

			// get the list of kie-container elements
			NodeList nodes = container.getChildNodes();

			for (int k = 0; k < nodes.getLength(); k++) {

				// get the kie-container element and check the attribute for
				// container id and status
				Node subNode = nodes.item(k);

				String containerID = subNode.getAttributes().getNamedItem("container-id").getNodeValue();
				String status = subNode.getAttributes().getNamedItem("status").getNodeValue();

				System.out.println("##### doesContainerExist check of container and status: " + containerID
						+ " status: " + status);

				if (containerID != null && containerID.equals(cid)) {
					return true;
				} else {
					return false;
				}
			}
		}

		return false;
	}

	private boolean isContainerStarted(Document document, String cid) {
		NodeList kieContainers = document.getElementsByTagName("kie-containers");

		// there is only 1 kie-containers elements
		System.out.println("#### isContainerStarted kieContainers.getLength()=" + kieContainers.getLength());

		for (int i = 0; i < kieContainers.getLength(); i++) {
			Node container = kieContainers.item(i);

			// are there are kie-container elements, if not return false
			if (!container.hasChildNodes())
				return false;

			// get the list of kie-container elements
			NodeList nodes = container.getChildNodes();

			for (int k = 0; k < nodes.getLength(); k++) {

				// get the kie-container element and check the attribute for
				// container id and status
				Node subNode = nodes.item(k);

				String containerID = subNode.getAttributes().getNamedItem("container-id").getNodeValue();
				String status = subNode.getAttributes().getNamedItem("status").getNodeValue();

				System.out.println("##### isContainerStarted check of container and status: " + containerID
						+ " status: " + status);

				if (containerID != null && status != null && containerID.equals(cid)
						&& (status.equalsIgnoreCase("started") || (status.equalsIgnoreCase("starting")))) {
					return true;
				} else {
					return false;
				}
			}
		}

		return false;
	}

	private String getContainerStatus(Document document, String cid) {
		String status = null;
		NodeList kieContainers = document.getElementsByTagName("kie-containers");

		// there is only 1 kie-containers elements
		System.out.println("#### kieContainers.getLength()=" + kieContainers.getLength());

		for (int i = 0; i < kieContainers.getLength(); i++) {
			Node container = kieContainers.item(i);

			// are there are kie-container elements, if not return false
			if (!container.hasChildNodes())
				return status;

			// get the list of kie-container elements
			NodeList nodes = container.getChildNodes();

			for (int k = 0; k < nodes.getLength(); k++) {

				// get the kie-container element and check the attribute for
				// container id and status
				Node subNode = nodes.item(k);

				String containerID = subNode.getAttributes().getNamedItem("container-id").getNodeValue();
				status = subNode.getAttributes().getNamedItem("status").getNodeValue();

				System.out.println("##### check of container and status: " + containerID + " status: " + status);

				if (containerID != null && containerID.equals(cid)) {
					return status;
				}
			}
		}

		return status;
	}
}
