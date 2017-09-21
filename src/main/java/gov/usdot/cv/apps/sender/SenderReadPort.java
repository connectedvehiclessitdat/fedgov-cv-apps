package gov.usdot.cv.apps.sender;

import gov.usdot.cv.apps.sender.SenderConfig.ContentType;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.util.Arrays;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;

public class SenderReadPort extends SenderReader {
	
	final private int MAX_PACKET_SIZE = 65535;
	
	private static final Logger log = Logger.getLogger(SenderReadPort.class);

	public SenderReadPort(SenderConfig config) {
		super(config);
	}

	@Override
	public void run() {
		DatagramSocket socket = null;
		try {
			socket = new DatagramSocket(config.source.port);
			DatagramPacket packet = null;
			while ( true ) {
				packet = new DatagramPacket(new byte[MAX_PACKET_SIZE], MAX_PACKET_SIZE);
				socket.receive(packet);
				put(packet);
			}
		} catch ( SocketException ex ) {
			log.error(String.format("Socket exception receiving packet on port %d", config.source.port), ex);
		} catch (IOException ex) {
			log.error(String.format("Error receiving packet on port %d", config.source.port), ex);
		} finally {
			if ( socket != null && !socket.isClosed() ) {
				socket.close();
				socket = null;
			}
		}
	}
	
	private void put(DatagramPacket packet) {
		final byte[] data = packet.getData();
		final int length = packet.getLength();	
		
		if ( data != null && length > 0 ) {
			final int offset = packet.getOffset();
			byte[] packetData = Arrays.copyOfRange(data, offset, length);
			
			if ( config.source.contentType == ContentType.UPER) {
				put(packetData);
			} else {
				String messages = new String(packetData);
				if ( !StringUtils.isBlank(messages) ) {
					for ( String message : messages.trim().split("[\r\n]+") )
						if ( !StringUtils.isBlank(message) )
							put( message.trim() );
				}
			}

		}
	}

}
