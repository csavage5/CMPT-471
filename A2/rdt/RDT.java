
/**
 * @author mohamed
 *
 */
package rdt;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RDT {

	public static final int MSS = 100; // Max segment size in bytes
	public static final int RTO = 500; // Retransmission Timeout in msec
	public static final int ERROR = -1;
	public static final int MAX_BUF_SIZE = 3;  
	public static final int GBN = 1;   // Go back N protocol
	public static final int SR = 2;    // Selective Repeat
	public static final int protocol = GBN;

	public int seqNum = 1;
	
	public static double lossRate = 0.0;
	public static Random random = new Random(); 
	public static Timer timer = new Timer();	
	
	private DatagramSocket socket; 
	private InetAddress dst_ip;
	private int dst_port;
	private int local_port; 
	
	private RDTBuffer sndBuf;
	private RDTBuffer rcvBuf;
	
	private ReceiverThread rcvThread;

	/**
	 * Constructor - for default buffer sizes
	 * @param dst_hostname_
	 * @param dst_port_
	 * @param local_port_
	 */
	RDT (String dst_hostname_, int dst_port_, int local_port_) {
		local_port = local_port_;
		dst_port = dst_port_; 
		try {
			 socket = new DatagramSocket(local_port);
			 dst_ip = InetAddress.getByName(dst_hostname_);
		 } catch (IOException e) {
			 System.out.println("RDT constructor: " + e);
		 }
		sndBuf = new RDTBuffer(MAX_BUF_SIZE);
		if (protocol == GBN)
			rcvBuf = new RDTBuffer(1);
		else 
			rcvBuf = new RDTBuffer(MAX_BUF_SIZE);
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();
	}

	/**
	 * Constructor - for custom buffer sizes
	 * @param dst_hostname_
	 * @param dst_port_
	 * @param local_port_
	 * @param sndBufSize
	 * @param rcvBufSize
	 */
	RDT (String dst_hostname_, int dst_port_, int local_port_, int sndBufSize, int rcvBufSize) {
		local_port = local_port_;
		dst_port = dst_port_;
		 try {
			 socket = new DatagramSocket(local_port);
			 dst_ip = InetAddress.getByName(dst_hostname_);
		 } catch (IOException e) {
			 System.out.println("RDT constructor: " + e);
		 }
		sndBuf = new RDTBuffer(sndBufSize);
		if (protocol == GBN)
			// CASE: Go-Back-N takes in received packets in order,
			//       doesn't need a buffer
			rcvBuf = new RDTBuffer(1);
		else 
			rcvBuf = new RDTBuffer(rcvBufSize);
		
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();
	}
	
	public static void setLossRate(double rate) {lossRate = rate;}
	
	// called by app
	// returns total number of sent bytes  
	public int send(byte[] data, int size) {
		
		//TODO ****** complete
		
		// divide data into segments
		int index = 0;
		ArrayList<RDTSegment> segments = new ArrayList<>();

		while (size - index > MSS) {
			segments.add(new RDTSegment());
			segments.get(segments.size()-1).fillData(Arrays.copyOfRange(data, index, index + MSS - 1), MSS);

			index += MSS;
		}

		if (index < size - 1) {
			// CASE: have left over data < MSS that needs to be sent
			segments.add(new RDTSegment());
			segments.get(segments.size()-1).fillData(Arrays.copyOfRange(data, index, size - 1), size - 1 - index);
			index += size - index;
		}

		// put each segment into sndBuf
		for (RDTSegment rdtSeg:segments) {
			// TODO - modify flags for segment
			rdtSeg.seqNum = seqNum;
			rdtSeg.checksum = rdtSeg.computeChecksum();
			rdtSeg.rcvWin = rcvBuf.size;
			sndBuf.putNext(rdtSeg);
			increaseSeqNum(rdtSeg.length);
		}


		// TODO send using udp_send()
		//sndBuf.sendAllNewSegments();
		//Utility.udp_send(segment, socket, dst_ip, dst_port);
		// mark as sent in buffer

		// TODO schedule timeout for segment(s)
			
		return size;
	}

	/**
	 * Increments RDT.seqNum and returns it
	 * @return
	 */
	public void increaseSeqNum(int bytes) {
		seqNum += bytes;
	}
	
	// called by app
	// receive one segment at a time
	// returns number of bytes copied in buf
	public int receive (byte[] buf, int size) {
		//TODO *****  complete
		// pop a segment from the receive buffer?
		//socket.receive();

		return 0;   // fix
	}
	
	// called by app
	public void close() {
		// TODO OPTIONAL: close the connection gracefully
		// you can use TCP-style connection termination process
	}
	
}  // end RDT class 


class RDTBuffer {
	public RDTSegment[] buf;
	public int size;	
	public int base; // leftmost in-flight segment
	public int nextToSend;
	public int nextFreeSlot; // next segment to send
	public Semaphore semMutex; // for mutual exclusion (mutex for buf)
	public Semaphore semFull; // #of full slots
	public Semaphore semEmpty; // #of Empty slots
	
	RDTBuffer (int bufSize) {
		buf = new RDTSegment[bufSize];
		for (int i=0; i<bufSize; i++)
			buf[i] = null;
		size = bufSize;
		base = nextFreeSlot = nextToSend = 0;
		semMutex = new Semaphore(1, true);
		semFull =  new Semaphore(0, true);
		semEmpty = new Semaphore(bufSize, true);
	}

	// Put a segment in the next available slot in the buffer
	public void putNext(RDTSegment seg) {
		try {
			semEmpty.acquire(); // wait for an empty slot 
			semMutex.acquire(); // wait for buffer access
				buf[nextFreeSlot % size] = seg; //save seg in slot nextAvailableSlot is at
				nextFreeSlot++;  // increment next, since this slot is full
			semMutex.release();
			semFull.release(); // increase #of full slots
		} catch(InterruptedException e) {
			System.out.println("Buffer put(): " + e);
		}
	}

	/**
	 * get the next in-order segment and increment NEXT cursor
	 * @return Returns an RDTSegment if there's one queued, NULL if not
	 */
	public RDTSegment getNextToSend() {
		RDTSegment seg = null;

		try {
			semMutex.acquire(); // wait for buffer access
			seg = buf[nextToSend % size]; // get segment at NEXT cursor

			if (seg != null) {
				// CASE: nextToSend was indexing a segment, not blank space
				nextToSend++;
			}

			semMutex.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return seg;
	}
	
	// Put a segment in the *right* slot based on seg.seqNum
	// used by receiver in Selective Repeat
	public void putSeqNum (RDTSegment seg) {
		// TODO ***** complete


	}

	public void ackSeqNum() {
		// TODO - if ack == base: remove from buffer, increment full/empty semaphores
	}
	
	// for debugging
	public void dump() {
		System.out.println("Dumping the receiver buffer ...");
		// Complete, if you want to

		System.out.println("Base: " + base + "; NextToSend: " + nextToSend + "; NextFreeSlot: " + nextFreeSlot);
		for (int i = base; i < base + size; i++) {
			if (buf[i] != null) {
				System.out.print("[ SEG ]");
			} else if (buf[i % size] == null) {
				System.out.print("[     ]");
			}

			if (i == base) {System.out.print(" <-- BASE"); }
			if (i == nextToSend) {System.out.print(" <-- NXTOSND"); }
			if (i == nextFreeSlot) {System.out.print(" <-- NXFREESLT"); }

			System.out.println();
		}

		
	}
} // end RDTBuffer class



class ReceiverThread extends Thread {

	RDTBuffer rcvBuf, sndBuf;
	DatagramSocket socket;
	InetAddress dst_ip;
	int dst_port;
	
	ReceiverThread (RDTBuffer rcv_buf, RDTBuffer snd_buf, DatagramSocket s, 
			InetAddress dst_ip_, int dst_port_) {
		rcvBuf = rcv_buf;
		sndBuf = snd_buf;
		socket = s;
		dst_ip = dst_ip_;
		dst_port = dst_port_;
	}

	public void run() {
		
		// TODO *** complete
		// Essentially:  while(cond==true){  // may loop for ever if you will not implement RDT::close()  
		//                socket.receive(pkt)
		//                seg = make a segment from the pkt
		//                verify checksum of seg
		//	              if seg contains ACK, process it potentially removing segments from sndBuf
		//                if seg contains data, put the data in rcvBuf and do any necessary 
		//                             stuff (e.g, send ACK)
		//

		byte[] packetBuffer = new byte[RDT.MSS];
		DatagramPacket packet = new DatagramPacket(packetBuffer, RDT.MSS);

		while(true) {
			try {
				socket.receive(packet);
			} catch (IOException e) {
				e.printStackTrace();
			}

			//make segment from packet
			RDTSegment receivedSegment = new RDTSegment();
			makeSegment(receivedSegment, packet.getData());

			//verify receivedSegment checksum
			receivedSegment.isValid();

			if (receivedSegment.containsAck()) {
				// TODO remove segments waiting for ACK from sndBuf
			} else if (receivedSegment.containsData()) {
				// TODO put data in rcvBuf
				//rcvBuf.
				// TODO send ack
			}

		}

	}
	
	
	//	 create a segment from received bytes
	void makeSegment(RDTSegment seg, byte[] payload) {
	
		seg.seqNum = Utility.byteToInt(payload, RDTSegment.SEQ_NUM_OFFSET);
		seg.ackNum = Utility.byteToInt(payload, RDTSegment.ACK_NUM_OFFSET);
		seg.flags  = Utility.byteToInt(payload, RDTSegment.FLAGS_OFFSET);
		seg.checksum = Utility.byteToInt(payload, RDTSegment.CHECKSUM_OFFSET);
		seg.rcvWin = Utility.byteToInt(payload, RDTSegment.RCV_WIN_OFFSET);
		seg.length = Utility.byteToInt(payload, RDTSegment.LENGTH_OFFSET);
		//Note: Unlike C/C++, Java does not support explicit use of pointers! 
		// we have to make another copy of the data
		// This is not efficient in protocol implementation
		for (int i=0; i< seg.length; i++) {
			seg.data[i] = payload[i + RDTSegment.HDR_SIZE];
		}
	}
	
} // end ReceiverThread class

