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
#define DAYTIME_PORT 3333

static char *portRemote;
static char *addrRemote;

struct addrinfo hints;

struct addrinfo* pRemoteAddrInfo;

//struct sockaddr_in servaddr;

void decodemsg(char *msg, int n);

int main(int argc, char **argv) {
    int     sockfd, n;
    char    recvline[MAXLINE + 1];
    
    if (argc <= 1 || argc == 3 || argc > 4) {
        printf("usage: client [ <tunnel IP address | hostname> <tunnel port> ] <server IP address | hostname> <server port>\n");
        exit(1);
    } else if (argc == 2) {
        addrRemote = argv[1];
        portRemote = argv[2];   
    } else if (argc == 4) {
        //addrTunnel = argv[1]
        //portTunnel = argv[2]
        addrRemote = argv[3];
        portRemote = argv[4]; 
    }
    
    
    bzero(&hints, sizeof(hints));
    hints.ai_family = AF_INET;
    hints.ai_socktype = SOCK_STREAM;
    //hints.ai_protocol = 
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
        if (fputs(recvline, stdout) == EOF) {
            printf("fputs error\n");
            exit(1);
        }
        decodemsg(recvline, n);
    }
    
    if (n < 0) {
        printf("read error\n");
        exit(1);
    }

    exit(0);
}

void decodemsg(char msg[], int n) {
    printf("%s", msg);
}