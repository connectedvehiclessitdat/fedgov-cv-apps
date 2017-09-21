package gov.usdot.cv.apps.sender;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Send a file or a list of files from a directory on the local disk 
 * to a remote server over UDP. This application was written to overcome
 * the limitation of netcat on ubuntu. The utility, netcat, splits a file
 * over 1024 bytes and send each chunk of data to the SDC. We want to
 * send the entire file as one udp packet to the SDC.  
 */
public class LocalUDPSender {
	private String host;
	private int port;
	private String path;
	
	private LocalUDPSender(
		String host, 
		int port, 
		String path) {
		this.host = host;
		this.port = port;
		this.path = path;
	}
	
	private void send() throws IOException {
		InetAddress address = InetAddress.getByName(this.host);
		
		File [] files = null;
		
		File pathFile = new File(this.path);
		if (pathFile.isDirectory()) {
			files = pathFile.listFiles();
		} else if (pathFile.isFile()) {
			files = new File[1];
			files[0] = pathFile;
		}
		
		if (files == null) return;
		
		for (File file : files) {
			FileInputStream fis = null;
			DatagramSocket sock = null;
			
			try {
				int len = (int) file.length();
				byte[] buff = new byte[len];
				fis = new FileInputStream(file);
				fis.read(buff);

				DatagramPacket packet = new DatagramPacket(buff, len);
				packet.setLength(len);
			
				sock = new DatagramSocket();
				sock.connect(address, this.port);
				sock.send(packet);
			} finally {
				if (sock != null) {
					try { sock.close(); sock = null; } catch (Exception ignore) {}
				}
				if (fis != null) {
					try { fis.close(); fis = null; } catch (Exception ignore) {}
				}
			}
		}
	}
	
	public static void main(String [] args) throws IOException {
		if (args.length != 3) {
			System.out.println("Usage: java LocalUDPSender <send-host> <send-port> [<filepath>|<dirpath>]");
			System.exit(1);
		}
		
		LocalUDPSender sender = new LocalUDPSender(
			args[0], 
			Integer.parseInt(args[1]), 
			args[2]);
		sender.send();
	}

}