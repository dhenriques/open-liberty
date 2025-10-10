/*******************************************************************************
 * Copyright (c) 2013, 2021 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-2.0/
 * 
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.ws.webcontainer.osgi.mbeans;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.ws.webcontainer.httpsession.SessionManager;
import com.ibm.ws.webcontainer.osgi.DynamicVirtualHost;
import com.ibm.ws.webcontainer.osgi.DynamicVirtualHostManager;
import com.ibm.ws.webcontainer.osgi.WebContainer;
import com.ibm.ws.webcontainer.osgi.mbeans.PluginGenerator.HttpEndpointInfo;
import com.ibm.ws.webcontainer.osgi.mbeans.PluginGenerator.ServerData;
import com.ibm.ws.webcontainer.osgi.mbeans.PluginGenerator.VHostData;
import com.ibm.ws.webcontainer.osgi.webapp.WebApp;
import com.ibm.ws.webcontainer.webapp.WebAppConfiguration;
import com.ibm.wsspi.kernel.service.location.WsLocationAdmin;
import com.ibm.wsspi.kernel.service.location.WsResource;

import test.common.SharedOutputManager;

/**
 *
 */
public class PluginGeneratorTest {

    private static SharedOutputManager outputMgr = SharedOutputManager.getInstance().trace("*=info:webcontainer=all");

    final Mockery context = new JUnit4Mockery() {
        {
            setImposteriser(ClassImposteriser.INSTANCE);
        }
    };

    @Rule
    public TestRule mockRule = new TestRule() {
        @Override
        public Statement apply(final Statement stmt, final Description desc) {
            return new Statement() {
                @Override
                public void evaluate() throws Throwable {
                    // run the test
                    stmt.evaluate();
                    context.assertIsSatisfied();
                }
            };
        }
    };

    @Rule
    public TestRule rule = outputMgr;

    final BundleContext mockBundleContext = context.mock(BundleContext.class);
    final Bundle mockBundle = context.mock(Bundle.class);
    final WsLocationAdmin mockLocationAdmin = context.mock(WsLocationAdmin.class);
    final DynamicVirtualHostManager mockVhostMgr = context.mock(DynamicVirtualHostManager.class);
    final DynamicVirtualHost mockDefaultHost = context.mock(DynamicVirtualHost.class, "default_host");

    final ServiceReference<?> mockDefVhostRef = context.mock(ServiceReference.class, "default_hostRef");
    final ServiceReference<?> mockEndpointInfoRef = context.mock(ServiceReference.class, "endpointInfo_Ref");
    final HttpEndpointInfo mockEndpointInfo = context.mock(HttpEndpointInfo.class, "EndpointInfo");
    final Element element = context.mock(Element.class);
    final Document doc = context.mock(Document.class);
    final Comment comment = context.mock(Comment.class);
    final WebContainer mockWebContainer = context.mock(WebContainer.class);
    final SessionManager mockSessionManager = context.mock(SessionManager.class);
    final WsResource mockWsResource = context.mock(WsResource.class);

    private static final String testClassesDir = System.getProperty("test.classesDir", "bin_test");

    String lastComment = null;

    @Test
    public void testBuildServerTransportData() throws Exception {

        context.checking(new Expectations() {
            {
                allowing(mockBundleContext).getBundle();
                will(returnValue(mockBundle));

                allowing(mockBundle).getDataFile("cached-PluginCfg.xml");
                will(returnValue(new File("")));

                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));

                one(mockEndpointInfo).getProperty("_defaultHostName");
                will(returnValue("localhost")); // this is the default, will always be present in the system

                one(mockEndpointInfo).getProperty("host");
                will(returnValue("*"));

                one(mockEndpointInfo).getProperty("httpPort");
                will(returnValue(1));

                one(mockEndpointInfo).getProperty("httpsPort");
                will(returnValue(-1));
            }
        });

        List<ServerData> clusterServers = new LinkedList<ServerData>();
        Map<String, Object> config = new HashMap<String, Object>();
        config.put("httpEndpointRef", "Endpoint1");

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.buildServerTransportData("serverName", "dummyId", mockEndpointInfo, clusterServers, false, mockWebContainer);
        System.out.println(clusterServers);
        assertEquals("1 elements in server data list", 1, clusterServers.size());

        assertTrue("clusterServer1 " + clusterServers.get(0), !"localhost".equals(clusterServers.get(0).hostName));
        assertEquals("clusterServer1 " + clusterServers.get(0), 1, clusterServers.get(0).transports.size());
        assertEquals("clusterServer1 " + clusterServers.get(0), 1, clusterServers.get(0).transports.get(0).port);
        assertFalse("clusterServer1 " + clusterServers.get(0), clusterServers.get(0).transports.get(0).isSslEnabled);
    }

    private void setCommonExpectations() throws Exception {
        context.checking(new Expectations() {
            {
                allowing(mockBundleContext).getBundle();
                will(returnValue(mockBundle));

                allowing(mockBundle).getDataFile("cached-PluginCfg.xml");
                will(returnValue(new File(testClassesDir + "/cached-PluginCfg.xml")));

                allowing(mockBundle).getState();
                will(returnValue(Bundle.ACTIVE));

                allowing(element).getOwnerDocument();
                will(returnValue(doc));

                allowing(doc).createComment(with(any(String.class)));
                will(new Action() {
                    @Override
                    public void describeTo(org.hamcrest.Description description) {
                        description.appendText("saves comment value");
                    }

                    @Override
                    public Object invoke(Invocation arg0) throws Throwable {
                        lastComment = (String) arg0.getParameter(0);
                        System.out.println(lastComment);
                        return comment;
                    }
                });

                allowing(element).appendChild(comment);
            }
        });
    }

    private void setCommonVHostExpectations() throws Exception {
        setCommonExpectations();
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockDefVhostRef }));

                allowing(mockDefVhostRef).getProperty("id");
                will(returnValue("default_host"));

                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockDefaultHost));

                allowing(mockDefaultHost).getName();
                will(returnValue("default_host"));

                allowing(element).getOwnerDocument();
                will(returnValue(doc));
            }
        });
    }

    @Test
    public void testDefaultConfig() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Variations..
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));
                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // First variation
        // This configuration is JUST the default_host with the default aliases for the transports..
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);
        assertEquals("There should be one element in the virtual host set", 1, virtualHostSet.size());
        assertSame("The default host object should be in the virtual host set", mockDefaultHost, virtualHostSet.iterator().next());

        assertEquals("There should be one element in the virtual host alias data", 1, vhostAliasData.size());
        List<VHostData> data = vhostAliasData.get("default_host");
        assertNotNull("There should be a default_host element in the vhostAliasData map", data);
        assertEquals("There should be two elements in the VHostData", 2, data.size());
        assertTrue("VHostData should contain an alias for *:9080", data.contains(new VHostData("*", 9080)));
        assertTrue("VHostData should contain an alias for *:9443", data.contains(new VHostData("*", 9443)));
    }

    @Test
    public void testModifiedDefaultConfig() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Variations..
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(returnValue(Arrays.asList("*:1", "*:3"))));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                one(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList("*:1", "*:3")));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // First variation
        // This configuration is JUST the default_host, but one of the aliases has been changed.
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);
        assertEquals("There should be one element in the virtual host set", 1, virtualHostSet.size());
        assertSame("The default host object should be in the virtual host set", mockDefaultHost, virtualHostSet.iterator().next());

        // vhostAliasData will contain default_host but with empty list (no aliases match webserver ports)
        // For explicit default_host only configurations, we do NOT generate wildcards
        assertEquals("vhostAliasData should contain default_host", 1, vhostAliasData.size());
        List<VHostData> data = vhostAliasData.get("default_host");
        assertNotNull("There should be a default_host element in the vhostAliasData map", data);
        assertEquals("VHostData should be empty when no aliases match webserver ports", 0, data.size());

        // Verify comment about alias filtering
        assertTrue("Should have comment about filtering aliases",
                   outputMgr.checkForStandardOut("Virtual host aliases have been automatically filtered to include only those matching the configured web server ports"));

        // Verify that warning comments are logged about the missing webserver ports
        assertTrue("comment about missing port 9080", outputMgr.checkForStandardOut("No virtual hosts are configured to accept requests from the webserver http port \\(\\*:9080\\)"));
        assertTrue("comment about missing port 9443", outputMgr.checkForStandardOut("No virtual hosts are configured to accept requests from the webserver https port \\(\\*:9443\\)"));
        vhostAliasData.clear();
    }

    @Test
    public void testProcessTwoHosts() throws Exception {
        final DynamicVirtualHost mockAltHost = context.mock(DynamicVirtualHost.class, "alternate");
        final ServiceReference<?> mockAltVhostRef = context.mock(ServiceReference.class, "alternateRef");

        setCommonExpectations();

        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockDefVhostRef, mockAltVhostRef }));

                allowing(mockDefVhostRef).getProperty("id");
                will(returnValue("default_host"));
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(returnValue(null)));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                allowing(mockAltVhostRef).getProperty("id");
                will(returnValue("alternate"));
                allowing(mockAltVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("*:3", "*:4")));
                allowing(mockAltVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockDefaultHost, mockAltHost));

                allowing(mockDefaultHost).getName();
                will(returnValue("default_host"));
                allowing(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList("*:1", "*:2")));

                allowing(mockAltHost).getName();
                will(returnValue("alternate"));
                allowing(mockAltHost).getAliases();
                will(returnValue(Arrays.asList("*:3", "*:4")));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Map of virtual host name to the list of alias data being collected...
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();

        // Process the virtual host configuration..
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);
    }

    private String getCanonicalHost(String host) throws UnknownHostException {
        InetAddress addr = InetAddress.getByName(host);
        String canonHost = addr.getCanonicalHostName();
        // If this is an IPv6 address, we need extra text to make it usable in messages
        if (addr instanceof Inet6Address && canonHost.contains(":") && !canonHost.startsWith("[")) {
            canonHost = "[" + canonHost + "]";
        }

        System.out.println("getCanonicalHost: " + host + " --> " + canonHost);
        return canonHost;
    }

    /**
     * Test method for {@link PluginGenerator#tryDetermineHostName(String, String, String, boolean)}
     */
    @Test
    public void testTryDetermineHostName() throws Exception {
        String hostName = PluginGenerator.tryDetermineHostName("*", "localhost", false);
        assertFalse("Should resolve * to something other than localhost", "localhost".equals(hostName));

        String canonHost = getCanonicalHost("127.0.0.1");
        hostName = PluginGenerator.tryDetermineHostName("127.0.0.1", "we.should.not.see.this", false);
        assertEquals("Should resolve 127.0.0.1 to " + canonHost, canonHost, hostName);

        canonHost = getCanonicalHost("::1");
        hostName = PluginGenerator.tryDetermineHostName("::1", "we.should.not.see.this", false);
        assertEquals("::1 should resolve to " + canonHost, canonHost, hostName);

        canonHost = getCanonicalHost("[::1]");
        hostName = PluginGenerator.tryDetermineHostName("[::1]", "we.should.not.see.this", false);
        assertEquals("[::1] should resolve to " + canonHost, canonHost, hostName);
    }

    @Test
    public void testResolveHostName() throws Exception {
        // This behavior is somewhat different than what is done in http endpoint. Since the plugin generator
        // is run against a running server, it will only see ports that successfully bound

        //  defaultHostName == localhost (which is the default value), so it should try to resolve '*' to something else
        String hostName = PluginGenerator.tryDetermineHostName("*", "localhost", false);
        assertFalse("Should resolve * to something other than localhost", "localhost".equals(hostName));

        //  defaultHostName is something specific, so it should use that instead
        // (defaultHostName is not canonicalized, left as-is, but must be something reachable from this machine)
        hostName = PluginGenerator.tryDetermineHostName("*", "127.0.0.1", false);
        assertEquals("Should resolve * to 127.0.0.1, due to default host", "127.0.0.1", hostName);

        // specify a specific host value that is a bunch of garbage.
        hostName = PluginGenerator.tryDetermineHostName("unresolvable.nonsense.no.way", "we.should.not.see.this.either", false);
        assertEquals("Should resolve localhost, due to unresolvable value", "localhost", hostName);

        // specify a defaultHostName value that is a bunch of garbage.
        hostName = PluginGenerator.tryDetermineHostName("*", "we.should.not.see.this", false);
        assertEquals("Should resolve localhost, due to unresolvable defaultHostName value", "localhost", hostName);

        // The defaultHostName is empty, so is not used. The answer should not be localhost
        hostName = PluginGenerator.tryDetermineHostName("*", "", false);
        assertFalse("Should resolve * to something other than localhost, defaultHostName is also empty", "localhost".equals(hostName));
    }

    // Expectations for testing generateXML method
    private void setXMLGenerateExpectations() throws Exception {
        context.checking(new Expectations() {
            {
                allowing(mockBundleContext).getAllServiceReferences(null, "(&(enabled=true)(|(httpPort>=1)(httpsPort>=1))(service.pid=Endpoint1))");
                will(returnValue(new ServiceReference<?>[] { mockEndpointInfoRef }));
                allowing(mockEndpointInfoRef).getProperty("id");
                will(returnValue("Endpoint1"));

                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
                one(mockEndpointInfoRef).getProperty("_defaultHostName");
                will(returnValue("localhost")); // this is the default, will always be present in the system
                one(mockEndpointInfoRef).getProperty("host");
                will(returnValue("*"));
                one(mockEndpointInfoRef).getProperty("httpPort");
                will(returnValue(1));
                one(mockEndpointInfoRef).getProperty("httpsPort");
                will(returnValue(-1));
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));
                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
                allowing(mockSessionManager).getCloneSeparator();
                will(returnValue(':'));
                allowing(mockSessionManager).getCloneID();
                will(returnValue("ServerCloneID"));
                allowing(mockDefaultHost);
                allowing(mockSessionManager);
            }
        });
    }

    // Config settings required for XML generation - these are defaults that would be automatically provided
    // in a live server process.
    private void setDefaultConfig(Map<String, Object> config) throws Exception {
        config.put("pluginInstallRoot", "/opt/IBM/WebSphere/Plugins");
        config.put("httpEndpointRef", "Endpoint1");
        config.put("webserverName", "webserver1");
        config.put("webserverPort", "9080");
        config.put("webserverSecurePort", "9443");
        config.put("httpEndpointRef", "Endpoint1");
        config.put("httpEndpointRef", "Endpoint1");
        config.put("ipv6Preferred", new Boolean(false));
        config.put("sslKeyringLocation", "keyringString");
        config.put("sslStashfileLocation", "stashfileString");
        config.put("serverIOTimeout", new Long(900));
        config.put("connectTimeout", new Long(5));
        config.put("extendedHandshake", new Boolean(false));
        config.put("waitForContinue", new Boolean(false));
        config.put("logDirLocation", "/opt/IBM/WebSphere/Plugins/logs/webserver1");
        config.put("serverIOTimeoutRetry", new Integer(-1));
        config.put("loadBalanceWeight", new Integer(20));
        config.put("serverRole", "PRIMARY");
    }

    @Test
    public void testServerIOTimeoutRetry() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        config.put("serverIOTimeoutRetry", new Integer(14));

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());
        // and that it contains the serverIOTimeoutRetry value
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("ServerCluster");
            // we're only expecting one ServerCluser
            assertEquals(1, nl.getLength());
            Node node = nl.item(0);
            Element eElement = (Element) node;
            String value = eElement.getAttribute("ServerIOTimeoutRetry");
            assertEquals("14", value);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/serverIOTimeoutRetry-plugin-cfg.xml"));
    }

    private void commonImplicitSetup() throws IOException {
        // create folder to contain file
        new File(testClassesDir + "/logs/state").mkdirs();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("logs/state/plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/logs/state/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue(new File(testClassesDir + "/logs/state/plugin-cfg.xml")));
            }
        });
    }

    // Test generation of XML file via implicit request - which writes to different location and distinguishes
    // web server and app server names correctly
    @Test
    public void testImplicitDefaultXMLGenerate() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();
        commonImplicitSetup();

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);

        // invoke generateXML method specifying implicit request and not overriding webserver location or app server name (last argument is true)
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML(null, null, mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, true, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/logs/state/plugin-cfg.xml");
        assertTrue(testfile.exists());
        // and that it contains the correct default values
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            // find the PluginInstallRoot property
            String installRoot = null;
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("Property");
            System.out.println("number of Property elements is: " + nl.getLength());
            for (int temp = 0; temp < nl.getLength(); temp++) {
                Node nNode = nl.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (eElement.getAttribute("Name").equals("PluginInstallRoot"))
                        installRoot = eElement.getAttribute("Value");
                }
            }
            assertEquals("/opt/IBM/WebSphere/Plugins", installRoot);
            // make sure plugin log file name was correctly constructed
            NodeList nl2 = docEle.getElementsByTagName("Log");
            // we're only expecting one entry for Log
            assertEquals(1, nl2.getLength());
            Node node = nl2.item(0);
            Element eElement = (Element) node;
            String value = eElement.getAttribute("Name");
            assertEquals("/opt/IBM/WebSphere/Plugins/logs/webserver1/http_plugin.log", value);

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/logs/state/ImplicitDefaultXMLGenerate-plugin-cfg.xml"));
    }

    // Test generation of XML file via explicit request with user-provided plugin location and server name
    @Test
    public void testExplicitXMLGenerate() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);

        // invoke generateXML method specifying implicit request (last argument is true)
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();

            // find the PluginInstallRoot property
            String installRoot = null;
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("Property");
            for (int temp = 0; temp < nl.getLength(); temp++) {
                Node nNode = nl.item(temp);
                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                    Element eElement = (Element) nNode;
                    if (eElement.getAttribute("Name").equals("PluginInstallRoot"))
                        installRoot = eElement.getAttribute("Value");
                }
            }
            assertEquals("userSpecifiedWebserverLocation", installRoot);

            // make sure plugin log file name was correctly constructed
            NodeList nl2 = docEle.getElementsByTagName("Log");
            // we're only expecting one entry for Log
            assertEquals(1, nl2.getLength());
            Node node = nl2.item(0);
            Element eElement = (Element) node;
            String value = eElement.getAttribute("Name");
            assertEquals("userSpecifiedWebserverLocation/logs/userSpecifiedServerName/http_plugin.log", value);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/ExplicitXMLGenerate-plugin-cfg.xml"));
    }

    @Test
    public void testAdditionalProperties() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        config.put("extraConfigProperties.0.config.referenceType", "true");
        config.put("extraConfigProperties.0.myKey", "myValue");
        config.put("extraConfigProperties.0.secondKey", "secondValue");
        config.put("not.an.extra.property", "invalid");
        // test override of property with hard-coded default
        config.put("extraConfigProperties.0.RefreshInterval", "99");

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());
        // and that it contains the additional properties in the <Config> element
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            String value = docEle.getAttribute("myKey");
            assertNotNull(value);
            assertEquals("myValue", value);
            value = docEle.getAttribute("secondKey");
            assertNotNull(value);
            assertEquals("secondValue", value);
            value = docEle.getAttribute("not.an.extra.property");
            assert (value.isEmpty());
            value = docEle.getAttribute("RefreshInterval");
            assertNotNull(value);
            assertEquals("99", value);
            // check one of the hard-coded defaults without override
            value = docEle.getAttribute("IISPluginPriority");
            assertNotNull(value);
            assertEquals("High", value);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/AdditionalProperties-plugin-cfg.xml"));
    }

    @Test
    public void testIgnoreAffinityRequest() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        config.put("ignoreAffinityRequests", Boolean.FALSE);

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());
        // and that it contains the loadBalanceWeight value
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("ServerCluster");
            // we're only expecting one ServerCluser
            assertEquals(1, nl.getLength());
            Node node = nl.item(0);
            Element eElement = (Element) node;

            String value = eElement.getAttribute("IgnoreAffinityRequests");
            assertEquals("false", value);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/ignoreAffinityRequests-plugin-cfg.xml"));
    }

    @Test
    public void testLoadBalanceWeight() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        config.put("loadBalanceWeight", new Integer(15));

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());
        // and that it contains the loadBalanceWeight value
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("ServerCluster");
            // we're only expecting one ServerCluser
            assertEquals(1, nl.getLength());
            Node node = nl.item(0);
            Element eElement = (Element) node;
            //we're in the first Server tag of the ServerCluster
            NodeList nl1 = eElement.getElementsByTagName("Server");
            Node node1 = nl1.item(0);
            Element eElement1 = (Element) node1;
            String value = eElement1.getAttribute("LoadBalanceWeight");
            assertEquals("15", value);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/loadBalanceWeight-plugin-cfg.xml"));
    }

    // Test generation of XML file and check value of ESIEnable whenh user-provided esiDisable is set in server.xml
    @Test
    public void testDisableESI() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        config.put("ESIEnable", new Boolean(false));
        config.put("ESIMaxCacheSize", new Integer(15));
        config.put("ESIInvalidationMonitor", new Boolean(true));
        config.put("ESIEnableToPassCookies", new Boolean(true));
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("ServerCluster");
            // we're only expecting one ServerCluser
            assertEquals(1, nl.getLength());
            Node node = nl.item(0);
            Element eElement = (Element) node;
            //we're in the first Server tag of the ServerCluster
            NodeList nl1 = eElement.getElementsByTagName("Server");
            Node node1 = nl1.item(0);
            Element eElement1 = (Element) node1;
            String value = eElement1.getAttribute("ESIEnable");
            assertTrue("Check if ESIEnable is set in server.xml", (new Boolean(false)).equals(value));
            value = eElement1.getAttribute("ESIMaxCacheSize");
            assertTrue("Check if ESIMaxCacheSize is set in server.xml", (new Integer(15)).equals(value));
            value = eElement1.getAttribute("ESIInvalidationMonitor");
            assertTrue("Check if ESIInvalidationMonitor is set in server.xml", (new Boolean(true)).equals(value));
            value = eElement1.getAttribute("ESIEnableToPassCookies");
            assertTrue("Check if ESIEnableToPassCookies is set in server.xml", (new Boolean(true)).equals(value));

        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/disableesi-plugin-cfg.xml"));
    }

    @Test
    public void testServerRole() throws Exception {
        setCommonVHostExpectations();
        setXMLGenerateExpectations();
        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockWsResource));
                allowing(mockWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        config.put("serverRole", "BACKUP");

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());
        // and that it contains the loadBalanceWeight value
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
            Element docEle = dom.getDocumentElement();
            //get a nodelist of ServerCluster elements
            NodeList nl = docEle.getElementsByTagName("ServerCluster");
            // we're only expecting one ServerCluser
            assertEquals(1, nl.getLength());
            Node node = nl.item(0);
            Element eElement = (Element) node;
            //we're in the first Server tag of the ServerCluster
            NodeList nlBackupServers = eElement.getElementsByTagName("BackupServers");
            Node nodeBackupServers = nlBackupServers.item(0);
            Element eElementBackupServers = (Element) nodeBackupServers;
            //Counting how many Server tags inside BackupServers
            int nodesInBackupServers = nodeBackupServers.getChildNodes().getLength();
            int backupServersCount = 0;
            for (int i = 0; i < nodesInBackupServers; i++) {
                if (nodeBackupServers.getChildNodes().item(i).getNodeType() == Node.ELEMENT_NODE && nodeBackupServers.getChildNodes().item(i).getNodeName().equals("Server")) {
                    backupServersCount++;
                }
            }
            //We're expecting one server listed in the body of the BackupServers tag
            assertEquals(1, backupServersCount);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
        } catch (SAXException se) {
            se.printStackTrace();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/serverRole-plugin-cfg.xml"));
    }

    @Test
    public void testWebserverPortsWithHostAliases() throws Exception {
        final WsResource mockTempWsResource = context.mock(WsResource.class, "tempResource");
        final WsResource mockFinalWsResource = context.mock(WsResource.class, "finalResource");
        final WebApp mockWebApp = context.mock(WebApp.class, "testApp");
        final WebAppConfiguration mockWebAppConfig = context.mock(WebAppConfiguration.class, "testAppConfig");

        setCommonExpectations();

        // set expectations specific for this test
        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockDefVhostRef }));

                allowing(mockDefVhostRef).getProperty("id");
                will(returnValue("default_host"));
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("*:80", "*:443", "*:49080", "*:49443")));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                // Return default_host with a test application
                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockDefaultHost));

                allowing(mockDefaultHost).getName();
                will(returnValue("default_host"));
                allowing(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList("*:80", "*:443", "*:49080", "*:49443")));
                allowing(mockDefaultHost).getWebApps();
                will(returnIterator(mockWebApp));

                // Mock the WebApp and its configuration
                allowing(mockWebApp).getName();
                will(returnValue("testApp"));
                allowing(mockWebApp).getConfiguration();
                will(returnValue(mockWebAppConfig));
                allowing(mockWebApp).getSessionCookieConfig();
                will(returnValue(null)); // Will use defaults

                // Mock the WebAppConfiguration
                allowing(mockWebAppConfig).getContextRoot();
                will(returnValue("/testApp"));
                allowing(mockWebAppConfig).getVirtualHostName();
                will(returnValue("default_host"));
                allowing(mockWebAppConfig).getDisplayName();
                will(returnValue("Test Application"));

                allowing(mockBundleContext).getAllServiceReferences(null, "(&(enabled=true)(|(httpPort>=1)(httpsPort>=1))(service.pid=Endpoint1))");
                will(returnValue(new ServiceReference<?>[] { mockEndpointInfoRef }));
                allowing(mockEndpointInfoRef).getProperty("id");
                will(returnValue("Endpoint1"));

                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
                one(mockEndpointInfoRef).getProperty("_defaultHostName");
                will(returnValue("localhost"));
                one(mockEndpointInfoRef).getProperty("host");
                will(returnValue("*"));
                one(mockEndpointInfoRef).getProperty("httpPort");
                will(returnValue(1));
                one(mockEndpointInfoRef).getProperty("httpsPort");
                will(returnValue(-1));

                allowing(mockSessionManager).getCloneSeparator();
                will(returnValue(':'));
                allowing(mockSessionManager).getCloneID();
                will(returnValue("ServerCloneID"));
                allowing(mockDefaultHost);
                allowing(mockSessionManager);

                allowing(element).getOwnerDocument();
                will(returnValue(doc));

                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockFinalWsResource));
                allowing(mockLocationAdmin).getServerOutputResource(".plugin-cfg.xml");
                will(returnValue(mockTempWsResource));
                allowing(mockTempWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/.plugin-cfg.xml"))));
                allowing(mockTempWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/.plugin-cfg.xml"))));
                allowing(mockTempWsResource).exists();
                will(returnValue(true));
                allowing(mockFinalWsResource).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockFinalWsResource).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockFinalWsResource).exists();
                will(returnValue(true));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        // Define webserver http and https ports in plugin configuration
        config.put("webserverPort", "49080");
        config.put("webserverSecurePort", "49443");

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());

        // Write the equivalent server.xml for reference
        writeServerXML(testClassesDir + "/webserverPortsWithHostAliases-server.xml",
            "Endpoint1", "*", "50080", "50443",
            new String[]{"default_host", "*:50080", "*:50443", "*:49080", "*:49443"},
            "49080", "49443");

        // and that it contains the webserver ports
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
            fail("Failed to configure XML parser: " + pce.getMessage());
        } catch (SAXException se) {
            se.printStackTrace();
            fail("Failed to parse plugin-cfg.xml (file may be empty or invalid): " + se.getMessage());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail("Failed to read plugin-cfg.xml: " + ioe.getMessage());
        }

        assertNotNull("Document should not be null after parsing", dom);
        Element docEle = dom.getDocumentElement();
        assertNotNull("Document root element should not be null", docEle);

        // Verify the VirtualHostGroup contains the correct ports
        NodeList vhGroups = docEle.getElementsByTagName("VirtualHostGroup");
        assertTrue("Should have at least one VirtualHostGroup", vhGroups.getLength() > 0);

        // Check for VirtualHost entries with the configured ports
        boolean foundHttp = false;
        boolean foundHttps = false;

        for (int i = 0; i < vhGroups.getLength(); i++) {
            Element vhGroup = (Element) vhGroups.item(i);
            NodeList vhosts = vhGroup.getElementsByTagName("VirtualHost");

            for (int j = 0; j < vhosts.getLength(); j++) {
                Element vhost = (Element) vhosts.item(j);
                String name = vhost.getAttribute("Name");

                if (name.contains(":49080")) {
                    foundHttp = true;
                }
                if (name.contains(":49443")) {
                    foundHttps = true;
                }
            }
        }

        assertTrue("Should find VirtualHost entry for HTTP port 49080", foundHttp);
        assertTrue("Should find VirtualHost entry for HTTPS port 49443", foundHttps);
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/explicitDefaultHostFiltersAliases-plugin-cfg.xml"));
    }

    @Test
    public void testWebserverPortsWithCustomVirtualHost() throws Exception {
        final DynamicVirtualHost mockCustomHost = context.mock(DynamicVirtualHost.class, "custom_host");
        final ServiceReference<?> mockCustomVhostRef = context.mock(ServiceReference.class, "custom_hostRef");
        final WsResource mockTempWsResource2 = context.mock(WsResource.class, "tempResource2");
        final WsResource mockFinalWsResource2 = context.mock(WsResource.class, "finalResource2");
        final WebApp mockWebApp2 = context.mock(WebApp.class, "customApp");
        final WebAppConfiguration mockWebAppConfig2 = context.mock(WebAppConfiguration.class, "customAppConfig");

        setCommonExpectations();

        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                // Return both default_host and custom_host
                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockDefVhostRef, mockCustomVhostRef }));

                // default_host configuration with different ports (not matching webserver ports)
                allowing(mockDefVhostRef).getProperty("id");
                will(returnValue("default_host"));
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("*:48080", "*:48443")));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                // custom_host configuration with webserver ports
                allowing(mockCustomVhostRef).getProperty("id");
                will(returnValue("custom_host"));
                allowing(mockCustomVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("*:49080", "*:49443")));
                allowing(mockCustomVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockDefaultHost, mockCustomHost));

                allowing(mockDefaultHost).getName();
                will(returnValue("default_host"));
                allowing(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList("*:48080", "*:48443")));

                allowing(mockCustomHost).getName();
                will(returnValue("custom_host"));
                allowing(mockCustomHost).getAliases();
                will(returnValue(Arrays.asList("*:49080", "*:49443")));

                // Add WebApp to custom_host only (default_host has no apps)
                allowing(mockDefaultHost).getWebApps();
                will(returnIterator()); // Empty - no apps on default_host
                allowing(mockCustomHost).getWebApps();
                will(returnIterator(mockWebApp2));

                // Mock the WebApp and its configuration
                allowing(mockWebApp2).getName();
                will(returnValue("customApp"));
                allowing(mockWebApp2).getConfiguration();
                will(returnValue(mockWebAppConfig2));
                allowing(mockWebApp2).getSessionCookieConfig();
                will(returnValue(null)); // Will use defaults

                // Mock the WebAppConfiguration - app is on custom_host
                allowing(mockWebAppConfig2).getContextRoot();
                will(returnValue("/customApp"));
                allowing(mockWebAppConfig2).getVirtualHostName();
                will(returnValue("custom_host"));
                allowing(mockWebAppConfig2).getDisplayName();
                will(returnValue("Custom Application"));

                allowing(mockBundleContext).getAllServiceReferences(null, "(&(enabled=true)(|(httpPort>=1)(httpsPort>=1))(service.pid=Endpoint1))");
                will(returnValue(new ServiceReference<?>[] { mockEndpointInfoRef }));
                allowing(mockEndpointInfoRef).getProperty("id");
                will(returnValue("Endpoint1"));

                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
                one(mockEndpointInfoRef).getProperty("_defaultHostName");
                will(returnValue("localhost"));
                one(mockEndpointInfoRef).getProperty("host");
                will(returnValue("*"));
                one(mockEndpointInfoRef).getProperty("httpPort");
                will(returnValue(1));
                one(mockEndpointInfoRef).getProperty("httpsPort");
                will(returnValue(-1));

                allowing(mockSessionManager).getCloneSeparator();
                will(returnValue(':'));
                allowing(mockSessionManager).getCloneID();
                will(returnValue("ServerCloneID"));
                allowing(mockDefaultHost);
                allowing(mockCustomHost);
                allowing(mockSessionManager);

                allowing(element).getOwnerDocument();
                will(returnValue(doc));

                allowing(mockLocationAdmin).getServerOutputResource("plugin-cfg.xml");
                will(returnValue(mockFinalWsResource2));
                allowing(mockLocationAdmin).getServerOutputResource(".plugin-cfg.xml");
                will(returnValue(mockTempWsResource2));
                allowing(mockTempWsResource2).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/.plugin-cfg.xml"))));
                allowing(mockTempWsResource2).asFile();
                will(returnValue((new File(testClassesDir + "/.plugin-cfg.xml"))));
                allowing(mockTempWsResource2).exists();
                will(returnValue(true));
                allowing(mockFinalWsResource2).putStream();
                will(returnValue(new FileOutputStream(new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockFinalWsResource2).asFile();
                will(returnValue((new File(testClassesDir + "/plugin-cfg.xml"))));
                allowing(mockFinalWsResource2).exists();
                will(returnValue(true));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // set config values for this test
        // Define webserver http and https ports in plugin configuration
        config.put("webserverPort", "49080");
        config.put("webserverSecurePort", "49443");

        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);
        pluginGen.generateXML("userSpecifiedWebserverLocation", "userSpecifiedServerName", mockWebContainer, mockSessionManager, mockVhostMgr, mockLocationAdmin, false, null);

        // check that the config file was created
        File testfile = new File(testClassesDir + "/plugin-cfg.xml");
        assertTrue(testfile.exists());

        // Write the equivalent server.xml for reference
        writeServerXMLWithMultipleHosts(testClassesDir + "/webserverPortsWithCustomVirtualHost-server.xml",
            "Endpoint1", "*", "50080", "50443",
            new String[]{"default_host", "*:48080", "*:48443"},
            new String[]{"custom_host", "*:49080", "*:49443"},
            "49080", "49443");

        // and that it contains the webserver ports
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document dom = null;
        try {
            //Using factory get an instance of document builder
            DocumentBuilder db = dbf.newDocumentBuilder();
            //parse using builder to get DOM representation of the XML file
            dom = db.parse(testfile);
        } catch (ParserConfigurationException pce) {
            pce.printStackTrace();
            fail("Failed to configure XML parser: " + pce.getMessage());
        } catch (SAXException se) {
            se.printStackTrace();
            fail("Failed to parse plugin-cfg.xml (file may be empty or invalid): " + se.getMessage());
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail("Failed to read plugin-cfg.xml: " + ioe.getMessage());
        }

        assertNotNull("Document should not be null after parsing", dom);
        Element docEle = dom.getDocumentElement();
        assertNotNull("Document root element should not be null", docEle);

        // Verify the VirtualHostGroup contains the correct ports
        NodeList vhGroups = docEle.getElementsByTagName("VirtualHostGroup");
        assertTrue("Should have at least one VirtualHostGroup", vhGroups.getLength() >= 1);

        // Check for VirtualHost entries with the configured ports
        // Since custom_host has the matching aliases, it should have a VirtualHostGroup
        // with the webserver ports (preserving the virtual host structure)
        boolean foundHttp = false;
        boolean foundHttps = false;

        for (int i = 0; i < vhGroups.getLength(); i++) {
            Element vhGroup = (Element) vhGroups.item(i);
            String vhGroupName = vhGroup.getAttribute("Name");
            NodeList vhosts = vhGroup.getElementsByTagName("VirtualHost");

            for (int j = 0; j < vhosts.getLength(); j++) {
                Element vhost = (Element) vhosts.item(j);
                String name = vhost.getAttribute("Name");

                // Check for matching aliases in custom_host (which has the matching ports)
                if (vhGroupName.equals("custom_host")) {
                    if (name.contains(":49080")) {
                        foundHttp = true;
                    }
                    if (name.contains(":49443")) {
                        foundHttps = true;
                    }
                }
            }
        }

        assertTrue("Should find VirtualHost entry for HTTP port 49080 in custom_host", foundHttp);
        assertTrue("Should find VirtualHost entry for HTTPS port 49443 in custom_host", foundHttps);
        // rename generated file to leave a clean space for the next test, but keep the file for debug
        testfile.renameTo(new File(testClassesDir + "/customHostWithExplicitDefaultHost-plugin-cfg.xml"));
    }

    @Test
    public void testCustomHostWithoutDefaultHostGeneratesCatchAll() throws Exception {
        final DynamicVirtualHost mockCustomHost = context.mock(DynamicVirtualHost.class, "custom_host");
        final ServiceReference<?> mockCustomVhostRef = context.mock(ServiceReference.class, "custom_hostRef");

        setCommonExpectations();

        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                // Return only custom_host (NO default_host in config)
                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockCustomVhostRef }));

                // custom_host configuration with ports that DON'T match webserver ports
                allowing(mockCustomVhostRef).getProperty("id");
                will(returnValue("custom_host"));
                allowing(mockCustomVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("myapp.com:8080", "myapp.com:8443")));
                allowing(mockCustomVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                // VirtualHostManager returns both custom_host AND default_host (default_host exists but not in config)
                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockCustomHost, mockDefaultHost));

                allowing(mockCustomHost).getName();
                will(returnValue("custom_host"));
                allowing(mockCustomHost).getAliases();
                will(returnValue(Arrays.asList("myapp.com:8080", "myapp.com:8443")));

                // default_host exists in runtime but has no explicit config
                allowing(mockDefaultHost).getName();
                will(returnValue("default_host"));
                allowing(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList())); // No aliases from runtime
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have both custom_host and generated default_host
        assertEquals("Should have two virtual hosts (custom_host + generated default_host)", 2, virtualHostSet.size());

        // Check custom_host has no aliases (ports don't match)
        List<VHostData> customData = vhostAliasData.get("custom_host");
        assertNotNull("custom_host should be in vhostAliasData", customData);
        assertEquals("custom_host should have no matching aliases", 0, customData.size());

        // Check default_host was generated with wildcards for webserver ports
        List<VHostData> defaultData = vhostAliasData.get("default_host");
        assertNotNull("default_host should be generated in vhostAliasData", defaultData);
        assertEquals("default_host should have 2 wildcard aliases", 2, defaultData.size());
        assertTrue("default_host should contain wildcard for *:9080", defaultData.contains(new VHostData("*", 9080)));
        assertTrue("default_host should contain wildcard for *:9443", defaultData.contains(new VHostData("*", 9443)));

        // Verify comments about generated catchall default_host
        assertTrue("Should have comment about generated HTTP wildcard",
                   outputMgr.checkForStandardOut("No virtual host had an alias matching the webserver http port \\(\\*:9080\\)"));
        assertTrue("Should have comment about generated HTTPS wildcard",
                   outputMgr.checkForStandardOut("No virtual host had an alias matching the webserver https port \\(\\*:9443\\)"));
        assertTrue("Should mention catchall default_host generation",
                   outputMgr.checkForStandardOut("Generated a catchall default_host"));
    }

    @Test
    public void testCustomHostWithoutDefaultHostAllPortsMatched() throws Exception {
        final DynamicVirtualHost mockCustomHost = context.mock(DynamicVirtualHost.class, "custom_host");
        final ServiceReference<?> mockCustomVhostRef = context.mock(ServiceReference.class, "custom_hostRef");

        setCommonExpectations();

        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                // Return only custom_host (NO default_host in config)
                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockCustomVhostRef }));

                // custom_host configuration with ports that MATCH webserver ports
                allowing(mockCustomVhostRef).getProperty("id");
                will(returnValue("custom_host"));
                allowing(mockCustomVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("myapp.com:9080", "myapp.com:9443")));
                allowing(mockCustomVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                // VirtualHostManager returns only custom_host (no default_host in runtime)
                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockCustomHost));

                allowing(mockCustomHost).getName();
                will(returnValue("custom_host"));
                allowing(mockCustomHost).getAliases();
                will(returnValue(Arrays.asList("myapp.com:9080", "myapp.com:9443")));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have only custom_host (no default_host generated)
        assertEquals("Should have only one virtual host (custom_host)", 1, virtualHostSet.size());
        DynamicVirtualHost vh = virtualHostSet.iterator().next();
        assertEquals("Virtual host should be custom_host", "custom_host", vh.getName());

        // Check custom_host has matching aliases (both ports match)
        List<VHostData> customData = vhostAliasData.get("custom_host");
        assertNotNull("custom_host should be in vhostAliasData", customData);
        assertEquals("custom_host should have 2 matching aliases", 2, customData.size());
        assertTrue("custom_host should contain myapp.com:9080", customData.contains(new VHostData("myapp.com", 9080)));
        assertTrue("custom_host should contain myapp.com:9443", customData.contains(new VHostData("myapp.com", 9443)));

        // Check default_host was NOT generated (all ports handled by custom_host)
        List<VHostData> defaultData = vhostAliasData.get("default_host");
        assertTrue("default_host should NOT be generated", defaultData == null || defaultData.isEmpty());

        // Verify comment about all ports being handled
        assertTrue("Should have comment about all ports handled by custom hosts",
                   outputMgr.checkForStandardOut("All webserver ports are handled by custom virtual hosts"));
        assertTrue("Should mention no default_host generated",
                   outputMgr.checkForStandardOut("No default_host VirtualHostGroup was generated"));
    }

    @Test
    public void testExplicitDefaultHostWithPartialPortMatches() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Explicit default_host with only ONE port matching (HTTP matches, HTTPS doesn't)
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("*:9080", "*:8443"))); // 9080 matches, 8443 doesn't match 9443
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                one(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList("*:9080", "*:8443")));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config); // webserver ports are 9080 and 9443
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);
        assertEquals("Should have one element in the virtual host set", 1, virtualHostSet.size());
        assertSame("The default host object should be in the virtual host set", mockDefaultHost, virtualHostSet.iterator().next());

        // default_host should have ONLY the matching alias (*:9080), not the non-matching one (*:8443)
        assertEquals("vhostAliasData should contain default_host", 1, vhostAliasData.size());
        List<VHostData> data = vhostAliasData.get("default_host");
        assertNotNull("There should be a default_host element in the vhostAliasData map", data);
        assertEquals("VHostData should contain only the matching HTTP port", 1, data.size());
        assertTrue("VHostData should contain an alias for *:9080", data.contains(new VHostData("*", 9080)));
        assertFalse("VHostData should NOT contain an alias for *:8443", data.contains(new VHostData("*", 8443)));

        // Verify comment about alias filtering
        assertTrue("Should have comment about filtering aliases",
                   outputMgr.checkForStandardOut("Virtual host aliases have been automatically filtered to include only those matching the configured web server ports"));

        // Verify warning for missing HTTPS port (9443), but NOT for HTTP port (9080 is covered)
        assertFalse("Should NOT have warning about HTTP port (it matches)",
                    outputMgr.checkForStandardOut("No virtual hosts are configured to accept requests from the webserver http port"));
        assertTrue("Should have warning about missing HTTPS port 9443",
                   outputMgr.checkForStandardOut("No virtual hosts are configured to accept requests from the webserver https port \\(\\*:9443\\)"));

        // Should NOT generate wildcards for explicit default_host
        assertFalse("Should NOT generate wildcard for HTTPS (explicit config = no wildcards)",
                    outputMgr.checkForStandardOut("Generated.*wildcard"));
    }

    @Test
    public void testCustomHostWithoutDefaultHostPartialMatches() throws Exception {
        final DynamicVirtualHost mockCustomHost = context.mock(DynamicVirtualHost.class, "custom_host");
        final ServiceReference<?> mockCustomVhostRef = context.mock(ServiceReference.class, "custom_hostRef");

        setCommonExpectations();

        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                // Return only custom_host (NO default_host in config)
                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockCustomVhostRef }));

                // custom_host configuration with only HTTP port matching (HTTPS doesn't match)
                allowing(mockCustomVhostRef).getProperty("id");
                will(returnValue("custom_host"));
                allowing(mockCustomVhostRef).getProperty("hostAlias");
                will(returnValue(Arrays.asList("myapp.com:9080"))); // Only HTTP matches
                allowing(mockCustomVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                // VirtualHostManager returns both custom_host AND default_host (default_host exists in runtime)
                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator(mockCustomHost, mockDefaultHost));

                allowing(mockCustomHost).getName();
                will(returnValue("custom_host"));
                allowing(mockCustomHost).getAliases();
                will(returnValue(Arrays.asList("myapp.com:9080")));

                // default_host exists in runtime but has no explicit config
                allowing(mockDefaultHost).getName();
                will(returnValue("default_host"));
                allowing(mockDefaultHost).getAliases();
                will(returnValue(Arrays.asList())); // No aliases from runtime
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config); // webserver ports are 9080 and 9443
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have both custom_host and generated default_host
        assertEquals("Should have two virtual hosts (custom_host + generated default_host)", 2, virtualHostSet.size());

        // Check custom_host has only HTTP matching alias
        List<VHostData> customData = vhostAliasData.get("custom_host");
        assertNotNull("custom_host should be in vhostAliasData", customData);
        assertEquals("custom_host should have 1 matching alias", 1, customData.size());
        assertTrue("custom_host should contain myapp.com:9080", customData.contains(new VHostData("myapp.com", 9080)));

        // Check default_host was generated with wildcard ONLY for unmatched HTTPS port
        List<VHostData> defaultData = vhostAliasData.get("default_host");
        assertNotNull("default_host should be generated in vhostAliasData", defaultData);
        assertEquals("default_host should have 1 wildcard alias (only HTTPS)", 1, defaultData.size());
        assertTrue("default_host should contain wildcard for *:9443", defaultData.contains(new VHostData("*", 9443)));
        assertFalse("default_host should NOT contain wildcard for *:9080 (already covered)", defaultData.contains(new VHostData("*", 9080)));

        // Verify comment about generated HTTPS wildcard only (HTTP is covered by custom_host)
        assertFalse("Should NOT have comment about HTTP wildcard (covered by custom_host)",
                    outputMgr.checkForStandardOut("No virtual host had an alias matching the webserver http port"));
        assertTrue("Should have comment about generated HTTPS wildcard",
                   outputMgr.checkForStandardOut("No virtual host had an alias matching the webserver https port \\(\\*:9443\\)"));
        assertTrue("Should mention catchall default_host generation",
                   outputMgr.checkForStandardOut("Generated a catchall default_host"));
    }

    @Test
    public void testNoVirtualHostsReturnsEmpty() throws Exception {
        setCommonExpectations();

        context.checking(new Expectations() {
            {
                allowing(mockLocationAdmin).getServerName();
                will(returnValue("SystemProvidedServerName"));

                // Return only default_host in config (catch-all)
                allowing(mockBundleContext).getAllServiceReferences(null, "(&(service.factoryPid=com.ibm.ws.http.virtualhost)(|(enabled=true)(id=default_host)))");
                will(returnValue(new ServiceReference<?>[] { mockDefVhostRef }));

                allowing(mockDefVhostRef).getProperty("id");
                will(returnValue("default_host"));
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null)); // Catch-all
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));

                // VirtualHostManager returns NO virtual hosts (vh == null scenario)
                allowing(mockVhostMgr).getVirtualHosts();
                will(returnIterator()); // Empty iterator - no virtual hosts
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should return empty set
        assertEquals("virtualHostSet should be empty when no virtual hosts exist", 0, virtualHostSet.size());
        assertTrue("vhostAliasData should be empty", vhostAliasData.isEmpty());

        // Verify warning comment about no virtual hosts
        assertTrue("Should have comment about no virtual hosts found",
                   outputMgr.checkForStandardOut("No Virtual Hosts were found"));
        assertTrue("Should suggest verifying applications",
                   outputMgr.checkForStandardOut("Verify that at least one application is defined"));
    }

    @Test
    public void testBothWebserverPortsDisabled() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Catch-all default_host
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));
                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // Disable both ports
        config.put("webserverPort", "-1");
        config.put("webserverSecurePort", "-1");

        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have default_host but with NO aliases (both ports disabled)
        assertEquals("Should have one virtual host", 1, virtualHostSet.size());
        assertSame("Should be default_host", mockDefaultHost, virtualHostSet.iterator().next());

        // vhostAliasData should NOT contain default_host (no aliases generated when both ports disabled)
        assertTrue("vhostAliasData should be empty when both ports disabled",
                   vhostAliasData.isEmpty() || !vhostAliasData.containsKey("default_host") || vhostAliasData.get("default_host").isEmpty());

        // Verify warning comment about disabled ports
        assertTrue("Should have comment about both ports being disabled",
                   outputMgr.checkForStandardOut("Both of the plugin web server ports are disabled"));
        assertTrue("Should mention default_host will be empty",
                   outputMgr.checkForStandardOut("The default_host will be empty"));
    }

    @Test
    public void testOnlyHttpPortEnabled() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Catch-all default_host
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));
                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // Only HTTP enabled, HTTPS disabled
        config.put("webserverSecurePort", "-1");

        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have default_host with only HTTP alias
        assertEquals("Should have one virtual host", 1, virtualHostSet.size());
        List<VHostData> data = vhostAliasData.get("default_host");
        assertNotNull("default_host should be in vhostAliasData", data);
        assertEquals("Should have only one alias (HTTP)", 1, data.size());
        assertTrue("Should contain wildcard for *:9080", data.contains(new VHostData("*", 9080)));
        assertFalse("Should NOT contain HTTPS wildcard", data.contains(new VHostData("*", 9443)));

        // Verify informational comment mentions only HTTP port
        assertTrue("Should mention HTTP port in comment",
                   outputMgr.checkForStandardOut("webserverPort=9080"));
        assertFalse("Should NOT mention HTTPS port in generated aliases comment",
                    outputMgr.checkForStandardOut("webserverSecurePort=9443"));
    }

    @Test
    public void testOnlyHttpsPortEnabled() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Catch-all default_host
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));
                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // Only HTTPS enabled, HTTP disabled
        config.put("webserverPort", "-1");

        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have default_host with only HTTPS alias
        assertEquals("Should have one virtual host", 1, virtualHostSet.size());
        List<VHostData> data = vhostAliasData.get("default_host");
        assertNotNull("default_host should be in vhostAliasData", data);
        assertEquals("Should have only one alias (HTTPS)", 1, data.size());
        assertTrue("Should contain wildcard for *:9443", data.contains(new VHostData("*", 9443)));
        assertFalse("Should NOT contain HTTP wildcard", data.contains(new VHostData("*", 9080)));

        // Verify informational comment mentions only HTTPS port
        assertTrue("Should mention HTTPS port in comment",
                   outputMgr.checkForStandardOut("webserverSecurePort=9443"));
        assertFalse("Should NOT mention HTTP port in generated aliases comment",
                    outputMgr.checkForStandardOut("webserverPort=9080"));
    }

    @Test
    public void testHttpPortNotConfigured() throws Exception {
        setCommonVHostExpectations();
        context.checking(new Expectations() {
            {
                // Catch-all default_host
                allowing(mockDefVhostRef).getProperty("hostAlias");
                will(returnValue(null));
                allowing(mockDefVhostRef).getProperty("allowFromEndpointRef");
                will(returnValue(null));
                allowing(mockEndpointInfo).getEndpointId();
                will(returnValue(mockEndpointInfo.toString()));
            }
        });

        Map<String, Object> config = new HashMap<String, Object>();
        setDefaultConfig(config);
        // HTTP not configured (0), HTTPS enabled
        config.put("webserverPort", "0");

        Map<String, List<VHostData>> vhostAliasData = new HashMap<String, List<VHostData>>();
        PluginGenerator pluginGen = new PluginGenerator(config, mockLocationAdmin, mockBundleContext);

        // Process virtual hosts
        Set<DynamicVirtualHost> virtualHostSet = pluginGen.processVirtualHosts(mockVhostMgr, vhostAliasData, mockEndpointInfo, element);

        // Should have default_host with only HTTPS alias (HTTP=0 means not configured, skip it)
        assertEquals("Should have one virtual host", 1, virtualHostSet.size());
        List<VHostData> data = vhostAliasData.get("default_host");
        assertNotNull("default_host should be in vhostAliasData", data);
        assertEquals("Should have only one alias (HTTPS)", 1, data.size());
        assertTrue("Should contain wildcard for *:9443", data.contains(new VHostData("*", 9443)));
        assertFalse("Should NOT contain HTTP wildcard when port=0", data.contains(new VHostData("*", 0)));

        // Verify informational comment mentions only HTTPS port (HTTP not configured)
        assertTrue("Should mention HTTPS port in comment",
                   outputMgr.checkForStandardOut("webserverSecurePort=9443"));
    }

    // Helper method to write server.xml for single virtual host tests
    private void writeServerXML(String filename, String endpointId, String host, String httpPort, String httpsPort,
                                String[] vhostConfig, String webserverPort, String webserverSecurePort) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(filename);
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<server description=\"Test Server Configuration\">\n\n");
            fw.write("    <!-- Feature Manager -->\n");
            fw.write("    <featureManager>\n");
            fw.write("        <feature>servlet-3.1</feature>\n");
            fw.write("        <feature>webserverPluginUtility-1.0</feature>\n");
            fw.write("        <feature>localConnector-1.0</feature>\n");
            fw.write("    </featureManager>\n\n");
            fw.write("    <!-- HTTP Endpoint configuration -->\n");
            fw.write("    <httpEndpoint id=\"" + endpointId + "\"\n");
            fw.write("                  host=\"" + host + "\"\n");
            fw.write("                  httpPort=\"" + httpPort + "\"\n");
            fw.write("                  httpsPort=\"" + httpsPort + "\" />\n\n");
            fw.write("    <!-- Virtual Host configuration -->\n");
            fw.write("    <virtualHost id=\"" + vhostConfig[0] + "\">\n");
            for (int i = 1; i < vhostConfig.length; i++) {
                fw.write("        <hostAlias>" + vhostConfig[i] + "</hostAlias>\n");
            }
            fw.write("    </virtualHost>\n\n");
            fw.write("    <!-- Plugin Configuration -->\n");
            fw.write("    <pluginConfiguration\n");
            fw.write("        webserverName=\"webserver1\"\n");
            fw.write("        webserverPort=\"" + webserverPort + "\"\n");
            fw.write("        webserverSecurePort=\"" + webserverSecurePort + "\"\n");
            fw.write("        pluginInstallRoot=\"/opt/IBM/WebSphere/Plugins\"\n");
            fw.write("        httpEndpointRef=\"" + endpointId + "\"\n");
            fw.write("        sslKeyringLocation=\"keyringString\"\n");
            fw.write("        sslStashfileLocation=\"stashfileString\"\n");
            fw.write("        serverIOTimeout=\"900\"\n");
            fw.write("        connectTimeout=\"5\"\n");
            fw.write("        extendedHandshake=\"false\"\n");
            fw.write("        waitForContinue=\"false\"\n");
            fw.write("        logDirLocation=\"/opt/IBM/WebSphere/Plugins/logs/webserver1\"\n");
            fw.write("        serverIOTimeoutRetry=\"-1\"\n");
            fw.write("        loadBalanceWeight=\"20\"\n");
            fw.write("        serverRole=\"PRIMARY\"\n");
            fw.write("        ipv6Preferred=\"false\" />\n\n");
            fw.write("    <!-- Test Application to activate virtual hosts -->\n");
            fw.write("    <webApplication id=\"testApp\" location=\"test.war\" contextRoot=\"/\">\n");
            fw.write("        <classloader delegation=\"parentLast\"/>\n");
            fw.write("    </webApplication>\n\n");
            fw.write("</server>\n");
            fw.close();
            System.out.println("Wrote server.xml to: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Helper method to write server.xml for multiple virtual host tests
    private void writeServerXMLWithMultipleHosts(String filename, String endpointId, String host, String httpPort, String httpsPort,
                                                  String[] vhost1Config, String[] vhost2Config,
                                                  String webserverPort, String webserverSecurePort) {
        try {
            java.io.FileWriter fw = new java.io.FileWriter(filename);
            fw.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            fw.write("<server description=\"Test Server Configuration\">\n\n");
            fw.write("    <!-- Feature Manager -->\n");
            fw.write("    <featureManager>\n");
            fw.write("        <feature>servlet-3.1</feature>\n");
            fw.write("        <feature>webserverPluginUtility-1.0</feature>\n");
            fw.write("        <feature>localConnector-1.0</feature>\n");
            fw.write("    </featureManager>\n\n");
            fw.write("    <!-- HTTP Endpoint configuration -->\n");
            fw.write("    <httpEndpoint id=\"" + endpointId + "\"\n");
            fw.write("                  host=\"" + host + "\"\n");
            fw.write("                  httpPort=\"" + httpPort + "\"\n");
            fw.write("                  httpsPort=\"" + httpsPort + "\" />\n\n");
            fw.write("    <!-- Virtual Host configurations -->\n");
            fw.write("    <virtualHost id=\"" + vhost1Config[0] + "\">\n");
            for (int i = 1; i < vhost1Config.length; i++) {
                fw.write("        <hostAlias>" + vhost1Config[i] + "</hostAlias>\n");
            }
            fw.write("    </virtualHost>\n\n");
            fw.write("    <virtualHost id=\"" + vhost2Config[0] + "\">\n");
            for (int i = 1; i < vhost2Config.length; i++) {
                fw.write("        <hostAlias>" + vhost2Config[i] + "</hostAlias>\n");
            }
            fw.write("    </virtualHost>\n\n");
            fw.write("    <!-- Plugin Configuration -->\n");
            fw.write("    <pluginConfiguration\n");
            fw.write("        webserverName=\"webserver1\"\n");
            fw.write("        webserverPort=\"" + webserverPort + "\"\n");
            fw.write("        webserverSecurePort=\"" + webserverSecurePort + "\"\n");
            fw.write("        pluginInstallRoot=\"/opt/IBM/WebSphere/Plugins\"\n");
            fw.write("        httpEndpointRef=\"" + endpointId + "\"\n");
            fw.write("        sslKeyringLocation=\"keyringString\"\n");
            fw.write("        sslStashfileLocation=\"stashfileString\"\n");
            fw.write("        serverIOTimeout=\"900\"\n");
            fw.write("        connectTimeout=\"5\"\n");
            fw.write("        extendedHandshake=\"false\"\n");
            fw.write("        waitForContinue=\"false\"\n");
            fw.write("        logDirLocation=\"/opt/IBM/WebSphere/Plugins/logs/webserver1\"\n");
            fw.write("        serverIOTimeoutRetry=\"-1\"\n");
            fw.write("        loadBalanceWeight=\"20\"\n");
            fw.write("        serverRole=\"PRIMARY\"\n");
            fw.write("        ipv6Preferred=\"false\" />\n\n");
            fw.write("    <!-- Test Application to activate virtual hosts -->\n");
            fw.write("    <webApplication id=\"testApp\" location=\"test.war\" contextRoot=\"/\">\n");
            fw.write("        <classloader delegation=\"parentLast\"/>\n");
            fw.write("    </webApplication>\n\n");
            fw.write("</server>\n");
            fw.close();
            System.out.println("Wrote server.xml to: " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
