/**
 * @author mhefeeda
 *
 */

package rdt;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.TimerTask;

class TimeoutHandler extends TimerTask {
	RDTBuffer sndBuf;
	RDTSegment seg;
	Utility utility;

	DatagramSocket socket;
	InetAddress ip;
	int port;
	
//	TimeoutHandler (RDTBuffer sndBuf_, RDTSegment s, DatagramSocket sock,
//			InetAddress ip_addr, int p) {
//		sndBuf = sndBuf_;
//		seg = s;
//		socket = sock;
//		ip = ip_addr;
//		port = p;
//	}

	TimeoutHandler (RDTBuffer sndBuf_, Utility _utility, RDTSegment _seg) {
		sndBuf = sndBuf_;
		utility = _utility;
		seg = _seg;
	}
	
	public void run() {
		
		System.out.println(System.currentTimeMillis()+ ":Timeout for seg: " + seg.seqNum);
		System.out.flush();
		
		// complete 
		switch(RDT.protocol){
			case RDT.GBN:
				// move sndBuf.next to base, restart timer
				sndBuf.GoBackN();
				System.out.println("[TimeoutHandler] timer expired on SEG " +
						seg.seqNum + " ACK " + seg.ackNum +
						" - re-setting sndBuf.next to base");
				break;
			case RDT.SR:
				// re-send packet with Utility.udpsend, restart timer
				utility.udp_send(seg);
				System.out.println("[TimeoutHandler] timer expired on SEG " +
						seg.seqNum + " ACK " + seg.ackNum +
						" - re-sending.");
				break;
			default:
				System.out.println("Error in TimeoutHandler:run(): unknown protocol");
		}
		
	}
} // end TimeoutHandler class

