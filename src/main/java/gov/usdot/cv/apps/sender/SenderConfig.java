package gov.usdot.cv.apps.sender;

import gov.usdot.asn1.generated.j2735.dsrc.Latitude;
import gov.usdot.asn1.generated.j2735.dsrc.Longitude;
import gov.usdot.asn1.generated.j2735.dsrc.Position3D;
import gov.usdot.asn1.generated.j2735.semi.GeoRegion;
import gov.usdot.asn1.generated.j2735.semi.GroupID;
import gov.usdot.asn1.generated.j2735.semi.SemiDialogID;
import gov.usdot.asn1.generated.j2735.semi.TimeToLive;
import gov.usdot.asn1.j2735.J2735Util;
import gov.usdot.cv.common.asn1.DialogIDHelper;
import gov.usdot.cv.common.asn1.GroupIDHelper;

import java.net.InetAddress;
import java.net.UnknownHostException;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class SenderConfig {
	
	private static final Logger log = Logger.getLogger(SenderConfig.class);
	
	public final Source source;
	public final Destination destination;
	public final Dialog dialog;
	public final Request request;
	public final Resend resend;
	public final Other other;
	public final Secure secure;
	
	public SenderConfig(JSONObject config) throws UnknownHostException {
		source = new Source(config);
		destination = new Destination(config);
		dialog = new Dialog(config);
		request = new Request(config);
		resend = new Resend(config);
		other = new Other(config);
		secure = new Secure(config);
	}
	
	@Override
	public String toString() {
		return String.format("%s\n%s\n%s\n%s\n%s\n%s\n%s\n", source, destination, dialog, request, resend, other, secure );
	}
	
	static public enum SourceType {
		PIPE, FILE, FOLDER, PORT
	}
	
	static public enum ContentType {
		UPER, HEX, BASE64
	}
	
	private static final int DEFAULT_START_PORT = 46750;
	
	public class Source {
		
		static private final String SECTION_NAME = "source";
		private final SourceType DEFAULT_SOURCE_TYPE = SourceType.PORT;
		private final ContentType DEFAULT_CONTENT_TYPE = ContentType.UPER;
		private static final int DEFAULT_PORT = DEFAULT_START_PORT;
		private static final int DEFAULT_DELAY = 500;
		
		public final SourceType sourceType;
		public final ContentType contentType;
		public final String path;
		public final String filter;
		public final Integer port;
		public final Integer delay;							// sleep milliseconds before sending a record

		private Source(JSONObject config) {
			JSONObject source = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			
			SourceType sourceType = DEFAULT_SOURCE_TYPE;
			ContentType contentType = DEFAULT_CONTENT_TYPE;
			String path = null;
			String filter = null;
			Integer port = null;

			if ( source.optBoolean("pipe", false) )
				sourceType = SourceType.PIPE;
			
			String file = source.optString("file");
			if ( !StringUtils.isBlank(file) ) {
				sourceType = SourceType.FILE;
				path = file;
			}
			
			if ( source.has("folder")) {
				JSONObject folder = source.getJSONObject("folder");
				if ( folder.has("path")) {
					sourceType = SourceType.FOLDER;
					path = folder.getString("path");
					filter = folder.optString("filter");
				}
			}
			
			if ( source.has("port") ) {
				sourceType = SourceType.PORT;
				port = source.getInt("port");
				if( port <= 0 ) {
					log.warn(String.format("Invalid source port value %d. Defaulting to %d", port, DEFAULT_PORT ));
					port = DEFAULT_PORT;
				}
			}
			
			if ( source.has("type") ) {
				String strType = source.getString("type");
				if ( !StringUtils.isBlank(strType) ) {
					try {
						contentType = ContentType.valueOf(strType);
					} catch(IllegalArgumentException ex) {
						log.warn(String.format("Invalid source content type value: '%s'. Valid values are: UPER, HEX, BASE64. Defaulting to: %s",
								strType, DEFAULT_CONTENT_TYPE.name()) );
						contentType = DEFAULT_CONTENT_TYPE;
					}
				}
			}
			
			if ( sourceType == SourceType.PORT && port == null )
				port = DEFAULT_PORT;

			this.sourceType = sourceType;
			this.contentType = contentType;
			this.path = path;
			this.filter = filter;
			this.port = port;
			this.delay = source.optInt("delay", DEFAULT_DELAY);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\tsourceType\t%s\n\tcontentType\t%s\n\tpath\t\t%s\n\tfilter\t\t%s\n\tport\t\t%d\n\tdelay\t\t%d", SECTION_NAME, sourceType, contentType, path, filter, port, delay );
		}
	}
	
	public class Destination {
		static private final String SECTION_NAME = "destination";
		static private final String DEFAULT_HOST = "localhost";
		private static final int DEFAULT_SEND_PORT = DEFAULT_START_PORT + 1;
		private static final int DEFAULT_RECV_PORT = DEFAULT_START_PORT + 2;
		private static final int DEFAULT_FROM_PORT = DEFAULT_START_PORT + 3;
		
		public final InetAddress host;
		public final int sendPort;
		public final int recvPort;
		public final int fromPort;
		
		private Destination(JSONObject config) throws UnknownHostException {
			JSONObject destination = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			
			final String hostname = destination.optString("host", DEFAULT_HOST);
			host = InetAddress.getByName(hostname);
			sendPort = destination.optInt("sendPort", DEFAULT_SEND_PORT);
			recvPort = destination.optInt("recvPort", DEFAULT_RECV_PORT);
			fromPort = destination.optInt("fromPort", DEFAULT_FROM_PORT);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\thost\t\t%s\n\tsendPort\t%d\n\trecvPort\t%d\n\tfromPort\t%d", SECTION_NAME, host, sendPort, recvPort, fromPort );
		}
	}
	
	public class Dialog {
		static private final String SECTION_NAME = "dialog";
		private static final int DEFAULT_GROUP_ID = 0;		// groupID unknown or unused
		private final SemiDialogID DEFAULT_DIALOG_TYPE = SemiDialogID.intersectionSitDataDep;
		private static final int DEFAULT_RECORDS = 5000;	// maximum records per dialog
		private static final int DEFAULT_TIMEOUT = 20000;	// close dialog after XX seconds of inactivity
		
		public final SemiDialogID dialogID;
		public final GroupID groupID;
		public final int records;
		public final int timeout;
		public final Custom custom;
		
		private Dialog(JSONObject config) {
			JSONObject dialog = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			SemiDialogID dlgID = DEFAULT_DIALOG_TYPE;
			if ( dialog.has("type") ) {
				String strType = dialog.getString("type");
				if ( !StringUtils.isBlank(strType) ) {
					dlgID = DialogIDHelper.getDialogID(strType);
					if ( dlgID == null ) {
						log.warn(String.format("Invalid dialog type value: '%s'. Defaulting to: %s",
								strType, DialogIDHelper.getDialogID(DEFAULT_DIALOG_TYPE)));
						dlgID = DEFAULT_DIALOG_TYPE;
					}
				}
			}
			dialogID = dlgID;
			groupID = GroupIDHelper.toGroupID(dialog.optInt("group", DEFAULT_GROUP_ID));
			timeout = dialog.optInt("timeout", DEFAULT_TIMEOUT);
			records = dialog.optInt("records", DEFAULT_RECORDS);
			custom = new Custom(dialog.optJSONObject("custom"));
		}
		
		@Override
		public String toString() {
			String str = String.format("    %s\n\ttype\t\t%s\n\tgroup\t\t%d\n\ttimeout\t\t%d\n\trecords\t\t%d", 
					SECTION_NAME, DialogIDHelper.getDialogID(dialogID), GroupIDHelper.fromGroupID(groupID), timeout, records);
			if ( custom.present ) 
				str += "\n\tcustom\t\t" + custom;
			return str;
		}
	}
	
	public class Request {
		static private final String SECTION_NAME = "request";
		private static final int DEFAULT_TIMEOUT = 3000;
		private static final int DEFAULT_ATTEMPTS = 3;
		private static final boolean DEFAULT_IGNORE = false;

		public final int timeout;
		public final int attempts;
		public final boolean ignore;
		
		private Request(JSONObject config) {
			JSONObject dialog = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			timeout = dialog.optInt("timeout", DEFAULT_TIMEOUT);
			attempts = dialog.optInt("attempts", DEFAULT_ATTEMPTS);
			ignore = dialog.optBoolean("ignore", DEFAULT_IGNORE);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\ttimeout\t\t%d\n\tattempts\t%d\n\tignore\t\t%s", SECTION_NAME, timeout, attempts, ignore ? "true" : "false" );
		}
	}
	
	public class Resend {
		static private final String SECTION_NAME = "resend";
		private static final int DEFAULT_TIMEOUT = 3000;	// number of milliseconds to wait for receipt
		private static final int DEFAULT_DELAY = 500;		// sleep milliseconds before sending a record
		private static final int DEFAULT_ATTEMPTS = 3;
		private static final int DEFAULT_RECV_PORT = DEFAULT_START_PORT + 4;
		private static final int DEFAULT_FROM_PORT = DEFAULT_START_PORT + 5;		

		public final int timeout;
		public final int attempts;
		public final int delay;
		public final int fromPort;
		public final int recvPort;
		
		private Resend(JSONObject config) {
			JSONObject resend = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			timeout = resend.optInt("timeout", DEFAULT_TIMEOUT);
			attempts = resend.optInt("attempts", DEFAULT_ATTEMPTS);
			delay = resend.optInt("delay", DEFAULT_DELAY);
			recvPort = resend.optInt("recvPort", DEFAULT_RECV_PORT);
			fromPort = resend.optInt("fromPort", DEFAULT_FROM_PORT);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\ttimeout\t\t%d\n\tattempts\t%d\n\tdelay\t\t%d\n\trecvPort\t%d\n\tfromPort\t%d", SECTION_NAME, timeout, attempts, delay, recvPort, fromPort );
		}
	}
	
	public class Other {
		static private final String SECTION_NAME = "other";
		static private final boolean DEFAULT_VERBOSE = false;
		static private final boolean DEFAULT_TIMESTAMP = false;
		static private final boolean DEFAULT_RELAXED = false;
		
		public final boolean verbose;
		public final boolean timestamp;
		public final boolean relaxed;
		
		private Other(JSONObject config) {
			JSONObject other = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			verbose = other.optBoolean("verbose", DEFAULT_VERBOSE);
			timestamp = other.optBoolean("timestamp", DEFAULT_TIMESTAMP);
			relaxed = other.optBoolean("relaxed", DEFAULT_RELAXED);
		}
		
		@Override
		public String toString() {
			return String.format("    %s\n\tverbose\t\t%s\n\ttimestamp\t%s\n\trelaxed\t\t%s", SECTION_NAME, verbose, timestamp, relaxed);
		}
	}
	
	public class CertEntry {
		public final String name;
		public final String path;
		public final String key;
		
		static private final String SECTION_NAME = "cert";
		
		private CertEntry(JSONObject config) {
			JSONObject cert = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			name = cert.getString("name");
			path = cert.getString("path");
			key  = cert.optString("key", null);
		}
		
		@Override
		public String toString() {
			return String.format("\t  cert\n\t    name\t%s\n\t    path\t%s\n\t    key\t\t%s", name != null ? name : "", path != null ? path : "", key != null ? key : "");
		}
	}
	
	public class Secure {
		static private final String SECTION_NAME = "secure";
		static private final String CERTS_NAME = "certs";
		static private final boolean DEFAULT_ENABLE = false;
		static private final int DEFAULT_PSID = 0x2fe1;
		
		public final boolean enable;
		public final int psid;
		public final CertEntry[] certs;
		
		private Secure(JSONObject config) {
			JSONObject secure = config.has(SECTION_NAME) ? config.getJSONObject(SECTION_NAME) : new JSONObject();
			enable = secure.optBoolean("enable", DEFAULT_ENABLE);
			psid = secure.optInt("psid", DEFAULT_PSID);
			if ( secure.has(CERTS_NAME) ) {
				JSONArray jsonCerts = secure.getJSONArray(CERTS_NAME);
				final int count = jsonCerts.size();
				certs = new CertEntry[count];
				for( int i = 0; i < count; i++ )
					certs[i] = new CertEntry(jsonCerts.getJSONObject(i));
			} else {
				certs = null;
			}
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder(String.format("    %s\n\tenable\t\t%s\n\tpsid\t\t0x%x\n\tcount\t\t%s\n", SECTION_NAME, enable, psid, certs != null ? certs.length : 0));
			if ( certs != null )
				for( CertEntry cert : certs)
					sb.append(cert.toString() + "\n");
			return sb.toString();
		}
	}
	
	public class Custom {
		
		private final double DEFAULT_REGION_NW_LAT = 43.0;
		private final double DEFAULT_REGION_NW_LON = -85.0;
		private final double DEFAULT_REGION_SE_LAT = 41.0;
		private final double DEFAULT_REGION_SE_LON = -82.0;

		public final boolean present;
		public final Boolean record;
		public final Integer serviceId;
		public final Integer serviceProviderId;
		public final TimeToLive ttl;
		public final GeoRegion region;
		
		private Custom(JSONObject custom) {
			TimeToLive ttl   = null;
			Integer serviceId = 1;
			Integer serviceProviderId = 5;
			GeoRegion region = null;
			Boolean record   = null;
			if ( custom != null ) {
				present = true;
				record = custom.has("record") ? custom.getBoolean("record") : null;
				serviceId = custom.has("serviceId") ? custom.getInt("serviceId") : 1;
				serviceProviderId = custom.has("svcProvId") ? custom.getInt("svcProvId") : 5;
				if ( custom.has("ttl")  ) {
					try {
						int timeToLive = custom.getInt("ttl");
						ttl = TimeToLive.valueOf(timeToLive);
						if ( ttl != null ) 
							log.debug(String.format("custom.ttl is %s", ttl));
						else
							log.error(String.format("Ignoring unexpected value for custom.ttl %d", timeToLive));
					} catch ( Exception ex ) {
						log.error("Unexpected value for custom.ttl", ex);
					}
				}
				if ( custom.has("region")  ) {
					try {
						region = buildGeoRegion(custom.getJSONObject("region"));
						log.debug(String.format("custom.region is %s", region));
					} catch ( Exception ex ) {
						log.error("Unexpected value for custom.region.", ex);
					}
				}
				if ( record != null && record == true && region == null ) {
					region = new GeoRegion(
						getPosition3D(DEFAULT_REGION_NW_LAT, DEFAULT_REGION_NW_LON),
						getPosition3D(DEFAULT_REGION_SE_LAT, DEFAULT_REGION_SE_LON));
				}
			} else {
				present = false;
			}
			this.record = record;
			this.serviceId = serviceId;
			this.serviceProviderId = serviceProviderId;
			this.ttl = ttl;
			this.region = region;
		}
		
		private GeoRegion buildGeoRegion(JSONObject jsonRegion) {
			assert(jsonRegion != null);
			if ( !jsonRegion.has("nw") || !jsonRegion.has("se") ) {
				log.error("Skipping region without NW or SE");
				return null;
			}
			JSONObject nw = jsonRegion.getJSONObject("nw");
			if ( !nw.has("lat") || !nw.has("lon") ) {
				log.error("Skipping rectangle without NW lat or lon");
				return null;
			}
			JSONObject se = jsonRegion.getJSONObject("se");
			if ( !se.has("lat") || !se.has("lon") ) {
				log.error("Skipping rectangle without SE lat or lon");
				return null;
			}
			try {
				Position3D nwCnr = getPosition3D(nw.getDouble("lat"), nw.getDouble("lon"));
				Position3D seCnr = getPosition3D(se.getDouble("lat"), se.getDouble("lon"));
				return new GeoRegion(nwCnr,seCnr);
			} catch (Exception ex) {
				log.error("Coulnd't create GeoRegion from configuration provided", ex);
			}
			return null;
		}
		
		private Position3D getPosition3D(double lat, double lon) {
			return new Position3D(
				new Latitude(J2735Util.convertGeoCoordinateToInt(lat)), 
				new Longitude(J2735Util.convertGeoCoordinateToInt(lon)));
		}
		
		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			if ( present ) {
				if ( record != null ) {
					sb.append("\n\t\trecord\t");
					sb.append(record);
				}
				if ( ttl != null ) {
					sb.append("\n\t\tttl\t");
					sb.append(ttl.toString().replaceFirst("value","").trim());
				}
				if ( region != null ) { 
					sb.append("\n\t\tregion\t");
					sb.append(region.toString().replaceFirst("value","").trim().replaceAll("[\t\n\r]", "").replaceAll("  ","").replaceAll(",",", "));
				}
			}
			return sb.toString();
		}
	}

}
