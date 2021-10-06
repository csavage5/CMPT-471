/**
 * @author mohamed
 *
 */

package rdt;

import java.io.*;
import java.net.*;
import java.util.*;

public class TestClient {

	/**
	 * 
	 */
	public TestClient() {

	}


	public static void TestFeature(){
		// test checksum
		RDTSegment seg = new RDTSegment();
		seg.seqNum = 1;

		byte[] data = new byte[10];
		for (int i=0; i<10; i++)
			data[i] = 1;
		seg.fillData(data, 10);

		RDTSegment seg2 = new RDTSegment();
		seg2.seqNum = 2;
		for (int i=0; i<10; i++)
			data[i] = 1;
		seg2.fillData(data, 10);

		RDTSegment seg3 = new RDTSegment();
		seg3.seqNum = 3;
		for (int i=0; i<10; i++)
			data[i] = 1;
		seg3.fillData(data, 10);

		RDTSegment seg4 = new RDTSegment();
		seg4.seqNum = 4;
		for (int i=0; i<10; i++)
			data[i] = 1;
		seg4.fillData(data, 10);

		RDTSegment seg5 = new RDTSegment();
		seg5.seqNum = 5;
		for (int i=0; i<10; i++)
			data[i] = 1;
		seg5.fillData(data, 10);

		RDTBuffer sndBuf = new RDTBuffer(4);
		System.out.println("Filling buffer:");
		sndBuf.dump();
		sndBuf.putNext(seg);
		sndBuf.putNext(seg2);
		sndBuf.putNext(seg3);
		sndBuf.putNext(seg4);
		sndBuf.dump();

		System.out.println("\nSending packet 1:");
		sndBuf.getNextToSend();
		sndBuf.dump();

		System.out.println("\nSending packet 2:");
		sndBuf.getNextToSend();
		sndBuf.dump();

		System.out.println("\nSending packet 3:");
		sndBuf.getNextToSend();
		sndBuf.dump();

		System.out.println("\nSending packet 4:");
		sndBuf.getNextToSend();
		sndBuf.dump();

		System.out.println("\nSending packet 5 - should fail:");
		sndBuf.getNextToSend();
		sndBuf.dump();

		System.out.println("\nACK 3 packets");
		RDTSegment ack = new RDTSegment();
		ack.ackNum = 3;
		ArrayList<RDTSegment> ALACK = sndBuf.ackSeqNum(ack);
		sndBuf.dump();
		System.out.println("\n" + ALACK.toString());

		System.out.println("\nAdd packet 5 - should work:");
		sndBuf.putNext(seg5);
		sndBuf.dump();
//		seg.checksum = seg.computeChecksum();
//		System.out.println("Checksum: " + seg.checksum);
//		System.out.println("Checksum result: " + seg.isValid());
	}

	public static void main(String[] args) {

		TestFeature();
		/*
		if (args.length != 3) {
	         System.out.println("Required arguments: dst_hostname dst_port local_port");
	         return;
	      }
		String hostname = args[0];
		int dst_port = Integer.parseInt(args[1]);
		int local_port = Integer.parseInt(args[2]);

		RDT rdt = new RDT(hostname, dst_port, local_port, 1, 3);
		RDT.setLossRate(0.4);

		 byte[] buf = new byte[RDT.MSS];
		 byte[] data = new byte[10];
		 for (int i=0; i<10; i++)
			 data[i] = 0;
		 rdt.send(data, 10);

		 for (int i=0; i<10; i++)
			 data[i] = 1;
		 rdt.send(data, 10);

		 for (int i=0; i<10; i++)
			 data[i] = 2;
		 rdt.send(data, 10);

		 for (int i=0; i<10; i++)
			 data[i] = 3;
		 rdt.send(data, 10);

		 for (int i=0; i<10; i++)
			 data[i] = 4;
		 rdt.send(data, 10);
	 
	     
	     System.out.println(System.currentTimeMillis() + ":Client has sent all data " );
	     System.out.flush();
	     
	     rdt.receive(buf, RDT.MSS);
	     rdt.close();
	     System.out.println("Client is done " );

		 */
	}



}
