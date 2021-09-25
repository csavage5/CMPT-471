#include <netinet/in.h>
#include <time.h>
#include <strings.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <stdlib.h>

#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netdb.h>

#define MAXLINE 4096
#define LISTENQ 1024

struct message{
    int addrlen, timelen, msglen;
    char addr[MAXLINE];
    char currtime[MAXLINE];
    char payload[MAXLINE];
}msg;

// Server
int listenfd, connfd;
struct sockaddr_in servaddr;
int daytimePort;

struct sockaddr_in clientAddr;
socklen_t clientAddrLen = sizeof(clientAddr);

// client



int main(int argc, char **argv) {
    // get port

    if (argc != 2) {
        printf("usage: server <server port>\n");
        exit(1);
    } 
    
    if ( (daytimePort = atoi(argv[1])) == 0) {
        printf("Error: either did not input a number for <server port> or entered invalid port 0\n");
        printf("usage: server <server port>\n");
    }


    for ( ; ; ) {
        
        // wait for client
        waitForClient();


    }
    

    // get server ip / port from client

        // discover client, servername, print w/ IP

    // send connect request to server

    // accept reply from server

    // forward message to client
        // print forward info
}

void waitForClient() {


    listenfd = socket(AF_INET, SOCK_STREAM, 0);

    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(daytimePort); /* daytime server */

    bind(listenfd, (struct sockaddr *) &servaddr, sizeof(servaddr));
    listen(listenfd, LISTENQ);
    printf("Listening...\n\n");

    connfd = accept(listenfd, (struct sockaddr *) &clientAddr, &clientAddrLen);
}

void sendMessageToClient() {
    char outgoingBuffer[MAXLINE];
    write(connfd, outgoingBuffer, strlen(outgoingBuffer));
    close(connfd);
}