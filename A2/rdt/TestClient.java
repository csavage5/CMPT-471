/**
 * @author mohamed
 * @author Cameron Savage | cdsavage@sfu.ca
 */

package rdt;

import java.util.*;

public class TestClient {

	/**
	 * 
	 */
	public TestClient() {

	}


	public static void main(String[] args) {


		if (args.length != 3) {
	         System.out.println("Required arguments: dst_hostname dst_port local_port");
	         return;
		}

		String hostname = args[0];
		int dst_port = Integer.parseInt(args[1]);
		int local_port = Integer.parseInt(args[2]);

		RDT rdt = new RDT(hostname, dst_port, local_port, 3, 3);
		RDT.setLossRate(0.4);

		// send data
		byte[] buf = new byte[RDT.MSS];
		int dataSize = 10;
		byte dataFill = 1;
		byte[] data = new byte[dataSize];

		for (int num = 1; num <= 5; num++ ) {

			for (int i=0; i < dataSize; i++) {
				data[i] = dataFill;
			}

			dataFill++;

			rdt.send(data, dataSize);
		}


	     System.out.println(System.currentTimeMillis() + ":Client has sent all data " );
	     System.out.flush();
	     
	     //rdt.receive(buf, RDT.MSS);

	     rdt.close();
	     System.out.println("Client is done - Ctrl-C to exit." );

	}



}
