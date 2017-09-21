package gov.usdot.cv.apps.poller.writer;

import gov.usdot.asn1.generated.j2735.semi.AdvisoryBroadcast;
import gov.usdot.asn1.generated.j2735.semi.BroadcastInstructions;
import gov.usdot.asn1.generated.j2735.semi.DsrcInstructions;
import gov.usdot.cv.apps.poller.PollerException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.codec.binary.Hex;

public class RSUAdvSitDataFileWriter {
	
	private enum BiType {
		SPAT(0), MAP(1), TIM(2), EV(3);
		@SuppressWarnings("unused")
		private final int code;
		private BiType(int code) { this.code = code; }
		public static String codeToString(int code) {
			String result;
			switch (code) {
				case 0	: result = SPAT.toString(); break;
				case 1	: result = MAP.toString(); break;
				case 2	: result = TIM.toString(); break;
				case 3	: result = EV.toString(); break;
				default : result = "INVALID"; break;
			}
			return result;
		}
	}
	
	private enum TxMode {
		CONT(0), ALT(1);
		@SuppressWarnings("unused")
		private final int code;
		private TxMode(int code) { this.code = code; }
		public static String codeToString(int code) {
			String result;
			switch (code) {
				case 0	: result = CONT.toString(); break;
				case 1	: result = ALT.toString(); break;
				default : result = "INVALID"; break;
			}
			return result;
		}
	}
	
	private enum TxChannel {
		CCH("CCH"), SCH("SCH"), OneSevenTwo("172"), OneSevenFour("174"), OneSevenSix("176"), 
		OneSevenEight("178"), OneEighty("180"), OneEightTwo("182"), OneEightFour("184");
		private String value;
		private TxChannel(String value) { this.value = value; }
		public static String codeToString(int code) {
			String result;
			switch (code) {
				case 0	: result = CCH.value; break;
				case 1	: result = SCH.value; break;
				case 2	: result = OneSevenTwo.value; break;
				case 3	: result = OneSevenFour.value; break;
				case 4	: result = OneSevenSix.value; break;
				case 5	: result = OneSevenEight.value; break;
				case 6	: result = OneEighty.value; break;
				case 7	: result = OneEightTwo.value; break;
				case 8	: result = OneEightFour.value; break;
				default : result = "INVALID"; break;
			}
			return result;
		}
	}
	
	private static final String BROADCAST_INSTRUCTION_FORMAT =
		"Version=%s;Type=%s;PSID=%s;" + 
		"Priority=%s;TxMode=%s;TxChannel=%s;" + 
		"TxInterval=%s;DeliveryStart=%s;" + 
		"DeliveryStop=%s;Signature=%s;" + 
		"Encryption=%s;Payload=%s;\n";
	
	/** Format is Month/Day/Year, Hour:Minute */
	private static final String DATE_FORMAT = "%s/%s/%s, %s:%s";
	
	private String savePath;
	private String filename;
	private String filePath;

	public RSUAdvSitDataFileWriter(String savePath, String filename) {
		this.savePath = savePath;
		this.filename = filename;
		this.filePath = this.savePath + File.separator + this.filename;
	}
	
	public void write(List<AdvisoryBroadcast> records) throws PollerException {
		PrintWriter pw = null;
		
		try {
			pw = new PrintWriter(this.filePath);

			if (records == null || records.size() == 0) {
				pw.write("");
				return;
			}
			
			for (int i = 0; i < records.size(); i++) {
				AdvisoryBroadcast record = records.get(i);
				
				if ( !record.hasBroadcastInst() )
					continue;
				
				BroadcastInstructions broadcastInst = record.getBroadcastInst();
				
				String version = "0.5";
				String type = BiType.codeToString((int) broadcastInst.getBiType().longValue());
				String psid = Hex.encodeHexString(record.getMessagePsid().byteArrayValue());
				String priority = new String(broadcastInst.getBiPriority().byteArrayValue());
				

				
				String deliveryStart = String.format(DATE_FORMAT, 
					zeroPadValue(broadcastInst.getBiDeliveryStart().getMonth().intValue()),
					zeroPadValue(broadcastInst.getBiDeliveryStart().getDay().intValue()),
					broadcastInst.getBiDeliveryStart().getYear().intValue(),
					zeroPadValue(broadcastInst.getBiDeliveryStart().getHour().intValue()),
					zeroPadValue(broadcastInst.getBiDeliveryStart().getMinute().intValue()));
				String deliveryStop = String.format(DATE_FORMAT, 
					zeroPadValue(broadcastInst.getBiDeliveryStop().getMonth().intValue()),
					zeroPadValue(broadcastInst.getBiDeliveryStop().getDay().intValue()),
					broadcastInst.getBiDeliveryStop().getYear().intValue(),
					zeroPadValue(broadcastInst.getBiDeliveryStop().getHour().intValue()),
					zeroPadValue(broadcastInst.getBiDeliveryStop().getMinute().intValue()));
				String signature = Boolean.toString(broadcastInst.getBiSignature());
				String encryption = Boolean.toString(broadcastInst.getBiEncryption());
				String payload = Hex.encodeHexString(record.getAdvisoryMessage().byteArrayValue());
				

				String txMode = "";
				String txChannel = "";
				String txInterval = "";
				if ( broadcastInst.hasDsrcInst() ) {
					DsrcInstructions dsrcInst = broadcastInst.getDsrcInst();
					txMode = TxMode.codeToString((int) dsrcInst.getBiTxMode().longValue());
					txChannel = TxChannel.codeToString((int) dsrcInst.getBiTxChannel().longValue());
					txInterval = String.valueOf(dsrcInst.getBiTxInterval());
				}
		
				String instruction = String.format(
					BROADCAST_INSTRUCTION_FORMAT, 
					version, 
					type, 
					psid, 
					priority, 
					txMode,
					txChannel,
					txInterval, 
					deliveryStart, 
					deliveryStop, 
					signature, 
					encryption,
					payload);
		
				pw.write(instruction);
			}
		} catch (IOException ioe) {
			throw new PollerException(String.format("Failed to write broadcast instructions to '%s'.", this.filePath), ioe);
		} finally {
			if (pw != null) {
				pw.flush();
				pw.close();
			}
		}
	}
	
	private String zeroPadValue(int value) {
		String result = String.valueOf(value);
		if (value < 10) result = "0" + result;
		return result;
	}
	
}