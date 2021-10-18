/**
 * 
 * @author mohamed
 * @author Cameron Savage | cdsavage@sfu.ca
 */

package rdt;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.CRC32;

public class RDTSegment {
	public int seqNum;
	public int ackNum;
	public int flags;
	public int checksum;
	public int rcvWin; // number of bytes the sender has to hold response
	public int length;  // number of data bytes (<= MSS)
	public byte[] data;

	public boolean ackReceived;

	private Timer timer;
	public Utility utility;
	public RDTBuffer sndBuf;
	public TimeoutHandler timeoutHandler;  // make it for every segment, 
	                                       // will be used in selective repeat
	
  // constants 
	public static final int SEQ_NUM_OFFSET = 0;
	public static final int ACK_NUM_OFFSET = 4;
	public static final int FLAGS_OFFSET = 8;
	public static final int CHECKSUM_OFFSET = 12;
	public static final int RCV_WIN_OFFSET = 16;
	public static final int LENGTH_OFFSET = 20;
	public static final int HDR_SIZE = 24; 
	public static final int FLAGS_ACK = 1;

	RDTSegment() {
		data = new byte[RDT.MSS];
		flags = 0; 
		checksum = 0;
		seqNum = 0;
		ackNum = 0;
		length = 0;
		rcvWin = 0;
		ackReceived = false;
	}
	
	public boolean containsAck() {
		return (ackNum != 0);
	}
	
	public boolean containsData() {
		return (seqNum != 0);
	}

	/**
	 * Compute checksum, return 1's complement value of lowest 8 bits
	 * @return
	 */
	public int computeChecksum() {

		// header
		int sum = (seqNum & 0x000000FF); // take lowest 8 bits
		//System.out.println("[RDTSegment] computeChecksum: add seqNum; sum = " + sum);
		sum += ackNum & 0x000000FF;
		//System.out.println("[RDTSegment] computeChecksum: add ackNum; sum = " + sum);
		sum += flags & 0x000000FF;
		//System.out.println("[RDTSegment] computeChecksum: add flags; sum = " + sum);
		sum += rcvWin & 0x000000FF;
		//System.out.println("[RDTSegment] computeChecksum: add rcvWin; sum = " + sum);
		sum += length & 0x000000FF;
		//System.out.println("[RDTSegment] computeChecksum: add length; sum = " + sum);

		// data
		for (int i = 0; i < length; i++) {
			sum += data[i] & 0x000000FF;
			//System.out.println("[RDTSegment] computeChecksum: add data[" + i + "] = " + data[i] + "; sum = " + sum);
		}

		//System.out.println("[RDTSegment] computeChecksum = " + (sum & 0x000000FF) );
		return ~sum & 0x000000FF;
	}

	/**
	 * Computes checksum and verifies equivalence to saved checksum
	 * @return
	 */
	public boolean isValid() {
		return ( checksum + (~computeChecksum() & 0x000000FF) == 0x000000FF);
	}

	/**
	 * Copies passed array into data[]
	 * @param _size must be <= RDT.MSS
	 */
	public void fillData(byte[] _data, int _size) {
		if (_size > RDT.MSS) {
			// CASE: passed array too large for segment
			System.out.printf("Error: segment cannot hold %d bytes of data\n", _size);
			// TODO add throw?
		}
		if (_size >= 0) {
			System.arraycopy(_data, 0, data, 0, _size);
		}
		length = _size;
	}

	/**
	 * Adds headers and data[] to payload in bytes
	 * @param payload
	 */
	public void makePayload(byte[] payload) {
		// add header 
		Utility.intToByte(seqNum, payload, SEQ_NUM_OFFSET);
		Utility.intToByte(ackNum, payload, ACK_NUM_OFFSET);
		Utility.intToByte(flags, payload, FLAGS_OFFSET);
		Utility.intToByte(checksum, payload, CHECKSUM_OFFSET);
		Utility.intToByte(rcvWin, payload, RCV_WIN_OFFSET);
		Utility.intToByte(length, payload, LENGTH_OFFSET);
		//add data
		for (int i=0; i<length; i++) {
			// put data after all header info
			payload[i + HDR_SIZE] = data[i];
		}
	}

	/**
	 * Begin timer after segment is sent
	 */
	public void startTimer(){
		timer = new Timer();
		timeoutHandler = new TimeoutHandler(sndBuf, utility, this);
		timer.schedule(this.timeoutHandler, RDT.RTO);
	}

	/**
	 * Stop timer if ACK received before timeout occurs
	 */
	public void stopTimer() {
		if (timer != null) {
			timer.cancel();
		}
	}

	public void printHeader() {
		System.out.println("SeqNum: " + seqNum);
		System.out.println("ackNum: " + ackNum);
		System.out.println("flags: " +  flags);
		System.out.println("checksum: " + checksum);
		System.out.println("rcvWin: " + rcvWin);
		System.out.println("length: " + length);
	}
	public void printData() {
		System.out.println("Data ... ");
		for (int i=0; i<length; i++) 
			System.out.print(data[i]);
		System.out.println(" ");
	}
	public void dump() {
		printHeader();
		printData();
	}
	
} // end RDTSegment class
