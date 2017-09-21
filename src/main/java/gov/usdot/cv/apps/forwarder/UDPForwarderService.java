package gov.usdot.cv.apps.forwarder;

//package gov.usdot.cv;

import gov.usdot.cv.apps.forwarder.model.Encoding;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class UDPForwarderService {





    //	private static final Logger log = Logger.getLogger(UDPForwarderService.class);
    	//private static final Logger logger =  ForLogger.getAppLogger("For.log");
	
    final private int DEFAULT_MAX_PACKET_SIZE = 65535;	
    final private int DEFAULT_LISTEN_PORT     = 46751;
    final private long TERMINATE_TIMEOUT_SECONDS = 15;	// shutdown timeout in seconds
	
    final private String ENCODE_CONTENT_DEFAULT = "None";
	
    private int listenPort    = DEFAULT_LISTEN_PORT;
    private int maxPacketSize = DEFAULT_MAX_PACKET_SIZE;
    private UDPMessageForwarderProcessor messageProcessorCtor = null;

    //    private String targetIP = null;

    private Encoding contentEncoding = Encoding.None;

    private ExecutorService executorSvc = null;
	
    private DatagramSocket socket = null;

    private ConcurrentHashMap loadBalanceHashMap = new ConcurrentHashMap<String,String>();

	
    private static String externInterface = "";
    private static String ingressPort = "";
    private static String egressPort = "";
    private static String transportIP = "";
    private static String transportIPPool = "";
    private static String loadBalancer = "";


	
    private String targetIPpoolSelection = null;
    private String targetIP = null;



    public int getListenPort() {
	return listenPort;
    }
	
    public void setListenPort(int listenPort) {
	this.listenPort = listenPort;
    }

    public void addTargetIP(String targetIP) {
        this.targetIP = targetIP;
    }
	
    public int getMaxPacketSize() {
	return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
	this.maxPacketSize = maxPacketSize;
    }

    public Encoding getEncoding() {
	return this.contentEncoding;
    }
	
    public void setEncoding(Encoding contentEncoding) {
	this.contentEncoding = contentEncoding;
    }
	
    public String getEncodeContent() {
	return this.contentEncoding.toString();
    }

    public void setContentEncoding(String contentEncoding) {
	try {
	    this.contentEncoding = Encoding.valueOf(contentEncoding);
	}
	catch (IllegalArgumentException e) {
	    //log.error(String.format("Unexpected content encoding type '%s'", contentEncoding) );
	    this.contentEncoding = Encoding.None;
	}
    }

    public String makeMD5(String input)
	throws NoSuchAlgorithmException
    {
	MessageDigest md = MessageDigest.getInstance("MD5");
	md.reset();
	byte[] buffer = input.getBytes();
	md.update(buffer);
	byte[] digest = md.digest();

	String hexStr = "";
	for (int i = 0; i < digest.length; i++) {
	    hexStr +=  Integer.toString( ( digest[i] & 0xff ) + 0x100, 16).substring( 1 );
	}
	return hexStr;
    }

    public void execute() {
	//log.info( "Starting UDPTransportService" );
/*
        Logger logger = null;
        ForLogger tlg;
        try {
        logger = ForLogger.getAppLogger("For.log");
        } catch (IOException e1) {
            e1.printStackTrace();
        }
*/		
	if ( executorSvc == null )
	    executorSvc = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

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
                loadBalancer = props.getProperty("LoadBalancer");
                System.out.println("externInterface");
                System.out.println(externInterface);
	 //logger.info("reading properties");


            }
            catch (Exception e)
                {
                    e.printStackTrace();
                }



	    DatagramPacket datagramPacketA = new DatagramPacket(new byte[maxPacketSize], maxPacketSize);


	    //messageProcessorCtor = new ForSimpleUDPMessageProcessor(new DatagramPacket(),new ForSimpleUDPMessageProcessor());
	    //messageProcessorCtor = new ForSimpleUDPMessageProcessor(datagramPacketA, new ForSimpleUDPMessageProcessor());
            //    messageProcessorCtor = new ForSimpleUDPMessageProcessor(datagramPacketA,new UDPTransportService() );

	  
	    socket = new DatagramSocket(listenPort);
	
	    System.out.println(transportIPPool.split(","));
	    String[] ipPool = (transportIPPool.split(","));

	    //load up target pool
	    int poolCount = 0;
	    for(String targetIP : ipPool) {
		poolCount++;
		System.out.println(targetIP);
	    }
		
 

	
	
	    DatagramPacket datagramPacket = null;
	    while ( !executorSvc.isShutdown() ) {
		datagramPacket = new DatagramPacket(new byte[maxPacketSize], maxPacketSize);
	    	ArrayList routingList = new ArrayList<String>(); 
		try {
		    try {
	//	disable randomIP	Random rand = new Random();
			//int poolSelection = rand.nextInt(3);
// disable randomIP			int poolSelection = rand.nextInt(poolCount);
		//	System.out.println("ipPool[poolSelection]");
		//	System.out.println(ipPool[poolSelection]);
//			targetIPpoolSelection = ipPool[poolSelection];


			socket.receive(datagramPacket);
		    } catch ( SocketException ex ) {
			if ( socket != null &&  !socket.isClosed() )
			    //log.error(String.format("Socket exception receiving packet on port %d", listenPort), ex);
			    break;
		    }

		    System.out.println("datagramPacket.toString()");
		    System.out.println(datagramPacket.toString());
		    System.out.println(datagramPacket.getSocketAddress().toString());
		    String srcAddress = (datagramPacket.getAddress().toString());
		    String srcPort = (String.valueOf(datagramPacket.getPort()));
		

				
		    // routingList.add(srcAddress);
		    // routingList.add(srcPort);
	
		    routingList.clear();	
		    routingList.add(ingressPort);	
		    //##routingList.add(transportIP);	


//

			//String targetModulus = ""; 
			int targetModulus = 0; 
			targetIP="";
			

			//targetModulus  = String.valueOf((srcAddress).hashCode() % 3);
			//targetModulus  = ((srcAddress).hashCode() % 3);

			//targetModulus  = ((srcAddress).hashCode() % 3);

			int hashValue = (((srcAddress).hashCode() % 3));
			//System.out.println(abs(hashValue));
			targetModulus = java.lang.Math.abs(hashValue);


			
			//targetIP = (String)loadBalanceHashMap.get(targetModulus);
			targetIP = ipPool[targetModulus];


		    //routingList.add(targetIPpoolSelection);	
		    routingList.add(targetIP);	
		    routingList.add(loadBalancer);	


		System.out.println("routingList");
		System.out.println(routingList);
		System.out.println(targetIP);
		System.out.println(targetModulus);
		System.out.println("routingList");


	


/*
		    String srcSocketHash = "";
		    try {			
			System.out.println("srcSocket");
			//ignore srcPort for hash to favor session persistency over load distribution

			//srcSocketHash = makeMD5(srcAddress+srcPort);
			System.out.println("hashCode int");
			System.out.println(srcAddress.hashCode());
			System.out.println((srcAddress).hashCode() % 3);
			targetModulus  = String.valueOf((srcAddress).hashCode() % 3);
			System.out.println("hashCode int");

			srcSocketHash = makeMD5(srcAddress);
			System.out.println("srcSocketHash");
			System.out.println(srcSocketHash);
		    }
		    catch (Exception e)
			{
			    e.printStackTrace();
			}


		    int muxInt = 0;


		    //ConcurrentHashMap loadBalanceHashMap = new ConcurrentHashMap<String,String>();
	
		    //if(loadBalanceHashMap.get(srcSocketHash)!=null) {
		    if(loadBalanceHashMap.get(targetModulus)!=null) {
			//targetIP = (String)loadBalanceHashMap.get(srcSocketHash);
			targetIP = (String)loadBalanceHashMap.get(targetModulus);
			System.out.println("NULL selected targetIP "+targetIP);
		    } else {
			//System.out.println("ipPool[poolSelection]");
			//System.out.println(ipPool[poolSelection]);
			//targetIPpoolSelection = ipPool[poolSelection];

			targetIP = targetIPpoolSelection;
			System.out.println("NOT NULLselected targetIP "+targetIP);
			//loadBalanceHashMap.put(srcSocketHash,"54.87.129.17");
			//loadBalanceHashMap.put(srcSocketHash,targetIPpoolSelection);
			loadBalanceHashMap.put(targetModulus,targetIPpoolSelection);
		    }

		    System.out.println(loadBalanceHashMap.toString());

			targetIPpoolSelection = ipPool[poolSelection];
		

		    System.out.println(loadBalanceHashMap.get("srcSocketHash retrieval"));	
		    System.out.println(loadBalanceHashMap.get(srcSocketHash));	
		    System.out.println(loadBalanceHashMap.get("srcSocketHash retrieval"));	

*/

		    boolean isIPv6 = (datagramPacket.getAddress()) instanceof Inet6Address;
		    System.out.println(isIPv6);

		    boolean isIPv4 = (datagramPacket.getAddress()) instanceof Inet4Address;
		    System.out.println(isIPv4);


		    messageProcessorCtor = new UDPMessageForwarderProcessor(datagramPacket,new UDPForwarderService(), routingList );
		    //@@    messageProcessorCtor = new UDPMessageForwarderProcessor(datagramPacket,new UDPForwarderService() );



		    //messageProcessorCtor = new ForSimpleUDPMessageProcessor(datagramPacket,this );
		    //Runnable messageProcessor = createMessageProcessorInstance(datagramPacket, this);
         
		    //    Runnable messageProcessor = new ForSimpleUDPMessageProcessor(datagramPacketA,this );

		    if ( messageProcessorCtor != null ) 
			executorSvc.submit(messageProcessorCtor);
		    //if ( messageProcessor != null )
		    //	executorSvc.submit(messageProcessor);
		} catch (IOException ex) {
		    //log.error(String.format("Error receiving packet on port %d", listenPort), ex);
		}
	    }
			
	    try {
		//log.info(String.format("Awaiting worker threads termination with timeout %d sec", TERMINATE_TIMEOUT_SECONDS ));
		executorSvc.awaitTermination( TERMINATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	    } catch (InterruptedException ignored) {
		executorSvc.shutdownNow();
		try {
		    //log.info(String.format("Executing shutdown now with timeout %d sec", TERMINATE_TIMEOUT_SECONDS ));
		    executorSvc.awaitTermination( TERMINATE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (InterruptedException ignored2) {
		}
	    }
			
	} catch (SocketException ex) {
	    //log.error(String.format("Could not start datagram listener on port %d due to socket exception", listenPort), ex);
	} catch (SecurityException ex) {
	    //log.error(String.format("Could not start datagram listener on port %d due to security exception", listenPort), ex);
	    //log.error(String.format("Message processor class '%s' does not have adeqate constructor", messageProcessorClass), ex);
	} finally {
	    if ( socket != null && !socket.isClosed() )	{
		socket.close();
		socket = null;
	    }
	}

    }

    public void terminate() {
	executorSvc.shutdown();
	if ( socket != null && !socket.isClosed() )
	    socket.close();
    }
	

////

/*
final class ForLogger {

    //public static Logger getAppLogger(String path) throws IOException {
    public Logger getAppLogger(String path) throws IOException {
        Logger rootLogger = Logger.getLogger(ForLogger.class.getCanonicalName());
        Layout pattern = new PatternLayout("%d{MM-dd-yyyy HH:mm:ss,SSS} %m%n");
        // Layout pattern = new PatternLayout("%d{MM-dd-yyyy HH:mm:ss,SSS} %-5p %c:%m%n");
        //Layout pattern = new PatternLayout("%d{MM-dd-yyyy HH:mm:ss} %-5p :%m%n");
        Appender newAppender = null;
        newAppender = new FileAppender(pattern, path, true);
        rootLogger.setAdditivity(false);
        rootLogger.setLevel(Level.INFO);
        rootLogger.addAppender(newAppender);
        return rootLogger;

    }

}

*/

////




}
