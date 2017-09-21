package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.J2735;
import gov.usdot.asn1.generated.j2735.semi.DataConfirmation;
import gov.usdot.asn1.generated.j2735.semi.DataReceipt;
import gov.usdot.asn1.generated.j2735.semi.ObjectDiscoveryData;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.GroupIDHelper;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;
import gov.usdot.cv.common.dialog.TrustEstablishment;
import gov.usdot.cv.common.dialog.TrustEstablishmentException;
import gov.usdot.cv.security.crypto.CryptoProvider;
import gov.usdot.cv.security.msg.IEEE1609p2Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.codec.binary.Hex;
import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.Coder;
import com.oss.asn1.ControlTableNotFoundException;
import com.oss.asn1.DecodeFailedException;
import com.oss.asn1.DecodeNotSupportedException;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;
import com.oss.asn1.InitializationException;

public class SenderDialog {
	private static final Logger log = Logger.getLogger(SenderDialog.class);
	
	final protected int MAX_PACKET_SIZE = 65535;
	static private final String DIGEST_ALGORITHM_NAME = "SHA-256";

	protected final SenderConfig config;
	protected DatagramSocket fromSocket = null;
	protected DatagramSocket recvSocket = null;
	
	protected MessageDigest messageDigest = null;
	
	protected List<byte[]> messages = null;
	
	protected Coder coder;
	protected final SemiDialogID dialogID;
	protected int requestID = 0;
	
	private boolean endOfDataMarker = false;
	protected final boolean isResending;
	
	protected final CryptoProvider cryptoProvider;
	protected byte[] certId8;
	
	protected boolean hasDataConfirmation = false;
	
	public SenderDialog(SenderConfig config, SemiDialogID dialogID) {
		this.config = config;
		this.dialogID = dialogID;
		this.cryptoProvider = this.config.secure.enable ? new CryptoProvider() : null;
		isResending = false;
	}

	protected SenderDialog(SenderDialog senderDialog) {
		this.config = senderDialog.config;
		this.dialogID = senderDialog.dialogID;
		this.requestID = senderDialog.requestID;
		this.messages = senderDialog.messages != null ? new ArrayList<byte[]>(senderDialog.messages) : null;
		this.cryptoProvider = this.config.secure.enable ? new CryptoProvider() : null;
		isResending = true;
	}
	
	public static final SenderDialog getEndOfDataMarker(SenderConfig config) {
		SenderDialog endOfData = new SenderDialog(config, config.dialog.dialogID);
		endOfData.endOfDataMarker = true;
		return endOfData;
	}
	
	public static final boolean isEndOfDataMarker(SenderDialog dialog) {
		return dialog != null ? dialog.endOfDataMarker : false;
	}
	
	public void open() throws TrustEstablishmentException, SocketException {
		if ( !isResending ) {
			messages = new ArrayList<byte[]>();
			++requestID;
		}
		log.debug(String.format("Opening %sdialog for requestID %d", isResending ? "resend " : "", requestID));
		if ( !establishTrust(dialogID, requestID) ) {
			log.error("Couldn't establish trust");
			if ( config.request.ignore ) 
				log.info("Ignoring failure to establish trust due to configuration request.ignore option being set to true.");
			else
				throw new TrustEstablishmentException("Couldn't establish trust. Aborting. To ignore this error set request.ignore to true in the configuration file");
		}
		recvSocket = new DatagramSocket(isResending ? config.resend.recvPort : config.destination.recvPort);
		fromSocket = new DatagramSocket(isResending ? config.resend.fromPort : config.destination.fromPort);
		
		// Include timeouts on the receiving socket
		recvSocket.setSoTimeout(config.request.timeout);
	}
	
	public void send(byte[] message) throws SocketException, IOException {
		send(message, true, true);
	}
	
	public void send(byte[] message, boolean updateMessage, boolean storeMessage) throws SocketException, IOException {
		if ( updateMessage && !isResending ) {
			message = updateMessage(message);
			if ( message == null ) {
				log.debug("Dropping a message that failed required build or update.");
				return;
			}
		}
		if ( storeMessage && !isResending )
			store(message);
		log.debug(String.format("%sending packet to host: %s, port %d, message: %s", isResending ? "Res" : "S", 
				config.destination.host.getHostAddress(), config.destination.sendPort, Hex.encodeHexString(message)));
		byte[] hash = null;
		if ( hasDataConfirmation ) {
			hash = messageDigest.digest(message);
			log.debug("Sent ASD message hash is: " + Hex.encodeHexString(hash));
		}
		if ( config.secure.enable ) {
			message = encrypt(message);
			if ( message == null )
				return;
		}
        DatagramPacket packetSend = new DatagramPacket(message, message.length,config.destination.host, config.destination.sendPort);
		DatagramPacket packetRecv = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
        fromSocket.send(packetSend);
        if ( hasDataConfirmation ) {
        	try {
	        	recvSocket.receive(packetRecv);
	        	if ( receiveDataConfirmation(packetRecv, hash) == false ) {
	        		log.warn("Did not receive data confirmation");
	        	}
        	} catch (SocketTimeoutException ex) {
    			log.error(
    				String.format(
    					"Caught socket timeout exception while receiving data confirmation message on port %d. Max size is %d. Timeout %d.", 
    					config.destination.recvPort, MAX_PACKET_SIZE, config.request.timeout),
    				ex);
        	}
        }
        if ( config.source.delay > 0 )
        	nap(config.source.delay);
	}
	
	protected void sendOnly(byte[] message) throws SocketException, IOException {
		log.debug(String.format("%sending only packet to host: %s, port %d, message: %s", isResending ? "Res" : "S", 
				config.destination.host.getHostAddress(), config.destination.sendPort, Hex.encodeHexString(message)));
		if ( config.secure.enable ) {
			message = encrypt(message);
			if ( message == null )
				return;
		}
        DatagramPacket packet = new DatagramPacket(message, message.length,config.destination.host, config.destination.sendPort);
        fromSocket.send(packet);
	}

	public boolean close() {
		if ( recvSocket != null && !recvSocket.isClosed() ) {
			recvSocket.close();
			recvSocket = null;
		}
		if ( fromSocket != null && !fromSocket.isClosed() ) {
			fromSocket.close();
			fromSocket = null;
		}
		return true;
	}

	public void initialize() throws SocketException, ControlTableNotFoundException, InitializationException, NoSuchAlgorithmException {
		J2735.initialize();
		coder = J2735.getPERUnalignedCoder();
		if ( config.other.relaxed ) {
			coder.enableRelaxedDecoding();
			coder.disableDecoderConstraints();
		}
		if ( config.other.verbose  ) {
			coder.enableEncoderDebugging();
			coder.enableDecoderDebugging();
		} else {
			coder.disableEncoderDebugging();
			coder.disableDecoderDebugging();
		}
		try {
			messageDigest = MessageDigest.getInstance(DIGEST_ALGORITHM_NAME);
		} catch (NoSuchAlgorithmException e) {
			log.error(String.format("Couldn't instantiate digest algorithm %s", DIGEST_ALGORITHM_NAME));
			throw e;
		}
	}
	
	public void dispose() {
		dispose(fromSocket);
		dispose(recvSocket);
		messages = null;
		try {J2735.deinitialize();} catch (Exception ignore) { }
	}
	
	private void dispose(DatagramSocket socket) {
		if ( socket != null && !socket.isClosed() ) {
			socket.close();
			socket = null;
		}
	}
	
	protected boolean establishTrust(SemiDialogID dialogID, int requestID) throws TrustEstablishmentException {
		int recvPort = isResending ? config.resend.recvPort : config.destination.recvPort;
		int fromPort = isResending ? config.resend.fromPort : config.destination.fromPort;
		TrustEstablishment te = new TrustEstablishment(coder, dialogID, config.dialog.groupID, requestID, config.destination.host, config.destination.sendPort, (InetAddress)null, recvPort, fromPort);
		te.setTimeout(config.request.timeout);
		te.setAttempts(config.request.attempts);
		te.setVerbose(config.other.verbose);
		if ( config.secure.enable ) {
			te.setSecure(true);
			te.setCryptoProvider(cryptoProvider);
			te.setPsid(config.secure.psid);
		}
		boolean rcode = te.establishTrust(config.request.attempts, config.request.timeout);
		if ( config.secure.enable ) {
			certId8 = te.getCertId8();
			log.debug("Trust Establishment returned CertId8: " + (certId8 != null ? Hex.encodeHexString(certId8) : "<null>"));
		}
		return rcode;
	}
	
	protected byte[] encrypt(byte[] message) {
		IEEE1609p2Message msg1609p2 = new IEEE1609p2Message(cryptoProvider);
		msg1609p2.setPSID(config.secure.psid);
		try {
			if ( certId8 != null ) {
				log.debug("Encrypting message for recipient: " + Hex.encodeHexString(certId8));
				return msg1609p2.encrypt(message, certId8);
			}
			log.error("Couldn't encrypt outgoing message. Reason: Recipient certificate is not available (probably due to ignored failed trust establishment)" ); 
		} catch (Exception ex) {
			log.error("Couldn't encrypt outgoing message. Reason: " + ex.getMessage(), ex);
		}
		return null;
	}
	
	protected byte[] decrypt(byte[] message) {
		try {
			IEEE1609p2Message msg1609p2 = IEEE1609p2Message.parse(message, cryptoProvider);
			assert(msg1609p2 != null);
			return msg1609p2.getPayload();
		} catch (Exception ex) {
			log.error("Couldn't decrypt incoming message. Reason: " + ex.getMessage(), ex);
		}
		return null;
	}
	
	protected AbstractData prepareMessage(byte[] message) {
		return null;
	}
	
	protected byte[] updateMessage(byte[] message) {
		AbstractData pdu = prepareMessage(message);
		if ( pdu != null ) {
			byte[] msg = encode(pdu);
			if ( msg != null ) {
				log.debug(String.format("Updated message of type %s to: %s", pdu.getClass().getSimpleName(), Hex.encodeHexString(msg)));
				return msg;
			}
		} else {
			log.error("Couldn't update message of unexpected type. Message: " + Hex.encodeHexString(message));
			return null;
		}
		return message;
	}
	
	protected void store(byte[] message) {
		messages.add(message);
	}
	
	static protected boolean resend(SenderDialog senderDialog) {
		if ( senderDialog == null )
			return true;
		try {
			senderDialog.open();
			for( byte[] message : senderDialog.messages ) {
				nap(senderDialog.config.resend.delay);
				senderDialog.send(message);
			}
			if ( senderDialog.close() )
				return true;
		} catch (TrustEstablishmentException ex ) {
			log.error("Couldn't establish trust", ex);
		} catch (IOException ex) {
			log.error("Caught IOException while sedning messages", ex);
		}
		log.error("Couldn't complete dialog for requestID " + senderDialog.requestID );
		return false;
	}
	
	protected byte[] encode(AbstractData pdu) {
		try {
			ByteArrayOutputStream sink = new ByteArrayOutputStream();
			coder.encode(pdu, sink);
			return sink.toByteArray();
		} catch (EncodeFailedException ex) {
			log.error("Couldn't encode ServiceResponse message because encoding failed", ex);
		} catch (EncodeNotSupportedException ex) {
			log.error("Couldn't encode ServiceResponse message because encoding is not supported", ex);
		}
		return null;
	}
	
	
	protected boolean receiveDataConfirmation(DatagramPacket packet, byte[] hash) {
		try {
			final byte[] data = packet.getData();
			final int length = packet.getLength();	
			final int offset = packet.getOffset();
			byte[] packetData = Arrays.copyOfRange(data, offset, length);
			if ( config.secure.enable ) {
				packetData = decrypt(packetData);
			}
			AbstractData pdu = J2735Util.decode(coder, packetData);
			if ( pdu instanceof DataConfirmation ) {
				log.debug("Got DataConfirmation");
				DataConfirmation confirmation = (DataConfirmation)pdu;
				int rID = TemporaryIDHelper.fromTemporaryID(confirmation.getRequestID());
				int gID = GroupIDHelper.fromGroupID(confirmation.getGroupID());
				if ( rID == requestID && gID == GroupIDHelper.fromGroupID(config.dialog.groupID ) ) {
					boolean hashIsValid = Arrays.equals(hash, confirmation.getHash().byteArrayValue());
					log.debug("Is hash valid: " + hashIsValid );
					return hashIsValid;
				} else {
					log.debug("RequestID or groupID mismatch!");
				}
			} else if ( pdu instanceof ObjectDiscoveryData ) {
				log.debug("Got ObjectDiscoveryData");
				ObjectDiscoveryData odd = (ObjectDiscoveryData)pdu;
				int rID = TemporaryIDHelper.fromTemporaryID(odd.getRequestID());
				int gID = GroupIDHelper.fromGroupID(odd.getGroupID());
				if ( rID == requestID && gID == GroupIDHelper.fromGroupID(config.dialog.groupID ) ) {
					log.debug("Received " + odd.getServiceInfo().getCountRecords().intValue() + " service records");
					log.debug(odd.getServiceInfo().getServiceRecords());
					return true;
				} else {
					log.debug("RequestID or groupID mismatch!");
				}
			} else {
				log.debug("Unexpected PDU type: " + pdu);
			}
/*
		} catch (SocketException ex) {
			log.error(String.format("Caught socket exception while receiving message on port %d. Max size is %d. Timeout %d.", 
					config.destination.recvPort, MAX_PACKET_SIZE, config.resend.timeout), ex);
		} catch (IOException ex) {
			log.error(String.format("Caught IO exception exception while receiving message on port %d. Max size is %d. Timeout %d.", 
					config.destination.recvPort, MAX_PACKET_SIZE, config.resend.timeout), ex);
*/
		} catch (DecodeFailedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding failed", ex);
		} catch (DecodeNotSupportedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding is not supported", ex);
		}
		return false;
	}
	
	protected boolean receiveDataReceipt() {
		try {
			DatagramPacket packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
			recvSocket.receive(packet);
			final byte[] data = packet.getData();
			final int length = packet.getLength();	
			final int offset = packet.getOffset();
			byte[] packetData = Arrays.copyOfRange(data, offset, length);
			log.debug( "Got data receipt: " + Hex.encodeHexString(packetData));
			if ( config.secure.enable ) {
				packetData = decrypt(packetData);
			}
			AbstractData pdu = J2735Util.decode(coder, packetData);
			if ( pdu instanceof DataReceipt ) {
				DataReceipt receipt = (DataReceipt)pdu;
				int rID = TemporaryIDHelper.fromTemporaryID(receipt.getRequestID());
				return rID == requestID;
			}
		} catch (SocketTimeoutException ex) {
			log.error(String.format("Caught socket timeout exception while receiving message on port %d. Max size is %d. Timeout %d.", 
					config.destination.recvPort, MAX_PACKET_SIZE, config.request.timeout), ex);
		} catch (SocketException ex) {
			log.error(String.format("Caught socket exception while receiving message on port %d. Max size is %d. Timeout %d.", 
					config.destination.recvPort, MAX_PACKET_SIZE, config.resend.timeout), ex);
		} catch (IOException ex) {
			log.error(String.format("Caught IO exception exception while receiving message on port %d. Max size is %d. Timeout %d.", 
					config.destination.recvPort, MAX_PACKET_SIZE, config.resend.timeout), ex);
		} catch (DecodeFailedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding failed", ex);
		} catch (DecodeNotSupportedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding is not supported", ex);
		}
		return false;
	}
	
    static protected void nap(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
        }
    }
}
