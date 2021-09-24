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
static char *addrRemote;
static char *portRemote;


struct addrinfo hints;
struct addrinfo* pRemoteAddrInfo;

char tempBuffer[MAXLINE];

//struct sockaddr_in servaddr;

void decodemsg(char *msg, int n);
void displayMessage();

int main(int argc, char **argv) {
    int     sockfd, n;
    char    recvline[MAXLINE + 1];
    


    if (argc <= 1 || argc == 4 || argc >= 5) {
        printf("usage: client [ <tunnel IP address | hostname> <tunnel port> ] <server IP address | hostname> <server port>\n");
        exit(1);
    } else if (argc == 3) {
        addrRemote = argv[1];
        portRemote = argv[2];   
    } else if (argc == 5) {
        addrTunnel = argv[1];
        portTunnel = argv[2];
        addrRemote = argv[3];
        portRemote = argv[4]; 
    }
    
    
    bzero(&hints, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    //hints.ai_protocol = MSGLEN
    //hints.ai_flags = 

    int error = getaddrinfo(addrRemote, portRemote, &hints, &pRemoteAddrInfo);
    if (error == 1) {
        printf("Error retrieving remote address info\n");
        exit(1);
    }


    if ( (sockfd = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        printf("socket error\n");
        exit(1);
    }

    // not needed, getaddrinfo does this and saves it in pRemoteAddrInfo
    // bzero(&servaddr, sizeof(servaddr));
    // servaddr.sin_family = AF_INET;
    // servaddr.sin_port = htons(DAYTIME_PORT);  /* daytime server */
    // if (inet_pton(AF_INET, argv[1], &servaddr.sin_addr) <= 0) {
    //     printf("inet_pton error for %s\n", argv[1]);
    //     exit(1);
    // }

    if (connect(sockfd, pRemoteAddrInfo->ai_addr, pRemoteAddrInfo->ai_addrlen) < 0) {
        printf("connect error\n");
        exit(1);
    }

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

    decodemsg(recvline, n);

    exit(0);
}

void decodemsg(char message[], int n) {
    printf("Decoding message...\n");
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

    displayMessage();

}

void displayMessage() {
    
    getnameinfo(pRemoteAddrInfo->ai_addr, pRemoteAddrInfo->ai_addrlen, tempBuffer, MAXLINE, NULL, 0, 0);
    printf("Server name: %s\n", tempBuffer);
    bzero(&tempBuffer, MAXLINE);

    inet_ntop(AF_INET, &((struct sockaddr_in *) pRemoteAddrInfo->ai_addr)->sin_addr.s_addr, tempBuffer, pRemoteAddrInfo->ai_addrlen);
    printf("IP Address: %s\n", tempBuffer); 
    
    printf("Time: %s", msg.currtime);
    printf("Who:\n%s", msg.payload);
}