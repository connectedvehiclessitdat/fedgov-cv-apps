package gov.usdot.cv.apps.poller.writer;

import gov.usdot.asn1.generated.j2735.semi.IntersectionRecord;
import gov.usdot.cv.apps.poller.PollerException;
import gov.usdot.cv.apps.poller.util.J2735CoderHolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.util.List;

import org.apache.commons.codec.binary.Hex;

import com.oss.asn1.Coder;

public class IntersectionSitDataFileWriter {
	
	private static String NEWLINE = System.getProperty("line.separator");
	
	private String savePath;
	private String filename;
	private String filePath;
	
	public IntersectionSitDataFileWriter(String savePath, String filename) {
		this.savePath = savePath;
		this.filename = filename;
		this.filePath = this.savePath + File.separator + this.filename;
	}
	
	public void write(List<IntersectionRecord> records) throws PollerException {
		PrintWriter pw = null;
		
		try {
			pw = new PrintWriter(this.filePath);

			if (records == null || records.size() == 0) {
				pw.write("");
				return;
			}
			
			Coder coder = J2735CoderHolder.getCoder();
			for (int i = 0; i < records.size(); i++) {
				IntersectionRecord record = records.get(i);
				
				ByteArrayOutputStream sink = new ByteArrayOutputStream();
				coder.encode(record, sink);
				byte [] encoded = sink.toByteArray();
		
				pw.write(Hex.encodeHexString(encoded) + NEWLINE);
			}
		} catch (Exception ioe) {
			throw new PollerException(String.format("Failed to write broadcast instructions to '%s'.", this.filePath), ioe);
		} finally {
			if (pw != null) {
				pw.flush();
				pw.close();
			}
		}
	}
	
}