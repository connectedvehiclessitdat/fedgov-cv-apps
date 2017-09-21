package gov.usdot.cv.apps.poller.executor;

import gov.usdot.asn1.generated.j2735.semi.DistributionType;
import gov.usdot.asn1.generated.j2735.semi.GroupID;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.cv.apps.poller.PollerException;
import gov.usdot.cv.apps.poller.dialog.DataExchange;
import gov.usdot.cv.apps.poller.dialog.DialogException;
import gov.usdot.cv.apps.poller.dialog.IntersectionSitDataExchange;
import gov.usdot.cv.apps.poller.dialog.NonRepudiation;
import gov.usdot.cv.apps.poller.dialog.RSUAdvSitDataExchange;
import gov.usdot.cv.apps.poller.util.J2735CoderHolder;
import gov.usdot.cv.common.asn1.GroupIDHelper;
import gov.usdot.cv.common.dialog.TrustEstablishment;
import gov.usdot.cv.security.SecureConfig;
import gov.usdot.cv.security.crypto.CryptoProvider;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

public abstract class DialogExecutor {
	protected static final int MAX_PACKET_SIZE = 65535;
	protected static final int MAX_TIME_TO_COLLECT_BUNDLES = 1 * 60 * 1000;
	
	private static final Logger log = Logger.getLogger(DialogExecutor.class);
	
	protected int groupId;
	protected String warehouseIP;
	protected int warehousePort;
	protected int recvPort;
	protected int fromPort;
	protected double nwLat;
	protected double nwLon;
	protected double seLat;
	protected double seLon;
	protected int timeBound;
	protected int distType;
	protected int maxPackets;
	protected SecureConfig config;
	
	protected UDPSocketListener listener;
	protected Thread listener_t;
	protected BlockingQueue<DatagramPacket> queue = new LinkedBlockingQueue<DatagramPacket>();
	
	protected List<?> resultSet;
	
	protected final CryptoProvider cryptoProvider;
	protected byte[] certId8;
	
	protected class UDPSocketListener implements Runnable {
		private static final int DEFAULT_SOC_TIMEOUT = 5 * 1000;
		
		private int responsePort;
		private DatagramSocket socket;
		private boolean isTerminated = false;
		
		public UDPSocketListener(int responsePort) {
			this.responsePort = responsePort;
		}
		
		public void terminate() {
			this.isTerminated = true;
		}
		
		public void run() {
			System.out.println("UDPSocketListener starting ...");
			try {
				socket = new DatagramSocket(responsePort);
				socket.setSoTimeout(DEFAULT_SOC_TIMEOUT);
			} catch (SocketException se) {
				se.printStackTrace();
				isTerminated = true;
			}
			
			System.out.println(String.format("UDPSocketListener listening on port %s ...", responsePort));
			try {
				while (! isTerminated) {	
					try {
						byte [] packet = new byte[MAX_PACKET_SIZE];
						DatagramPacket datagram = new DatagramPacket(packet, packet.length);
						socket.receive(datagram);
						queue.put(datagram);
					} catch (Exception ex) {
						if (! (ex instanceof SocketTimeoutException)) {
							System.err.println("Exception occurred while listening for datagram packet(s).");
							ex.printStackTrace();
							isTerminated = true;
						}
					}
				}
			} finally {
				if (socket != null) {
					socket.close();
					socket = null;
				}
			}
			
			System.out.println("UDPSocketListener terminating ...");
		}
	}
	
	protected DialogExecutor(
			int groupId,
			String warehouseIP,
			int warehousePort,
			int recvPort,
			int fromPort,
			double nwLat,
			double nwLon,
			double seLat,
			double seLon,
			int timeBound,
			int distType,
			int maxPackets,
			SecureConfig config) {
			this.groupId = groupId;
			this.warehouseIP = warehouseIP;
			this.warehousePort = warehousePort;
			this.recvPort = recvPort;
			this.fromPort = fromPort;
			this.nwLat = nwLat;
			this.nwLon = nwLon;
			this.seLat = seLat;
			this.seLon = seLon;
			this.timeBound = timeBound;
			this.distType = distType;
			this.maxPackets = maxPackets;
			this.config = config;
			cryptoProvider = new CryptoProvider();
		}
	
	public abstract void performDialog() throws PollerException;
	public abstract List<?> getResultSet();
	
	protected void performTrustEstablishment(SemiDialogID semiDialogId, int requestId) throws DialogException {
		System.out.println(String.format("Establishing trust with warehouse at %s:%s ...", this.warehouseIP, this.warehousePort));
		
		try {
			TrustEstablishment te = new TrustEstablishment(J2735CoderHolder.getCoder(), semiDialogId, GroupIDHelper.toGroupID(this.groupId), requestId, 
					InetAddress.getByName(this.warehouseIP), this.warehousePort, (InetAddress)null, this.recvPort, this.fromPort);
			System.out.println("whport:" + this.warehousePort + " recvPort:" + recvPort + " fromPort:" + fromPort);
			te.setVerbose(true);
			if (config != null && config.secure.enable) {
				te.setSecure(true);
				te.setCryptoProvider(cryptoProvider);
				te.setPsid(config.secure.psid);
			}
			te.establishTrust(3, 5000);
			if (config != null && config.secure.enable) {
				certId8 = te.getCertId8();
				log.debug("Trust Establishment returned CertId8: " + Hex.encodeHexString(certId8));
			}
		} catch (Exception e) {
			log.error(e);
			throw new DialogException("Failed to establish trust " + e.toString(), e);
		}
	}
	
	protected void performDataExchange(SemiDialogID semiDialogId, SemiSequenceID semiSeqId, int requestId) throws DialogException {
		DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
		
		System.out.println(String.format("Sending data request to warehouse at %s:%s at %s ...", 
			this.warehouseIP, this.warehousePort, formatter.format(new Date(System.currentTimeMillis()))));
		
		GroupID groupID = GroupIDHelper.toGroupID(groupId);
		DistributionType distributionType = new DistributionType(new byte[] { (byte)distType } );
		DataExchange de = null;
		if (SemiDialogID.advSitDatDist == semiDialogId && 
			SemiSequenceID.dataReq == semiSeqId) {
			de = new RSUAdvSitDataExchange(
				semiDialogId, 
				semiSeqId,
				groupID,
				this.warehouseIP, 
				this.warehousePort, 
				this.fromPort,
				requestId,
				this.nwLat,
				this.nwLon,
				this.seLat,
				this.seLon,
				this.timeBound,
				distributionType);
		} else if (SemiDialogID.intersectionSitDataQuery == semiDialogId && 
				   SemiSequenceID.dataReq == semiSeqId) {
			de = new IntersectionSitDataExchange(
				semiDialogId, 
				semiSeqId,
				groupID,
				this.warehouseIP, 
				this.warehousePort, 
				this.fromPort,
				requestId,
				this.nwLat,
				this.nwLon,
				this.seLat,
				this.seLon,
				this.timeBound,
				distributionType);
		} else {
			throw new DialogException("Failed to send data request to warehouse because dialogId and sequenceId is invalid.");
		}
		
		try {
			de.sendDataRequest(config, cryptoProvider, certId8);
		} catch (Exception ex) {
			throw new DialogException("Failed to send data request to warehouse.", ex);
		}
		
		try {
			System.out.println("Collecting data bundles from warehouse ...");
			if (! collectDataBundles(de)) {
				throw new DialogException("Failed to collect all data bundles from warehouse.");
			}
			System.out.println(String.format("Successfully collected data bundles from the warehouse at %s.", formatter.format(new Date(System.currentTimeMillis()))));
		} finally {
			this.resultSet = de.getResultSet();
		}
	}
	
	protected void performNonRepudiation(SemiDialogID semiDialogId, SemiSequenceID semiSeqId, int requestId) throws DialogException {
		System.out.println(String.format("Sending data acceptance to warehouse at %s:%s ...", this.warehouseIP, this.warehousePort));
		
		NonRepudiation ne = new NonRepudiation(
			semiDialogId,
			semiSeqId,
			GroupIDHelper.toGroupID(groupId),
			this.warehouseIP, 
			this.warehousePort, 
			this.fromPort,
			requestId);
		
		int tries = 3;
		while (tries > 0) {
			try {
				ne.sendDataAcceptance(config, cryptoProvider, certId8);
				if (ne.isReceiptValid(semiDialogId, dequeue(5, TimeUnit.SECONDS), config, cryptoProvider)) {
					System.out.println("Successfully received receipt back from the warehouse.");
					return;
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}

			tries--;
		}
		
		throw new DialogException("Failed to received a valid receipt from the warehouse, retries exhausted.");
	}
	
	protected boolean collectDataBundles(DataExchange de) {
		long start = System.currentTimeMillis();
		while (true) {
			try { Thread.sleep(15 * 1000); } catch (InterruptedException ie) {}
			List<DatagramPacket> packets = dequeue(this.maxPackets);
			log.debug(String.format("Collected %d packets", packets != null ? packets.size() : -1));
			int bundlesRemaining = de.storeDataBundles(packets, config, cryptoProvider);
			System.out.println(String.format("Waiting for %s more data bundle(s) ...", bundlesRemaining));
			if (bundlesRemaining == 0) {
				return true;
			}
			long duration = System.currentTimeMillis() - start;
			if (duration > MAX_TIME_TO_COLLECT_BUNDLES) {
				System.out.println("Time expired while collecting data bundles from the warehouse.");
				break;
			}
		}
		return false;
	}
	
	protected void cleanup() {
		try {
			this.listener.terminate();
			this.listener_t.join();
			this.listener_t = null;
			this.listener = null;
		} catch (InterruptedException e) {
			// ignore exception
		}
	}
	
	/**
	 * Polls for as many packets from the queue as possible up to the specified limit.
	 * If a null packet is encountered, the list is returned immediately.
	 */
	protected List<DatagramPacket> dequeue(int limit) {
		List<DatagramPacket> packets = new ArrayList<DatagramPacket>();
		while (true) {
			DatagramPacket packet = this.queue.poll();
			if (packet == null) break;
			packets.add(packet);
			if (packets.size() >= limit) break;
		}
		return packets;
	}
	
	/**
	 * Polls for a single packet from the queue, waiting for the
	 * specified amount of time before quitting.
	 */
	protected DatagramPacket dequeue(long timeout, TimeUnit unit) {
		return dequeue(timeout, unit, 1);
	}
	
	/**
	 * Polls for a single packet from the queue, waiting for the
	 * specified amount of time before retrying. When all retries
	 * are exhausted than it quits.
	 */
	protected DatagramPacket dequeue(long timeout, TimeUnit unit, int tries) {
		DatagramPacket packet = null;
		
		int attempts = 0;
		while (attempts < tries) {
			try {
				packet = this.queue.poll(timeout, unit);
			} catch (InterruptedException ie) {
				// ignore
			}
			
			if (packet != null) break;
			attempts++;
		}
		
		return packet;
	}
}