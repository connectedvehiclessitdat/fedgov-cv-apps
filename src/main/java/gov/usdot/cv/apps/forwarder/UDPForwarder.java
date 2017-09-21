package gov.usdot.cv.apps.forwarder;

import gov.usdot.cv.apps.forwarder.model.Encoding;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;



public class UDPForwarder {

	
	static final private boolean isDebugOutput = false;
/*
	static
	{
	    Logger rootLogger = Logger.getRootLogger();
	    rootLogger.setLevel(isDebugOutput ? Level.DEBUG : Level.INFO);
//	    rootLogger.addAppender(new ConsoleAppender(new PatternLayout("%-6r [%p] %c - %m%n")));
	}
	
	private static Logger log = Logger.getLogger(UDPForwarder.class);
*/	
	private static MessageDigest messageDigest = null;
	static private final String DIGEST_ALGORITHM_NAME = "SHA-256";
	static private final int HASH_SIZE = 32;
	
	UDPForwarderService udpForwarder= null;
	UDPForwarderServiceEgress udpForwarderEgress= null;

	Thread executeTransportThread = null;
	Thread executeTransportThreadEgress = null;
	
	private int packetsToSend = 1000;
	private int sendPort;
	private int maxPacketSize; 
	
	private static String externInterface = "";
	private static String ingressPort = "";
	private static String egressPort = ""; 
	private static String transportIP = ""; 
	private static String transportIPPool = ""; 
	private ConcurrentHashMap loadBalanceHashMap = new ConcurrentHashMap<String,String>();


	static class DataPacket {
		
		private final int packetId;
		private final byte[] hash;
		private final String data;
		
		public DataPacket(int packetId, String data) {
			this.packetId = packetId;
			this.data = data != null && data.length() > 0 ? data : null;
			this.hash = this.data != null ? messageDigest.digest(data.getBytes()) : null;
		}
		
		public DataPacket(byte[] bytes) {
			this(bytes, 0, bytes != null ? bytes.length : 0);
		}
		
		public DataPacket(byte[] bytes, int offset, int length) {
			ByteBuffer byteBuffer = ByteBuffer.wrap(bytes, offset, length);
			byteBuffer.order(ByteOrder.nativeOrder());
			packetId = byteBuffer.getInt();
			//log.debug("Data ID: " + packetId );
			int size = byteBuffer.getInt();
			//log.debug("Data size: " + size );
			if ( size > 0 ) {
				if ( size + HASH_SIZE > byteBuffer.remaining() ) {
					//log.error(String.format("Packet data size is out of bounds. Packet length: %d, size %d", length, size));
					size = byteBuffer.remaining() - HASH_SIZE;
				}
				byte[] dataBytes = new byte[size];
				byteBuffer.get(dataBytes);
				data = new String(dataBytes);
				hash = new byte[HASH_SIZE];
				byteBuffer.get(hash);
			} else {
				this.data = null;
				this.hash = null;
			}
		}
		
		public final byte[] getBytes() {

			int capacity = (Integer.SIZE/Byte.SIZE)*2;
			int dataSize = 0;
			byte[] dataBytes = null;
			if ( data != null ) {
				dataBytes = data.getBytes();
				dataSize = dataBytes.length;
				capacity +=  dataSize + HASH_SIZE;
			}
			ByteBuffer byteBuffer = ByteBuffer.allocate(capacity);
			byteBuffer.order(ByteOrder.nativeOrder());
			byteBuffer.putInt(packetId);
			byteBuffer.putInt(dataSize);
			if ( dataSize > 0 ) {
				byteBuffer.put(dataBytes);
				byteBuffer.put(hash);
			}
			return byteBuffer.array();
		}
		
		public int getPacketId() {
			return packetId;
		}
		
		public String getData() {
			return data;
		}
		
		public byte[] getHash() {
			return hash;
		}
		
		boolean isHashValid() {
			return data != null ? Arrays.equals(hash, messageDigest.digest(data.getBytes())) : hash == null;
		}
		

	}
	
	public class ExecuteUDPForwarderService implements Runnable {

	    private UDPForwarderService transportService;

	    public ExecuteUDPForwarderService(UDPForwarderService transportService) {
	        this.transportService = transportService;
	    }

	    public void run() {
	    	transportService.execute();
	    }
	}


	public class ExecuteUDPForwarderServiceEgress implements Runnable {

	    private UDPForwarderServiceEgress transportService;

		//transportService.addTargetIP(String str = new String("10101010"));
		//System.out.println("adding targetIP");	


	    public ExecuteUDPForwarderServiceEgress(UDPForwarderServiceEgress transportService) {
	        this.transportService = transportService;
	    }

	    public void run() {
	    	transportService.execute();
	    }
	}





  static void displayInterfaceInformation(NetworkInterface netint)
        throws SocketException
    {
        System.out.println("Display name: " + netint.getDisplayName());
        System.out.println("Hardware address: " + Arrays.toString(netint.getHardwareAddress()));
        Enumeration addrs = netint.getInetAddresses();
        //      for(Object addrsS : Collections.list(addrs)) {
        for(Object addrsS : Collections.list(addrs)) {
            //System.out.println(addrsS.getClass().getName());
            System.out.println(((InetAddress)addrsS).getHostAddress());
            //  System.out.println(addrsS.getHostAddress());

        }

    }



    public static void main(String[] args) {
/*
	String externInterface = "";
	String ingressPort = "";
	String egressPort = ""; 
	String transportIP = ""; 
*/




	try {



	    try {
			//import java.util.Properties;
		Properties props = new Properties();
		InputStream input = null;
		input = new FileInputStream("settings.properties");
		props.load(input);
		externInterface = props.getProperty("ExternalInterface");
		ingressPort = props.getProperty("IngressPort");
		egressPort = props.getProperty("EgressPort");
		transportIP = props.getProperty("TransportIP");
		transportIPPool = props.getProperty("TransportIPPool");
		System.out.println("externInterface");
		System.out.println(externInterface);

	    }
	    catch (Exception e)
		{
		    e.printStackTrace();
		}

	    NetworkInterface nif = NetworkInterface.getByName(externInterface);
	    Enumeration<InetAddress> nifAddresses = nif.getInetAddresses();

	    System.out.println("nifAddresses.toString()");
	    System.out.println(nifAddresses.toString());


	    Enumeration nets = NetworkInterface.getNetworkInterfaces();
	    for (Object netint : Collections.list(nets))
		displayInterfaceInformation((NetworkInterface)netint);


	    UDPForwarder udpTsT = new UDPForwarder();
	    udpTsT.setUp();

	} catch (Throwable e) {
	    e.printStackTrace();
	}

    }

	public void setUp() throws Exception {
		startUdpTransport();
	}

	public void startUdpTransport() {
		try {

			//ingress thread proc
			udpForwarder= new UDPForwarderService();
			udpForwarder.setEncoding(Encoding.Base16);
			//udpForwarder.setListenPort(46751);


			udpForwarder.setListenPort(Integer.parseInt(ingressPort));
			maxPacketSize = udpForwarder.getMaxPacketSize();
			sendPort = udpForwarder.getListenPort();

			executeTransportThread = new Thread(new ExecuteUDPForwarderService(udpForwarder));
			executeTransportThread.start();


			//egress thread proc
			udpForwarderEgress = new UDPForwarderServiceEgress();
			udpForwarderEgress.setEncoding(Encoding.Base16);
			//udpForwarderEgress.setListenPort(46761);
			udpForwarderEgress.setListenPort(Integer.parseInt(egressPort));
			maxPacketSize = udpForwarderEgress.getMaxPacketSize();
	
			udpForwarderEgress.addTargetIP("10101010");
			System.out.println("adding targetIP");	


			executeTransportThreadEgress = new Thread(new ExecuteUDPForwarderServiceEgress(udpForwarderEgress));
			executeTransportThreadEgress.start();



		} catch (Exception ex ) {
			//log.error("Couldn't execute UDP transport service", ex);
		}
	}
	
	private void stopUdpTransport() {
		if ( udpForwarder != null ) {
			try {
				udpForwarder.terminate();
				udpForwarder = null;
			} catch (Exception ex ) {
				//log.error("Couldn't terminate UDP transport service", ex);
			}			
		}
	}
}
