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

#define MAXLINE     4096    /* max text line length */
#define LISTENQ     1024    /* 2nd argument to listen() */

struct message{
    int addrlen, timelen, msglen;
    char addr[MAXLINE];
    char currtime[MAXLINE];
    char payload[MAXLINE];
}msg;

char outgoingBuffer[MAXLINE];
char tempBuffer[MAXLINE];
char tempBuffer2[MAXLINE];


// server
int     listenfd, connfd;
struct sockaddr_in servaddr;
time_t ticks;

int daytimePort;

// connecting peer
struct sockaddr_in clientAddr;
socklen_t clientAddrLen = sizeof(clientAddr);

void getCurrTime();
void getPayload();
void encodeMessage();

void resetBuffers();

int main(int argc, char **argv) {
    
    if (argc != 2) {
        printf("usage: server <server port>\n");
        exit(1);
    } 
    
    if ( (daytimePort = atoi(argv[1])) == 0) {
        printf("Error: either did not input a number for <server port> or entered invalid port 0\n");
        printf("usage: server <server port>\n");
    }

    // initialization
    resetBuffers();


    // set up server
    listenfd = socket(AF_INET, SOCK_STREAM, 0);

    bzero(&servaddr, sizeof(servaddr));
    servaddr.sin_family = AF_INET;
    servaddr.sin_addr.s_addr = htonl(INADDR_ANY);
    servaddr.sin_port = htons(daytimePort); /* daytime server */

    bind(listenfd, (struct sockaddr *) &servaddr, sizeof(servaddr));
    listen(listenfd, LISTENQ);
    printf("Listening...\n\n");

    for ( ; ; ) {
        //wait for incoming connection
        connfd = accept(listenfd, (struct sockaddr *) &clientAddr, &clientAddrLen);

        //TODO: print the name and IP of the client who sent the message
        printf("Received message from:\n");
        inet_ntop(AF_INET, &clientAddr.sin_addr.s_addr, tempBuffer, clientAddrLen);        
        printf("   IP Address: %s\n", tempBuffer);
        bzero(&tempBuffer, sizeof(tempBuffer));
        
        getnameinfo((struct sockaddr *) &clientAddr, clientAddrLen, tempBuffer, MAXLINE, tempBuffer2, MAXLINE, 0);
        printf("   Hostname: %s\n", tempBuffer);
        // get WHO info to send to client
        
        getCurrTime();
        getPayload();

        encodeMessage();

        write(connfd, outgoingBuffer, strlen(outgoingBuffer));
        close(connfd);

        printf("Sent response.\n\n");
        resetBuffers();
    }
}

void getCurrTime() {
    ticks = time(NULL);
    msg.timelen = snprintf(msg.currtime, MAXLINE, "%.24s\r\n", ctime(&ticks));
    //printf("CurrTime buffer: %s\n", msg.currtime);

}

void getPayload() {
    FILE *whofd = popen("who", "r");
    if (whofd == NULL) {
        printf("Error executing WHO command\n");
        exit(1);
    }
    //printf("Opened popen stream\n");

    while ( fgets(msg.payload, MAXLINE, whofd) != NULL);

    //printf("Playload buffer:\n%s\n", msg.payload);
    msg.msglen = strlen(msg.payload);
    pclose(whofd);
    //printf("Closed popen stream\n");
}

void encodeMessage() {
    //strcat(outgoingBuffer, (msg.addrlen)
    int offset = 0;
    msg.addrlen = 0;

    offset += sprintf(outgoingBuffer, "%d", msg.addrlen);
    offset += sprintf(outgoingBuffer + offset, "%c", '=');

    offset += sprintf(outgoingBuffer + offset, "%d", msg.timelen);
    offset += sprintf(outgoingBuffer + offset, "%c", '=');

    offset += sprintf(outgoingBuffer + offset, "%d", msg.msglen);
    offset += sprintf(outgoingBuffer + offset, "%c", '=');

    strncpy(outgoingBuffer + offset, msg.currtime, msg.timelen);
    offset += msg.timelen;

    strncpy(outgoingBuffer + offset, msg.payload, msg.msglen);

    //printf("Encoded message:\n%s\n", outgoingBuffer);
}

void resetBuffers() {

    bzero(&msg, sizeof(msg));
    bzero(&outgoingBuffer, sizeof(outgoingBuffer));
    bzero(&tempBuffer, sizeof(tempBuffer));
    bzero(&tempBuffer2, sizeof(tempBuffer2));

    msg.addrlen = 0;
    msg.msglen = 0;
    msg.timelen = 0;

}