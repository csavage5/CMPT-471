/**
 * 
 * @author mohamed
 *
 */

package rdt;

import java.io.*;
import java.net.*;
import java.util.*;

public class RDTSegment {
	public int seqNum;
	public int ackNum;
	public int flags;
	public int checksum; 
	public int rcvWin; // number of bytes the sender has to hold response
	public int length;  // number of data bytes (<= MSS)
	public byte[] data;

	public boolean ackReceived;
	
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
	 * Compute checksum, return 1's complement value
	 * @return
	 */
	public int computeChecksum() {
		int sum = seqNum;
		sum += ackNum;
		sum += flags;
		sum += rcvWin;
		sum += length;

		for (int i=0; i<length; i++) {
			sum += data[i];
		}

		// return 1's complement of sum
		return 0xFFFFFFF ^ sum;
	}

	/**
	 * Computes checksum and verifies equivalence to saved checksum
	 * @return
	 */
	public boolean isValid() {
		// XOR 1's complement result to convert back to non-flipped, then add
		// 1's complement checksum value
		return ( (computeChecksum() ^ 0xFFFFFFF)  + checksum == 0xFFFFFFF);
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

		data = _data;
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
