package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.*;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import javax.sip.address.SipURI;
import javax.sip.message.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.WebArchiveUtil;

/**
 * Tests for the Dial forking
 * Created by gvagenas on 12/19/15.
 */
@RunWith(Arquillian.class)
@Category(value={FeatureAltTests.class, ParallelClassTests.class})
public class DialForkAnswerDelayTest {

    private final static Logger logger = Logger.getLogger(CallLifecycleTest.class.getName());

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
    private static final String body = new String(bytes);

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;
    private static SipStackTool tool5;

    // Bob is a simple SIP Client. Will not register with Restcomm
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    // Henrique is a simple SIP Client. Will not register with Restcomm
    private SipStack henriqueSipStack;
    private SipPhone henriquePhone;
    private static String henriquePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String henriqueContact = "sip:henrique@127.0.0.1:" + henriquePort;

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private static String georgePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String georgeContact = "sip:+131313@127.0.0.1:" + georgePort;

    // Fotini is a simple SIP Client. Will not register with Restcomm
    private SipStack fotiniSipStack;
    private SipPhone fotiniPhone;
    private static String fotiniPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String fotiniContact = "sip:fotini@127.0.0.1:" + fotiniPort;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private String dialAliceRcmlWithPlay;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialForkAnswerDelay1");
        tool2 = new SipStackTool("DialForkAnswerDelay2");
        tool3 = new SipStackTool("DialForkAnswerDelay3");
        tool4 = new SipStackTool("DialForkAnswerDelay4");
        tool5 = new SipStackTool("DialForkAnswerDelay5");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
        }
        if (System.getProperty("arquillian_http_port") != null) {
            restcommHTTPPort = Integer.valueOf(System.getProperty("arquillian_http_port"));
        }
    }


    @Before
    public void before() throws Exception {
        dialAliceRcmlWithPlay = "<Response><Play>" + deploymentUrl.toString() + "/audio/demo-prompt.wav</Play><Dial><Client>alice</Client></Dial></Response>";

        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);

        henriqueSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", henriquePort, restcommContact);
        henriquePhone = henriqueSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, henriqueContact);

        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort, restcommContact);
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContact);

        fotiniSipStack = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", fotiniPort, restcommContact);
        fotiniPhone = fotiniSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, fotiniContact);
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }

        if (alicePhone != null) {
            alicePhone.dispose();
        }
        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }

        if (henriqueSipStack != null) {
            henriqueSipStack.dispose();
        }
        if (henriquePhone != null) {
            henriquePhone.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }

        if (fotiniPhone != null) {
            fotiniPhone.dispose();
        }
        if (fotiniSipStack != null) {
            fotiniSipStack.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    private String dialFork = "<Response><Dial><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number>+131313</Number></Dial></Response>";

    @Test
    public synchronized void testDialForkNoAnswerButHenrique() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        //Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

  //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test
    public synchronized void testDialForkNoAnswerButFromAliceClient() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 600));

        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());

        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(henriqueCall.listenForCancel());
        assertTrue(georgeCall.listenForCancel());

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(30 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        aliceCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();
        assertTrue(bobCall.waitForAnswer(5000));

        assertTrue(aliceCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject cdrs = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        JsonObject jsonObj = cdr.getAsJsonObject();
        String status = jsonObj.get("status").getAsString();
        System.out.println("%%%%Status : "+status);
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    @Test
    public synchronized void testDialForkWithBusy() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        assertTrue(georgeCall.sendIncomingCallResponse(486, "Busy Here-George", 3600));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        assertTrue(aliceCall.sendIncomingCallResponse(486, "Busy Here-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));
        assertTrue(alicePhone.unregister(aliceContact, 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        logger.info("%%%% CallSID: "+callSid+" Status : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    @Test
    public synchronized void testDialForkWithDecline() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }


        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //int DECLINE = 603;
        assertTrue(georgeCall.sendIncomingCallResponse(Response.DECLINE, "Busy Here-George", 3600));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        assertTrue(aliceCall.sendIncomingCallResponse(Response.DECLINE, "Busy Here-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));
        assertTrue(alicePhone.unregister(aliceContact, 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        logger.info("%%%% CallSID: "+callSid+" Status : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkWithTimeout = "<Response><Dial timeout=\"2\"><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number>+131313</Number></Dial></Response>";

    @Test
    public synchronized void testDialForkNoAnswer() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TIMEOUT, bobCall.getLastReceivedResponse().getStatusCode());

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(180, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(aliceCancelTransaction);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);


        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(1000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    @Test
    @Category(WithInMinsTests.class)
    public synchronized void testDialForkNoAnswerAndNoResponseFromAlice() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TIMEOUT, bobCall.getLastReceivedResponse().getStatusCode());

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(180, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        aliceCall.disposeNoBye();

        //Alice will send no response at all
//        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
//        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);

        Thread.sleep(1000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);

        Thread.sleep(60000);

        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        Thread.sleep(5000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkWithTimeout15 = "<Response><Dial timeout=\"15\"><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number>+131313</Number></Dial></Response>";

    @Test
    public synchronized void testDialForkNoAnswerWith183() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout15)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(183, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TIMEOUT, bobCall.getLastReceivedResponse().getStatusCode());

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 0);
        assertTrue(liveCallsArraySize == 0);

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkWithActionUrl = "<Response><Dial timeLimit=\"1000\" timeout=\"2\" action=\"http://127.0.0.1:" + mockPort +"/test\">" +
            "<Number>+131313</Number><Uri>sip:henrique@127.0.0.1:" + henriquePort + "</Uri><Client>alice</Client></Dial></Response>";
    private String rcmlToReturn = "<Response><Dial timeout=\"50\"><Uri>sip:fotini@127.0.0.1:" + fotiniPort + "</Uri></Dial></Response>";
    //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test //TODO Fails when the whole test class runs but Passes when run individually
    @Category(UnstableTests.class)
    public synchronized void testDialForkNoAnswerExecuteRCML_ReturnedFromActionURL() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithActionUrl)));

        stubFor(post(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlToReturn)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(180, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and RCML will move to the next verb to call Fotini

        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        Thread.sleep(1000);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

//        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        //There will be the initial call from Bob and the new call to Fotini
//        logger.info("&&&& LiveCalls: "+liveCalls);
//        logger.info("&&&& LiveCallsArraySize: "+liveCallsArraySize);
//        assertTrue(liveCalls == 2);
//        assertTrue(liveCallsArraySize == 2);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        //Now Fotini should receive a call
        assertTrue(fotiniCall.waitForIncomingCall(30 * 1000));
        assertTrue(fotiniCall.sendIncomingCallResponse(100, "Trying-Fotini", 600));
        assertTrue(fotiniCall.sendIncomingCallResponse(180, "Ringing-Fotini", 600));
        String receivedBody = new String(fotiniCall.getLastReceivedRequest().getRawContent());
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.OK, "OK-Fotini", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(fotiniCall.waitForAck(5000));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));
        fotiniCall.listenForDisconnect();

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);

        Thread.sleep(2000);

        // hangup.

        assertTrue(bobCall.disconnect());

        assertTrue(fotiniCall.waitForDisconnect(50 * 1000));

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialAliceRcml = "<Response><Dial><Client>alice</Client></Dial></Response>";

    @Test //TODO Fails when the whole test class runs but Passes when run individually
    @Category(UnstableTests.class)
    public void testDialClientAlice() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());
        assertTrue(aliceCall.waitForAck(5000));

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    @Test
    public void testDialClientAliceWithPlay() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcmlWithPlay)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialSequential = "<Response><Dial timeout=\"5\"><Sip>sip:nonexistent@127.0.0.1:5566</Sip></Dial><Dial timeout=\"5\"><Sip>sip:nonexistent2@127.0.0.1:6655</Sip></Dial><Dial><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";

    @Test
    public synchronized void testDialSequentialFirstCallTimeouts() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSequential)));

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        Thread.sleep(9000);

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        //Wait to cancel the other branches
        Thread.sleep(2000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkTwoSipUrisRcml = "<Response><Dial><Sip>sip:fotini@127.0.0.1:" + fotiniPort + "</Sip><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";
    @Test
    public synchronized void testDialForkWithServerErrorReponse() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkTwoSipUrisRcml)));

        //Prepare Fotini to receive call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }


        assertTrue(fotiniCall.waitForIncomingCall(30 * 1000));
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.TRYING, "Trying-Fotini", 600));
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.SERVER_INTERNAL_ERROR, "Fotini-Internal-Server-Error", 600));
        assertTrue(fotiniCall.waitForAck(5000));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.
        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));
        assertTrue(henriqueCall.respondToDisconnect());

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        logger.info("%%%% CallSID: "+callSid+" Status : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }
    @Deployment(name = "DialForkAnswerDelayTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String,String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5070", String.valueOf(georgePort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));
        replacements.put("5092", String.valueOf(henriquePort));
        replacements.put("5093", String.valueOf(fotiniPort));

        List<String> resources = new ArrayList(Arrays.asList("hello-play.xml"));
        return WebArchiveUtil.createWebArchiveNoGw("restcomm-delay.xml",
                "restcomm.script_dialForkTest",resources, replacements);
    }
}
