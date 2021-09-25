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

// Server-side
int listenfd, clientConnFD, n;
struct sockaddr_in servaddr;
int daytimePort;
char recvline[MAXLINE + 1];

struct sockaddr_in clientAddr;
socklen_t clientAddrLen = sizeof(clientAddr);

// static char *addrClient;
// static char *portClient;
struct addrinfo* pClientAddrInfo;


// Client-side
static char *addrServer;
static char *portServer;
struct addrinfo hints;
struct addrinfo* pServerAddrInfo;

int serverSockFD;


void InitializeServer();

void WaitForClientConnection();
void ReadServerInfoFromClient();

void ConnectToServer();
void WaitForServerMsg();
void ForwardMsgToClient();

void DecodeServerInfo(char message[], int n);
void RetrieveServerAddrInfo();
void DisplayClientToServerRequest();
void DisplayServerToClientRequest();

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

    InitializeServer();

    for ( ; ; ) {
        
        WaitForClientConnection();
        ReadServerInfoFromClient();

        ConnectToServer();
        WaitForServerMsg();
        ForwardMsgToClient();

    }

        // discover client, servername, print w/ IP

    // send connect request to server

    // accept reply from server

    // forward message to client
        // print forward info
}

void InitializeServer() {
    listenfd = socket(AF_INET, SOCK_STREAM, 0);

    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(daytimePort); /* daytime server */

    bind(listenfd, (struct sockaddr *) &servaddr, sizeof(servaddr));
    listen(listenfd, LISTENQ);
}

void WaitForClientConnection() {
    printf("\nListening...\n");
    

    clientConnFD = accept(listenfd, (struct sockaddr *) &clientAddr, &clientAddrLen);
    printf("Received connection request.\n");
}

// read initial message from client that contains server info 
// and save it into addrServer and portServer
void ReadServerInfoFromClient() {
    bzero(&recvline, MAXLINE);

    while ( (n = read(clientConnFD, recvline, MAXLINE)) > 0) {
        recvline[n] = 0;        /* null terminate */
        // if (fputs(recvline, stdout) == EOF) {
        //     printf("fputs error\n");
        //     exit(1);
        // }

        //printf("Raw message:\n%s", recvline);
    }

    printf("Received server info from client: %s.\n", recvline);

    DecodeServerInfo(recvline, n);

    //DisplayClientToServerRequest();

    // remove after testing - close after FINAL message to client
    //close(clientConnFD);
}

// extract server IP and port from client's initial message
void DecodeServerInfo(char message[], int n) {
    //printf("Decoding message...\n\n");
    
    bzero(&msg, sizeof(msg));

    char tokenString[2] = ":";

    //get server IP / hostname
    char *pToken = strtok(message, tokenString);
    addrServer = pToken;

    //get server port
    pToken = strtok(NULL, tokenString);
    portServer = pToken;

    printf("Decoded server info from client: %s:%s\n", addrServer, portServer);

}

void ConnectToServer() {
    
    RetrieveServerAddrInfo();

    if ( (serverSockFD = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        printf("socket error\n");
        exit(1);
    }

    if (connect(serverSockFD, pServerAddrInfo->ai_addr, pServerAddrInfo->ai_addrlen) < 0) {
        printf("connect error\n");
        exit(1);
    }

    DisplayClientToServerRequest();

}

void WaitForServerMsg() {
    
    bzero(&recvline, MAXLINE);

    while ( (n = read(serverSockFD, recvline, MAXLINE)) > 0) {
        recvline[n] = 0;        /* null terminate */
        // if (fputs(recvline, stdout) == EOF) {
        //     printf("fputs error\n");
        //     exit(1);
        // }

        //printf("Raw message:\n%s", recvline);
    }
    
    if (n < 0) {
        printf("read error\n");
        exit(1);
    }

    DisplayServerToClientRequest();
}

void ForwardMsgToClient() {
    printf("Forwarding: %s\n", recvline);
    
    write(clientConnFD, recvline, n);
    close(clientConnFD);
}

// fill server's addrinfo struct with info from client
void RetrieveServerAddrInfo() {
    bzero(&hints, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    
    int error = getaddrinfo(addrServer, portServer, &hints, &pServerAddrInfo);
    if (error == 1) {
        printf("Error retrieving remote address info\n");
        exit(1);
    }

    printf("Resolved server address.\n");

}

void DisplayClientToServerRequest() {
    char tempBuffer[MAXLINE];
    char tempBuffer2[MAXLINE];

    bzero(&tempBuffer, MAXLINE);
    bzero(&tempBuffer2, MAXLINE);

    getnameinfo((struct sockaddr *) &clientAddr, clientAddrLen, tempBuffer, MAXLINE, tempBuffer2, MAXLINE, 0);
    printf("Received request from client %s at port %s destined to ", tempBuffer, tempBuffer2);

    bzero(&tempBuffer, MAXLINE);
    bzero(&tempBuffer2, MAXLINE);

    getnameinfo(pServerAddrInfo->ai_addr, pServerAddrInfo->ai_addrlen, tempBuffer, MAXLINE, tempBuffer2, MAXLINE, 0);
    printf("server %s at port %s\n", tempBuffer, tempBuffer2);    
    bzero(&tempBuffer, MAXLINE);
}

void DisplayServerToClientRequest() {

    char tempBuffer[MAXLINE];
    char tempBuffer2[MAXLINE];

    bzero(&tempBuffer, MAXLINE);
    bzero(&tempBuffer2, MAXLINE);

    getnameinfo(pServerAddrInfo->ai_addr, pServerAddrInfo->ai_addrlen, tempBuffer, MAXLINE, tempBuffer2, MAXLINE, 0);
    printf("Received request from server %s at port %s destined to ", tempBuffer, tempBuffer2);

    bzero(&tempBuffer, MAXLINE);
    bzero(&tempBuffer2, MAXLINE);

    getnameinfo((struct sockaddr *) &clientAddr, clientAddrLen, tempBuffer, MAXLINE, tempBuffer2, MAXLINE, 0);
    printf("client %s at port %s\n", tempBuffer, tempBuffer2);
}