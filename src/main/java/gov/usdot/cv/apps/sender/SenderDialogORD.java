package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.semi.DataAcceptance;
import gov.usdot.asn1.generated.j2735.semi.ObjectRegistrationData;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.j2735.CVSampleMessageBuilder;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.DecodeFailedException;
import com.oss.asn1.DecodeNotSupportedException;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;

public class SenderDialogORD extends SenderDialog {

	private static final Logger log = Logger.getLogger(SenderDialogORD.class);
	
	public SenderDialogORD(SenderConfig config) {
		super(config, SemiDialogID.objReg);
		hasDataConfirmation = true;
	}
	
	protected SenderDialogORD(SenderDialog senderDialog) {
		super(senderDialog);
		hasDataConfirmation = true;
	}
	
	private ObjectRegistrationData buildMessage() throws DecodeFailedException, DecodeNotSupportedException {
		return CVSampleMessageBuilder.buildObjectRegistrationData(
				ByteBuffer.wrap(config.dialog.groupID.byteArrayValue()).getInt(), requestID, 
				config.dialog.custom.serviceId, config.dialog.custom.serviceProviderId, 1, config.dialog.custom.region, "127.0.0.1,443");
	}
	
	@Override
	protected AbstractData prepareMessage(byte[] message) {
		try {
			boolean mustBuild = config.dialog.custom.record != null && config.dialog.custom.record == true;
			AbstractData pdu = mustBuild ? buildMessage() : J2735Util.decode(coder, message);
			if ( pdu instanceof ObjectRegistrationData ) {
				ObjectRegistrationData ord = (ObjectRegistrationData)pdu;
				if ( !mustBuild ) {
					ord.setGroupID(config.dialog.groupID);
					ord.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
					//if ( config.dialog.custom.region != null ) {
					//	ord.setServiceID(ServiceID.valueOf(config.dialog.custom.serviceId));
					//	ord.getServiceRecord().setServiceRegion(config.dialog.custom.region);
					//}
				}
				return ord;
			} else if ( pdu != null ){
				log.error("Couldn't update fields in an unexpected message of type " + pdu.getClass().getName());
			} else {
				log.error("Couldn't update requestID in null PDU");
			}
		} catch (DecodeFailedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding failed", ex);
		} catch (DecodeNotSupportedException ex) {
			log.error("Couldn't decode J2735 ASN.1 UPER message because decoding is not supported", ex);
		}
		return null;
	}
	
	@Override
	public boolean close() {
		try {
			log.debug("Closing objReg dialog");
			Boolean rcode = sendDataAcceptance();
			if ( rcode == null )
				return true;
			if ( rcode == true && receiveDataReceipt() ) {
				log.debug(String.format("Successfully closed objReg dialog for requestID %d.", requestID));
				return true;
			} else {
				log.debug(String.format("Couldn't close %sobjReg dialog for requestID %d.", isResending ? "resending " : "", requestID));
				return false;
			}
		} finally {
			super.close();
		}
	}
	
	private Boolean sendDataAcceptance() {
		int recordsSent =  messages != null ? messages.size() : -1;
		if ( recordsSent <= 0 ) {
			log.debug("Skipped sending DataAcceptance because no messages were sent during this objReg dialog.");
			return null;
		}
		log.debug(String.format("Sending ObjectRegistrationDataAcceptance message with recordsSent value of %d", recordsSent));
		DataAcceptance dataAcceptance = new DataAcceptance(
				SemiDialogID.objReg,
				SemiSequenceID.accept,
				config.dialog.groupID,
				TemporaryIDHelper.toTemporaryID(requestID));
		try {
			recvSocket.setSoTimeout(config.resend.timeout); // to save time in subsequent receiveDataReceipt() call
			sendOnly(CVSampleMessageBuilder.messageToEncodedBytes(dataAcceptance));
			return true;
		} catch (EncodeFailedException ex) {
			log.error("Couldn't encode ObjectRegistrationDataAcceptance message because encoding failed", ex);
		} catch (EncodeNotSupportedException ex) {
			log.error("Couldn't encode ObjectRegistrationDataAcceptance message because encoding is not supported", ex);
		} catch ( IOException ex ) {
			log.error("Couldn't send ObjectRegistrationDataAcceptance message due to IOError.", ex);
		}
		return false;
	}
}
