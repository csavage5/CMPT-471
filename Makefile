# compiler to use
CC=gcc

# enable debug symbols and warnings
CFLAGS= -Wall -g


all: 
	$(CC) $(CFLAGS) client.c -o client
	$(CC) $(CFLAGS) server.c -o server
	$(CC) $(CFLAGS) tunnel.c -o tunnel
	
#all: server client



#server: server.o
#$(CC) server.o -o server

#client: client.o
#$(CC) client.o -o client

#server.o: server.c
#$(CC) $(CFLAGS) server.c

#client.o: client.c
#$(CC) $(CFLAGS) client.c

clean:
	rm -rf *.o client server

