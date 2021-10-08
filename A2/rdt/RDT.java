
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

	/**
	 * Interface for main() to call. Enqueues messages to send.
	 * @param data Total data to enqueue
	 * @param size Size of data array
	 * @return total bytes sent
	 */
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


		// TODO start up sender thread
		Semaphore sem = new Semaphore(1, true);
		SenderThread sndThread = new SenderThread(rcvBuf, sndBuf, socket, dst_ip, dst_port, sem);
		// split enqueuing and sending into separate threads?
			// one keeps putting into buffer until no segments left,
		// other waits on buffer and sends a certain number of times,
			// then returns to block this level
		// what if too many segments for the buffer? will get stuck waiting
			// for an opening, but will never come because it isn't sending anything

		// put each segment into sndBuf
		for (RDTSegment rdtSeg:segments) {
			// TODO - modify flags for segment
			rdtSeg.seqNum = seqNum;
			rdtSeg.checksum = rdtSeg.computeChecksum();
			rdtSeg.rcvWin = rcvBuf.size;
			sndBuf.putNext(rdtSeg);
			increaseSeqNum();
		}


		// block here until all sent
		return size;
	}

	/**
	 * Increments RDT.seqNum and returns it
	 * @return
	 */
	public void increaseSeqNum() {
		//seqNum += bytes;
		seqNum += 1;
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

class SenderThread extends Thread {

	RDTBuffer rcvBuf, sndBuf;
	DatagramSocket socket;
	InetAddress dst_ip;
	int dst_port;
	Semaphore semThreadKillCondition;

	SenderThread(RDTBuffer _rcv_buf, RDTBuffer _snd_buf, DatagramSocket _socket,
				 InetAddress _dst_ip, int _dst_port, Semaphore sem) {

		rcvBuf = _rcv_buf;
		sndBuf = _snd_buf;
		socket = _socket;
		dst_ip = _dst_ip;
		dst_port = _dst_port;
		semThreadKillCondition = sem;
	}

	public void run() {
		RDTSegment seg;

		while(semThreadKillCondition.availablePermits() == 1) {
			// CASE: RDT.send() hasn't sent the kill condition yet, so
			// 		it still has more data to enqueue

			seg = sndBuf.getNextToSend();

			if (seg != null) {
				Utility.udp_send(seg, socket, dst_ip, dst_port);
			}

			// TODO schedule timeout for segment(s)

		}

	}

}

class RDTBuffer {
	public RDTSegment[] buf;
	public int size;	
	public int base; // leftmost in-flight segment
	public int nextToSend; // next segment to send
	public int nextFreeSlot;
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

	private RDTSegment getBuf(int index) {
		return buf[index%size];
	}

	private void putBuf(int index, RDTSegment seg) {
		buf[index%size] = seg;
	}

	// Put a segment in the next available slot in the buffer
	public void putNext(RDTSegment seg) {
		try {
			semEmpty.acquire(); // wait for an empty slot 
			semMutex.acquire(); // wait for buffer access
				buf[nextFreeSlot % size] = seg;
				nextFreeSlot++;  // increment next, since this slot is full
			semMutex.release();
			semFull.release(); // increase #of full slots
		} catch(InterruptedException e) {
			System.out.println("Buffer put(): " + e);
		}
	}

	/**
	 * get the next in-order segment, moves NextToSend to next window slot
	 * @return Returns an RDTSegment if there's one ready, NULL if not
	 */
	public RDTSegment getNextToSend() {
		RDTSegment seg = null;

		// TODO case when nextToSend has sent last segment in window and is == to base (b/c of %)
		if (nextToSend > base && nextToSend%size == base % size) {
			// CASE: have reached end of window, all packets are sent
			return null;
		}

		try {
			semMutex.acquire(); // wait for buffer access

			seg = buf[nextToSend % size]; // get segment at NEXT cursor

			if (seg != null) {
				// CASE: nextToSend was indexing a segment, not blank space -
				//        increment nextToSend
				nextToSend++;
			} else {
				// CASE: nextToSend pointing to empty space
				System.out.println("Buffer: can't get seg to send, none left");
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

	/**
	 * Acknowledge matching packet(s) to this ackNumber
	 * @param ackSeg Received Acknowledgement segment
	 * @return ArrayList of in-order segments that were removed from base
	 */
	public ArrayList<RDTSegment> ackSeqNum(RDTSegment ackSeg) {
		// scan through and ACK all matching packets
		// move base whenever base is ack'd
		ArrayList<RDTSegment> ackdSegList = new ArrayList<>();
		RDTSegment seg;

		try {
			semMutex.acquire();
			for (int i = base; i < nextToSend; i++) {

				seg = buf[i%size];
				if (seg.seqNum <= ackSeg.ackNum) {
					// CASE: seg should be acknowledged
					seg.ackReceived = true;
					if (i == base) {
						System.out.println("found base");
						// CASE: seg is acknowledged AND base - shift window
						shiftWindow();
						ackdSegList.add(seg);
					}

				}
			}

			semMutex.release();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return ackdSegList;

	}

	/**
	 * Iterate base - MUST have already acquired mutex for buf[]
	 * @return ack'd segment @ base if successful
	 */
	public RDTSegment shiftWindow() {
		RDTSegment seg = null;

		//verify base is ack'd
		if (buf[base].ackReceived == false) {
			System.out.println("RDTBuffer: cannot shift base - base is unack'd");
			return null;
		}

		//remove seg @ base
		seg = buf[base%size];
		buf[base%size] = null;
		base++;

		// increment SemEmpty, decrement SemFull
		semEmpty.release();
		try {
			semFull.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		return seg;

	}
	
	// for debugging
	public void dump() {
		System.out.println("Dumping the receiver buffer ...");
		// Complete, if you want to

		System.out.println("Base: " + base + "; NextToSend: " + nextToSend + "; NextFreeSlot: " + nextFreeSlot);
//		for (int i = base; i < base + size; i++) {
		for (int i = 0; i < size; i++) {
			if (buf[i] != null) {
				System.out.print("[ SEG " + buf[i].seqNum + " ]");
			} else if (buf[i % size] == null) {
				System.out.print("[       ]");
			}

			if (i == base%size) {System.out.print(" <-- BASE"); }
			if (i == nextToSend%size) {System.out.print(" <-- NXTOSND"); }
			if (i == nextFreeSlot%size) {System.out.print(" <-- NXFREESLT"); }

			System.out.println();
		}

		
	}
} // end RDTBuffer class


class ReceiverThread extends Thread {

	RDTBuffer rcvBuf, sndBuf;
	DatagramSocket socket;
	InetAddress dst_ip;
	int dst_port;

	//GBN variables
	int lastReceivedSegment = 1;

	
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

			if (!receivedSegment.isValid()) {
				// CASE: packet was corrupted, drop it
				continue;
			}

			if (receivedSegment.containsAck()) {
				// CASE: packet is an ACK; remove matching
				// 		 segments waiting for ACK from sndBuf
				sndBuf.ackSeqNum(receivedSegment);

			} else if (receivedSegment.containsData()) {
				// CASE: packet is data segment, verify it's
				// 		 received in-order

				if (receivedSegment.seqNum < lastReceivedSegment ||
						receivedSegment.seqNum >= lastReceivedSegment + 2) {
					// CASE: packet is out-of-order, drop
					continue;
				}

				if (receivedSegment.seqNum == lastReceivedSegment + 1) {
					// CASE: packet is not a duplicate, place in rcvBuf
					rcvBuf.putNext(receivedSegment);
					lastReceivedSegment += 1;
				}

				// CASE: packet is either duplicate or
				// 		 in-order, send ACK
				RDTSegment ackSegment = new RDTSegment();
				ackSegment.ackNum = receivedSegment.seqNum;
				sndBuf.putNext(ackSegment);

			}

		}

	}
	
	
	//

	/**
	 * Create a segment from received bytes
	 * @param seg
	 * @param payload
	 */
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

