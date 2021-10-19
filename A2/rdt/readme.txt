Compiling code:
- Working directory should be one level above the unzipped rdt directory
- Compile .java files in the RDT directory, putting the output in new folder "out":
    javac -d out rdt/*.java

Running Code:
- Run TestClient:
    java -cp out rdt.TestClient localhost dst_port local_port

- Run TestServer:
    java -cp out rdt.TestServer localhost dst_port local_port

Changing protocol
    - Change the value of RDT.protocol

Bugs / issues:
    - RDT.close() is not implemented, so Client and Server must be manually killed with Ctrl-C

Reason for being late:
    - I found a bug at around 4:30, couldn't fix it in time for the deadline.

