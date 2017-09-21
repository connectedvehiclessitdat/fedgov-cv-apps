package gov.usdot.cv.apps.forwarder;


import gov.usdot.cv.apps.forwarder.model.Encoding;
import gov.usdot.cv.common.inet.InetPacket;
import gov.usdot.cv.common.inet.InetPacketSender;
import gov.usdot.cv.common.inet.InetPoint;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;

import org.apache.log4j.Logger;

public class UDPMessageForwarderProcessor implements Runnable {

    private static final Logger log = Logger.getLogger(UDPMessageForwarderProcessor.class);


    private static MessageDigest messageDigest = null;

    Thread executeTransportThread = null;

    private int sendPort;
    private int maxPacketSize;

    private boolean inbound = true;
    private long started;
    private DatagramPacket packet;
    private UDPForwarderService receiver;
    private UDPForwarderServiceEgress receiverE;
    private Encoding encoding = Encoding.None; 
    private ArrayList routingParams = null;
    private String targetIPpoolSelection = null;
    private boolean loadBalancer = false;


    public UDPMessageForwarderProcessor(DatagramPacket packet, UDPForwarderService receiver) {
	this.packet = packet;
	this.receiver = receiver;
    }

    public UDPMessageForwarderProcessor(DatagramPacket packet, UDPForwarderServiceEgress receiver) {
	this.packet = packet;
	this.receiverE = receiver;
    }




    public UDPMessageForwarderProcessor(DatagramPacket packet, UDPForwarderService receiver, ArrayList routingParams) {
/*
    Logger logger = null;
        ForLogger tlg;
        try {
            logger = ForLogger.getAppLogger("For.log");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
*/
	this.packet = packet;
	this.receiver = receiver;
	inbound = true;
	System.out.println("Ingress");
	System.out.println("routingParams.toString()");
	System.out.println(routingParams.toString());
	System.out.println(routingParams.get(0)+"");
	System.out.println(routingParams.get(1)+"");
	targetIPpoolSelection = (routingParams.get(1)+"");
	//loadBalancer = Boolean.parseBoolean(routingParams.get(2)+"");

    }

    public UDPMessageForwarderProcessor(DatagramPacket packet, UDPForwarderServiceEgress receiver, ArrayList routingParams) {
/*
    Logger logger = null;
        ForLogger tlg;
        try {
            logger = ForLogger.getAppLogger("For.log");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
*/
	this.packet = packet;
	this.receiverE = receiver;
	inbound = false;
	System.out.println("Egress");
	System.out.println("routingParams.toString()");
	System.out.println(routingParams.toString());
	System.out.println(routingParams.get(0)+"");
	System.out.println(routingParams.get(1)+"");
	targetIPpoolSelection = (routingParams.get(1)+"");
	//loadBalancer = Boolean.parseBoolean(routingParams.get(2)+"");
	
    }

    public void run() {
/*
    Logger logger = null;
        ForLogger tlg;
        try {
            logger = ForLogger.getAppLogger("For.log");
        } catch (IOException e1) {
            e1.printStackTrace();
        }

*/

	try {



	    if(inbound) {
		//inbound
		sendUdpPacketWithMeta(packet);
	        log.info("inbound packet");
		//outbound
	    } else {
		sendUdpPacketWithMetaEgress(packet);
	        log.info("outbound packet");
	    }


	} catch (Exception ex ) {
	    //logger.error("Caught exception while processing a packet", ex );
	}
    }


    //inbound path to transport
    private void sendUdpPacketWithMeta(DatagramPacket datagramPacket) {

/*
    Logger logger = null;
        ForLogger tlg;
        try {
            logger = ForLogger.getAppLogger("For.log");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
*/

	try {

/*
	    System.out.println("clean address");
	    System.out.println(datagramPacket.getAddress().toString().replace("/",""));
	    System.out.println(datagramPacket.getAddress());
	    System.out.println(datagramPacket.getPort());
	    System.out.println(datagramPacket.getData());
	    System.out.println(datagramPacket.getLength());
	    System.out.println(datagramPacket.getOffset());
*/

	    int OriginatingHostPort = datagramPacket.getPort();
	    String OriginatingHost = (datagramPacket.getAddress().toString().replace("/",""));

	    byte[] PAYLOAD = new byte[datagramPacket.getLength()];
	    System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), PAYLOAD, 0, datagramPacket.getLength());

	    String TRANSPORT_HOST = "204.236.245.193";
	    int TRANSPORT_PORT = 46751; 
	    TRANSPORT_HOST = targetIPpoolSelection;

	  //  TRANSPORT_PORT = Integer.parseInt((String)routingParams.get(0));
	  //  TRANSPORT_HOST = routingParams.get(1).toString();
    
	    //logger.debug("original src"+OriginatingHost+":"+OriginatingHostPort);
	    System.out.println("original src"+OriginatingHost+":"+OriginatingHostPort);
	    System.out.println("transport"+TRANSPORT_HOST+":"+TRANSPORT_PORT);
		System.out.println("targetIPpoolSelection");
		System.out.println(targetIPpoolSelection);


	    InetPoint transport = new InetPoint(getAddressBytes( TRANSPORT_HOST ), TRANSPORT_PORT);
	    InetPacketSender sender = new InetPacketSender(transport);
	    DatagramPacket packet = new DatagramPacket(PAYLOAD, PAYLOAD.length, InetAddress.getByName(OriginatingHost), OriginatingHostPort);
	    sender.forward(packet);


	} catch (Exception ex) {
	    ex.printStackTrace();
	    //logger.error("Caught exception while processing a packet", ex );
	}
    }




    //outbound return path to external nodes 
    private void sendUdpPacketWithMetaEgress(DatagramPacket datagramPacket) {
/*
    Logger logger = null;
        ForLogger tlg;
        try {
            logger = ForLogger.getAppLogger("For.log");
        } catch (IOException e1) {
            e1.printStackTrace();
        }

*/

	try {


///
/*
	    byte[] PAYLOAD = new byte[datagramPacket.getLength()];
	    System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), PAYLOAD, 0, datagramPacket.getLength());
	    DatagramPacket packetOutboundTMP = new DatagramPacket(PAYLOAD, PAYLOAD.length, InetAddress.getByName("54.237.53.5"), 46751);
*/
////

	    InetPacket p = new InetPacket(datagramPacket);
	    //InetPacket p = new InetPacket(packetOutboundTMP);

	    InetPoint point = p.getPoint();
	    byte[] srcAddress = point.address;

	    System.out.println("point.getInetAddress()");
	    System.out.println(	point.getInetAddress());
	    System.out.println(	point.port);
	    System.out.println(	point.address);
	    System.out.println(	point.forward);
	    System.out.println("point.getInetAddress()");

	    byte[] unbundledPayload = p.getPayload();

	    //byte[] PAYLOAD = new byte[datagramPacket.getLength()];
	    //System.arraycopy(datagramPacket.getData(), datagramPacket.getOffset(), PAYLOAD, 0, datagramPacket.getLength());
	    //DatagramPacket packetOutbound = new DatagramPacket(PAYLOAD, PAYLOAD.length, InetAddress.getByName(hostname), CLIENT_PORT);
	    //DatagramPacket packetOutbound = new DatagramPacket(PAYLOAD, PAYLOAD.length, point.getInetAddress(), point.port);
	    //DatagramPacket packetOutbound = new DatagramPacket(PAYLOAD, PAYLOAD.length, InetAddress.getByName("54.237.53.5"), point.port);
	    DatagramPacket packetOutbound = new DatagramPacket(unbundledPayload, unbundledPayload.length, point.getInetAddress(), point.port);
	    	//logger.debug(point.getInetAddress()+"egress point address");
		System.out.println("this is egress IP address");
	    	//logger.debug(point.port+"egress point port");
		System.out.println("this is egress PORT");
		System.out.println(point.port);

	    DatagramSocket socket = null;
	    socket = new DatagramSocket();

	    	//logger.debug("sending");
	    socket.send(packetOutbound);
	    	//logger.debug("sent");

	
	} catch (Exception ex) {
	    //logger.error("Caught exception while processing a packet", ex );
	    ex.printStackTrace();
	}

	//END new inbound to transport code

    }


    private static byte[] getAddressBytes(String host) throws UnknownHostException {
	return InetAddress.getByName( host ).getAddress();
    }

/*
    private void sendUdpPacketNoMeta(DatagramPacket datagramPacket) {
	InetAddress ipAddress = null;
	try {
	    //ipAddress = Inet6Address.getByName("localhost");
	    //ipAddress = Inet6Address.getByName("54.82.140.15");
	    ipAddress = Inet6Address.getByName("54.87.129.17");
	} catch (UnknownHostException ex) {
	    System.out.println(ipAddress);
	    //log.error("Couldn't get local host by name", ex);
	    //assertFalse(true);
	}

	DatagramSocket socket = null;
	started = System.currentTimeMillis();
	try {
	    System.out.println("dgs ");
	    socket = new DatagramSocket();
	    System.out.println("dgsB ");
	    System.out.println("dgsC ");
	    byte[] packet = new byte[65535];
	    byte[] packetOut = new byte[65535];

	    datagramPacket.setAddress(ipAddress);
	    datagramPacket.setPort(46761);
	    socket.send(datagramPacket);
	    System.out.println("dgsE ");
	    try {
		Thread.sleep(10);
	    } catch (InterruptedException igonore) {
		System.out.println("send exception ");
	    }
	    // }
	} catch (IOException ex) {
	    //log.error("Couldn't send datagram packet", ex);
	    System.out.println("io send exception ");
	    ex.printStackTrace();
	} finally {
	    if ( socket != null ) {
		if ( !socket.isClosed() )
		    socket.close();
		socket = null;
	    }
	}
    }
*/


}
