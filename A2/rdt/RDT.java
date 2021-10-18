
/**
 * @author mohamed
 * @author Cameron Savage | cdsavage@sfu.ca
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

	private Utility utility;

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
		this(dst_hostname_, dst_port_, local_port_, MAX_BUF_SIZE, 1);
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

		utility = new Utility(socket, dst_ip, dst_port);

		// start up receiver thread
		rcvThread = new ReceiverThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
		rcvThread.start();

		// start up sender thread
		sndThread = new SenderThread(rcvBuf, sndBuf, socket, dst_ip, dst_port);
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
		RDTSegment seg;
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
			seg = new RDTSegment();
			// give segment instance of utility and sendBuf to give to interal timeoutHandler
			seg.utility = utility;
			seg.sndBuf = sndBuf;

			segments.add(seg);
			segments.get(segments.size()-1).fillData(Arrays.copyOfRange(data, index, size - 1), size - 1 - index);
			index += size - index;
		}

		// enqueue segments into sndBuf
		for (RDTSegment rdtSeg:segments) {
			// TODO - modify flags for segment
			rdtSeg.seqNum = seqNum;
			rdtSeg.rcvWin = rcvBuf.size;
			rdtSeg.checksum = rdtSeg.computeChecksum();
			sndBuf.putNext(rdtSeg);
			sndBuf.dump();
			increaseSeqNum();
		}

		// TODO block until senderthread waits for new data / sndBuf is empty
		// TODO kill senderthread

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

	SenderThread(RDTBuffer _rcv_buf, RDTBuffer _snd_buf, DatagramSocket _socket,
				 InetAddress _dst_ip, int _dst_port) {

		rcvBuf = _rcv_buf;
		sndBuf = _snd_buf;
		socket = _socket;
		dst_ip = _dst_ip;
		dst_port = _dst_port;
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
				//seg.printHeader();
				//seg.printData();

				Utility.udp_send(seg, socket, dst_ip, dst_port);
				System.out.println("[SenderThread] sent packet SEQ " + seg.seqNum +
						" ACK " + seg.ackNum);
				//sndBuf.dump();
			}


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

	public final Object mutBaseFull = new Object(); // wait on this for base to be acknowledged
	public boolean condBaseFull = false;

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

		try {
			// wait for an empty slot
			semEmpty.acquire();
		} catch(InterruptedException e) {
			System.out.println("Buffer put(): " + e);
		}

		synchronized (mutBufAccess) {
			setBuf(nextFreeSlot, seg);

			nextFreeSlot++;  // increment nextFreeSlot, since this slot is full
			semFull.release(); // increase #of full slots

			System.out.println("[RDTBuffer] added segment.");
		}

		synchronized (mutNextNull) {
			// notify a waiting thread that NEXT points to
			// a not-null segment
			condNextNotNull = true;
			mutNextNull.notifyAll();
		}

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
				if (next == base && RDT.protocol == RDT.GBN || RDT.protocol == RDT.SR) {
					// CASE: GBN: seg is base, start timer
					//		 SR: start timer for every segment
					seg.startTimer();
				}

				next++;
			}

		}

		return seg;
	}

	/**
	 * [SR] Put a segment in the *right* slot based on seg.seqNum
	 * For rcvBuf
	 * @param seg
	 * @return TRUE if successful / duplicate \ already accepted, FALSE if outside of window (SEQ too large)
	 */
	public boolean putSeqNum (RDTSegment seg) {
		boolean result = true;
		synchronized (mutBufAccess) {

			if (seg.seqNum-1 > base + (size - 1)) {
				// CASE: window can't accept packet, SEQ too large for window
				result = false;
				System.out.println("[RDTBuffer] [SR] Can't add packet to rcvBuf, SEQ # outside of window");

			} else if(seg.seqNum-1 < base) {
				//CASE: packet has already been accepted by main thread, re-send ACK
				result = true;
			} else if (getBuf(seg.seqNum-1) == null) {
				// CASE: buffer slot is empty, put in
				setBuf(seg.seqNum-1, seg);

				// for rcvBuf - all segments should be ack'd so shift_window always
				// works when receive() calls it
				getBuf(seg.seqNum-1).ackReceived = true;
				semFull.release();

				System.out.println("[RDTBuffer] [SR] added packet to buffer.");
			} else if (getBuf(seg.seqNum-1) != null) {
				System.out.println("[RDTBuffer] [SR] can't add packet to rcvBuf, slot occupied.");
			}
		}

		return result;

		// seg.seqNum-1 must be between [base, base + size - 1] (seq starts at 1, base/next start at 0)
		// getBuf[seg.SeqNum-1] == null
		// caller must also call check for baseACK - if it's at base,
		// notify receiver to shift window and receive base
	}

	/**
	 * [GBN] Reset NEXT to BASE
	 * called from TimeoutHandler when timer expires
	 */
	public void GoBackN() {
		synchronized (mutBufAccess) {
			RDTSegment baseSeg = getBuf(base);

			if (baseSeg != null) {
				next = base;

				// notify any waiting threads that next points to a
				// not-null segment
				synchronized (mutNextNull) {
					condNextNotNull = true;
					mutNextNull.notifyAll();
				}

			}

		}

		System.out.println("[RDTBuffer] GBN: moved NEXT back to BASE.");
	}

	/**
	 * [GBN] Acknowledge cumulative matching packet(s) to this ackNumber
	 * @param ackSeg Received Acknowledgement segment
	 */
	public void ackSeqNumGBN(RDTSegment ackSeg) {
		// scan through and ACK all matching packets
		// move base whenever base is ack'd
		//ArrayList<RDTSegment> ackdSegList = new ArrayList<>();
		RDTSegment seg;
		boolean baseAck = false;

		synchronized (mutBufAccess) {
			for (int i = base; i < next; i++) {
				seg = getBuf(i);

				if (seg != null && seg.seqNum <= ackSeg.ackNum) {
					// CASE: seg should be acknowledged
					seg.ackReceived = true;
					seg.stopTimer();
				}
			}
		}

	}

	/**
	 * [GBN] Acknowledge individual matching packet(s) to this ackNumber
	 * @param ackSeg Received Acknowledgement segment
	 */
	public void ackSeqNumSR(RDTSegment ackSeg) {
		// scan through and ACK all matching packets
		// move base whenever base is ack'd
		//ArrayList<RDTSegment> ackdSegList = new ArrayList<>();
		RDTSegment seg;
		boolean baseAck = false;

		synchronized (mutBufAccess) {
			for (int i = base; i < next; i++) {
				seg = getBuf(i);

				if (seg != null && seg.seqNum == ackSeg.ackNum) {
					// CASE: seg should be acknowledged
					seg.ackReceived = true;
					seg.stopTimer();
				}
			}
		}

	}

	/**
	 * Wait for base of rcvBuf to be eligible to be received
	 * @return
	 */
	public RDTSegment receiveBase() {

		RDTSegment seg = null;
		boolean baseNull = true;

		// check if base is occupied
		synchronized (mutBufAccess) {
			seg = getBuf(base);

			if (seg != null) {
				baseNull = false;
				System.out.println("[RDTBuffer] Base is ACK'd");
			}
		}

		if (baseNull) {
			// CASE: base is not full, wait
			synchronized (mutBaseFull) {
				while (!condBaseFull) {
					try {
						mutBaseFull.wait();

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				condBaseFull = false;
			}
		}

		// BASE is full, shift window and receive base
		return shiftWindow();
	}
	/**
	 * Attempt to shift window - keep calling until null if multiple shifts are necessary
	 * @return ack'd segment @ base if successful, null if not
	 */
	public RDTSegment shiftWindow() {
		dump();

		RDTSegment seg = null;

		// get base, verify seg is ACK'd, remove if ACK
		synchronized (mutBufAccess) {
			seg = getBuf(base);

			if (seg == null || !seg.ackReceived) {
				// CASE: window cannot be shifted - base is null or not ack'd
				return null;
			}

			// replace base with null, increment
			setBuf(base, null);

			// stop timer on base
			seg.stopTimer();

			if (base == next) {
				next++;
			}

			base++;

			if (getBuf(base) != null) {

				notifyThreadOnValidBase();

				if (next > base && RDT.protocol == RDT.GBN) {
					 // CASE: new base has already been sent,
					 // start timer on new base (GBN only)
					 getBuf(base).startTimer();
				 }
			} else {
				synchronized (mutBaseFull) {
					condBaseFull = false;
				}
			}

			// increment SemEmpty, decrement SemFull
			semEmpty.release();
			try {
				semFull.acquire();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

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
			RDTSegment seg = getBuf(base);
			if (seg != null && seg.ackReceived) {
				result = true;
			}
		}

		return result;
	}

	public boolean checkForBaseFull() {
		boolean result = false;
		synchronized (mutBufAccess) {
			RDTSegment seg = getBuf(base);
			if (seg != null) {
				result = true;
			}
		}

		return result;
	}

	/**
	 * When segment at BASE is not null, wake up thread waiting
	 * for a segment to receive in the application layer
	 */
	public void notifyThreadOnValidBase() {
		synchronized (mutBufAccess) {
			RDTSegment seg = getBuf(base);
			if (seg != null) {
				synchronized (mutBaseFull) {
					condBaseFull = true;
					mutBaseFull.notifyAll();
					System.out.println("[RDTBuffer] notifying main thread");
					System.out.flush();
				}
			}
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
						System.out.print("[ SEG " + buf[i].seqNum + " A:");

						if (buf[i].ackReceived) {
							System.out.print("Y ]");
						} else {
							System.out.print("N ]");
						}

					} else if (buf[i].ackNum != 0) {
						System.out.print("[ ACK " + buf[i].ackNum + " ]");
					}

				} else if (buf[i % size] == null) {
					System.out.print("[           ]");
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
		switch (RDT.protocol) {
			case RDT.GBN:
				run_GBN();
				break;

			case RDT.SR:
				run_SR();
				break;

			default:
				System.out.println("Error in ReceiverThread.run(): unknown protocol");
		}
	}


	private void run_GBN() {

		// Essentially:  while(cond==true){  // may loop forever if you don't implement RDT::close()
		//                socket.receive(pkt)
		//                seg = make a segment from the pkt
		//                verify checksum of seg
		//	              if seg contains ACK, process it potentially removing segments from sndBuf
		//                if seg contains data, put the data in rcvBuf and do any necessary 
		//                             stuff (e.g, send ACK)
		//

		System.out.println("[ReceiverThread] GBN protocol started.");

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

			//System.out.print("[ReceiverThread] received packet:\n  ");
			//receivedSegment.dump();

			if (!receivedSegment.isValid()) {
				// CASE: packet was corrupted, drop it
				System.out.println("[ReceiverThread] dropped corrupted packet.");
				continue;
			}

			if (receivedSegment.containsAck()) {
				// CASE: packet is an ACK; remove matching
				// 		 segments waiting for ACK from sndBuf
				System.out.println("[ReceiverThread] received ACK packet ACK " + receivedSegment.ackNum);
				sndBuf.ackSeqNumGBN(receivedSegment);
				sndBuf.dump();

				if (sndBuf.checkForBaseAck()) {
					// CASE: base segment in sndBuf is ACK'd - shift SEND window
					while(true) {
						if (sndBuf.shiftWindow() == null){
							// CASE: send window can't shift any further, stop
							break;
						}
					}
				}

				sndBuf.dump();

			} else if (receivedSegment.containsData()) {
				// CASE: packet is data segment, verify it's
				// 		 received in-order

				System.out.println("[ReceiverThread] received data packet SEG " + receivedSegment.seqNum);

				if (receivedSegment.seqNum < lastReceivedSegment ||
						receivedSegment.seqNum >= lastReceivedSegment + 2) {
					// CASE: packet is out-of-order, drop
					System.out.println("[ReceiverThread] SEG " + receivedSegment.seqNum + " out of order, dropping.");
					continue;
				}

				if (receivedSegment.seqNum == lastReceivedSegment + 1) {
					// CASE: packet is not a duplicate, place in rcvBuf
					System.out.println("[ReceiverThread] SEG " + receivedSegment.seqNum + " in order, placing in rcvBuf.");
					receivedSegment.ackReceived = true; // tells the buffer it's OK to shift window
					rcvBuf.putNext(receivedSegment);
					rcvBuf.dump();
//					if (rcvBuf.checkForBaseFull()) {
//						// CASE: rcvBuf[base] is full, notify main thread waiting
//						rcvBuf.notifyThreadOnValidBase();
//					}
					rcvBuf.notifyThreadOnValidBase();
					rcvBuf.dump();
					lastReceivedSegment += 1;
				}

				// CASE: packet is either duplicate or
				// 		 in-order, send ACK
				System.out.println("[ReceiverThread] sending ACK for SEG " + lastReceivedSegment);
				RDTSegment ackSegment = new RDTSegment();
				ackSegment.ackNum = lastReceivedSegment;
				ackSegment.checksum = ackSegment.computeChecksum();
				//System.out.println("[ReceiverThread] checksum for ACK " + lastReceivedSegment + ": " +
						//ackSegment.checksum);

				Utility.udp_send(ackSegment, socket, dst_ip, dst_port);
				sndBuf.dump();

			}

		}

	}


	private void run_SR() {
		System.out.println("[ReceiverThread] SR protocol started.");

		byte[] packetBuffer = new byte[RDT.MSS];
		DatagramPacket packet = new DatagramPacket(packetBuffer, RDT.MSS);

		while (true) {
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
				System.out.println("[ReceiverThread] dropped corrupted packet.");
				continue;
			}

			if (receivedSegment.containsAck()) {
				// CASE: packet is an ACK; remove matching
				// 		 segment waiting for ACK from sndBuf
				System.out.println("[ReceiverThread] received ACK packet ACK " + receivedSegment.ackNum);
				sndBuf.ackSeqNumSR(receivedSegment);
				sndBuf.dump();

				if (sndBuf.checkForBaseAck()) {
					// CASE: base segment in sndBuf is ACK'd - shift SEND window
					while(true) {
						if (sndBuf.shiftWindow() == null){
							// CASE: send window can't shift any further, stop
							break;
						}
					}
				}

				sndBuf.dump();

			} else if (receivedSegment.containsData()) {
				// CASE: packet is data segment, put in rcvBuf based on seq #

				System.out.println("[ReceiverThread] received data packet SEG " + receivedSegment.seqNum);

				if (!rcvBuf.putSeqNum(receivedSegment)) {
					// CASE: packet is outside of window (after window)
					System.out.println("[ReceiverThread] SEG " + receivedSegment.seqNum + " is " +
							"outside of rcvBuf window, dropping.");
				} else {
					// CASE: packet is either duplicate (within or before window) or new, send ACK

					receivedSegment.ackReceived = true; // tells the buffer it's OK to shift window

//					if (rcvBuf.checkForBaseFull()) {
//						// CASE: rcvBuf[base] is full, notify thread waiting
//						rcvBuf.notifyThreadOnValidBase();
//					}

					rcvBuf.notifyThreadOnValidBase();

					System.out.println("[ReceiverThread] sending ACK for SEG " + receivedSegment.seqNum);
					RDTSegment ackSegment = new RDTSegment();
					ackSegment.ackNum = receivedSegment.seqNum;
					ackSegment.checksum = ackSegment.computeChecksum();

					Utility.udp_send(ackSegment, socket, dst_ip, dst_port);
					rcvBuf.dump();

				}

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

