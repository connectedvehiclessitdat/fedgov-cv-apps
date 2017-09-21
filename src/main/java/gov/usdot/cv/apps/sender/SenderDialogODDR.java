package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.semi.DataAcceptance;
import gov.usdot.asn1.generated.j2735.semi.ObjectDiscoveryDataRequest;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.generated.j2735.semi.ServiceID;
import gov.usdot.asn1.j2735.CVSampleMessageBuilder;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;

import java.io.IOException;

import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.DecodeFailedException;
import com.oss.asn1.DecodeNotSupportedException;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;

public class SenderDialogODDR extends SenderDialog {

	private static final Logger log = Logger.getLogger(SenderDialogODDR.class);
	
	public SenderDialogODDR(SenderConfig config) {
		super(config, SemiDialogID.objDisc);
		hasDataConfirmation = true;
	}
	
	protected SenderDialogODDR(SenderDialog senderDialog) {
		super(senderDialog);
		hasDataConfirmation = true;
	}
	
	private ObjectDiscoveryDataRequest buildMessage(byte[] message) throws DecodeFailedException, DecodeNotSupportedException {
		ObjectDiscoveryDataRequest oddr = new ObjectDiscoveryDataRequest();
		oddr.setDialogID(SemiDialogID.objDisc);
		oddr.setSeqID(SemiSequenceID.dataReq);
		oddr.setGroupID(config.dialog.groupID);
		oddr.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
		oddr.setServiceID(ServiceID.valueOf(config.dialog.custom.serviceId));
		oddr.setServiceRegion(config.dialog.custom.region);
		return oddr;
	}
	
	@Override
	protected AbstractData prepareMessage(byte[] message) {
		try {
			return buildMessage(message);
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
			log.debug("Closing objDisc dialog");
			Boolean rcode = sendDataAcceptance();
			if ( rcode == null )
				return true;
			if ( rcode == true && receiveDataReceipt() ) {
				log.debug(String.format("Successfully closed objDisc dialog for requestID %d.", requestID));
				return true;
			} else {
				log.debug(String.format("Couldn't close %sobjDisc dialog for requestID %d.", isResending ? "resending " : "", requestID));
				return false;
			}
		} finally {
			super.close();
		}
	}
	
	private Boolean sendDataAcceptance() {
		int recordsSent =  messages != null ? messages.size() : -1;
		if ( recordsSent <= 0 ) {
			log.debug("Skipped sending DataAcceptance because no messages were sent during this objDisc dialog.");
			return null;
		}
		log.debug(String.format("Sending ObjectDiscoveryDataAcceptance message with recordsSent value of %d", recordsSent));
		DataAcceptance dataAcceptance = new DataAcceptance(
				SemiDialogID.objDisc,
				SemiSequenceID.accept,
				config.dialog.groupID,
				TemporaryIDHelper.toTemporaryID(requestID));
		try {
			recvSocket.setSoTimeout(config.resend.timeout); // to save time in subsequent receiveDataReceipt() call
			sendOnly(CVSampleMessageBuilder.messageToEncodedBytes(dataAcceptance));
			return true;
		} catch (EncodeFailedException ex) {
			log.error("Couldn't encode ObjectDiscoveryDataAcceptance message because encoding failed", ex);
		} catch (EncodeNotSupportedException ex) {
			log.error("Couldn't encode ObjectDiscoveryDataAcceptance message because encoding is not supported", ex);
		} catch ( IOException ex ) {
			log.error("Couldn't send ObjectDiscoveryDataAcceptance message due to IOError.", ex);
		}
		return false;
	}
}
