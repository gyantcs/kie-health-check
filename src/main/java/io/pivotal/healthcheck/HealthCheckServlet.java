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
	
	public static final Map<String, String> env = 	System.getenv();
	public static final String KIE_CONTAINER 	= 	env.get("KIE_CONTAINER");
	public static final String KIE_SCANNER 		= 	env.get("KIE_SCANNER");
	public static final String KIE_CONTAINER_LIST = "http://localhost:8080/services/rest/server/containers";
	public static final String KIE_SERVER_AUTH	= 	"Basic a2lldXNlcjpwYXNzd29yZDE=";
	
	public static Document kieContainer = null;
	public static Document kieScanner = null;

	
	@Override
	public void init() throws ServletException {
		System.out.println("Servlet " + this.getServletName() + " has started");
		System.out.println("##### Loading Container and Scanner config from Env. ###########################\n");

		try {
			kieContainer = loadXML(KIE_CONTAINER);
			if(KIE_SCANNER != null && !KIE_SCANNER.isEmpty() ){
			   kieScanner = loadXML(KIE_SCANNER);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	
	@Override
	public void destroy() {
		System.out.println("Servlet " + this.getServletName() + " has stopped");
	}

	
	
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException {
		System.out.println("##### START OF HEALTHCHECK ###########################\n");
		
		String returnString = "PCF Health Check ";
		
		String kcid = kieContainer.getFirstChild().getAttributes().getNamedItem("container-id").getNodeValue();

		try {
			 Document d = listContainers();

			if (!doesContainerExist(d, kcid)) {
				System.out.println("##### container does not exist ###########################\n");

				createContainer(kcid, KIE_CONTAINER);
			
				if(KIE_SCANNER != null && !KIE_SCANNER.isEmpty()){
					createScanner(kcid, KIE_SCANNER);
				}
				response.sendError(503);

			} else {
				System.out.println("##### container exist ###########################\n");
				if (!isContainerStarted(d, kcid)){
					response.sendError(503);
				} else {
					if(!isScannerStarted(d, kcid)){
						createScanner(kcid, KIE_SCANNER);
					}
				} 				
			}

		} catch (Exception e1) {
			e1.printStackTrace();
		}
		System.out.println("##### END OF HEALTHCHECK ###########################");

		response.getWriter().println(returnString);
	}



	private String getAuthorizationString() {
		String username = "kieuser";
		String password = "password1";

		String basicAuth = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

		return "Basic " + basicAuth;
	}

	
	
	private String createContainer(String kcid, String container) throws Exception {
		System.out.println("######## create container START");

		String responsestring = "";
		
		Document d = listContainers();

		String urlString = KIE_CONTAINER_LIST + "/" + kcid;
		URL url = new URL(urlString);
		HttpURLConnection c = (HttpURLConnection) url.openConnection();

		c.setRequestProperty("Authorization", KIE_SERVER_AUTH );
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

		System.out.println("######## create container results=r" + responsestring);
		Document doc = loadXML(responsestring);

		return responsestring;
	}

	
	/**
	 *  Add scanner to given container
	 * @param kcid
	 * @param kieScanner
	 */
	private String createScanner(String kcid, String kieScannerBody)  throws Exception{
		
		System.out.println("######## create Scanner START");

		String responsestring = "";
		
		Document d = listContainers();

		String urlString = KIE_CONTAINER_LIST + "/" + kcid + "/scanner";
		URL url = new URL(urlString);
		HttpURLConnection c = (HttpURLConnection) url.openConnection();

		c.setRequestProperty("Authorization", KIE_SERVER_AUTH );
		c.setRequestProperty("Content-Type", "application/xml");
		c.setUseCaches(false);
		c.setDoInput(true);
		c.setDoOutput(true);
		c.setRequestMethod("POST");

		// Send request
		DataOutputStream wr = new DataOutputStream(c.getOutputStream());
		wr.writeBytes(kieScannerBody);
		wr.flush();
		wr.close();

		BufferedReader in = new BufferedReader(new InputStreamReader(c.getInputStream()));
		String str;
		while ((str = in.readLine()) != null) // reading data
			responsestring += str + "\n";// process the response and save it in

		System.out.println("######## create container results=r" + responsestring);
		Document doc = loadXML(responsestring);

		return responsestring;	
	}
	
	
	/**
	 * Get list of present contianers
	 * @return
	 * @throws Exception
	 */
	private Document listContainers() throws Exception {
	
		String responsestring = "";

		URL url = new URL(KIE_CONTAINER_LIST);
		HttpURLConnection c = (HttpURLConnection) url.openConnection();
		c.setRequestProperty("Authorization", KIE_SERVER_AUTH );
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

	
	
	/**
	 * Convert  String xml content to XML Document
	 * @param xml
	 * @return
	 * @throws Exception
	 */
	private static Document loadXML(String xml) throws Exception {
		System.out.println("#################### start loadXML ###########################");
		System.out.println("####################" + xml);

		DocumentBuilderFactory fctr = DocumentBuilderFactory.newInstance();
		DocumentBuilder bldr = fctr.newDocumentBuilder();
		InputSource insrc = new InputSource(new StringReader(xml));
		Document d = bldr.parse(insrc);

		System.out.println("#################### end loadXML ###########################");
		return d;
	}
	
	

	/**
	 * Check for given container-id exits
	 * @param document
	 * @param cid
	 * @return
	 */
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
	

	private boolean isScannerStarted(Document document, String kcid) {

		NodeList kieContainers = document.getElementsByTagName("kie-containers");

		// there is only 1 kie-containers elements
		System.out.println("#### isScannerStarted kieContainers.getLength()=" + kieContainers.getLength());

		for (int i = 0; i < kieContainers.getLength(); i++) {
			
			Node container = kieContainers.item(i);

			// are there are kie-container elements, if not return false
			if (!container.hasChildNodes())
				return false;
			
			// get the list of kie-container elements
			NodeList nodes = container.getChildNodes();

			for (int k = 0; k < nodes.getLength(); k++) {

				// get the kie-container element and check the attribute for container id 
				Node subNode = nodes.item(k);
				String containerID = subNode.getAttributes().getNamedItem("container-id").getNodeValue();
				
				NodeList innerNodes = subNode.getChildNodes();
				//loop through container 
				for(int s= 0; s < innerNodes.getLength(); s++){
					Node innerNode = innerNodes.item(s);
					if("scanner".equalsIgnoreCase(innerNode.getNodeName())){
						String scannerStatus = innerNode.getAttributes().getNamedItem("status").getNodeValue();
						System.out.println("##### isScannerStarted check of container and status: " + containerID
								+ " scanner status: " + scannerStatus);
						if(scannerStatus!= null && scannerStatus.equalsIgnoreCase("STARTED") 
								&& containerID != null &&  containerID.equalsIgnoreCase(kcid)){
							return true;
							
						}
					}	
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
