
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
	private SenderThread sndThread;

	private final Object mutEnqueueing = new Object();

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

		// start up sender thread
		Semaphore sigDoneEnqueing = new Semaphore(1, true);
		sndThread = new SenderThread(rcvBuf, sndBuf, socket, dst_ip, dst_port, sigDoneEnqueing);
		sndThread.start();
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



		// split enqueuing and sending into separate threads?
			// one keeps putting into buffer until no segments left,
			// other waits on buffer and sends a certain number of times,
			// then returns to block this level
		// what if too many segments for the buffer? will get stuck waiting
			// for an opening, but will never come because it isn't sending anything

		// enqueue segments into sndBuf
		for (RDTSegment rdtSeg:segments) {
			// TODO - modify flags for segment
			rdtSeg.seqNum = seqNum;
			rdtSeg.checksum = rdtSeg.computeChecksum();
			rdtSeg.rcvWin = rcvBuf.size;
			sndBuf.putNext(rdtSeg);
			sndBuf.dump();
			increaseSeqNum();
		}

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
		System.out.println("[RDT] waiting for rcvBuf...");
		RDTSegment seg = rcvBuf.receiveBase();
		System.out.println("[RDT] received segment.");
		int counter = 0;
		while (counter < size && counter < seg.length) {
			buf[counter] = seg.data[counter];
			counter++;
		}
		System.out.println("[RDT] received " + counter + " bytes of data.");
		return counter;
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
		System.out.println("[SenderThread] started.");
		RDTSegment seg;

		//while(semThreadKillCondition.availablePermits() == 1) {
		while(true) {
			// RUN CONDITION: if RDT.send() is still enqueueing
			// 				   AND sndBuf is not empty

			// CASE: RDT.send() hasn't sent the kill condition yet, so
			// 		it still has more data to enqueue

			seg = sndBuf.getNext();

			if (seg != null) {
				seg.printHeader();
				seg.printData();

				Utility.udp_send(seg, socket, dst_ip, dst_port);
				System.out.println("[SenderThread] sent UDP packet");
				sndBuf.dump();
			}

			// TODO schedule timeout for segment(s)
			//RDT.timer.schedule(new TimeoutHandler(sndBuf, seg), RDT.RTO);
			// TODO when timer expires, move sndBuf.next to
			//  base (GBN)

		}

	}

}

class RDTBuffer {
	public RDTSegment[] buf;
	public int size;	
	public int base; // leftmost in-flight segment
	public int next; // next segment to send
	public int nextFreeSlot;
	public Semaphore semNextFull; // next is pointing to non-null segment
	public Semaphore semFull; // #of full slots
	public Semaphore semEmpty; // #of Empty slots

	public final Object mutBaseAck = new Object(); // wait on this for base to be acknowledged
	public boolean condBaseAck = false;

	public final Object mutNextNull = new Object(); // wait on this for base to be acknowledged
	public boolean condNextNotNull = false;

	public final Object mutBufAccess = new Object(); // mutex for access to buf[]
	
	RDTBuffer (int bufSize) {
		buf = new RDTSegment[bufSize];
		for (int i=0; i<bufSize; i++)
			buf[i] = null;
		size = bufSize;
		base = nextFreeSlot = next = 0;
		semNextFull = new Semaphore(1, true);
		semFull =  new Semaphore(0, true);
		semEmpty = new Semaphore(bufSize, true);

	}

	private RDTSegment getBuf(int index) {
		return buf[index%size];
	}

	private void setBuf(int index, RDTSegment seg) {
		buf[index%size] = seg;
	}

	// Put a segment in the next available slot in the buffer
	public void putNext(RDTSegment seg) {

		synchronized (mutBufAccess) {
			try {
				semEmpty.acquire(); // wait for an empty slot
				setBuf(nextFreeSlot, seg);

				nextFreeSlot++;  // increment nextFreeSlot, since this slot is full
				semFull.release(); // increase #of full slots
			} catch(InterruptedException e) {
				System.out.println("Buffer put(): " + e);
			}

			System.out.println("[RDTBuffer] added segment.");

		}

		synchronized (mutNextNull) {
			condNextNotNull = true;
			mutNextNull.notifyAll();
		}

//		if (semNextFull.availablePermits() < 1) {
//			semNextFull.release();
//		}

	}

	/**
	 * get the next in-order segment, moves NextToSend to next window slot
	 * @return Returns an RDTSegment if there's one ready, NULL if not
	 */
	public RDTSegment getNext() {
		RDTSegment seg;

		// get segment at NEXT cursor
		synchronized (mutBufAccess) {
			seg = getBuf(next);

			if ((next > base) && (next % size == base % size)) {
				// CASE: have reached end of window, all packets are sent
				seg = null;
			}
		}

		if (seg == null) {
			// CASE: nothing to get, wait for new segment
			synchronized (mutNextNull) {
				condNextNotNull = false;
				while (!condNextNotNull) {
					try {
						mutNextNull.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}

			synchronized (mutBufAccess) {
				seg = getBuf(next);
			}
		}

		if (seg != null) {
			// CASE: next was indexing a segment, not blank space -
			//        increment next
			synchronized (mutBufAccess){
				next++;
			}

			// TODO if next == base, start timer

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
	 */
	public void ackSeqNum(RDTSegment ackSeg) {
		// scan through and ACK all matching packets
		// move base whenever base is ack'd
		//ArrayList<RDTSegment> ackdSegList = new ArrayList<>();
		RDTSegment seg;
		boolean baseAck = false;

		synchronized (mutBufAccess) {
			for (int i = base; i < next; i++) {
				seg = getBuf(i);
				if (seg.seqNum <= ackSeg.ackNum) {
					// CASE: seg should be acknowledged
					seg.ackReceived = true;
//					if (i == base) {
//						// CASE: BASE is acknowledged - signal waiting thread to shift window
//						baseAck = true;
//					}
				}
			}
		}

	}

	public RDTSegment receiveBase() {
		// wait for data in the buffer
		// TODO verify BASE is ack'd before taking it out of buffer

		RDTSegment seg = null;
		boolean baseAck = false;

		// check if base is ACK'd
		synchronized (mutBufAccess) {
			seg = getBuf(base);

			if (seg != null && seg.ackReceived) {
				baseAck = true;
				System.out.println("[RDTBuffer] Base is ACK'd");
			}

		}

		if (!baseAck) {
			// CASE: base is not ack'd, wait
			synchronized (mutBaseAck) {
				while (!condBaseAck) {
					try {
						mutBaseAck.wait();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				condBaseAck = false;
			}
		}

		// BASE is ack'd, shift window and receive base
		return shiftWindow();

	}
	/**
	 * Attempt to shift window - keep calling until null if need to shift multiple times
	 * @return ack'd segment @ base if successful, null if not
	 */
	public RDTSegment shiftWindow() {
		//ArrayList<RDTSegment> ackdSegList = new ArrayList<>();
		RDTSegment seg = null;
		boolean baseAck = false;

		// get base, verify seg is ACK'd, remove if ACK
		synchronized (mutBufAccess) {
			seg = getBuf(base);

			if (seg == null || !seg.ackReceived) {
				// CASE: window cannot be shifted - base is null or not ack'd
				return null;
			}

			// replace base with null, increment
			setBuf(base, null);
			base++;

			// TODO if next > base, start new base's timer 

		}

		// increment SemEmpty, decrement SemFull
		semEmpty.release();
		try {
			semFull.acquire();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println("[RDTBuffer] shifted window.");
		dump();

		return seg;

	}

	/**
	 * Checks the ack status of buf[base]
	 * @return TRUE if base is ack'd, FALSE if not
	 */
	public boolean checkForBaseAck() {
		boolean result = false;
		synchronized (mutBufAccess) {
			if (getBuf(base).ackReceived) {
				result = true;
			}
		}

		return result;
	}

	/**
	 * When segment at BASE is ack'd, wake up thread waiting
	 * for a segment to receive in the application layer
	 */
	public void notifyThreadOnReceiveBase() {
		synchronized (mutBaseAck) {
			condBaseAck = true;
			mutBaseAck.notifyAll();
		}
	}
	// for debugging
	public void dump() {
		System.out.println("Base: " + base + "; NextToSend: " + next + "; NextFreeSlot: " + nextFreeSlot);
//		for (int i = base; i < base + size; i++) {

		synchronized (mutBufAccess) {
			for (int i = 0; i < size; i++) {
				if (buf[i] != null) {
					if (buf[i].seqNum != 0) {
						System.out.print("[ SEG " + buf[i].seqNum + " ]");
					} else if (buf[i].ackNum != 0) {
						System.out.print("[ ACK " + buf[i].ackNum + " ]");
					}

				} else if (buf[i % size] == null) {
					System.out.print("[       ]");
				}

				if (i == base%size) {System.out.print(" <-- BASE"); }
				if (i == next %size) {System.out.print(" <-- NEXT"); }
				if (i == nextFreeSlot%size) {System.out.print(" <-- NXFREESLT"); }

				System.out.println();
			}
		}
		
	}
} // end RDTBuffer class


class ReceiverThread extends Thread {

	RDTBuffer rcvBuf, sndBuf;
	DatagramSocket socket;
	InetAddress dst_ip;
	int dst_port;

	//GBN variables
	int lastReceivedSegment = 0;

	
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

		System.out.println("[ReceiverThread] started.");

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

			System.out.print("[ReceiverThread] received packet:\n  ");
			receivedSegment.printHeader();
			receivedSegment.printData();

			if (!receivedSegment.isValid()) {
				// CASE: packet was corrupted, drop it
				System.out.println("[ReceiverThread] dropped corrupted packet.");
				receivedSegment.printHeader();
				receivedSegment.printData();
				continue;
			}

			if (receivedSegment.containsAck()) {
				// CASE: packet is an ACK; remove matching
				// 		 segments waiting for ACK from sndBuf
				System.out.println("[ReceiverThread] packet is ACK " + receivedSegment.ackNum);
				sndBuf.ackSeqNum(receivedSegment);

				if (sndBuf.checkForBaseAck()) {
					// CASE: base segment in send buffer is ACK'd - shift window
					while(true) {
						if (sndBuf.shiftWindow() == null){
							// CASE: window can't shift any further, stop
							break;
						}
					}
				}

				sndBuf.dump();

			} else if (receivedSegment.containsData()) {
				// CASE: packet is data segment, verify it's
				// 		 received in-order

				System.out.println("[ReceiverThread] received data: SEG " + receivedSegment.seqNum);

				if (receivedSegment.seqNum < lastReceivedSegment ||
						receivedSegment.seqNum >= lastReceivedSegment + 2) {
					// CASE: packet is out-of-order, drop
					System.out.println("SEG " + receivedSegment.seqNum + " out of order, dropping.");
					continue;
				}

				if (receivedSegment.seqNum == lastReceivedSegment + 1) {
					// CASE: packet is not a duplicate, place in rcvBuf
					System.out.println("SEG " + receivedSegment.seqNum + " in order, placing in rcvBuf.");
					receivedSegment.ackReceived = true; // tells the buffer it's OK to shift window
					rcvBuf.putNext(receivedSegment);
					if (rcvBuf.checkForBaseAck()) {
						// CASE: rcvBuf[base] is ACK'd, notify thread waiting
						rcvBuf.notifyThreadOnReceiveBase();
					}
					rcvBuf.dump();
					lastReceivedSegment += 1;
				}

				// CASE: packet is either duplicate or
				// 		 in-order, send ACK
				System.out.println("[ReceiverThread] sending ACK for SEG " + lastReceivedSegment);
				RDTSegment ackSegment = new RDTSegment();
				ackSegment.ackNum = lastReceivedSegment;
				ackSegment.checksum = ackSegment.computeChecksum();
				System.out.println("[ReceiverThread] checksum for ACK " + lastReceivedSegment + ": " +
						ackSegment.checksum);


				Utility.udp_send(ackSegment, socket, dst_ip, dst_port);

				//sndBuf.putNext(ackSegment);

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

