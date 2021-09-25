#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <stdlib.h>
#include <strings.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <netdb.h>


#define MAXLINE     4096    /* max text line length */

struct message{
    int addrlen, timelen, msglen;
    char addr[MAXLINE];
    char currtime[MAXLINE];
    char payload[MAXLINE];
}msg;

static char *addrTunnel;
static char *portTunnel;
static char *addrServer;
static char *portServer;

struct addrinfo hints;
struct addrinfo* pServerAddrInfo;
struct addrinfo* pTunnelAddrInfo;
int  sockfd, n;
char recvline[MAXLINE + 1];

char tempBuffer[MAXLINE];

//struct sockaddr_in servaddr;

void ConnectToServer();
void ConnectToTunnel();

void WaitForServerMessage();
void DecodeMsg(char *msg, int n);

void DisplayServerInfo();
void DisplayMsgStruct();
void DisplayTunnelInfo();

int main(int argc, char **argv) {

    if (argc <= 1 || argc == 4 || argc > 5) {
        printf("usage: client [ <tunnel IP address | hostname> <tunnel port> ] <server IP address | hostname> <server port>\n");
        exit(1);
    } else if (argc == 3) {
        addrServer = argv[1];
        portServer = argv[2]; 
        
        ConnectToServer();
        WaitForServerMessage();

        DisplayServerInfo();
        DisplayMsgStruct();

    } else if (argc == 5) {
        addrTunnel = argv[1];
        portTunnel = argv[2];
        addrServer = argv[3];
        portServer = argv[4]; 

        ConnectToTunnel();
        //WaitForServerMessage();

        // DisplayServerInfo();
        // DisplayMsgStruct();
        // DisplayTunnelInfo();
    }
    

    exit(0);
}

void ConnectToServer() {
    bzero(&hints, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    //hints.ai_protocol = MSGLEN
    //hints.ai_flags = 

    int error = getaddrinfo(addrServer, portServer, &hints, &pServerAddrInfo);
    if (error == 1) {
        printf("Error retrieving remote address info\n");
        exit(1);
    }

    if ( (sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        printf("socket error\n");
        exit(1);
    }

    if (connect(sockfd, pServerAddrInfo->ai_addr, pServerAddrInfo->ai_addrlen) < 0) {
        printf("connect error\n");
        exit(1);
    }

}

void ConnectToTunnel() {
    bzero(&hints, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;

    int error = getaddrinfo(addrTunnel, portTunnel, &hints, &pTunnelAddrInfo);
    if (error == 1) {
        printf("Error retrieving remote address info\n");
        exit(1);
    }

    if ( (sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        printf("socket error\n");
        exit(1);
    }

    if (connect(sockfd, pTunnelAddrInfo->ai_addr, pTunnelAddrInfo->ai_addrlen) < 0) {
        printf("connect error\n");
        exit(1);
    }
    
    printf("Connected to tunnel.\n");

    char outgoingBuffer[MAXLINE];
    bzero(&outgoingBuffer, MAXLINE);
    strcat(outgoingBuffer, addrServer);
    strcat(outgoingBuffer, ":");
    strcat(outgoingBuffer, portServer);

    if ( ( n = write(sockfd, outgoingBuffer, strlen(outgoingBuffer))) == -1) {
        printf("Error - write failed\n");
        exit(1);
    }
    printf("Sent server info to tunnel (%dB).\n", n);

}

//blocking - waits for server to send a message; also calls DecodeMsg afterwards
void WaitForServerMessage() {
    printf("Waiting for server response...\n");
    
    bzero(&recvline, MAXLINE);

    while ( (n = read(sockfd, recvline, MAXLINE)) > 0) {
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

    printf("Received message from server.");

    DecodeMsg(recvline, n);
}

void DecodeMsg(char message[], int n) {
    printf("Decoding message...\n\n");
    //printf("Raw message:\n%s\n", message);
    
    bzero(&msg, sizeof(msg));

    char tokenString[2] = "=";

    //get addrlen
    char *pToken = strtok(message, tokenString);
    msg.addrlen = atoi(pToken);

    //get timelen
    pToken = strtok(NULL, tokenString);
    msg.timelen = atoi(pToken);
    
    //get msglen
    pToken = strtok(NULL, tokenString);
    msg.msglen = atoi(pToken);

    // get ADDR
    pToken = strtok(NULL, tokenString);
    
    if (msg.addrlen > 0) {
        strncpy(msg.addr, pToken, msg.addrlen);
    }

    //get CURRTIME
    if (msg.currtime > 0) {
        strncpy(msg.currtime, pToken+msg.addrlen, msg.timelen);
    }

    //get PAYLOAD
    if (msg.msglen > 0) {
        strncpy(msg.payload, pToken + msg.addrlen + msg.timelen, msg.msglen);
    }

}

void DisplayServerInfo() {
    getnameinfo(pServerAddrInfo->ai_addr, pServerAddrInfo->ai_addrlen, tempBuffer, MAXLINE, NULL, 0, 0);
    printf("Server name: %s\n", tempBuffer);
    bzero(&tempBuffer, MAXLINE);

    inet_ntop(AF_INET, &((struct sockaddr_in *) pServerAddrInfo->ai_addr)->sin_addr.s_addr, tempBuffer, pServerAddrInfo->ai_addrlen);
    printf("IP Address: %s\n", tempBuffer); 
}

void DisplayMsgStruct() {
    
    printf("Time: %s", msg.currtime);
    printf("Who:\n%s", msg.payload);
}

void DisplayTunnelInfo() {
    bzero(&tempBuffer, MAXLINE);
    getnameinfo(pTunnelAddrInfo->ai_addr, pTunnelAddrInfo->ai_addrlen, tempBuffer, MAXLINE, NULL, 0, 0);
    
    printf("Via Tunnel: %s\n", tempBuffer);
    bzero(&tempBuffer, MAXLINE);

    printf("IP Address: %s\n", addrTunnel);
    printf("Port #: %s\n", portTunnel);
}