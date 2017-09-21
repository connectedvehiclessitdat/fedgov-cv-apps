package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.J2735;
import gov.usdot.asn1.generated.j2735.dsrc.Acceleration;
import gov.usdot.asn1.generated.j2735.dsrc.AccelerationSet4Way;
import gov.usdot.asn1.generated.j2735.dsrc.AntiLockBrakeStatus;
import gov.usdot.asn1.generated.j2735.dsrc.AuxiliaryBrakeStatus;
import gov.usdot.asn1.generated.j2735.dsrc.BrakeAppliedStatus;
import gov.usdot.asn1.generated.j2735.dsrc.BrakeBoostApplied;
import gov.usdot.asn1.generated.j2735.dsrc.BrakeSystemStatus;
import gov.usdot.asn1.generated.j2735.dsrc.DDateTime;
import gov.usdot.asn1.generated.j2735.dsrc.DDay;
import gov.usdot.asn1.generated.j2735.dsrc.DHour;
import gov.usdot.asn1.generated.j2735.dsrc.DMinute;
import gov.usdot.asn1.generated.j2735.dsrc.DMonth;
import gov.usdot.asn1.generated.j2735.dsrc.DOffset;
import gov.usdot.asn1.generated.j2735.dsrc.DSecond;
import gov.usdot.asn1.generated.j2735.dsrc.DYear;
import gov.usdot.asn1.generated.j2735.dsrc.Heading;
import gov.usdot.asn1.generated.j2735.dsrc.Latitude;
import gov.usdot.asn1.generated.j2735.dsrc.Longitude;
import gov.usdot.asn1.generated.j2735.dsrc.MsgCRC;
import gov.usdot.asn1.generated.j2735.dsrc.Position3D;
import gov.usdot.asn1.generated.j2735.dsrc.StabilityControlStatus;
import gov.usdot.asn1.generated.j2735.dsrc.SteeringWheelAngle;
import gov.usdot.asn1.generated.j2735.dsrc.TemporaryID;
import gov.usdot.asn1.generated.j2735.dsrc.TractionControlStatus;
import gov.usdot.asn1.generated.j2735.dsrc.TransmissionAndSpeed;
import gov.usdot.asn1.generated.j2735.dsrc.VehicleLength;
import gov.usdot.asn1.generated.j2735.dsrc.VehicleSize;
import gov.usdot.asn1.generated.j2735.dsrc.VehicleWidth;
import gov.usdot.asn1.generated.j2735.dsrc.VerticalAcceleration;
import gov.usdot.asn1.generated.j2735.dsrc.YawRate;
import gov.usdot.asn1.generated.j2735.semi.FundamentalSituationalStatus;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.SemiSequenceID;
import gov.usdot.asn1.generated.j2735.semi.VehSitDataMessage;
import gov.usdot.asn1.generated.j2735.semi.VehSitDataMessage.Bundle;
import gov.usdot.asn1.generated.j2735.semi.VehSitRecord;
import gov.usdot.asn1.generated.j2735.semi.VsmType;
import gov.usdot.asn1.j2735.CVTypeHelper;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.GroupIDHelper;
import gov.usdot.cv.common.asn1.TemporaryIDHelper;
import gov.usdot.cv.common.asn1.TransmissionAndSpeedHelper;
import gov.usdot.cv.common.util.CrcCccitt;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Random;
import java.util.TimeZone;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sf.json.JSONSerializer;

import org.apache.commons.io.IOUtils;

import com.oss.asn1.Coder;
import com.oss.asn1.ControlTableNotFoundException;
import com.oss.asn1.EncodeFailedException;
import com.oss.asn1.EncodeNotSupportedException;

/**
 * Given a gpx json file, construct a vehicle situation data message for 
 * each data point and send it into the Situation Data Clearinghouse.
 * 
 * The following command was used to strip the gpx file that contains
 * special windows character when transferring the files over from
 * windwos to linux.
 * 
 * 	sed -i 's/\xA0//g' *.json
 * 
 * Note: This is a quick and dirty application that was written to 
 * to do load testing on the SDC. If a more suitable way of testing
 * the SDC is developed later on this application can be removed.
 */
public class GpxRouteSender {
	private String host;
	private int port;
	private String filePath;
	
	private Coder coder;
	private DatagramSocket socket;
	
	private JSONArray rtept;
	
	private int last_lat_1 = 0, last_lat_2 = 0, last_lon_1 = 0, last_lon_2 = 0;
	
	private GpxRouteSender(
			String host, 
			int port, 
			String filePath) {
		this.host = host;
		this.port = port;
		this.filePath = filePath;
	}
	
	private void initialize() throws IOException {
		FileInputStream fis = new FileInputStream(this.filePath);
		String jsonTxt = IOUtils.toString(fis);
		JSONObject gpx = (JSONObject) JSONSerializer.toJSON(jsonTxt);
		
		try {
			J2735.initialize();
			this.coder = J2735.getPERUnalignedCoder();
		} catch (ControlTableNotFoundException ex) {
			System.err.println("Couldn't initialize J2735 parser. Message: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		} catch (com.oss.asn1.InitializationException ex) {
			System.err.println("Couldn't initialize J2735 parser. Message: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
		
		try {
			this.socket = new DatagramSocket();
		} catch (SocketException ex) {
			System.err.println("Couldn't create datagram socket instance. Message: " + ex.getMessage());
			ex.printStackTrace();
			System.exit(1);
		}
		
		if (! gpx.has("rte")) {
			System.err.println("Geo position file provided is not in GPX format: rte element is missing.");
			System.exit(1);
		}
		
		JSONObject rte = gpx.getJSONObject("rte");
		if (rte.isNullObject()) {
			System.err.println("Geo position file provided is not in GPX format: rte element is malformed.");
			System.exit(1);
		}
		
		if (! rte.has("rtept")) {
			System.err.println("Geo position file provided is not in GPX format: rtept element is missing.");
			System.exit(1);
		}
	
		this.rtept = rte.getJSONArray("rtept");
		if (! rtept.isArray()) {
			System.err.println("Geo position file provided is not in GPX format: rtept element is malformed.");
			System.exit(1);
		}
	}
	
	private void send() throws Exception {		
		Random r = new Random();
		int ms = r.nextInt(2000);
		int requestId = r.nextInt();
		
		int msgCount = 0;
		double latitude, longitude;
		for (int i = 0; i < rtept.size(); i++) {
			nap(ms);
			
			JSONObject pt = rtept.getJSONObject(i);
			if (! pt.isNullObject() && pt.has("lat") && pt.has("lon")) {
				latitude = pt.getDouble("lat");
				longitude = pt.getDouble("lon");
				double speed = 45.0 * r.nextDouble();
				byte [] packet = encodeVehSitData(requestId, latitude, longitude, speed);
				this.socket.send(new DatagramPacket(
					packet, 
					packet.length, 
					InetAddress.getByName(this.host), 
					this.port)
				);
				
				msgCount++;
			}
		}
		
		System.out.println(String.format("%s vehsitdata message(s) were sent.", msgCount));
	}
	
	private byte [] encodeVehSitData(int requestId, double latitude, double longitude, double speed) throws Exception {
		try {
			VehSitDataMessage sitDataMsg = createVehSitDataMessage(requestId, latitude, longitude, speed);
			ByteArrayOutputStream sink = new ByteArrayOutputStream();
			coder.encode(sitDataMsg, sink);
			byte [] responseBytes = sink.toByteArray();
			CrcCccitt.setMsgCRC(responseBytes);
			return responseBytes;
		} catch (EncodeFailedException ex) {
			throw new Exception("Couldn't encode VehicleServiceResponse message because encoding failed.", ex);
		} catch (EncodeNotSupportedException ex) {
			throw new Exception("Couldn't encode VehicleServiceResponse message because encoding is not supported.", ex);
		}
	}
	
	private void nap(long ms) {
		if (ms == 0) return;
        try {  Thread.sleep(ms); } catch (InterruptedException ex) {}
    }
	
	private VehSitDataMessage createVehSitDataMessage(
			int requestId,
			double latitude, 
			double longitude, 
			double mph) {
		int lon_int = J2735Util.convertGeoCoordinateToInt(longitude);
		int lat_int = J2735Util.convertGeoCoordinateToInt(latitude);
		
		Calendar now = GregorianCalendar.getInstance(TimeZone.getTimeZone("UTC"));
		DDateTime dt = new DDateTime(
				new DYear(now.get(Calendar.YEAR)), 
				new DMonth(now.get(Calendar.MONTH)+1), 
				new DDay(now.get(Calendar.DAY_OF_MONTH)), 
				new DHour(now.get(Calendar.HOUR_OF_DAY)), 
				new DMinute(now.get(Calendar.MINUTE)), 
				new DSecond(now.get(Calendar.SECOND)),
				new DOffset(-300));
	
		// update path history
		last_lat_2 = last_lat_1;
		last_lat_1 = lat_int;
		last_lon_2 = last_lon_1;
		last_lon_1 = lon_int;

		TemporaryID tempID = new TemporaryID("1234".getBytes());
		TransmissionAndSpeed speed = TransmissionAndSpeedHelper.createTransmissionAndSpeed(mph);
		Heading heading = new Heading(90);
		
		final Acceleration lonAccel = new Acceleration(1);
		final Acceleration latAccel = new Acceleration(1);
		final VerticalAcceleration vertAccel = new VerticalAcceleration(43);
		final YawRate yaw = new YawRate(0);
		final AccelerationSet4Way accelSet = new AccelerationSet4Way(lonAccel, latAccel, vertAccel, yaw);
	    
		final BrakeSystemStatus brakes = new BrakeSystemStatus(
					new BrakeAppliedStatus(new byte[] { (byte)0xf8 } ), 
					TractionControlStatus.unavailable, 
					AntiLockBrakeStatus.unavailable, 
					StabilityControlStatus.unavailable,
					BrakeBoostApplied.unavailable,
					AuxiliaryBrakeStatus.unavailable
				);
		
		SteeringWheelAngle steeringAngle = new SteeringWheelAngle(0);

		VehicleWidth vehWidth   = new  VehicleWidth(185); 	// Honda Accord 2014 width:   72.8 in -> ~ 185 cm
		VehicleLength vehLength = new VehicleLength(486);	// Honda Accord 2014 length: 191.4 in -> ~ 486 cm
		VehicleSize vehSize = new VehicleSize(vehWidth, vehLength);
		
		FundamentalSituationalStatus fundamental = new FundamentalSituationalStatus(speed, heading, steeringAngle, accelSet,  brakes, vehSize);
		Position3D pos1 = new Position3D(new Latitude(lat_int), new Longitude(lon_int));
		VehSitRecord vehSitRcd1 = new VehSitRecord(tempID, dt, pos1, fundamental);
		Position3D pos2 = new Position3D(new Latitude(last_lat_1), new Longitude(last_lon_1));
		VehSitRecord vehSitRcd2 = new VehSitRecord(tempID, dt, pos2, fundamental);
		Position3D pos3 = new Position3D(new Latitude(last_lat_2), new Longitude(last_lon_2));
		VehSitRecord vehSitRcd3 = new VehSitRecord(tempID, dt, pos3, fundamental);
		
		MsgCRC crc = new MsgCRC(new byte[2]);
		
		VsmType type = new VsmType(new byte[] { CVTypeHelper.bitWiseOr(CVTypeHelper.VsmType.VEHSTAT, CVTypeHelper.VsmType.ELVEH) }) ;
		VehSitDataMessage vsdm = new VehSitDataMessage(SemiDialogID.vehSitData, SemiSequenceID.data, GroupIDHelper.toGroupID(0), TemporaryIDHelper.toTemporaryID(requestId), type,  
			    new Bundle(new VehSitRecord[] { vehSitRcd1, vehSitRcd2, vehSitRcd3} ), crc);

		return vsdm;
	}
	
	public static void main(String [] args) throws Exception {
		if (args.length != 3) {
			System.out.println("Usage: java GpxRouteSender <hostname> <port> <filepath>");
			System.exit(1);
		}
		
		GpxRouteSender sender = new GpxRouteSender(
			args[0], 
			Integer.parseInt(args[1]), 
			args[2]);
		sender.initialize();
		sender.send();
	}
}