package gov.usdot.cv.apps.sender;

import org.apache.log4j.Logger;

import com.oss.asn1.AbstractData;
import com.oss.asn1.DecodeFailedException;
import com.oss.asn1.DecodeNotSupportedException;

import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.VehSitDataMessage;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;

public class SenderDialogVSD extends SenderDialog {
	
	private static final Logger log = Logger.getLogger(SenderDialog.class);

	public SenderDialogVSD(SenderConfig config) {
		super(config, SemiDialogID.vehSitData);
	}
	
	@Override
	protected AbstractData prepareMessage(byte[] message) {
		try {
			AbstractData pdu = J2735Util.decode(coder, message);
			if ( pdu instanceof VehSitDataMessage ) {
				VehSitDataMessage vsd = (VehSitDataMessage)pdu;
				vsd.setGroupID(config.dialog.groupID);
				vsd.setRequestID(TemporaryIDHelper.toTemporaryID(requestID));
				return vsd;
			} else if ( pdu != null ){
				log.error("Couldn't update groupID and requestID in an unexpected message of type " + pdu.getClass().getName());
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
	protected void store(byte[] message) {
	}

}
